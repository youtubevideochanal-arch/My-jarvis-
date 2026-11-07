#!/data/data/com.termux/files/usr/bin/bash
# Termux me chalao:  bash termux_push.sh
# Token screen par dikhta nahi aur git config me save nahi hota.
pkg install -y git >/dev/null 2>&1
read -p "GitHub repo URL (https://github.com/USER/REPO.git): " URL
read -p "GitHub username: " GU
read -s -p "Personal Access Token (typing dikhegi nahi): " GT; echo
git init -q
git config user.email >/dev/null 2>&1 || git config user.email "agent@local"
git config user.name  >/dev/null 2>&1 || git config user.name "agent"
git add -A
git commit -qm "MY AGENT" || true
git branch -M main
git remote remove origin 2>/dev/null
git remote add origin "$URL"
export GIT_ASKPASS_USER="$GU" GIT_ASKPASS_PASS="$GT"
ASK="$(mktemp)"
cat > "$ASK" <<'EOS'
#!/bin/sh
case "$1" in
  Username*) echo "$GIT_ASKPASS_USER" ;;
  *) echo "$GIT_ASKPASS_PASS" ;;
esac
EOS
chmod +x "$ASK"
GIT_ASKPASS="$ASK" git push -u origin main --force
rm -f "$ASK"
unset GIT_ASKPASS_PASS GT
echo "Ho gaya. GitHub -> Actions me build dekho. Token jab kaam khatam ho, GitHub se delete kar dena."
