#!/usr/bin/env python3
"""Drive the Mode engine from a Mac, the way the app will from the phone.

The engine speaks phone-harness's op protocol: GET /ops, POST /op {"op", "kw"}.
Reach it over the USB tunnel Xcode sets up (`xcrun devicectl device info
details` prints the device's "Tunnel IP Address"), with the engine started
with MODE_ENGINE_BIND_ALL=1.

    MODE_ENGINE_URL=http://[fdbd:...::1]:47610 MODE_ENGINE_TOKEN=... \
        tools/engine.py text
"""
import argparse
import base64
import json
import os
import sys
import urllib.error
import urllib.request


class Engine:
    def __init__(self, url, token):
        self.url = url.rstrip("/")
        self.token = token

    def _call(self, method, path, body=None, timeout=90):
        data = json.dumps(body).encode() if body is not None else None
        headers = {"Authorization": f"Bearer {self.token}"}
        if data:
            headers["Content-Type"] = "application/json"
        req = urllib.request.Request(self.url + path, data=data, method=method, headers=headers)
        try:
            with urllib.request.urlopen(req, timeout=timeout) as r:
                return json.loads(r.read())
        except urllib.error.HTTPError as e:
            err = json.loads(e.read() or b"{}")
            kind = "unsupported" if err.get("unsupported") else "failed"
            raise RuntimeError(f"{kind}: {err.get('error', e.code)}") from None

    def ops(self):
        return self._call("GET", "/ops")["ops"]

    def op(self, op_name, /, **kw):
        return self._call("POST", "/op", {"op": op_name, "kw": kw})["result"]

    # phone-harness helpers, enough for the spike

    def text(self):
        return self.op("screen.text")

    def find(self, query, exact=False):
        q = query.lower()
        return [b for b in self.text()
                if (b["text"].lower() == q if exact else q in b["text"].lower())]

    def tap_text(self, query, index=0, exact=False):
        hits = self.find(query, exact)
        if not hits:
            seen = [b["text"] for b in self.text()][:40]
            raise RuntimeError(f"no visible text matches {query!r}; saw: {seen}")
        hit = hits[index]
        self.op("input.tap", x=hit["x"], y=hit["y"])
        return hit

    def scroll(self, direction="down", amount=0.3):
        b = self.op("screen.bounds")
        sx, sy = {"down": (0, -1), "up": (0, 1), "right": (-1, 0), "left": (1, 0)}[direction]
        self.op("input.scroll", x=b["w"] / 2, y=b["h"] / 2, dx=sx * b["w"] * amount, dy=sy * b["h"] * amount)


def connect():
    url = os.environ.get("MODE_ENGINE_URL", "http://127.0.0.1:47610")
    token = os.environ.get("MODE_ENGINE_TOKEN")
    if not token:
        sys.exit("set MODE_ENGINE_TOKEN (the engine prints one when it starts without it)")
    return Engine(url, token)


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = p.add_subparsers(dest="cmd", required=True)
    sub.add_parser("ops")
    sub.add_parser("state")
    sub.add_parser("current")
    sub.add_parser("home")
    sub.add_parser("stop")
    sub.add_parser("tree")
    sub.add_parser("text")
    s = sub.add_parser("tap"); s.add_argument("x", type=float); s.add_argument("y", type=float)
    s = sub.add_parser("tap-text"); s.add_argument("query")
    s = sub.add_parser("type"); s.add_argument("s")
    s = sub.add_parser("key"); s.add_argument("combo")
    s = sub.add_parser("launch"); s.add_argument("name")
    s = sub.add_parser("open"); s.add_argument("url")
    s = sub.add_parser("scroll"); s.add_argument("direction", choices=["up", "down", "left", "right"])
    s = sub.add_parser("shot"); s.add_argument("path"); s.add_argument("--max-size", type=float)
    a = p.parse_args()

    e = connect()
    if a.cmd == "ops":
        out = e.ops()
    elif a.cmd == "state":
        out = e.op("session.state")
    elif a.cmd == "current":
        out = e.op("apps.current")
    elif a.cmd == "home":
        out = e.op("nav.home")
    elif a.cmd == "stop":
        out = e.op("engine.stop")
    elif a.cmd == "tree":
        out = e.op("tree")
    elif a.cmd == "text":
        out = [f'{b["type"]:>12} {b["text"]!r} @ ({b["x"]:.0f},{b["y"]:.0f})' for b in e.text()]
    elif a.cmd == "tap":
        out = e.op("input.tap", x=a.x, y=a.y)
    elif a.cmd == "tap-text":
        out = e.tap_text(a.query)
    elif a.cmd == "type":
        out = e.op("input.text", s=a.s)
    elif a.cmd == "key":
        out = e.op("input.keys", combo=a.combo)
    elif a.cmd == "launch":
        out = e.op("apps.launch", name=a.name)
    elif a.cmd == "open":
        out = e.op("url.open", url=a.url)
    elif a.cmd == "scroll":
        out = e.scroll(a.direction)
    elif a.cmd == "shot":
        kw = {"max_size": a.max_size} if a.max_size else {}
        r = e.op("screen.capture", **kw)
        blob = r.get("png_b64") or r.get("jpeg_b64")
        with open(a.path, "wb") as f:
            f.write(base64.b64decode(blob))
        out = {"path": a.path, "bounds": r["bounds"]}
    if isinstance(out, list) and out and isinstance(out[0], str):
        print("\n".join(out))
    else:
        print(json.dumps(out, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()
