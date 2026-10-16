"""Teleports a logged-in dev client to every wayfinder destination and saves its preview sprite.

Needs the OpenRune-Developer-Tools client plugin (http://127.0.0.1:7780/mcp), an admin account
(it uses ::tele) and no open interface. Writes pack/sprites/wayfinder_<key>.png (160x74, at most
255 colours) and prints the manifest lines (wayfinder_<key> = 9100 + slot) to previews.toml in
the same folder; merge those into manifest.toml.

usage: python capture.py <WayfinderDestinationsTable.kt> <pack/sprites dir> [only_key ...]
"""
import json
import os
import re
import sys
import time

from flux import call, save_image
from pngio import read_png, write_png

TABLE, OUT = sys.argv[1], sys.argv[2]
ONLY = set(sys.argv[3:])
PREVIEW_BASE = 9100
W, H = 160, 74
ROW = re.compile(r'destination\("(\w+)", "[^"]*", \w+, (?:tile\((\d+), (\d+)\)|CoordGrid\((\d+), (\d+), (\d+)\))')


def destinations():
    out = []
    for line in open(TABLE, encoding="utf-8"):
        m = ROW.search(line)
        if m:
            key = m.group(1)
            if m.group(2):
                out.append((key, int(m.group(2)), int(m.group(3)), 0))
            else:
                out.append((key, int(m.group(4)), int(m.group(5)), int(m.group(6))))
    return out


def downscale(width, height, pixels):
    out = []
    for y in range(H):
        y0, y1 = y * height // H, max((y + 1) * height // H, y * height // H + 1)
        for x in range(W):
            x0, x1 = x * width // W, max((x + 1) * width // W, x * width // W + 1)
            r = g = b = n = 0
            for yy in range(y0, y1):
                base = yy * width
                for xx in range(x0, x1):
                    pr, pg, pb = pixels[base + xx]
                    r += pr
                    g += pg
                    b += pb
                    n += 1
            out.append((r // n, g // n, b // n))
    return out


def median_cut(pixels, colours=250):
    boxes = [list(set(pixels))]
    while len(boxes) < colours:
        boxes.sort(key=len, reverse=True)
        box = boxes[0]
        if len(box) < 2:
            break
        ranges = [max(p[c] for p in box) - min(p[c] for p in box) for c in range(3)]
        channel = ranges.index(max(ranges))
        box.sort(key=lambda p: p[channel])
        mid = len(box) // 2
        boxes[0:1] = [box[:mid], box[mid:]]
    mapping = {}
    for box in boxes:
        avg = tuple(sum(p[c] for p in box) // len(box) for c in range(3))
        avg = tuple(max(1, v) for v in avg)
        for p in box:
            mapping[p] = avg
    return [mapping[p] for p in pixels]


def main():
    state = json.loads(call("get_client_state")["result"]["content"][0]["text"])
    cw, ch = state["canvasWidth"], state["canvasHeight"]
    width = cw * 70 // 100
    region = {"x": cw * 15 // 100, "y": ch * 28 // 100, "width": width, "height": width * H // W}
    manifest = []
    rows = destinations()
    for slot, (key, x, z, level) in enumerate(rows, start=1):
        manifest.append(f"wayfinder_{key} = {PREVIEW_BASE + slot}")
        if ONLY and key not in ONLY:
            continue
        call("type_chat", text=f"::tele {x} {z} {level}")
        time.sleep(3.2)
        raw = os.path.join(os.path.dirname(os.path.abspath(__file__)), "shots", f"raw_{key}.png")
        os.makedirs(os.path.dirname(raw), exist_ok=True)
        if not save_image(call("screenshot", **region), raw):
            print("no image for", key)
            continue
        width, height, pixels = read_png(raw)
        small = median_cut(downscale(width, height, pixels))
        write_png(os.path.join(OUT, f"wayfinder_{key}.png"), W, H, [p + (255,) for p in small])
        print("captured", slot, key, flush=True)
    with open(os.path.join(OUT, "previews.toml"), "w", newline="\r\n") as f:
        f.write("\n".join(manifest) + "\n")


main()
