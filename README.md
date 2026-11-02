# MY AGENT v5 — Android Jarvis-style AI Assistant (all-in-one)

3D orb UI, ON/OFF button, voice + chat, Groq/Venice/Gemini (har ek ka alag ON/OFF), Photo API, 2 future slots,
task kabhi nahi bhoolta, API fail ho to agli par apne aap switch, aur APIs aapas me salah bhi kar sakti hain.

## Kya-kya kar sakta hai
- **Phone control** (Accessibility): screen padhna, tap/swipe/long-press/type, app kholna, wait_for
- **Code likhna + khud chalana (Termux ke bina)**: JavaScript aur HTML/CSS/JS app ke andar ek band sandbox me chalte hain.
  Console, errors, page text, aur (Photo API ON ho to) screenshot se design check -> bug fix -> tabhi final code.
  Python/Node/Bash sirf tab jab Termux ho (optional).
- **Projects**: multi-file web-apps workspace me, public GitHub repo import, zip export (Download/MyAgent/Projects)
- **Bug/vulnerability audit**: code (secrets, XSS, SQLi, eval, weak TLS, cleartext... line number + fix) aur apni live site ka passive scan
- **Trading Hub**: chart, RSI/MACD/SMA/EMA/Bollinger/ATR, AI analysis, market research, 10 lessons, backtest, position-size example
- **Jarvis tools**: internet search, page padhna, HTTP request, reminders, device info, link kholna, clipboard, lambi yaaddasht
- **Floating bubble**, call ke dauran auto-pause, battery saver, 5 colour themes, boy/girl/child voice, naam se bulana

## v5 me naya
Numbered-screen control (tap_id), macros, stuck/handover, strategies + paper trading + debate, Telegram control, key se model auto-pehchan. Install error ho to INSTALL.md padho (purani app uninstall karke naya .apk).

## v4 me naya (poori list: docs/FEATURES.md, CHANGELOG.md)
- **Kisi bhi screen se bulao**: floating bubble (tap = chhota panel), Quick Settings tile, Digital assistant (home long-press), notification "🎙 Bolo", aur optional "hamesha naam sunna". Naam: doremon/doraemon/डोरेमोन sab pehchane jaate hain.
- **4 orb styles**, 8+ voice characters, saari installed apps ki list (trading/social apps chuno), 5 slots + local Ollama + OpenRouter free.
- **Token bachat**: chat sidhe jawab, chhota prompt, asli token counter. API fail par progress ke saath agli API.
- **Trading**: candle patterns, support/resistance, trade_plan; apni trading app (jaise MarketWolf) kholkar padhna; order submit par hamesha tumhari 'haan'.
- **Key paste = model khud pehchana**: key daalte hi provider (Groq/Gemini/OpenRouter/xAI/...) pehchan kar sahi slot me, chalta hua best model laga deta hai.
- **Mere social accounts** (Settings): Instagram/YouTube/LinkedIn/X/FB handles + niche; agent content calendar, drafts, free image, comments/analytics sambhalta hai (post se pehle tumhari haan). Notifications padhna (optional).
- **Social / Film / Earn / PC / Research** playbooks, public-apis se naye API dhundna, Extra APIs, PC companion (`pc_agent/pc_agent.py`), scheduled kaam + price watch + morning digest, skill import/save.
- Honest limits: kamai ki guarantee nahi (Earn sirf mauke dhundta/draft karta hai, payment/KYC tum karte ho); trading research hai, advice nahi; Android ka system accessibility icon app se hata nahi sakte (Permissions screen me tarika likha hai).

## ⚠ Install me dikkat? -> INSTALL.md
Sabse aasan: repo -> **Releases -> Latest APK** se seedha `MY-AGENT.apk` lo (zip nahi) aur purani app uninstall karke install karo.

## APK kaise banegi (phone se, Termux ke bina bhi)
1. GitHub par repo banao. **Public** theek hai: API keys is repo me nahi hain, har kisi ki keys uske apne phone me rehti hain.
2. Is zip ke andar ki files repo me daalo (Chrome me Add file -> Upload files, folders ke saath; ya Termux: `bash termux_push.sh`).
3. Repo -> **Actions** -> "Build APK" -> run khatam hone par **Artifacts -> MyAgent-debug-apk** download -> install.

### Signing (public repo ke liye behtar)
Repo me ek shared debug key hai (updates chalte rehne ke liye). Sirf aapke updates chalen, iske liye apni key lagao:
`keytool -genkeypair -keystore my.keystore -alias agent -keyalg RSA -keysize 2048 -validity 10000`
phir `base64 -w0 my.keystore` ko repo Settings -> Secrets me `KEYSTORE_BASE64` banao, aur `KS_PASS`, `KS_ALIAS` bhi. Build apne aap uski key use karega.
(Key badalne par ek baar purani app uninstall karni padegi.)

## Pehli baar
1. Menu (3 dot) -> Permissions: Accessibility ON (Android 13+: pehle App info -> 3 dot -> *Allow restricted settings*), Overlay, Mic, Notifications, Battery.
2. Settings -> API key daalo -> **Test** (tick aana chahiye). Photo API ON karoge to design bhi dekhega.
3. Bada ON button dabao. Likho ya mic dabao.

## Naam se bulana
Settings -> Voice -> "Hamesha naam sunna" (app ON karte waqt khuli ho). Ya Digital assistant / tile / bubble se panel kholo. Naam bolo + kaam: "doremon mausam batao".

## Dhyan
- Risky kaam (payment/delete/order, khatarnak shell commands) se pehle 'haan' poochta hai.
- Security audit live site par sirf Settings -> "Meri websites" me likhi apni sites par chalta hai.
- Trading output research/padhai ke liye hai, financial advice nahi; backtest purane data par hai, kamai ki guarantee nahi.
- Model naam badal jayein to Settings me Model box edit karo.
