"""The helmet's edge around the pilot's view (client/SuitVisor, 2026-10-27): the parts of the helmet you see from
inside it, drawn on Minecraft's GUI pixel grid like the pumpkin overlay but only at the edges.

    uv run --with pillow python3 tools/make-visor-textures.py

Writes src/main/resources/assets/powerarmor/textures/gui/visor/:
  corner_top.png / corner_bottom.png   the top-left / bottom-left corner pieces (the right ones are the same, drawn
                                       mirrored), solid black like the pumpkin's overlay: what you see without power
  brow.png                             the middle piece of the top edge (the helmet's visor band), on its own
  <piece>_lip.png                      the piece's steel lip alone: all that's left of it while the suit has power
                                       (the visor shows the view through the metal, 2026-10-27 option B of
                                       .scratch/hud/seethru.py); without power SuitVisor draws the whole piece

The shapes are straight facets (pixel steps, like the suit's octagon joints) after the 2026-10-27 mockups
(.scratch/hud/ours.py): the corners keep the first curve out to 80% of the way to the middle, then run straight to the
screen's edge on their own (u0 = 0.8), so nothing joins them to the brow.
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/powerarmor/textures/gui/visor"
# The solid piece (no power) is pure black and opaque, like the pumpkin's overlay (user, 2026-10-27: dark slate at 96%
# read as grey and see-through). The lip texture keeps its steel: it's what's left of the piece while the visor has power.
BLACK, LIP = (0, 0, 0, 255), (54, 60, 70, 250)

# Each piece: its canvas size and outline polygon (GUI px, top-left piece / bottom-left piece / brow). The top corner
# is 19 px deep at the side and ends 100 px in; the bottom one 28 deep, ending 84 in; the brow 132 wide, 6 deep, with
# 45-degree cut ends.
PIECES = {
    "corner_top": ((101, 21), [(0, 0), (100, 0), (48, 8), (16, 14), (0, 19)]),
    "corner_bottom": ((85, 30), [(0, 29), (84, 29), (48, 21), (16, 11), (0, 1)]),
    "brow": ((132, 8), [(0, 0), (131, 0), (125, 6), (6, 6)]),
}


def grow(mask: Image.Image) -> Image.Image:
    return mask.filter(ImageFilter.MaxFilter(3))


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    for name, (size, poly) in PIECES.items():
        mask = Image.new("L", size, 0)
        ImageDraw.Draw(mask).polygon(poly, fill=255)
        glass = mask.point(lambda v: 255 - v)
        piece = Image.new("RGBA", size, (0, 0, 0, 0))
        lip = Image.new("RGBA", size, (0, 0, 0, 0))
        m, near_glass = mask.load(), grow(glass).load()
        p, l = piece.load(), lip.load()
        for y in range(size[1]):
            for x in range(size[0]):
                if m[x, y]:
                    p[x, y] = BLACK
                    if near_glass[x, y]:
                        l[x, y] = LIP
        piece.save(OUT / f"{name}.png")
        lip.save(OUT / f"{name}_lip.png")
        print("wrote", name, size)


if __name__ == "__main__":
    main()
