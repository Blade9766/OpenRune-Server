import base64
import json
import sys
import time
import urllib.request

URL = "http://127.0.0.1:7780/mcp"
_id = 0


def call(tool, **args):
    global _id
    _id += 1
    body = json.dumps({"jsonrpc": "2.0", "id": _id, "method": "tools/call",
                       "params": {"name": tool, "arguments": args}}).encode()
    req = urllib.request.Request(URL, data=body, headers={
        "Content-Type": "application/json", "Accept": "application/json, text/event-stream"})
    with urllib.request.urlopen(req, timeout=60) as resp:
        raw = resp.read().decode("utf-8", "replace")
    if raw.startswith("event:") or "\ndata:" in raw or raw.startswith("data:"):
        for line in raw.splitlines():
            if line.startswith("data:"):
                raw = line[5:].strip()
    return json.loads(raw)


def save_image(result, path):
    for item in result.get("result", {}).get("content", []):
        if item.get("type") == "image":
            with open(path, "wb") as f:
                f.write(base64.b64decode(item["data"]))
            return True
    return False


if __name__ == "__main__":
    tool = sys.argv[1]
    args = json.loads(sys.argv[2]) if len(sys.argv) > 2 else {}
    res = call(tool, **args)
    if len(sys.argv) > 3:
        print("saved" if save_image(res, sys.argv[3]) else json.dumps(res)[:600])
    else:
        print(json.dumps(res)[:1500])
