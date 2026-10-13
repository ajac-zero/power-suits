"""Builds the suit's glowmask textures from its main texture (run after node tools/export-model.mjs):

    uv run --with pillow python3 tools/make-glowmasks.py

Glowing parts are painted in Blockbench with RESERVED colours: dark, so a powered-off suit shows dead coals and
dark glass. Each reserved colour maps to a bright colour in one glowmask; everything else is transparent black,
which adds nothing (SuitGlowLayer draws the masks full-bright and additively, tinted per frame):
  power_armor_glow_fire.png   the firebox coals (yellow at the bottom -> red at the top) and the ember vent slits
  power_armor_glow_visor.png  the porthole glass, white (tinted green / amber / red by the suit's state)
The script refuses to run if a reserved colour turns up outside the expected amount (someone painted with it).
"""
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
TEX = ROOT / 'src/main/resources/assets/powerarmor/textures/entity'
H = lambda s: tuple(int(s[i:i + 2], 16) for i in (0, 2, 4))

# reserved base colour -> (mask, glow colour)
GLOW = {
    H('3f211b'): ('fire', H('ffdc6e')),   # coal, bottom row: yellow-white heat
    H('371d19'): ('fire', H('ff9632')),   # coal, middle: orange
    H('2f1a17'): ('fire', H('d24619')),   # coal, top: red
    H('281614'): ('fire', H('963714')),   # vent slits under the firebox: dim ember
    H('262c34'): ('visor', H('ebebeb')),  # porthole glass
    H('1e232a'): ('visor', H('a0a0a0')),  # porthole glass, edge
    H('46505c'): ('visor', H('ffffff')),  # porthole glass, highlight
}
EXPECTED = {'fire': 15, 'visor': 15}       # pixels per mask (firebox 12 + 3 slits; visor glass 15)


def main():
    src = Image.open(TEX / 'power_armor.png').convert('RGBA')
    masks = {name: Image.new('RGBA', src.size, (0, 0, 0, 0)) for name in EXPECTED}
    count = dict.fromkeys(EXPECTED, 0)
    for y in range(src.height):
        for x in range(src.width):
            r, g, b, a = src.getpixel((x, y))
            hit = GLOW.get((r, g, b)) if a else None
            if hit:
                masks[hit[0]].putpixel((x, y), hit[1] + (255,))
                count[hit[0]] += 1
    if count != EXPECTED:
        raise SystemExit(f'glow pixels {count}, expected {EXPECTED}: a reserved colour was used elsewhere, or the '
                         f'glowing parts changed (update EXPECTED)')
    for name, im in masks.items():
        out = TEX / f'power_armor_glow_{name}.png'
        im.save(out)
        print(f'wrote {out.relative_to(ROOT)} ({count[name]} pixels)')


main()
