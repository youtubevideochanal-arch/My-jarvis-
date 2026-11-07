# Changelog

## 5.3
- Accessibility: robust detection (ColorOS format), banner refreshes on connect, optional self-enable via `pm grant ... WRITE_SECURE_SETTINGS` (auto re-enable, never asks again).
- NOTE: package id is `com.anis.doremon` - permissions (Accessibility, overlay, mic) must be granted once again for the new id.
- 7 interface skins (Settings -> Look): Orb Neon, Simple Dark, Warm Paper, Gradient Spark, Terminal, Messenger, Light Clean.
- Computer-agent style actions inside the phone agent: shell (Termux), file_list/read/write/find/move/delete, app_info, uninstall_app, clipboard_get, volume, torch, alarm, call/sms (draft), sys_stats.
- Goals: goal_set / goal_update / goal_list / goal_done (daily targets with score + auto re-check).
- Wake-name listening default ON (asks mic once); chat no longer claims it cannot hear.
- Clear API error explanations (invalid key, quota, model) in Test and in chat; AutoDetect no longer keeps a model for a rejected key.
- Removed install-bisect variants from CI.


## 5.0
- Install: signing key ka fallback (debug.keystore.b64), build me APK/signature ki jaanch, Releases me seedha .apk (zip nahi). INSTALL.md.
- Key paste karte hi provider + model khud pehchan (AutoDetect).
- Mobile-agent (AppAgent/Mobile-Agent/Droidrun se idea): screen ke [n] ids + tap_id/type_id, atka hua pakadna, login/OTP/captcha par user ko dena, app-wise seekhi skill auto-yaad, flow/macro (bina token), notifications padhna.
- Trading (Freqtrade/Jesse/OctoBot/TradingAgents se idea): 7 strategies compare + out-of-sample + Monte Carlo, SMA optimize (overfit check), grid/DCA plan, paper (nakli) trading, bull-vs-bear + risk-manager debate.
- Computer-agent (Open Interpreter/browser-use se idea): web_extract (links/headings/forms), PC agent, code-first fir GUI.
- Telegram: alerts + door se kaam dena (sirf apni chat-id). Social accounts, content drafts, image generation.

## 4.0
- Key paste karte hi provider + best model auto-detect (galat slot me daalo to sahi me bhej deta hai).
- Social accounts section, drafts, gen_image (free), notification reader.
- Build: APK check (aapt2/apksigner) + direct APK Release link; termux_push.sh me token chhupa.
- Accessibility: connected vs sirf-enabled alag dikhta hai; ColorOS/Realme ke liye fix guide; agent ab permission nahi maangta.
- Voice: nayi sunne wali engine (errors saaf dikhte hain, fallback dialog), naam se bulana (doremon/doraemon/डोरेमोन), hamesha-sunna (optional).
- Kisi bhi screen se: floating bubble -> chhota corner panel, Quick Settings tile, Digital assistant, notification "Bolo".
- 4 orb styles, 8+ voice characters, saari installed apps ki list + trading/social app picker.
- Token bachat: chat fast-path, chhota prompt, digest, screen sirf zarurat par, asli usage counter, model self-heal.
- API failover me HANDOFF (progress ke saath). Nayi slots: OpenRouter (free), local Ollama/LM Studio.
- Trading: candle patterns, support/resistance, trade_plan, trade submit par confirm.
- Social/Film/Earn playbooks, public-apis discovery, Extra APIs ({{KEY:naam}}), Earn ledger.
- PC agent (pc_agent/pc_agent.py), scheduler/watch/morning digest, skill import/save (OpenJarvis-style), deep research with sources.
