package com.myagent.app

import org.json.JSONObject

/**
 * Playbooks = "skills": kaam karne ke tareeke jo agent zarurat padne par hi padhta hai (tokens bachte hain).
 * 3 jagah se aate hain: app ke andar (built-in), Settings me diye gaye URL (remote), aur skill_import se laaye hue.
 * Naye playbook bina APK update ke aa sakte hain - sirf apni GitHub repo me JSON/markdown daalo.
 */
object Playbooks {
    private val builtin: Map<String, String> = mapOf(
        "trading" to """
TRADING (user ki installed trading app + analysis):
1. list_apps{filter:"trade"} ya user ki Settings me chuni trading apps se app ka naam lo; open_app{app}.
2. screen dekho: watchlist/positions/balance padho. Symbol ko Yahoo/Binance symbol me badlo (NSE: RELIANCE.NS, index ^NSEI/^NSEBANK, crypto BTCUSDT, forex EURUSD=X).
3. market_data{symbol,interval} (RSI/MACD/SMA/EMA/Bollinger/ATR + candle patterns + support/resistance) 2 timeframe par (jaise 15m aur 1d). Fir trade_plan{symbol,interval,side}.
4. User ko REPORT do: trend, candle pattern, indicators ka tally, S/R, entry/SL/target, qty (risk%), aur kya sahmat nahi hai. Seedhi bhasha, chhota.
5. Order tabhi lagao jab user ne trade saaf bola ho (symbol, buy/sell, qty). Order screen me qty + SL + target bharo, aakhri submit/confirm click par "risky":true lagao aur "why" me poora trade likho. Order ke baad positions screen se verify karo aur report karo.
6. Kabhi paisa-guarantee mat do. Risk 1-2% se zyada nahi. Real paisa lagane se pehle user ko paper/chhota trade suggest karo. Nayi cheez (option/margin) me user se pucho.
7. Nigaraani: watch{symbol,op,level,every_min} (bina token ke alert). Roz summary ke liye schedule{}.""".trimIndent(),

        "social" to """
SOCIAL MEDIA (Instagram/YouTube/Facebook/X/LinkedIn/Telegram):
- Content banana = text kaam (idea, script, caption, hashtags): pehle use likho, save_file{folder:"Social"} me rakho. Phir app khol kar UI se post karo.
- Posting flow: open_app -> screen padho -> '+'/Create -> media chuno (user ne jo file batayi) -> caption type -> aakhri 'Post/Share/Upload' par risky:true (public hone wali cheez) -> verify.
- Comments/DM reply: pehle padho, user ko sunao, bhejne se pehle risky:true.
- Spam, fake reviews/likes, doosre ka naam/photo, bots se follow-unfollow NAHI. Platform ke rules todne wala kaam mana kar do.
- Analytics: app ke Insights screen padh kar 3-4 line me batao: kya chala, kya nahi, agla kya banana hai.
Detail ke liye playbook: instagram | youtube | linkedin.""".trimIndent(),

        "instagram" to """
INSTAGRAM CONTENT (reels/carousel/post):
- Pehle 3 sec ka HOOK (sawal, chauka dene wala fact, ya "galti jo sab karte hain"). Reel 15-30 sec, ek hi idea.
- Reel script: Hook (0-3s) -> Problem (3-8s) -> Solution/Demo (8-22s) -> CTA (last 3s: "follow karo / save karo").
- Carousel: slide 1 = vaada, slide 2-8 = ek-ek point, aakhri = CTA. Har slide par 12 shabd se kam.
- Caption: pehli line hook, 3-5 line value, CTA. Hashtags 5-10 niche wale (bahut bade nahi).
- Posting samay: apne Insights ke active hours; hafte me 3-5 post, consistent.
- Output: save_file{folder:"Social",name:"ig_<topic>.txt"} me script+caption+hashtags+shot list; phir user ki haan par app me post.""".trimIndent(),

        "youtube" to """
YOUTUBE CONTENT (video + channel):
- Idea: web_search se trending/ gap dhundo; 5 title options (curiosity + faayda, 60 char se kam); thumbnail text 3-4 shabd.
- Script: Hook 15s -> Promise -> 3-5 sections -> CTA (like/subscribe/next video). Shorts: 40-55s, vertical, loop-able ending.
- Description: pehli 2 line me keyword + faayda, timestamps, links. Tags 8-12.
- Upload flow (app): open_app YouTube -> '+' -> Upload/Short -> file -> title/description -> visibility (pehle Private/Unlisted) -> Publish par risky:true.
- Analytics: CTR, avg view duration, retention drop point; agla video us point ko sudhaar kar.""".trimIndent(),

        "linkedin" to """
LINKEDIN CONTENT:
- Post: pehli line hook (data/anubhav), 5-8 chhoti lines, ek seekh, ek sawal se end. 1200-1500 character.
- Mix: 40% seekh, 30% apni kahani/project, 20% opinion, 10% promotion.
- Profile: headline me "kya karta hoon + kiske liye", About me 3 parts (problem, kaam, CTA).
- Outreach: 3 line ka personalised message, bina bechne ke pehle value. Mass-spam nahi.
- Post karne se pehle risky:true.""".trimIndent(),

        "film" to """
FILM-MAKING + LAUNCH (script se release tak):
1. Idea -> logline (1 line) -> 3-act structure -> characters.
2. Script (scene by scene) -> shot list (shot, angle, location, kitna time) -> storyboard text -> save_file{folder:"Film"}.
3. Production: user ki shooting/clip ya AI tools (find_api{query:"video generation"/"text to speech"/"image"} se free/APIs dhundo); voice-over ke liye TTS.
4. Edit plan: clip order, cuts, music/SFX, subtitles, color. Phone ke editor (CapCut/InShot/VN) ko open_app se kholo aur UI step-by-step chalao; export 1080p.
5. LAUNCH: title + thumbnail concept + description + tags + teaser (15s) + posting schedule (schedule{}) + share plan (YouTube, Reels, WhatsApp). Pehle Unlisted/Private; user ki haan par Public (risky:true).
6. Copyright: sirf apna/licensed music-footage. Kisi asli insaan ka naam/face/awaaz bina ijazat ke nahi.""".trimIndent(),

        "earn" to """
INTERNET SE KAMAI (API ka kharch nikalne ke liye) - IMANDAAR RAASTE:
- Koi guaranteed kamai nahi hoti; fake/get-rich-quick/spam/scam/fake reviews/hacking NAHI.
- Ideas jo agent kar sakta hai: (1) freelance kaam: content writing, translation, code/bug-fix, website/app security audit (sirf client ki likhit anumati se), data entry, social media captions; (2) apni digital cheezein: templates, e-book, scripts, mini web-tools (Gumroad/Payhip jaisa); (3) content: YouTube/Shorts/Instagram + affiliate/ads; (4) local dukaan ke liye menu/poster/WhatsApp catalog; (5) trading kamai ka tareeka NAHI hai - risk hai.
- Flow: web_search se 5 opportunities + zarooratein -> user ko shortlist do -> proposal/portfolio/gig description likho (save_file{folder:"Earn"}) -> user ki haan par apply/post (risky:true).
- KYC, bank, payout, tax user khud karega. Agent paisa nahi bhejta, kuch kharidta nahi.
- Hisaab: ledger_add{kind,amount,note} (API kharch ko note me 'api' likho); ledger_report se dikhao ki kamai API se zyada hai ya nahi.""".trimIndent(),

        "code" to """
CODING:
- Chhota/medium: run_code (js/html app ke andar chalta hai; python/node/bash Termux ho to). Error padho -> fix -> dobara chalao jab tak exit=0 aur output sahi.
- Web app: ws_write se files -> preview -> console error + visual dekho -> fix -> export_zip.
- Bade project: file-wise banao, har file ke baad chalao. Test likho. 5 koshish me na chale to sach batao.
- Security: audit_code{project}; findings me severity + fix. Secrets kabhi code me mat daalo.
- Final: save_file{folder:"Code"} me rakho aur user ko path batao.""".trimIndent(),

        "audit" to """
SECURITY AUDIT (sirf user ki apni site/app, jo Settings 'meri sites' me hai):
1. audit_site{url}: headers, TLS, cookies, CORS, exposed files. 2. import_repo/audit_code{project}: secrets, XSS, SQLi, eval, weak crypto.
3. Report: HIGH/MED/LOW, kahan (file:line), kaise exploit hota (1 line), fix. save_file{folder:"Security"}.
4. Kisi doosre ki site par scan, brute force, exploit, DoS NAHI.""".trimIndent(),

        "pc" to """
PC CONTROL (pc_agent.py chalna chahiye, same WiFi, Settings -> PC link):
pc{do:"list",arg:"~/"} | pc{do:"read",arg:"path"} | pc{do:"write",arg:"path",arg2:"content"} | pc{do:"run",arg:"cmd"} | pc{do:"shot"} | pc{do:"open",arg:"app/url"} | pc{do:"type",arg:"text"} | pc{do:"click",arg:"x",arg2:"y"}
- run: safe (ls/cat/git status...) seedha; baaki risky:true se pehle user ki haan. delete/format/sudo bina saaf haan ke nahi.""".trimIndent(),

        "digest" to """
MORNING DIGEST (OpenJarvis jaisa): schedule{text:"Subah ka digest: mausam, top 5 khabar, aaj ke reminders, watchlist ka haal, 1 line motivation. Chhota aur bol kar sunane layak.",at:"08:00",repeat_min:1440}.
Digest chalne par: device_info, web_search (news/weather), market_data (watchlist), memo_search; 6-8 line me done{}.""".trimIndent(),

        "apps" to """
APPS: list_apps{filter} se naam/package dekho (user ki downloaded apps * se marked). open_app{app} naam/nickname/package teeno chalte hain. Nahi mili to Play Store khulta hai. Nickname: Settings -> Apps -> nickname.""".trimIndent(),

        "api" to """
NAYI API CHAHIYE: find_api{query:"weather"} (public-apis list se). auth=none wali seedha http_request. apiKey wali: Extras me key ho to header/URL me {{KEY:naam}}; na ho to user ko API ka signup link batao aur Settings -> Extra APIs me key daalne ko kaho. Docs fetch_url se padho; chhota test call karo; fir use karo.""".trimIndent(),

        "quant" to """
QUANT/TRADING LAB (Freqtrade/Jesse/TradingAgents ke idea): 1) strategies{symbol} se 7 strategy compare; sirf wahi dekho jo OUT-OF-SAMPLE me bhi theek ho. 2) optimize se overfit check. 3) paper{do:open,...} se NAKLI trade chalao jab tak 20+ trade ka record na ho. 4) trade_debate se bull/bear + risk manager. 5) asli trade tabhi jab user 'haan' kahe; SL zaroor; per-trade risk Settings wala %. 6) grid_plan/dca_plan sirf calculation hain. Guarantee kabhi mat do.""".trimIndent(),

        "mobile-agent" to """
MOBILE AGENT TAREEKA (AppAgent/Mobile-Agent jaisa): 1) screen ke [n] ids se tap_id use karo. 2) har action ke baad nayi screen padho aur check karo asar hua; nahi hua to alag tareeka (scroll/back/wait_for). 3) login/OTP/captcha aaye to ask karke user ko khud karne do. 4) naya app: thoda explore karo, jo seekha use skill_save{name:"app-<package>"} me likho. 5) dohrane wale kaam macro_save karo (bina token chalte hain). 6) bade kaam ko plan karke remember me chhote steps likho.""".trimIndent(),

        "research" to """
DEEP RESEARCH (multi-hop + citations): 1) sawal ko 3-5 sub-sawal me todo, remember me likho. 2) har sub-sawal ke liye web_search, phir 2 best links fetch_url se padho. 3) alag-alag sources se milao; mismatch ho to likho. 4) jawab me har daave ke aage [1],[2] aur aakhir me 'Sources:' list (title+URL). 5) jo pakka nahi mila use 'pakka nahi' bolo; guess ko fact mat banao. 6) bada report ho to save_file folder Research me rakho.""".trimIndent(),

        "learning" to """
SKILL SEEKHNA (OpenJarvis jaisa): koi kaam kamyab ho aur dobara aane wala lage to {"action":"skill_save","name":"chhota-naam","text":"steps: 1... 2... (kaunsi app, kaunse button, kya dhyan rakhna)"} se save karo. Agli baar pehle {"action":"playbook","name":"list"} dekho.""".trimIndent(),

        "accessibility" to """
ACCESSIBILITY: app khud connection check karta hai. User se kabhi mat kaho "permission do" - agar screen nahi dikhi to non-screen actions (web/code/api) use karo ya done me ek line me batao ki Permissions screen me Accessibility connect nahi hai.""".trimIndent()
    )

