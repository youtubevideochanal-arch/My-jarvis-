#!/data/data/com.termux/files/usr/bin/bash
# Local coding model for My Agent (runs fully on the phone, ~2GB download).
# Needs Termux (from F-Droid) and ~4GB free storage + 6GB+ RAM recommended.
set -e
pkg update -y
pkg install -y ollama
termux-wake-lock || true
(ollama serve > "$HOME/ollama.log" 2>&1 &)
sleep 6
ollama pull qwen2.5-coder:3b
echo
echo "Done. Keep Termux running. In My Agent: Menu > API keys > My model > 'Fill: local coder (Termux)' > Save."
