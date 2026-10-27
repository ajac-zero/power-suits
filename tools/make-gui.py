"""Builds the suit menu background (textures/gui/power_armor.png) from vanilla's inventory screen.

    uv run --with pillow python3 tools/make-gui.py

Since 2026-10-27 the suit's menu is the player's inventory with the suit in it (user): E inside the suit opens it, like
a horse's. So it starts from vanilla's inventory.png and keeps its frame, inventory, hotbar, the preview window and the
off-hand slot (the socket tool goes there: it's the suit's off hand). The armour column holds the suit's helmet and
engine, then the burn flame, then the fuel slot (a furnace's order: what burns, the flame, the fuel); the 2x2 crafting
grid makes way for a status display (the Power and Pack up buttons go under it; they're widgets, drawn by the game).
Coordinates must match PowerArmorMenu (slots) and PowerArmorScreen (preview, flame, display, buttons).
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


fill(95, 7, 172, 81, PANEL)                 # clear the crafting grid, its arrow and result slot (not the off-hand slot)
fill(7, 43, 25, 61, PANEL)                  # the third armour slot becomes the flame's place
img.paste(flame_bg, (9, 45))                # flame between the engine (above) and the fuel slot (below)
inset(97, 17, 72, 29, (24, 26, 24, 255))    # status display: three lines (hull, fuel, power)
img.save('src/main/resources/assets/powerarmor/textures/gui/power_armor.png')
print('wrote textures/gui/power_armor.png')
