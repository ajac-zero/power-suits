"""Stand-in pilot head for Blockbench (2026-10-18, user: "use Steve or a dummy head"): the removable helmet shows the
pilot's own face in game (the mod draws their head with their skin on the `pilot_head` bone); in Blockbench a Steve
head stands in so the neck and collar can be designed around a real 8x8x8 head.

The project is Bedrock (one texture, box UV) and has no free 32x16 area for a head's box-UV net, so the stand-in cube
uses per-face UV: Steve's six 8x8 head faces are copied into six free 8x8 squares (alpha 0 and outside every cube's
net). The cube is marked not to export (`export: false`), so the game's geo never has it; its pixels stay in the
texture (unused there).

Inputs in /private/tmp/ub/: tex2.png (live texture), nets.json ([{n, uv, s}] of every cube), steve.png (from the
Minecraft jar: assets/minecraft/textures/entity/player/wide/steve.png). Writes pilot_head.json for
pilot_head_apply.js: {faces: {north: [u, v], ...}, paint: [[x, y, 'rrggbb'], ...]}.

    uv run --with pillow python3 model/art/pilot_head_art.py
"""
import json
from PIL import Image

D = '/private/tmp/ub/'
tex = Image.open(D + 'tex2.png').convert('RGBA')
W, H = tex.size
used = [[tex.getpixel((x, y))[3] > 0 for x in range(W)] for y in range(H)]
for e in json.load(open(D + 'nets.json')):
    u, v = e['uv']
    w, h, d = e['s']
    for y in range(v, min(H, v + d + h)):
        for x in range(u, min(W, u + 2 * (w + d))):
            used[y][x] = True

# Steve's head net (64x64 skin): top (8,0) bottom (16,0) right (0,8) front (8,8) left (16,8) back (24,8).
# The model faces -Z (north); the wearer's right is +X (east).
SRC = {'up': (8, 0), 'down': (16, 0), 'east': (0, 8), 'north': (8, 8), 'west': (16, 8), 'south': (24, 8)}
steve = Image.open(D + 'steve.png').convert('RGBA')
faces, paint = {}, []
for face, (sx, sy) in SRC.items():
    spot = next((x, y) for y in range(0, H - 7, 8) for x in range(0, W - 7, 8)
                if all(not used[y + j][x + i] for j in range(8) for i in range(8)))
    for j in range(8):
        for i in range(8):
            used[spot[1] + j][spot[0] + i] = True
            r, g, b, _ = steve.getpixel((sx + i, sy + j))
            paint.append([spot[0] + i, spot[1] + j, '%02x%02x%02x' % (r, g, b)])
    faces[face] = spot
json.dump({'faces': faces, 'paint': paint}, open(D + 'pilot_head.json', 'w'))
print(faces)
