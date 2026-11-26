# My Agent - APK banane ka tareeka (GitHub)
1. GitHub par naya repo banao, is folder ki saari files upload karo (".github" folder ke saath).
2. Repo > Actions > "Build APK" > Run workflow (ya push karte hi chalta hai).
3. Complete hone par Artifacts se `MyAgent-debug-apk` download karo, zip kholo, APK install karo.
4. App kholo > menu (3 lines) > API keys me Gemini / ChatGPT / Claude key paste karo (model apne aap detect hota hai).
5. Menu > Permissions: Microphone, Display over other apps, Accessibility ON karo.
   Accessibility grey ho to: Phone Settings > Apps > My Agent > ⋮ > Allow restricted settings.
Floating icon: dabake rakho = bolo (chhodte hi sunna band), ek tap = type box, ghasito = move.

Menu (3 lines): bada ON/OFF button upar, API keys, Brain, Voice characters (12 alag awaaz), Allowed domains, Permissions.

Side menu (hamburger): ON/OFF, API keys (Gemini / ChatGPT / Claude / Custom), Voice, Connected apps, Accessibility & permissions, Agent settings, chat history, New chat.

## Local coding model (optional)
I cannot train a new model, so this wires an existing open one: Qwen2.5-Coder 3B (~2GB).
1. Install Termux (F-Droid), open it and run: `bash local_coder_termux.sh` (copy the file to Termux first).
2. In My Agent: Menu > API keys > "My model" > "Fill: local coder (Termux)" > Save.
The agent then uses it through the `coder` action for writing/debugging code. Keep Termux running in the background.
If `pkg install ollama` is not available on your Termux, build llama.cpp there and run `llama-server` with a Qwen2.5-Coder GGUF on port 11434.
