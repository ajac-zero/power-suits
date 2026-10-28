"""Builds the suit menu background (textures/gui/power_armor.png) from vanilla's inventory screen.

    uv run --with pillow python3 tools/make-gui.py

Since 2026-10-27 the suit's menu is the player's inventory with the suit in it (user): E inside the suit opens it, like
a horse's. So it starts from vanilla's inventory.png and keeps its frame, inventory, hotbar, the preview window and the
off-hand slot's frame. The armour column holds the suit's helmet and engine, then the burn flame, then the fuel slot (a
furnace's order: what burns, the flame, the fuel). The column right of the preview mirrors it (user, 2026-10-27): the
Power key opposite the helmet, the socket slot (vanilla's off-hand frame, moved up) opposite the engine, the Pack up key
opposite the fuel; the keys are widgets the game draws (PowerArmorScreen.SuitKey), their symbols are the two sprites
written here. The 2x2 crafting grid and the "Crafting" title make way for the status screen, a terminal over the full
height (PowerArmorScreen draws its text in the visor's colour).
Coordinates must match PowerArmorMenu (slots) and PowerArmorScreen (preview, flame, screen, keys).
"""
import io
import zipfile
from glob import glob
from PIL import Image

jar = glob('build/moddev/artifacts/minecraft-patched-*-merged.jar')[0]
with zipfile.ZipFile(jar) as z:
    inventory = Image.open(io.BytesIO(z.read('assets/minecraft/textures/gui/container/inventory.png'))).convert('RGBA')
    furnace = Image.open(io.BytesIO(z.read('assets/minecraft/textures/gui/container/furnace.png'))).convert('RGBA')

DARK, LIGHT, PANEL, SLOT = (55, 55, 55, 255), (255, 255, 255, 255), (198, 198, 198, 255), (139, 139, 139, 255)
img = inventory.copy()
flame_bg = furnace.crop((56, 36, 70, 50))  # the furnace's grey unlit flame


def fill(x0, y0, x1, y1, c):
    for x in range(x0, x1):
        for y in range(y0, y1):
            img.putpixel((x, y), c)


def inset(x, y, w, h, inside):
    """Vanilla sunken frame: dark top/left edge, white bottom/right edge."""
    fill(x, y, x + w, y + h, inside)
    fill(x, y, x + w - 1, y + 1, DARK)
    fill(x, y, x + 1, y + h - 1, DARK)
    fill(x + 1, y + h - 1, x + w, y + h, LIGHT)
    fill(x + w - 1, y + 1, x + w, y + h, LIGHT)
    img.putpixel((x + w - 1, y), SLOT)
    img.putpixel((x, y + h - 1), SLOT)


fill(95, 4, 172, 81, PANEL)                 # clear the crafting grid, its arrow and result slot, the "Crafting" title
fill(7, 43, 25, 61, PANEL)                  # the third armour slot becomes the flame's place
img.paste(flame_bg, (9, 45))                # flame between the engine (above) and the fuel slot (below)
socket = img.crop((76, 61, 94, 79))         # vanilla's off-hand slot frame...
fill(76, 61, 94, 79, PANEL)                 # ...leaves the bottom (the Pack up key goes there)...
img.paste(socket, (76, 25))                 # ...for the engine's row (the Power key goes over it, opposite the helmet)
inset(97, 7, 72, 72, (6, 10, 8, 255))       # the status screen: near black, a hint of green like an unlit tube
img.save('src/main/resources/assets/powerarmor/textures/gui/power_armor.png')
print('wrote textures/gui/power_armor.png')

# The keys' symbols, white (the screen tints them: grey, or green when the Power key is on), positioned by
# PowerArmorScreen. Power, 9x8 (user's pick D, 2026-10-27, over 2 px stems that looked too heavy): a 1 px stem in a ring
# open at the top; an odd width can't be exactly centred on the 18 px key, it sits half a pixel left. Pack, 8x8 and
# centred: an arrow into a tray, a pixel of air between the arrow's tip and the tray's floor.
SYMBOLS = {
    'power': ["....X....",
              ".X..X..X.",
              "X...X...X",
              "X...X...X",
              "X.......X",
              "X.......X",
              ".X.....X.",
              "..XXXXX.."],
    'pack': ["...XX...",
             "...XX...",
             "...XX...",
             ".XXXXXX.",
             "..XXXX..",
             "X..XX..X",
             "X......X",
             "XXXXXXXX"],
}
for name, rows in SYMBOLS.items():
    sym = Image.new('RGBA', (len(rows[0]), len(rows)), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch == 'X':
                sym.putpixel((x, y), LIGHT)
    path = f'src/main/resources/assets/powerarmor/textures/gui/sprites/container/power_armor/{name}.png'
    import os
    os.makedirs(os.path.dirname(path), exist_ok=True)
    sym.save(path)
    print('wrote', path)