    private val SOCIAL_PB = setOf("social", "instagram", "youtube", "linkedin", "film", "earn")

    private fun custom(): JSONObject = try { JSONObject(Hub.prefs.str("pb_custom", "{}")) } catch (e: Exception) { JSONObject() }
    private fun remote(): JSONObject = try { JSONObject(Hub.prefs.str("pb_remote", "{}")) } catch (e: Exception) { JSONObject() }

    fun has(name: String): Boolean {
        val n = name.trim().lowercase()
        return custom().has(n) || remote().has(n)
    }

    fun names(): List<String> = (builtin.keys + custom().keys().asSequence().toList() + remote().keys().asSequence().toList()).distinct().sorted()

    fun get(name: String): String {
        val n = name.trim().lowercase()
        if (n.isEmpty() || n == "list") return "Playbooks: " + names().joinToString(", ")
        val c = custom()
        if (n in SOCIAL_PB && builtin.containsKey(n)) return builtin[n] + "\n" + Social.profile()
        if (c.has(n)) return c.getString(n)
        val r = remote()
        if (r.has(n)) return r.getString(n)
        return builtin[n] ?: ("ERROR: '$n' playbook nahi. Available: " + names().joinToString(", "))
    }

    private fun rawUrl(u: String): String {
        val m = Regex("^https://github\\.com/([^/]+)/([^/]+)/blob/(.+)$").find(u.trim())
        return if (m != null) "https://raw.githubusercontent.com/${m.groupValues[1]}/${m.groupValues[2]}/${m.groupValues[3]}" else u.trim()
    }

