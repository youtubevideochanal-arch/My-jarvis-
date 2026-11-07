#!/usr/bin/env python3
"""
MY AGENT - PC companion (laptop/PC ko phone se chalane ke liye).

Chalane ka tareeka:
    python3 pc_agent.py            # token khud ban kar screen par aayega
    python3 pc_agent.py --token ABC123 --port 8765

Phir phone app me: Settings -> PC link -> URL = http://<PC-ka-IP>:8765 aur wahi token.
Sirf apne ghar/office ke WiFi par chalao. Token ke bina koi request nahi chalti.
Optional (screenshot/click/type ke liye):  pip install pyautogui pillow
"""
import argparse, base64, io, json, os, secrets, subprocess, sys, webbrowser
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

TOKEN = ""
ROOT = os.path.expanduser("~")


def safe_path(p):
    full = os.path.abspath(os.path.join(ROOT, os.path.expanduser(p or ".")))
    return full


def do_run(arg, _):
    r = subprocess.run(arg, shell=True, capture_output=True, text=True, timeout=60, cwd=ROOT)
    return {"exit": r.returncode, "out": (r.stdout + r.stderr)[-4000:]}


def do_read(arg, _):
    with open(safe_path(arg), "r", errors="replace") as f:
        return {"text": f.read(6000)}


def do_write(arg, arg2):
    p = safe_path(arg)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, "w") as f:
        f.write(arg2)
    return {"saved": p}


def do_list(arg, _):
    p = safe_path(arg)
    return {"items": sorted(os.listdir(p))[:200]}


def do_shot(_a, _b):
    try:
        import pyautogui
        img = pyautogui.screenshot()
        buf = io.BytesIO()
        img.save(buf, "PNG")
        return {"png": base64.b64encode(buf.getvalue()).decode()}
    except Exception as e:
        return {"error": "pip install pyautogui pillow  (%s)" % e}


def do_type(arg, _):
    import pyautogui
    pyautogui.write(arg, interval=0.02)
    return {"typed": len(arg)}


def do_click(arg, arg2):
    import pyautogui
    pyautogui.click(int(arg), int(arg2))
    return {"clicked": [arg, arg2]}


def do_open(arg, _):
    if arg.startswith("http://") or arg.startswith("https://"):
        webbrowser.open(arg)
    elif sys.platform.startswith("win"):
        os.startfile(arg)  # noqa
    elif sys.platform == "darwin":
        subprocess.Popen(["open", arg])
    else:
        subprocess.Popen(["xdg-open", arg])
    return {"opened": arg}


ROUTES = {"run": do_run, "read": do_read, "write": do_write, "list": do_list,
          "shot": do_shot, "type": do_type, "click": do_click, "open": do_open}


class H(BaseHTTPRequestHandler):
    def log_message(self, *a):
        pass

    def _send(self, code, obj):
        b = json.dumps(obj).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(b)))
        self.end_headers()
        self.wfile.write(b)

    def do_POST(self):
        if not secrets.compare_digest(self.headers.get("X-Token", ""), TOKEN):
            return self._send(401, {"error": "bad token"})
        name = self.path.strip("/")
        fn = ROUTES.get(name)
        if not fn:
            return self._send(404, {"error": "unknown"})
        try:
            n = int(self.headers.get("Content-Length", "0"))
            d = json.loads(self.rfile.read(n) or b"{}")
            print("->", name, str(d.get("arg", ""))[:80])
            self._send(200, fn(d.get("arg", ""), d.get("arg2", "")))
        except Exception as e:
            self._send(500, {"error": str(e)})


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--port", type=int, default=8765)
    ap.add_argument("--token", default="")
    a = ap.parse_args()
    TOKEN = a.token or secrets.token_urlsafe(9)
    print("PC agent chal raha hai.  Port:", a.port, " TOKEN:", TOKEN)
    print("Phone me URL: http://<is PC ka IP>:%d" % a.port)
    ThreadingHTTPServer(("0.0.0.0", a.port), H).serve_forever()
