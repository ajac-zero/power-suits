"""Builds the suit menu background (textures/gui/power_armor.png) from vanilla's furnace screen.

    uv run --with pillow python3 tools/make-gui.py

Keeps the furnace's frame and player-inventory slots, clears the top half, and draws our parts.
Coordinates must match PowerArmorMenu (slots) and PowerArmorScreen (preview, flame, display).
"""
import io
import zipfile
from glob import glob
from PIL import Image

jar = glob('build/moddev/artifacts/neoforge-*-client-extra-aka-minecraft-resources.jar')[0]
with zipfile.ZipFile(jar) as z:
    furnace = Image.open(io.BytesIO(z.read('assets/minecraft/textures/gui/container/furnace.png'))).convert('RGBA')

DARK, LIGHT, PANEL, SLOT = (55, 55, 55, 255), (255, 255, 255, 255), (198, 198, 198, 255), (139, 139, 139, 255)
img = furnace.copy()
flame_bg = furnace.crop((56, 36, 70, 50))  # the grey unlit flame


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


fill(7, 14, 169, 80, PANEL)                 # clear the furnace parts (keep the inventory label row)
inset(7, 17, 54, 54, (0, 0, 0, 255))        # suit preview window
inset(71, 17, 18, 18, SLOT)                 # helmet slot (item at 72,18)
inset(71, 35, 18, 18, SLOT)                 # socket slot (item at 72,36)
inset(71, 53, 18, 18, SLOT)                 # fuel slot (item at 72,54)
img.paste(flame_bg, (91, 55))               # flame beside the fuel slot (the Pack up button starts at x 108)
inset(94, 17, 76, 35, (24, 26, 24, 255))    # status display (4 lines: hull, fuel, power, ammo)
img.save('src/main/resources/assets/powerarmor/textures/gui/power_armor.png')
print('wrote textures/gui/power_armor.png')
