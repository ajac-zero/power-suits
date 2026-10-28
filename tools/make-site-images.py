"""Web copies of the marketing pictures for the GitHub Pages site (site/img/).

The PNGs in marketing/ are 1-4 MB each; the page loads JPEGs at most 1920 px wide (~200-400 KB) instead.
Re-run after a new screenshot or render:
    uv run --with pillow python3 tools/make-site-images.py
"""
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "site" / "img"

# (source, name on the site, max width)
PICTURES = [
    ("marketing/gallery/1_walk_front.png", "walk_front", 1920),
    ("marketing/gallery/2_dusk_cockpit.png", "dusk_cockpit", 1920),
    ("marketing/gallery/3_mine_cockpit.png", "mine_cockpit", 1920),
    ("marketing/gallery/4_tree_side.png", "tree_side", 1920),
    ("marketing/gallery/5_reef_back.png", "reef_back", 1920),
    ("marketing/gallery/6_em_front.png", "em_front", 1920),
    ("marketing/out/suppressing_fire.png", "suppressing_fire", 2400),
    ("marketing/out/containment.png", "containment", 1920),
    ("marketing/out/big_daddy.png", "big_daddy", 1200),
    ("marketing/out/man_of_tomorrow.png", "man_of_tomorrow", 1920),
]


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for src, name, width in PICTURES:
        im = Image.open(ROOT / src).convert("RGB")
        if im.width > width:
            im = im.resize((width, round(im.height * width / im.width)), Image.LANCZOS)
        dst = OUT / f"{name}.jpg"
        im.save(dst, quality=84, optimize=True, progressive=True)
        print(f"{dst.relative_to(ROOT)}  {im.width}x{im.height}  {dst.stat().st_size // 1024} KB")

    # The mod icon (favicon + header) and the 16 px item icons, copied as they are: the page scales them up
    # with nearest-neighbour (image-rendering: pixelated) so they stay crisp pixel art.
    icon = Image.open(ROOT / "marketing/out/icon.png").convert("RGB")
    icon.resize((256, 256), Image.LANCZOS).save(OUT / "icon.png", optimize=True)
    icon.resize((64, 64), Image.LANCZOS).save(OUT / "favicon.png", optimize=True)
    items = ROOT / "src/main/resources/assets/power_suits/textures/item"
    (OUT / "items").mkdir(exist_ok=True)
    for png in sorted(items.glob("*.png")):
        (OUT / "items" / png.name).write_bytes(png.read_bytes())


if __name__ == "__main__":
    main()
