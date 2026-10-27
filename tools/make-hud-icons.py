"""The pilot HUD's pixel symbols (client/SuitHud), drawn here as text maps so they're easy to tweak and re-make.

    uv run --with pillow python3 tools/make-hud-icons.py

Writes src/main/resources/assets/powerarmor/textures/gui/hud/:
  suit_<piece>.png      the suit figure by the hull bar, one mask per armour piece, all on one canvas (white: the HUD
                        tints each piece)
  seal.png              the seal shield by HULL
  ember.png             one burn-rate ember, unlit (white mask, tinted dim)
  ember_lit.png         the same ember burning (its own fire colours)
(The socket tools had 16x16 symbols here until 2026-10-27; now each tool draws its own HUD: client/hud/ToolHuds.)
'#' = drawn, anything else = clear.
"""
from pathlib import Path
from PIL import Image

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/powerarmor/textures/gui/hud"

# The suit as the pilot sits in it (seen from behind: its left is the suit's left, the tool arm), blocky like a player
# model: a square head (5 px: half the body's height, narrower than it, like a helmet on a torso; at the body's full
# width it read as big as the body), the body, arms and legs as plain blocks, a 1 px gap between pieces. One letter per armour piece
# (H helmet, C chest, l/r left/right arm, L/R left/right leg): each becomes its own mask, so the HUD can colour every
# piece on its own (today all follow the hull; with modular armour, a critical piece flashes red alone).
FIGURE = """
.....HHHHH.....
.....HHHHH.....
.....HHHHH.....
.....HHHHH.....
.....HHHHH.....
...............
lll.CCCCCCC.rrr
lll.CCCCCCC.rrr
lll.CCCCCCC.rrr
lll.CCCCCCC.rrr
lll.CCCCCCC.rrr
lll.CCCCCCC.rrr
lll.CCCCCCC.rrr
lll.CCCCCCC.rrr
lll.CCCCCCC.rrr
lll.CCCCCCC.rrr
...............
....LLL.RRR....
....LLL.RRR....
....LLL.RRR....
....LLL.RRR....
....LLL.RRR....
....LLL.RRR....
....LLL.RRR....
....LLL.RRR....
....LLL.RRR....
"""
PIECES = {"H": "helmet", "C": "chest", "l": "arm_left", "r": "arm_right", "L": "leg_left", "R": "leg_right"}

# The seal, by HULL: a shield as tall as the font's capitals (6x7 px: an even width, so the lit and shaded halves
# match; amber while sealed, faded like an unlit ember when not). Two shades like the embers: '#' full, '+' 60% grey (the HUD's tint multiplies, so it stays the darker
# half of whatever colour the HUD is): lit from the left, the right side in shade, so it reads as a bevelled plate
# (user's pick 2026-10-27 over a top band, a rim and a centre ridge); the top corners rounded off.
SEAL = """
.##++.
###+++
###+++
###+++
.##++.
.##++.
..#+..
"""
SHADE = 153  # 60% of white

EMBER = """
..#..
.##..
.###.
#####
#####
.###.
"""

def mask(text, colours=None, mark="#"):
    rows = [r for r in text.strip("\n").split("\n")]
    w, h = max(len(r) for r in rows), len(rows)
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch == mark:
                img.putpixel((x, y), colours(x, y) if colours else (255, 255, 255, 255))
    return img


def main():
    # the socket tools' 16x16 symbols until 2026-10-27 (each tool now draws its own HUD: client/hud)
    if (OUT / "tool").exists():
        for old in (OUT / "tool").glob("*.png"):
            old.unlink()
        (OUT / "tool").rmdir()
    (OUT / "suit.png").unlink(missing_ok=True)  # the one-piece silhouette before 2026-10-27
    for letter, name in PIECES.items():
        mask(FIGURE, mark=letter).save(OUT / f"suit_{name}.png")
    seal = mask(SEAL)
    for y, row in enumerate(SEAL.strip("\n").split("\n")):
        for x, ch in enumerate(row):
            if ch == "+":
                seal.putpixel((x, y), (SHADE, SHADE, SHADE, 255))
    seal.save(OUT / "seal.png")
    mask(EMBER).save(OUT / "ember.png")
    # burning: a yellow tip over an orange body, like the firebox's own glow
    mask(EMBER, lambda x, y: (255, 220, 90, 255) if y < 3 else (255, 130, 30, 255)).save(OUT / "ember_lit.png")
    print("wrote", OUT)


if __name__ == "__main__":
    main()
