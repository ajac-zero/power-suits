"""Hand-made 16x16 item icons (textures/item/*.png), vanilla style: flat pixel art, light from the top left, a dark
outline round the silhouette.

    uv run --with pillow python3 model/art/item_icons_art.py [out_dir] [--sheet sheet.png]

Since 2026-10-23 (user: "actual 2D assets instead of generated 3D-to-2D icons") these replace the icons that
tools/make-item-icons.py, make-part-icons.py and make-template-icons.py rendered from the 3D model (32 px, retired).
Each icon is drawn from a few primitives (shaded boxes, cylinders, diagonal bars) plus single pixels, so a tweak is
a number, not a repaint. Run it after changing an icon; nothing else regenerates them.
"""
import math
import sys
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'src/main/resources/assets/powerarmor/textures/item'
N = 16


def hexrgb(h):
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), 255)


def ramp(*hexes):
    return [hexrgb(h) for h in hexes]


# dark -> light
STEEL = ramp('22262e', '3c424c', '58606a', '757c85', '8a9097', '9fa4a9', 'cdcfcf', 'fbf8f0')  # the suit's steel
SLATE = ramp('17191e', '23262d', '2f333b', '3d424b', '4d535c', '61676f')                      # joints, fists, bases
IRON = ramp('121418', '1c2026', '2a3038', '3a424c', '4f5864', '6a7480')                       # dark iron (tanks, barrels)
GLASS = ramp('0d0f14', '161a22', '222936', '33405a', '6f87a8', 'c9dcf0')                       # visor glass
AMBER = ramp('7a3a06', 'b85f0e', 'e8901c', 'ffbf3c', 'ffe28a', 'fff6d0')                       # lit visor
FIRE = ramp('5a1404', 'a8300a', 'e0601a', 'ffa02a', 'ffd55a', 'fff3b0')
DIAMOND = ramp('0b3b38', '127068', '1fae9f', '4ae3cf', '9ff7e8', 'e2fffa')
COPPER = ramp('4a2216', '6d3421', '9c4529', 'c15a36', 'e77c56', 'f6a585')
REDSTONE = ramp('3e0000', '6e0606', 'a50f0f', 'd6261a', 'ff5a42', 'ff9a80')
NETHERITE = ramp('171314', '241e1f', '31292a', '443a3b', '5a4d4e', '736465')
VIOLET = ramp('2a0f45', '4a1d78', '7a35b8', 'a865e8', 'd2a8ff', 'f3e6ff')
AQUA = ramp('0a3a44', '136a78', '1fa0b0', '4fd6e2', 'a8f3f8', 'eafeff')
GOLD = ramp('4a3208', '7a5612', 'a8801e', 'd6aa30', 'f0d060', 'fff2b0')
RED = ramp('3a0a08', '6a1410', '9a2018', 'c8352a', 'e86a5a')
OUTLINE = hexrgb('1a1c22')


