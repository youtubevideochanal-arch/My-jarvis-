# Install (MY AGENT v5.3)

1. Upload this folder to your GitHub repo (replace files). Actions builds `MY-AGENT.apk` (Releases -> Latest APK).
   `MY-AGENT-newkey.apk` is a spare build with a fresh signing key.
2. Install. If an update says "App not installed": the signing key differs from the installed one -> uninstall first, then install.
3. Open app -> 3-dot -> Permissions: Accessibility ON, overlay ON, mic Allow, files access Allow, battery No restrictions.
4. Optional, one time (wireless debugging, Termux `pkg install android-tools`):
   `adb shell pm grant com.anis.doremon android.permission.WRITE_SECURE_SETTINGS`
   After this the app turns Accessibility on by itself and revives it if the phone kills it.
5. Settings -> paste each API key (model auto-detect). Settings -> Look -> pick an interface skin.
