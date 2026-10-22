"""Draws the helmet styles' smithing template icons (textures/item/<style>_template.png, design sec. 4c).

    uv run --with pillow python3 tools/make-template-icons.py [out_dir]

A template is a slab like vanilla's trim templates (a worn tablet with a rim), tinted per style, with the style's helmet
on it seen straight from the front, its visor lit amber as while piloted (the visor glowmask tinted and added, as
SuitGlowLayer does in game). The helmet is
drawn from the Blockbench source with tools/make-part-icons.py's code (cubes of the style's bone) and
tools/make-item-icons.py's renderer, smaller than an icon so the slab shows round it. Re-run after the helmets' art changes.
"""
import importlib.util
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent


def module(name, file):
    spec = importlib.util.spec_from_file_location(name, ROOT / file)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


parts = module('parts', 'tools/make-part-icons.py')
icons = parts.icons

SIZE = 32
HELMET = 18  # the helmet's size on the slab, px
AMBER = (1.0, 0.68, 0.22)  # the visor's colour while piloted (PowerArmorEntity.VISOR_AMBER)
# style -> the slab's colours (dark, mid, light): a hint at each inspiration's own colours
SLABS = {
    'protector': ('3b2a1a', '6e4f2c', 'a47a45'),  # diving brass
    'spartan': ('2a3220', '4c5a35', '7d8f58'),    # olive drab
    'noble': ('262b30', '4a535b', '7f8a93'),      # grey EVA steel
    'robot': ('3a2f12', '7a6224', 'c9a648'),      # gold
    'hunter': ('4a140c', '9a3418', 'd8662c'),     # the Varia suit's red-orange
}
OUTLINE = (24, 22, 26, 255)


def rgb(h, a=255):
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (a,)


def slab(dark, mid, light):
    """A tablet 26 x 30 px with chipped corners: rim light at the top and left, shadow at the bottom and right."""
    img = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))
    px = img.load()
    x0, y0, x1, y1 = 3, 1, 28, 30
    chipped = {(x0, y0), (x1, y0), (x0, y1), (x1, y1), (x1 - 1, y0), (x0, y1 - 1)}
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            if (x, y) in chipped:
                continue
            edge = x in (x0, x1) or y in (y0, y1) or (x, y) in {(x1 - 1, y0 + 1), (x0 + 1, y1 - 1), (x0 + 1, y0), (x0, y0 + 1),
                                                                (x1, y1 - 1), (x1 - 1, y1)}
            if edge:
                px[x, y] = OUTLINE
            elif x == x0 + 1 or y == y0 + 1:
                px[x, y] = rgb(light)
            elif x == x1 - 1 or y == y1 - 1:
                px[x, y] = rgb(dark)
            else:
                # a faint diagonal grain, like vanilla's worn templates
                px[x, y] = rgb(dark) if (3 * x + 5 * y) % 13 == 0 else rgb(mid)
    return img


def main():
    out_dir = Path(sys.argv[1]) if len(sys.argv) > 1 else icons.ASSETS / 'textures/item'
    outliner, groups, els, tex = parts.load()
    lit = tex.copy()
    for mask in [ROOT / 'src/main/resources/assets/powerarmor/textures/entity/power_armor_glow_visor.png']:
        m = Image.open(mask).convert('RGBA').resize(tex.size, Image.NEAREST)
        px, mp = lit.load(), m.load()
        for y in range(tex.height):
            for x in range(tex.width):
                if mp[x, y][3]:
                    r, g, b, a = px[x, y]
                    px[x, y] = tuple(min(255, int(c + mp[x, y][i] * AMBER[i])) for i, c in enumerate((r, g, b))) + (a,)
    front = icons.rot_y(3.141592653589793)  # the model faces -Z: half a turn shows its face
    for style, colours in SLABS.items():
        cubes = parts.cubes_of(outliner, groups, els, {'helm_' + style}, set())
        size = icons.SIZE
        icons.SIZE = HELMET  # the renderer fits the drawing to SIZE
        try:
            helmet = icons.render_quads(parts.quads(cubes), front, lit)
        finally:
            icons.SIZE = size
        img = slab(*colours)
        img.alpha_composite(helmet, ((SIZE - HELMET) // 2, (SIZE - HELMET) // 2))
        path = out_dir / f'{style}_template.png'
        img.save(path)
        print('wrote', path, len(cubes), 'cubes')


if __name__ == '__main__':
    main()
