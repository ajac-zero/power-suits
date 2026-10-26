"""Builds the suit's glowmask textures from its main texture (run after node tools/export-model.mjs):

    uv run --with pillow python3 tools/make-glowmasks.py

Glowing parts are painted in Blockbench with RESERVED colours: dark, so a powered-off suit shows dead coals and
dark glass. Each reserved colour maps to a bright colour in one glowmask; everything else is transparent black,
which draws nothing (SuitGlowLayer draws the masks full-bright on top, tinted per frame; in 26.3 the "eyes" pass blends
by alpha, it doesn't add light, so a dark mask colour paints the part darker: lights that fade fade by opacity, and a
colour may carry its own opacity, the third item in GLOW):
  power_armor_glow_fire.png   the firebox coals (yellow at the bottom -> red at the top), painted flat on the backpack
  power_armor_glow_visor.png  the visor glass, white (tinted green / amber / red by the suit's state)
  power_armor_glow_heat.png   the chaingun's barrel tips (tinted by the barrels' heat)
  power_armor_glow_heat_barrel.png  the barrels further back (dark red -> orange-red toward the tips) and the front collar
                              around them; it lights up
                              only when the barrels get really hot, so the heat creeps back from the tips. It's what
                              the pilot sees: from the cockpit the front collar hides the tips.
  power_armor_glow_em_core.png  the EM projector's redstone lens and the vent slits round its housing
  power_armor_glow_em_arc.png   the EM projector's redstone prong tips (they spark while the beam holds something)
  power_armor_glow_tidal.png    the tidal engine's Heart of the Sea in the pump's porthole (teal)
  power_armor_glow_reactor.png  the star reactor's core and the energy in its canisters' glass strips (violet -> white)
  power_armor_glow_pilot.png    the flamethrower's pilot light, the tip of the slate rod by its nozzle (blue)
  power_armor_glow_rail_flow_{0,1,2}.png  the core's rings, a third each: lit in turn, energy runs along the core
  power_armor_glow_rail_emit.png  the emitter in the band's hole the energy comes out of (opaque, lit by the charge)
The script refuses to run if a reserved colour turns up outside the expected amount (someone painted with it).
The rail cannon's core colours are see-through (SEE_THROUGH): cleared from power_armor.png once their masks are made, so
run make-paint-textures.py after this script.
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
    H('281614'): ('fire', H('963714')),   # dim ember (the vent slits under the old firebox; unused since backpack v3)
    H('262c34'): ('visor', H('ebebeb')),  # visor glass
    H('1e232a'): ('visor', H('a0a0a0')),  # visor glass, edge
    H('46505c'): ('visor', H('ffffff')),  # visor glass, highlight
    H('3c4147'): ('heat', H('ff6a1a')),   # chaingun barrel tip, second row: orange
    H('353a40'): ('heat', H('ffc860')),   # chaingun barrel tip, last row: yellow-hot
    # barrel sides behind the tips: the stripes' own colours, one step off (cold barrels look the same). The heat fades
    # back from the tips by opacity, not by darker reds (until 2026-10-26 these were dark reds, 6e1a0a..., made for an
    # additive glow: blended over the metal they read as a dark smudge on the hot barrels)
    H('4c555a'): ('heat_barrel', H('ff4a14'), 90), H('3b4247'): ('heat_barrel', H('e8400f'), 90),    # 4th row from the tip
    H('4c555b'): ('heat_barrel', H('ff5a18'), 150), H('3b4248'): ('heat_barrel', H('ec4c14'), 150),  # 3rd
    H('4c555c'): ('heat_barrel', H('ff6a1e'), 210), H('3b4249'): ('heat_barrel', H('f05818'), 210),  # under the front collar
    # the front collar around the barrels (its own net; the back plate by the socket has its own cold copy since
    # 2026-10-26): the ring's greys, one step off
    H('89939b'): ('heat_barrel', H('ff7a2a')), H('7d888f'): ('heat_barrel', H('f0601e')), H('6f797f'): ('heat_barrel', H('dd4c14')),
    # EM projector (model/art/em_redstone_art.py): dead redstone, lit red
    H('3a0e0c'): ('em_core', H('7a0600')), H('4b1310'): ('em_core', H('b81206')),
    H('5c1813'): ('em_core', H('f0200c')), H('701f17'): ('em_core', H('ff6a4a')),
    H('4e1411'): ('em_arc', H('c01206')), H('621a14'): ('em_arc', H('ff2a14')),
    # engine backpacks (2026-10-21, model/art/backpack_kit.py GLOW_LIT): the tidal engine's Heart of the Sea in the
    # pump's porthole (teal), the star reactor's core and the energy in its canisters' glass (violet -> white)
    H('173a40'): ('tidal', H('1f8fa8')), H('1d4a50'): ('tidal', H('5fd3e6')), H('275c63'): ('tidal', H('d8fcff')),
    H('2b2238'): ('reactor', H('7a4fd0')), H('362a47'): ('reactor', H('c39bff')), H('433556'): ('reactor', H('fff6ff')),
    # the flamethrower's pilot light (2026-10-25, model/art/flame_drafts_art.py): dark blue when off, lit like the EM tips
    H('1b2638'): ('pilot', H('2a6cff')), H('22304a'): ('pilot', H('8ec2ff')),
    # the rail cannon's core glass between its prongs: its edges and ends (lit by the charge), and its rings in three
    # phases by position along it (ring r is phase r mod 3): lit in turn, the light runs toward the muzzle (flow)
    # the emitter: the hole in the band where the energy comes out (opaque dark blue, lit by the charge)
    H('183850'): ('rail_emit', H('5fc8ff')),
    H('254e73'): ('rail_flow_0', H('9ae8ff')), H('245079'): ('rail_flow_1', H('9ae8ff')), H('26507b'): ('rail_flow_2', H('9ae8ff')),
}
EXPECTED = {'fire': 12, 'visor': 441, 'heat': 16, 'heat_barrel': 120, 'em_core': 30, 'em_arc': 20, 'tidal': 4, 'reactor': 69, 'pilot': 6, 'rail_flow_0': 36, 'rail_flow_1': 36, 'rail_flow_2': 24, 'rail_emit': 2}  # pixels per mask (fire: the backpack's painted firebox, 4 coals x 3 rows (backpack v3, 2026-10-20: the old firebox cube and its 8 vent slits are gone); the visor cube: 7x5 glass front + 59 on its
# other faces, plus 150 left in the texture from the old visor that no cube uses (2026-10-19 export: harmless, never drawn);
# 2026-10-21 export: 540 = 236 of those + 304 on the hidden helmet drafts' faces (proto_helm_*: their visors are glass
# too; since 2026-10-22 they're the helmet styles, helm_*, drawn when worn: their visors glow like the standard one; 8 old leftovers were painted over by the drafts); 2026-10-21 engine
# packs: 498 (the packs' faces were packed over 42 of the never-drawn leftovers; the 398 under cubes are unchanged);
# 2026-10-22 the hunter helmet style (helm_hunter): 527 = + its visor's 42 glass texels - 13 never-drawn leftovers its faces reused;
# 2026-10-25 the flamethrower's faces were packed over 80 never-drawn visor leftovers (447) and 4 never-drawn tidal
# texels (4 left: the porthole's drawn faces; checked against the texture and model before, no drawn texel changed);
# 2026-10-26 the rail cannon's drafts were packed over 6 never-drawn visor leftovers (441; checked: no texel a cube
# draws changed);
# rail_flow_* (2026-10-26, flowing core; its ends are left transparent, so you can look down the slot at the emitter;
# the steady 'rail' mask of its edges and ends is gone): its rings, the full width of its 4 sides
# (4 x 3 x 8 by phase: 36 / 36 / 24; until the same day the two edge columns were steady glass in rail); rail_emit: the
# 2 texels in the middle of the hole under the band nearest the muzzle (shared by the band at the base, where it's hidden);
# pilot: the 1x1x1 tip of the flamethrower's pilot light, 6 faces;
# tidal: the 2x2 porthole x 2 faces' worth (8); reactor: the core's ring + the canisters' glass strips (69);
# 4 barrel sides x 2x2 tips; 4 barrel sides x 2x3 behind them + the front collar's 6x1x6 net, 96;
# em_core: the 3x1x3 lens's faces 30 (the housing and its vent slits went with the shared forearm, 2026-10-18); em_arc: the shared prong net's
# tip, 2x2 end + 4 sides x 2x2)


# Reserved colours that are only light (2026-10-26, user: the rail cannon's core should be see-through when off, "like
# actual energy charging up instead of some blue LEDs"): after the masks are made, these pixels are cleared from the
# base texture, so the cube is invisible (cut out) and only its glow shows. Blockbench keeps them painted (to design with,
# and so the next export brings them back for this script).
SEE_THROUGH = {'rail_flow_0', 'rail_flow_1', 'rail_flow_2'}


def main():
    path = TEX / 'power_armor.png'
    src = Image.open(path).convert('RGBA')
    masks = {name: Image.new('RGBA', src.size, (0, 0, 0, 0)) for name in EXPECTED}
    count = dict.fromkeys(EXPECTED, 0)
    clear = []
    for y in range(src.height):
        for x in range(src.width):
            r, g, b, a = src.getpixel((x, y))
            hit = GLOW.get((r, g, b)) if a else None
            if hit:
                masks[hit[0]].putpixel((x, y), hit[1] + (hit[2] if len(hit) > 2 else 255,))
                count[hit[0]] += 1
                if hit[0] in SEE_THROUGH:
                    clear.append((x, y))
    # Run again on a texture already cleared (no new export): the see-through masks are kept as they are.
    kept = {name for name in SEE_THROUGH if count[name] == 0 and (TEX / f'power_armor_glow_{name}.png').exists()}
    for name in kept:
        count[name] = EXPECTED[name]
    if count != EXPECTED:
        raise SystemExit(f'glow pixels {count}, expected {EXPECTED}: a reserved colour was used elsewhere, or the '
                         f'glowing parts changed (update EXPECTED)')
    for name, im in masks.items():
        if name in kept:
            print(f'kept power_armor_glow_{name}.png (its pixels were already cleared from the texture)')
            continue
        out = TEX / f'power_armor_glow_{name}.png'
        im.save(out)
        print(f'wrote {out.relative_to(ROOT)} ({count[name]} pixels)')
    if clear:
        for x, y in clear:
            src.putpixel((x, y), (0, 0, 0, 0))
        src.save(path)
        print(f'cleared {len(clear)} see-through pixels from {path.relative_to(ROOT)}')

main()
