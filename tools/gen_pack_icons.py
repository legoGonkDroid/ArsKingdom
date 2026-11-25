#!/usr/bin/env python3
"""Draws the blueprint pack icons for "Ars Kingdom (Archwood Collective)".

Original pixel art (Archwood Colonies' own icons are All Rights Reserved and must not be copied), sized like
Archwood's: a 400x400 pack icon and 20x20 category icons with greyed-out "_disabled" twins.

Run from the project root:  python3 tools/gen_pack_icons.py
"""
from pathlib import Path

from PIL import Image, ImageDraw

OUT = Path("src/main/resources/blueprints/ars_kingdom/arskingdom")

# Archwood-ish palette: dark archwood frame, source purple, gilded accents.
FRAME = (58, 40, 30, 255)
FRAME_LIGHT = (96, 68, 48, 255)
PURPLE = (150, 60, 200, 255)
PURPLE_LIGHT = (205, 120, 245, 255)
PURPLE_DARK = (90, 30, 130, 255)
GOLD = (230, 190, 70, 255)
CLEAR = (0, 0, 0, 0)


def grey(im: Image.Image) -> Image.Image:
    out = Image.new("RGBA", im.size)
    for x in range(im.width):
        for y in range(im.height):
            r, g, b, a = im.getpixel((x, y))
            v = int(0.3 * r + 0.59 * g + 0.11 * b) // 2 + 40
            out.putpixel((x, y), (v, v, v, a))
    return out


def gem(d: ImageDraw.ImageDraw, cx: int, cy: int, r: int) -> None:
    """A faceted source gem (diamond) centred on (cx, cy)."""
    d.polygon([(cx, cy - r), (cx + r, cy), (cx, cy + r), (cx - r, cy)], fill=PURPLE, outline=PURPLE_DARK)
    d.polygon([(cx, cy - r), (cx + r, cy), (cx, cy)], fill=PURPLE_LIGHT)
    d.polygon([(cx - r, cy), (cx, cy + r), (cx, cy)], fill=PURPLE_DARK)


def mystic_icon() -> Image.Image:
    im = Image.new("RGBA", (20, 20), CLEAR)
    d = ImageDraw.Draw(im)
    d.rectangle([1, 1, 18, 18], fill=FRAME, outline=FRAME_LIGHT)
    gem(d, 9, 9, 6)
    for x, y in [(3, 3), (16, 3), (3, 16), (16, 16)]:
        im.putpixel((x, y), GOLD)
    return im


def military_icon() -> Image.Image:
    im = Image.new("RGBA", (20, 20), CLEAR)
    d = ImageDraw.Draw(im)
    d.rectangle([1, 1, 18, 18], fill=FRAME, outline=FRAME_LIGHT)
    # A mage's staff: gilded shaft with a gem head.
    d.line([(5, 16), (12, 9)], fill=GOLD, width=2)
    gem(d, 13, 7, 4)
    # Crossed second staff behind it.
    d.line([(15, 16), (8, 9)], fill=FRAME_LIGHT, width=2)
    return im


def pack_icon() -> Image.Image:
    size = 400
    im = Image.new("RGBA", (size, size), CLEAR)
    d = ImageDraw.Draw(im)
    d.ellipse([10, 10, size - 10, size - 10], fill=FRAME, outline=GOLD, width=10)
    d.ellipse([45, 45, size - 45, size - 45], outline=FRAME_LIGHT, width=6)
    gem(d, size // 2, size // 2, 120)
    # Small gems at the four quarters, one per battlemage school colour family.
    for cx, cy in [(size // 2, 75), (size // 2, size - 75), (75, size // 2), (size - 75, size // 2)]:
        gem(d, cx, cy, 22)
    return im


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    pack_icon().save(OUT / "icon.png")
    for name, draw in [("mystic", mystic_icon), ("military", military_icon)]:
        folder = OUT / name
        folder.mkdir(exist_ok=True)
        icon = draw()
        icon.save(folder / "icon.png")
        grey(icon).save(folder / "icon_disabled.png")
    print("wrote icons to", OUT)


if __name__ == "__main__":
    main()