class Icon:
    def __init__(self):
        self.px = [[None] * N for _ in range(N)]

    def set(self, x, y, c):
        if 0 <= x < N and 0 <= y < N and c is not None:
            self.px[y][x] = c

    def get(self, x, y):
        return self.px[y][x] if 0 <= x < N and 0 <= y < N else None

    def rect(self, x0, y0, x1, y1, c):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.set(x, y, c)

    def box(self, x0, y0, x1, y1, r, k, cut=0, edge=True):
        """A lit box: fill r[k], top row +2, left column +1, right column -1, bottom row -2 (clamped); cut = corner
        pixels left out (1 = rounded corners)."""
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                dx, dy = min(x - x0, x1 - x), min(y - y0, y1 - y)
                if cut and dx + dy < cut:
                    continue
                i = k
                if edge:
                    if y == y0:
                        i = k + 2
                    elif y == y1:
                        i = k - 2
                    elif x == x0:
                        i = k + 1
                    elif x == x1:
                        i = k - 1
                self.set(x, y, r[max(0, min(len(r) - 1, i))])

    def cyl_v(self, x0, y0, x1, y1, r, k, cut=1):
        """Vertical cylinder: shaded across (light left of centre, dark right), rounded ends."""
        w = x1 - x0
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                dx, dy = min(x - x0, x1 - x), min(y - y0, y1 - y)
                if cut and dx + dy < cut:
                    continue
                t = (x - x0) / max(1, w)  # 0 left .. 1 right
                shade = [1, 2, 1, 0, -1, -2][min(5, int(t * 5.99))]
                self.set(x, y, r[max(0, min(len(r) - 1, k + shade))])

    def diag(self, ax, ay, bx, by, w0, w1, r, k, stripes=None):
        """A bar from a to b (pixel centres), w0 wide at a and w1 at b; lit from the upper left. stripes = (period,
        darker by) rings along the bar."""
        dx, dy = bx - ax, by - ay
        length = math.hypot(dx, dy)
        ux, uy = dx / length, dy / length
        nx, ny = uy, -ux  # perpendicular
        if nx + ny > 0:   # point it to the upper left (the light)
            nx, ny = -nx, -ny
        for y in range(N):
            for x in range(N):
                px, py = x - ax, y - ay
                t = (px * ux + py * uy) / length
                if t < -0.02 or t > 1.02:
                    continue
                s = px * nx + py * ny
                half = (w0 + (w1 - w0) * t) / 2
                if abs(s) > half:
                    continue
                rel = s / max(half, 0.5)  # +1 upper-left edge .. -1 lower-right edge
                i = k + (2 if rel > 0.6 else 1 if rel > 0.1 else 0 if rel > -0.5 else -1)
                if stripes and int(t * length) % stripes[0] == 0:
                    i -= stripes[1]
                self.set(x, y, r[max(0, min(len(r) - 1, i))])

    def outline(self, c=OUTLINE):
        """Darken every empty pixel that touches the silhouette (4-neighbours): vanilla's dark rim."""
        add = []
        for y in range(N):
            for x in range(N):
                if self.px[y][x] is None and any(self.get(x + ex, y + ey) is not None for ex, ey in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                    add.append((x, y))
        for x, y in add:
            self.px[y][x] = c

    def image(self):
        im = Image.new('RGBA', (N, N), (0, 0, 0, 0))
        for y in range(N):
            for x in range(N):
                if self.px[y][x] is not None:
                    im.putpixel((x, y), self.px[y][x])
        return im


# ---- helmets --------------------------------------------------------------------------------------------------------
def shell(ic, r=STEEL):
    """The standard helmet shell, seen from the front: a 13 x 13 box (the 9x9x9 helmet)."""
    ic.box(1, 1, 13, 13, r, 4, cut=1)
    ic.rect(2, 1, 12, 1, r[6])  # bright top edge
    ic.set(2, 2, r[7])


def glass(ic, x0, y0, x1, y1, cut=0):
    ic.box(x0, y0, x1, y1, GLASS, 1, cut=cut, edge=False)
    ic.rect(x0, y1, x1, y1, GLASS[0]) if y1 > y0 else None
    ic.set(x0 + 1, y0, GLASS[4])  # glint, top left
    ic.set(x0 + 2, y0, GLASS[3])
    ic.set(x0 + 1, y0 + 1, GLASS[3]) if y1 > y0 + 1 else None


def helmet_standard(ic):
    shell(ic)
    glass(ic, 2, 4, 12, 6)                       # the visor band
    ic.rect(2, 7, 12, 7, STEEL[2])               # its shadow on the shell
    ic.box(4, 9, 10, 12, STEEL, 3)               # the grill snout
    for x in (5, 7, 9):
        ic.rect(x, 10, x, 11, SLATE[0])


def helmet_protector(ic):  # Subject Delta: a porthole in a riveted frame, three bars across it
    shell(ic)
    ic.box(2, 3, 12, 11, STEEL, 3)               # the frame
    glass(ic, 4, 4, 10, 9)
    for x in (5, 7, 9):
        ic.rect(x, 4, x, 9, STEEL[6])
        ic.set(x, 9, STEEL[4])
    for x, y in ((3, 4), (11, 4), (3, 10), (11, 10), (7, 10)):
        ic.set(x, y, STEEL[7])                   # rivets


def helmet_spartan(ic):  # Master Chief: a band over the top, a wide flat visor with a stepped lower edge
    shell(ic)
    ic.rect(1, 3, 13, 3, STEEL[2])               # the band's lower edge, wrapping the sides
    ic.rect(2, 2, 12, 2, STEEL[5])
    glass(ic, 3, 5, 11, 8)
    ic.rect(3, 8, 3, 8, STEEL[4]); ic.rect(11, 8, 11, 8, STEEL[3])   # the step: visor narrower at the bottom
    ic.rect(4, 9, 10, 9, STEEL[2])
    ic.box(5, 10, 9, 12, STEEL, 3)               # chin
    ic.rect(6, 11, 8, 11, SLATE[1])


def helmet_noble(ic):  # Emile's EVA helmet: a big bubble visor, red bars on the cheeks
    shell(ic)
    ic.rect(2, 2, 12, 2, STEEL[5])
    glass(ic, 3, 3, 11, 10, cut=2)
    ic.set(9, 4, GLASS[4]); ic.set(10, 5, GLASS[3])  # a second streak, top right
    for x0 in (1, 12):
        ic.rect(x0, 10, x0 + 1, 11, RED[3])
        ic.set(x0, 10, RED[4])
    ic.rect(5, 12, 9, 12, STEEL[2])


def helmet_robot(ic):  # Guy-Manuel: a big flat visor in a raised gold U
    shell(ic)
    ic.rect(2, 3, 2, 12, GOLD[4]); ic.rect(12, 3, 12, 12, GOLD[2]); ic.rect(2, 12, 12, 12, GOLD[1])
    ic.rect(3, 12, 11, 12, GOLD[2])
    ic.set(2, 3, GOLD[5])
    glass(ic, 3, 3, 11, 11)
    ic.rect(3, 3, 11, 3, GLASS[2])
    ic.set(4, 4, GLASS[4]); ic.set(5, 4, GLASS[3])


def helmet_hunter(ic):  # Samus: a crest, a painted brow, a visor wrapping round, hoses at the jaw
    shell(ic)
    ic.rect(6, 0, 8, 3, STEEL[3]); ic.rect(6, 0, 6, 3, STEEL[5]); ic.rect(8, 0, 8, 3, STEEL[2])   # crest
    ic.rect(1, 5, 13, 8, GLASS[1])               # the wrap visor, edge to edge
    glass(ic, 2, 5, 12, 8)
    ic.rect(1, 5, 1, 8, GLASS[2]); ic.rect(13, 5, 13, 8, GLASS[0])
    ic.rect(3, 4, 11, 4, STEEL[2])               # the brow over it
    ic.set(7, 4, STEEL[1])
    for x in (3, 4, 10, 11):                     # hoses down the jaw
        ic.rect(x, 10, x, 12, SLATE[3] if x in (3, 10) else SLATE[1])
    ic.rect(6, 11, 8, 11, STEEL[2])

def power_armor(ic):
    """The suit as a bust (helmet, chest, pauldrons): a whole suit at 16 px was a blur of limbs."""
    ic.box(3, 9, 12, 14, STEEL, 4)               # chest
    ic.rect(4, 9, 11, 9, STEEL[6])
    ic.rect(4, 12, 11, 12, STEEL[3])             # plate band
    ic.rect(4, 13, 11, 13, STEEL[5])
    ic.box(4, 1, 11, 8, STEEL, 4, cut=1)         # helmet
    ic.rect(5, 1, 10, 1, STEEL[6]); ic.set(5, 2, STEEL[7])
    glass(ic, 5, 3, 10, 4)                       # visor band
    ic.box(6, 6, 9, 8, STEEL, 3)                 # grill
    ic.set(7, 7, SLATE[0]); ic.set(8, 7, SLATE[0])
    for x0, k in ((1, 5), (11, 4)):              # pauldrons
        ic.box(x0, 7, x0 + 3, 12, STEEL, k, cut=1)
        ic.rect(x0, 10, x0 + 3, 10, STEEL[3])
    ic.rect(5, 9, 5, 14, STEEL[2]); ic.rect(10, 9, 10, 14, STEEL[2])   # where the pads overlap the chest
def suit_torso(ic):
    ic.box(1, 3, 14, 8, STEEL, 4, cut=1)         # chest, shoulder to shoulder
    ic.rect(2, 3, 13, 3, STEEL[6])
    ic.rect(2, 5, 13, 5, STEEL[3])               # plate bands
    ic.rect(2, 6, 13, 6, STEEL[5])
    ic.box(3, 9, 12, 11, STEEL, 3)               # lower chest, narrower
    ic.rect(4, 9, 11, 9, STEEL[2])
    ic.box(4, 0, 11, 2, SLATE, 4)                # collar ring
    ic.rect(6, 1, 9, 2, SLATE[0])                # the neck hole
    ic.box(4, 12, 11, 13, SLATE, 3)              # abdomen
    ic.box(3, 14, 12, 15, STEEL, 3, edge=False)  # belt
    ic.rect(3, 14, 12, 14, STEEL[4])
    ic.rect(7, 14, 8, 15, STEEL[6])              # buckle

def suit_arm(ic):
    ic.box(5, 5, 9, 8, SLATE, 3)                 # upper arm (under the pad)
    ic.rect(5, 8, 9, 8, SLATE[1])                # elbow hinge
    ic.set(7, 8, STEEL[5])
    ic.box(4, 9, 10, 12, STEEL, 4)               # gauntlet
    ic.rect(4, 11, 10, 11, STEEL[3])
    ic.box(4, 13, 10, 15, SLATE, 3)              # fist
    for x in (5, 7, 9):
        ic.set(x, 14, SLATE[1])
    ic.box(2, 0, 12, 5, STEEL, 4, cut=1)         # pauldron
    ic.rect(3, 0, 11, 0, STEEL[6])
    ic.rect(2, 3, 12, 3, STEEL[3])
    ic.rect(3, 4, 11, 4, STEEL[5])


def suit_leg(ic):
    ic.box(4, 0, 10, 6, STEEL, 4, cut=1)         # thigh
    ic.rect(5, 0, 9, 0, STEEL[6])
    ic.rect(4, 3, 10, 3, STEEL[3])
    ic.box(5, 7, 9, 8, SLATE, 3)                 # knee hinge
    ic.set(7, 7, STEEL[6]); ic.set(7, 8, STEEL[4])
    ic.box(4, 9, 10, 12, STEEL, 4)               # shin
    ic.rect(4, 11, 10, 11, STEEL[3])
    ic.box(3, 13, 13, 14, STEEL, 3, cut=1)       # boot, toe forward (right)
    ic.rect(3, 15, 13, 15, SLATE[2])             # sole
    ic.set(12, 13, STEEL[5])


# ---- engines --------------------------------------------------------------------------------------------------------
def firebox(ic):
    ic.cyl_v(2, 0, 4, 5, STEEL, 4)               # exhausts
    ic.cyl_v(11, 0, 13, 5, STEEL, 4)
    ic.rect(2, 0, 4, 0, SLATE[0]); ic.rect(11, 0, 13, 0, SLATE[0])
    ic.box(1, 5, 14, 15, IRON, 3, cut=1)         # the box
    ic.rect(2, 5, 13, 5, IRON[5])
    ic.box(3, 8, 12, 13, IRON, 1, edge=False)    # the firebox window
    for y, c in ((9, FIRE[2]), (10, FIRE[3]), (11, FIRE[4]), (12, FIRE[5])):
        ic.rect(4, y, 11, y, c)
    ic.set(6, 9, FIRE[3]); ic.set(9, 9, FIRE[1]); ic.set(5, 12, FIRE[4]); ic.set(10, 12, FIRE[3])
    for x in (5, 8, 10):
        ic.rect(x, 8, x, 13, IRON[1])            # grate bars
    ic.rect(3, 8, 12, 8, IRON[0])


def tidal_engine(ic):
    for x0 in (0, 9):                            # twin diving tanks
        ic.cyl_v(x0, 0, x0 + 6, 15, IRON, 3, cut=2)
        for y in (3, 12):
            ic.rect(x0, y, x0 + 6, y, STEEL[5])  # steel straps
            ic.set(x0 + 1, y, STEEL[7]); ic.set(x0 + 6, y, STEEL[3])
    ic.box(5, 7, 10, 13, IRON, 2, cut=1)         # the pump between them
    ic.rect(6, 9, 9, 11, AQUA[2])                # porthole
    ic.rect(7, 9, 8, 10, AQUA[4]); ic.set(7, 9, AQUA[5])
    ic.set(6, 11, AQUA[1]); ic.set(9, 9, AQUA[1])


def star_reactor(ic):
    ic.box(4, 5, 11, 15, NETHERITE, 3, cut=1)    # the netherite housing
    for y in (8, 10, 12):
        ic.rect(5, y, 10, y, NETHERITE[1])       # vent grille
    ic.rect(6, 13, 9, 14, VIOLET[3]); ic.rect(7, 13, 8, 13, VIOLET[5]); ic.set(6, 14, VIOLET[2])  # the core's glow
    for x0 in (0, 11):                           # energy canisters
        ic.box(x0, 1, x0 + 4, 2, STEEL, 4)       # caps
        ic.box(x0, 9, x0 + 4, 10, STEEL, 3)
        ic.rect(x0 + 1, 3, x0 + 3, 8, VIOLET[2])
        ic.rect(x0 + 2, 3, x0 + 2, 8, VIOLET[4])
        ic.set(x0 + 2, 5, VIOLET[5]); ic.set(x0 + 1, 7, VIOLET[3])
        ic.rect(x0, 3, x0, 8, STEEL[3]); ic.rect(x0 + 4, 3, x0 + 4, 8, STEEL[2])   # the glass's frame posts


# ---- tools (diagonal like vanilla tools: socket base bottom left, head top right) -------------------------------------
def base(ic):
    ic.box(1, 10, 5, 14, SLATE, 4)               # the socket base (7x1x7 slate on the suit)
    ic.rect(2, 12, 4, 12, SLATE[1])


def drill(ic):
    ic.diag(4.5, 10.5, 7.5, 7.5, 6, 6, STEEL, 4)                     # collar
    ic.diag(7.5, 7.5, 14, 1, 6.2, 0.8, DIAMOND, 3, stripes=(2, 2))   # fluted diamond bit
    ic.set(14, 1, DIAMOND[5])
    base(ic)

def chainsaw(ic):
    # the bar runs along the diagonals x + y = 13..17 from the motor to the tip; the chain wraps it, teeth out
    for x in range(5, 16):
        for d in range(13, 18):
            y = d - x
            if not (0 <= y < N) or x + 0 < 6 and d in (13, 17):
                continue
            if d in (13, 17):
                c = DIAMOND[4] if (x % 2 == 0) == (d == 13) else DIAMOND[1]
            else:
                c = STEEL[5] if d == 14 else STEEL[4] if d == 15 else STEEL[2]
            if x == 15 and d != 15:
                continue
            ic.set(x, y, c)
    for x in range(6, 15, 2):                    # teeth sticking out of the top edge
        ic.set(x, 12 - x, DIAMOND[3])
    ic.diag(4.5, 10.5, 7, 8, 6, 6, STEEL, 3)     # motor housing
    base(ic)

def chaingun(ic):
    # three barrels on the diagonals x + y = 13, 15, 17, dark gaps between them
    for x in range(5, 16):
        for d in range(13, 18):
            y = d - x
            if not (0 <= y < N) or y < 0:
                continue
            if d % 2 == 1:
                ic.set(x, y, IRON[4] if d == 13 else IRON[3] if d == 15 else IRON[2])
            else:
                ic.set(x, y, IRON[0])
        if x == 14:
            pass
    for d in (13, 15, 17):                       # muzzles
        x = min(15, d - 1)
        ic.set(x, d - x, IRON[0])
    ic.diag(9, 6, 10, 5, 6.6, 6.6, STEEL, 4)     # barrel clamp
    ic.diag(4.5, 10.5, 7.5, 7.5, 6.6, 6.6, IRON, 3)  # rotor housing
    base(ic)

def em_projector(ic):
    ic.diag(9, 6, 13, 0.6, 1.3, 1.3, STEEL, 5)                       # the prongs
    ic.diag(10, 7, 15.2, 3, 1.3, 1.3, STEEL, 4)
    ic.diag(4.5, 10.5, 9.5, 5.5, 5.6, 5.0, COPPER, 3, stripes=(2, 2))  # copper coil
    for x, y in ((12, 0), (13, 1), (14, 2), (15, 3)):
        pass
    ic.rect(12, 0, 13, 1, REDSTONE[3]); ic.set(12, 0, REDSTONE[5])   # redstone tips
    ic.rect(14, 2, 15, 3, REDSTONE[3]); ic.set(14, 2, REDSTONE[5]); ic.set(15, 3, REDSTONE[1])
    base(ic)


def flamethrower(ic):
    # the red tank slung under the barrel (lower right of it), then the holed barrel, the nozzle, the slate pilot light
    ic.diag(7.5, 12.5, 12.5, 7.5, 3.6, 3.6, RED, 2)                    # tank
    ic.set(12, 7, RED[4]); ic.set(13, 8, RED[1])
    for x, y in ((9, 11), (11, 9)):                                    # its steel straps
        ic.set(x, y, STEEL[3]); ic.set(x + 1, y, STEEL[2])
    ic.diag(4.5, 10.5, 11, 4, 3.8, 3.8, STEEL, 4)                      # perforated barrel
    for x, y in ((7, 8), (9, 6), (6, 8), (8, 6), (10, 4)):            # its holes, black
        ic.set(x, y, IRON[0])
    ic.diag(11, 4, 14, 1, 2.2, 2.2, STEEL, 3)                          # nozzle
    ic.set(14, 1, IRON[0])                                             # exit hole
    ic.set(13, 4, SLATE[2]); ic.set(14, 3, SLATE[2]); ic.set(15, 2, AQUA[4])   # pilot light, its blue tip
    base(ic)


def tnt_launcher(ic):
    # a fat drum by the base (its front face showing the red ends of the TNT rounds round the barrel), the barrel off
    # centre toward the upper left (on top, like a revolver's), a slate muzzle ring with the bore, the fork sight on top
    ic.diag(4.5, 10.5, 8, 7, 9.0, 9.0, SLATE, 3)                      # drum
    for x, y in ((4, 9), (6, 11), (7, 6)):                             # flutes
        ic.set(x, y, SLATE[0])
    for x, y in ((9, 9), (10, 8), (11, 7), (8, 10)):                   # its front face, dark, between the rounds
        ic.set(x, y, SLATE[1])
    for x, y in ((10, 9), (11, 8)):                                    # the red TNT ends of the rounds below the barrel
        ic.set(x, y, RED[3])
    ic.set(9, 10, RED[2])
    ic.diag(7, 6, 12, 1, 4.4, 4.4, STEEL, 4)                           # barrel
    ic.diag(12, 1, 13.2, -0.2, 5.4, 5.4, SLATE, 3)                     # muzzle ring
    ic.set(14, 1, IRON[0]); ic.set(13, 1, IRON[1]); ic.set(14, 2, IRON[1])   # bore
    ic.set(9, 1, SLATE[4]); ic.set(10, 0, SLATE[4])                    # the fork sight
    base(ic)


# ---- smithing templates: a stone tablet in the style's colour with the helmet's face cut into it ----------------------
def tablet(ic, r):
    """Shaped like vanilla's templates: a slab with an uneven rim (corners broken off)."""
    ic.box(1, 1, 14, 14, r, 3, cut=2)
    for x, y in ((14, 4), (13, 1), (1, 9), (1, 10), (6, 14), (14, 12)):
        ic.px[y][x] = None                        # chips out of the rim
    ic.rect(3, 3, 12, 12, r[2])                  # the sunken face
    ic.rect(3, 3, 12, 3, r[1]); ic.rect(3, 3, 3, 12, r[1])
    ic.rect(4, 12, 12, 12, r[4]); ic.rect(12, 4, 12, 12, r[4])
    for x, y in ((2, 5), (13, 9), (5, 13), (11, 2), (2, 12)):
        ic.set(x, y, r[1])                        # worn specks

def mini_helmet(ic):
    ic.box(4, 4, 11, 11, STEEL, 4, cut=1)
    ic.rect(5, 4, 10, 4, STEEL[6])


def lit(ic, x0, y0, x1, y1):
    ic.rect(x0, y0, x1, y1, AMBER[3])
    ic.rect(x0, y1, x1, y1, AMBER[2])
    ic.set(x0, y0, AMBER[5])


def tone(base_hex, light=None):
    r, g, b = int(base_hex[0:2], 16), int(base_hex[2:4], 16), int(base_hex[4:6], 16)
    out = []
    for f in (0.35, 0.55, 0.75, 1.0, 1.2, 1.45):
        out.append((min(255, int(r * f)), min(255, int(g * f)), min(255, int(b * f)), 255))
    return out


def template(r, face):
    def draw(ic):
        tablet(ic, r)
        mini_helmet(ic)
        face(ic)
    return draw


def t_protector(ic):
    lit(ic, 5, 6, 10, 9)
    for x in (6, 9):
        ic.rect(x, 6, x, 9, STEEL[6])

def t_spartan(ic):
    ic.rect(4, 5, 11, 5, STEEL[2]); lit(ic, 5, 6, 10, 8); ic.rect(6, 9, 9, 9, STEEL[2])


def t_noble(ic):
    lit(ic, 5, 5, 10, 9); ic.set(5, 5, STEEL[4]); ic.set(10, 5, STEEL[4]); ic.set(5, 9, STEEL[4]); ic.set(10, 9, STEEL[4])
    ic.set(4, 10, RED[3]); ic.set(11, 10, RED[3])


def t_robot(ic):
    ic.rect(5, 5, 5, 10, GOLD[3]); ic.rect(10, 5, 10, 10, GOLD[2]); ic.rect(5, 10, 10, 10, GOLD[2])
    lit(ic, 6, 5, 9, 9)


def t_hunter(ic):
    ic.rect(7, 3, 8, 4, STEEL[6])
    ic.rect(4, 7, 11, 8, AMBER[2]); lit(ic, 5, 7, 10, 8); ic.rect(5, 6, 10, 6, STEEL[2])


ICONS = {
    'power_armor': power_armor,
    'suit_helmet': helmet_standard,
    'suit_helmet_protector': helmet_protector,
    'suit_helmet_spartan': helmet_spartan,
    'suit_helmet_noble': helmet_noble,
    'suit_helmet_robot': helmet_robot,
    'suit_helmet_hunter': helmet_hunter,
    'suit_torso': suit_torso,
    'suit_arm': suit_arm,
    'suit_leg': suit_leg,
    'firebox': firebox,
    'tidal_engine': tidal_engine,
    'star_reactor': star_reactor,
    'drill': drill,
    'chainsaw': chainsaw,
    'chaingun': chaingun,
    'em_projector': em_projector,
    'flamethrower': flamethrower,
    'tnt_launcher': tnt_launcher,
    'protector_template': template(tone('8a5a2b'), t_protector),
    'spartan_template': template(tone('5e7a2e'), t_spartan),
    'noble_template': template(tone('5a6470'), t_noble),
    'robot_template': template(tone('b0902a'), t_robot),
    'hunter_template': template(tone('c4501f'), t_hunter),
}


def render(name):
    ic = Icon()
    ICONS[name](ic)
    ic.outline()
    return ic.image()


def sheet(images, path, scale=8):
    """A review sheet: each icon big, on the inventory slot grey, with its 1x and 2x sizes under it."""
    cols = 6
    cell = N * scale + 16
    rows = (len(images) + cols - 1) // cols
    out = Image.new('RGB', (cols * cell, rows * (cell + 52)), (139, 139, 139))
    d = ImageDraw.Draw(out)
    for i, (name, im) in enumerate(images.items()):
        x, y = (i % cols) * cell + 8, (i // cols) * (cell + 52) + 4
        out.paste(im.resize((N * scale, N * scale), Image.NEAREST), (x, y), im.resize((N * scale, N * scale), Image.NEAREST))
        out.paste(im, (x, y + N * scale + 6), im)
        im2 = im.resize((N * 2, N * 2), Image.NEAREST)
        out.paste(im2, (x + 24, y + N * scale + 6), im2)
        d.text((x + 64, y + N * scale + 14), name, fill=(0, 0, 0))
    out.save(path)


if __name__ == '__main__':
    args = sys.argv[1:]
    sheet_path = None
    if '--sheet' in args:
        i = args.index('--sheet')
        sheet_path = args[i + 1]
        del args[i:i + 2]
    out = Path(args[0]) if args else OUT
    out.mkdir(parents=True, exist_ok=True)
    images = {name: render(name) for name in ICONS}
    for name, im in images.items():
        im.save(out / f'{name}.png')
    if sheet_path:
        sheet(images, sheet_path)
    print(f'{len(images)} icons -> {out}')