    /** Settings me diya URL: {"naam":"text"} JSON. 12 ghante me ek baar. */
    fun refreshRemote(force: Boolean = false): String {
        val url = Hub.prefs.playbookUrl
        if (url.isBlank()) return "playbook URL set nahi"
        val last = Hub.prefs.int("pb_remote_t", 0).toLong() * 1000L
        if (!force && System.currentTimeMillis() - last < 12 * 3600_000L) return "cached"
        return try {
            val r = WebTools.get(rawUrl(url), 400000)
            if (r.code != 200) return "ERROR: HTTP " + r.code
            val j = JSONObject(r.body)
            val out = JSONObject()
            for (k in j.keys()) out.put(k.lowercase().replace(Regex("[^a-z0-9_-]"), ""), j.optString(k).take(4000))
            Hub.prefs.putStr("pb_remote", out.toString())
            Hub.prefs.putInt("pb_remote_t", (System.currentTimeMillis() / 1000L).toInt())
            "remote playbooks: " + out.length()
        } catch (e: Exception) {
            "ERROR: " + (e.message ?: e.javaClass.simpleName)
        }
    }

    /** Kisi GitHub raw/blob URL se SKILL.md/markdown laakar skill ki tarah save karo (agentskills.io style). */
    fun importSkill(url: String, name: String): String {
        if (!url.startsWith("http")) return "ERROR: URL do"
        return try {
            val r = WebTools.get(rawUrl(url), 200000)
            if (r.code != 200) return "ERROR: HTTP " + r.code
            var body = r.body.trim()
            val nm = (name.ifBlank { Regex("name:\\s*([A-Za-z0-9_-]+)").find(body)?.groupValues?.get(1) ?: url.substringBeforeLast('/').substringAfterLast('/') })
                .lowercase().replace(Regex("[^a-z0-9_-]"), "").take(30)
            if (nm.isEmpty()) return "ERROR: naam do"
            if (body.startsWith("---")) body = body.substringAfter("---").substringAfter("---").trim()
            val c = custom()
            c.put(nm, body.take(4000))
            Hub.prefs.putStr("pb_custom", c.toString())
            "skill '$nm' import ho gayi (" + body.length + " chars). playbook{name:\"$nm\"} se padho."
        } catch (e: Exception) {
            "ERROR: " + (e.message ?: e.javaClass.simpleName)
        }
    }

    /** Agent ne jo kaam seekha use apni skill ki tarah save karo (local, user ke phone me). */
    fun saveSkill(name: String, text: String): String {
        val nm = name.lowercase().replace(Regex("[^a-z0-9_-]"), "").take(30)
        if (nm.isEmpty() || text.isBlank()) return "ERROR: naam aur text do"
        if (builtin.containsKey(nm)) return "ERROR: '$nm' built-in naam hai, dusra naam do"
        val c = custom()
        c.put(nm, text.take(3000))
        while (c.length() > 40) c.remove(c.keys().next())
        Hub.prefs.putStr("pb_custom", c.toString())
        return "skill '$nm' save ho gayi"
    }
}
