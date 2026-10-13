"""Builds the suit's glowmask textures from its main texture (run after node tools/export-model.mjs):

    uv run --with pillow python3 tools/make-glowmasks.py

Glowing parts are painted in Blockbench with RESERVED colours: dark, so a powered-off suit shows dead coals and
dark glass. Each reserved colour maps to a bright colour in one glowmask; everything else is transparent black,
which adds nothing (SuitGlowLayer draws the masks full-bright and additively, tinted per frame):
  power_armor_glow_fire.png   the firebox coals (yellow at the bottom -> red at the top) and the ember vent slits
  power_armor_glow_visor.png  the visor glass, white (tinted green / amber / red by the suit's state)
  power_armor_glow_heat.png   the chaingun's barrel tips (tinted by the barrels' heat)
  power_armor_glow_heat_barrel.png  the barrels further back (dark red -> orange-red toward the tips) and the front collar
                              around them; it lights up
                              only when the barrels get really hot, so the heat creeps back from the tips. It's what
                              the pilot sees: from the cockpit the front collar hides the tips.
  power_armor_glow_em_core.png  the EM projector's redstone lens and the vent slits round its housing
  power_armor_glow_em_arc.png   the EM projector's redstone prong tips (they spark while the beam holds something)
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
    H('262c34'): ('visor', H('ebebeb')),  # visor glass
    H('1e232a'): ('visor', H('a0a0a0')),  # visor glass, edge
    H('46505c'): ('visor', H('ffffff')),  # visor glass, highlight
    H('3c4147'): ('heat', H('ff6a1a')),   # chaingun barrel tip, second row: orange
    H('353a40'): ('heat', H('ffc860')),   # chaingun barrel tip, last row: yellow-hot
    # barrel sides behind the tips: the stripes' own colours, one step off (cold barrels look the same)
    H('4c555a'): ('heat_barrel', H('6e1a0a')), H('3b4247'): ('heat_barrel', H('5a1408')),  # 4th row from the tip: dark red
    H('4c555b'): ('heat_barrel', H('b4300e')), H('3b4248'): ('heat_barrel', H('96280c')),  # 3rd: red
    H('4c555c'): ('heat_barrel', H('f0501a')), H('3b4249'): ('heat_barrel', H('d24416')),  # under the front collar: orange-red
    # the front collar around the barrels (its own net; the rear collar stays cold): the ring's greys, one step off
    H('89939b'): ('heat_barrel', H('f0501a')), H('7d888f'): ('heat_barrel', H('d24416')), H('6f797f'): ('heat_barrel', H('b4300e')),
    # EM projector (model/art/em_redstone_art.py): dead redstone, lit red
    H('3a0e0c'): ('em_core', H('7a0600')), H('4b1310'): ('em_core', H('b81206')),
    H('5c1813'): ('em_core', H('f0200c')), H('701f17'): ('em_core', H('ff6a4a')),
    H('4e1411'): ('em_arc', H('c01206')), H('621a14'): ('em_arc', H('ff2a14')),
}
EXPECTED = {'fire': 15, 'visor': 35, 'heat': 16, 'heat_barrel': 120, 'em_core': 42, 'em_arc': 20}  # pixels per mask (firebox 12 + 3 slits; the 7x5 visor;
# 4 barrel sides x 2x2 tips; 4 barrel sides x 2x3 behind them + the front collar's 6x1x6 net, 96;
# em_core: the 3x1x3 lens's faces 30 + 3 vent slits on each of the housing's 4 sides; em_arc: the shared prong net's
# tip, 2x2 end + 4 sides x 2x2)


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
