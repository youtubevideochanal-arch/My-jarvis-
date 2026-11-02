package com.myagent.app

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** App ke andar project folders (multi-file code, import ki hui repos). Sab app-private storage me. */
object Workspace {
    val SKIP_DIRS = setOf(".git", "node_modules", "build", ".gradle", "dist", "vendor", "__pycache__", ".idea")

    private fun root(): File {
        val r = File(Hub.app.filesDir, "workspace")
        r.mkdirs()
        return r
    }

    fun safe(n: String): String = n.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "project" }.take(60)

    fun projectDir(name: String): File {
        val d = File(root(), safe(name))
        d.mkdirs()
        return d
    }

    fun projects(): List<String> =
        (root().listFiles() ?: emptyArray()).filter { it.isDirectory }.map { it.name }.sorted()

    fun delete(name: String) { projectDir(name).deleteRecursively() }

    private fun resolve(project: String, path: String): File? {
        val base = projectDir(project).canonicalFile
        val f = File(base, path).canonicalFile
        return if (f.path.startsWith(base.path + File.separator)) f else null
    }

    fun write(project: String, path: String, content: String): String {
        if (content.length > 400000) throw Exception("file bahut badi (400KB se kam rakho)")
        val f = resolve(project, path) ?: throw Exception("path project ke bahar hai")
        f.parentFile?.mkdirs()
        f.writeText(content)
        return safe(project) + "/" + path
    }

    fun read(project: String, path: String, max: Int = 6000): String {
        val f = resolve(project, path) ?: return "ERROR: path project ke bahar hai"
        if (!f.isFile) return "ERROR: file nahi mili: $path"
        return try { f.readText().take(max) } catch (e: Exception) { "ERROR: " + (e.message ?: "") }
    }

    fun list(project: String): String {
        val d = projectDir(project)
        val sb = StringBuilder()
        var n = 0
        d.walkTopDown().onEnter { it == d || it.name !in SKIP_DIRS }.filter { it.isFile }.forEach { f ->
            if (n < 200) sb.append(f.relativeTo(d).path).append(" (").append(f.length()).append("B)\n")
            n++
        }
        if (n == 0) return "(project khaali hai)"
        if (n > 200) sb.append("... aur ").append(n - 200).append(" files\n")
        return sb.toString()
    }

    fun unzipTo(project: String, input: InputStream): Int {
        val base = projectDir(project).canonicalFile
        var count = 0
        var total = 0L
        ZipInputStream(input).use { zis ->
            var e: ZipEntry? = zis.nextEntry
            while (e != null && total <= 60000000L && count < 6000) {
                if (!e.isDirectory) {
                    val out = File(base, e.name).canonicalFile
                    if (out.path.startsWith(base.path + File.separator)) {
                        out.parentFile?.mkdirs()
                        var size = 0L
                        out.outputStream().use { os ->
                            val buf = ByteArray(8192)
                            while (true) {
                                val r = zis.read(buf)
                                if (r < 0) break
                                size += r
                                total += r
                                if (size > 5000000L || total > 60000000L) break
                                os.write(buf, 0, r)
                            }
                        }
                        count++
                    }
                }
                e = zis.nextEntry
            }
        }
        flatten(base)
        return count
    }

    /** GitHub zips ek upar ka folder (repo-main/) rakhte hain - use hata do. */
    private fun flatten(base: File) {
        val kids = base.listFiles() ?: return
        if (kids.size == 1 && kids[0].isDirectory) {
            val inner = kids[0]
            for (c in inner.listFiles() ?: emptyArray()) c.renameTo(File(base, c.name))
            inner.delete()
        }
    }

    fun importRepo(url: String): String {
        val m = Regex("github\\.com/([^/\\s]+)/([^/\\s#?]+)").find(url)
            ?: return "ERROR: GitHub repo URL do (github.com/user/repo)"
        val user = m.groupValues[1]
        val repo = m.groupValues[2].removeSuffix(".git")
        var bytes: ByteArray? = null
        for (br in listOf("main", "master")) {
            try {
                bytes = WebTools.download("https://github.com/$user/$repo/archive/refs/heads/$br.zip", 40000000)
                break
            } catch (e: Exception) { }
        }
        if (bytes == null) return "ERROR: repo nahi mila ya public nahi hai (main/master branch)"
        projectDir(repo).deleteRecursively()
        val n = unzipTo(repo, ByteArrayInputStream(bytes))
        return "imported '" + safe(repo) + "' ($n files)\n" + list(repo)
    }

    fun exportZip(project: String): String {
        val dir = projectDir(project)
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zos ->
            dir.walkTopDown().onEnter { it == dir || it.name !in SKIP_DIRS }.filter { it.isFile }.forEach { f ->
                if (bos.size() < 40000000) {
                    zos.putNextEntry(ZipEntry(f.relativeTo(dir).path))
                    f.inputStream().use { ins -> ins.copyTo(zos) }
                    zos.closeEntry()
                }
            }
        }
        return FileStore.saveBytes(Hub.app, "Projects", safe(project) + ".zip", bos.toByteArray())
    }
}
