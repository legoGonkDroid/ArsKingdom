#!/usr/bin/env python3
"""Renders the CurseForge logo: blocky 3D letters, "Ars" (purple) over "Kingdom" (gold).

Usage: python3 tools/render_logo.py
Output: art/curseforge_logo.png (1024x1024, transparent background)

Each glyph pixel is a cube: a textured front face extruded down-right, so the bottom and right
faces show in darker shades. Drawn at 4x and scaled down for smooth edges.
"""
import random
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "art/curseforge_logo.png"
SIZE = 1024
SS = 4  # supersampling

# 9 rows: 0-1 ascender, 2-6 x-height, 7-8 descender.
GLYPHS = {
    "A": [".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#", ".....", "....."],
    "r": [".....", ".....", "#.##.", "##..#", "#....", "#....", "#....", ".....", "....."],
    "s": [".....", ".....", ".####", "#....", ".###.", "....#", "####.", ".....", "....."],
    "K": ["#...#", "#..#.", "#.#..", "##...", "#.#..", "#..#.", "#...#", ".....", "....."],
    "i": ["#", ".", "#", "#", "#", "#", "#", ".", "."],
    "n": [".....", ".....", "####.", "#...#", "#...#", "#...#", "#...#", ".....", "....."],
    "g": [".....", ".....", ".####", "#...#", "#...#", "#...#", ".####", "....#", "####."],
    "d": ["....#", "....#", ".####", "#...#", "#...#", "#...#", ".####", ".....", "....."],
    "o": [".....", ".....", ".###.", "#...#", "#...#", "#...#", ".###.", ".....", "....."],
    "m": [".....", ".....", "##.#.", "#.#.#", "#.#.#", "#.#.#", "#.#.#", ".....", "....."],
}

# Front-face palettes, dark to light (amethyst-like purple, gold-block gold).
PURPLE = [(74, 38, 120), (94, 52, 150), (112, 66, 176), (132, 86, 198), (160, 118, 220)]
GOLD = [(160, 108, 20), (196, 140, 28), (226, 176, 44), (246, 208, 72), (255, 236, 140)]


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c)


def cells(text):
    """Filled (col, row) cells of a word, glyphs separated by one empty column."""
    out, x = [], 0
    for ch in text:
        g = GLYPHS[ch]
        for r, line in enumerate(g):
            for c, v in enumerate(line):
                if v == "#":
                    out.append((x + c, r))
        x += len(g[0]) + 1
    rows = max(r for _, r in out) + 1
    return out, x - 1, rows


def draw_word(img, text, palette, cell, ox, oy, rng):
    d = ImageDraw.Draw(img)
    filled, _, _ = cells(text)
    fs = set(filled)
    dx, dy = cell * 0.45, cell * 0.6  # extrusion vector (down-right)
    side_r, side_b = shade(palette[1], 0.55), shade(palette[1], 0.38)

    # Side faces, far (up-left) cells first so nearer cells overlap them.
    for c, r in sorted(filled, key=lambda p: p[0] + p[1]):
        x0, y0 = ox + c * cell, oy + r * cell
        x1, y1 = x0 + cell, y0 + cell
        if (c + 1, r) not in fs:
            d.polygon([(x1, y0), (x1 + dx, y0 + dy), (x1 + dx, y1 + dy), (x1, y1)], fill=side_r)
        if (c, r + 1) not in fs:
            d.polygon([(x0, y1), (x1, y1), (x1 + dx, y1 + dy), (x0 + dx, y1 + dy)], fill=side_b)

    # Front faces: 4x4 block texture per cell, lighter toward the top-left, with a bevel.
    t = cell / 4
    for c, r in filled:
        x0, y0 = ox + c * cell, oy + r * cell
        for i in range(4):
            for j in range(4):
                k = 2 + rng.choice([-1, 0, 0, 0, 1])
                if i == 0 or j == 0:
                    k = min(4, k + 1)
                if i == 3 or j == 3:
                    k = max(0, k - 1)
                d.rectangle([x0 + i * t, y0 + j * t, x0 + (i + 1) * t, y0 + (j + 1) * t], fill=palette[k])

    # Dark outline around the front of the letters.
    edge = shade(palette[0], 0.45)
    w = max(1, int(cell * 0.06))
    for c, r in filled:
        x0, y0 = ox + c * cell, oy + r * cell
        x1, y1 = x0 + cell, y0 + cell
        if (c - 1, r) not in fs:
            d.line([(x0, y0), (x0, y1)], fill=edge, width=w)
        if (c + 1, r) not in fs:
            d.line([(x1, y0), (x1, y1)], fill=edge, width=w)
        if (c, r - 1) not in fs:
            d.line([(x0, y0), (x1, y0)], fill=edge, width=w)
        if (c, r + 1) not in fs:
            d.line([(x0, y1), (x1, y1)], fill=edge, width=w)


def main():
    rng = random.Random(7)
    S = SIZE * SS
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))

    top, bottom = "Ars", "Kingdom"
    _, tw, _ = cells(top)
    _, bw, brows = cells(bottom)
    top_rows = 7  # "Ars" has no descender
    bcell = S * 0.84 / bw
    tcell = bcell * 1.5
    gap = bcell * 2.4
    depth = bcell * 0.6
    total_h = top_rows * tcell + gap + brows * bcell + depth
    y = (S - total_h) / 2
    draw_word(img, top, PURPLE, tcell, (S - tw * tcell - tcell * 0.45) / 2, y, rng)
    draw_word(img, bottom, GOLD, bcell, (S - bw * bcell - bcell * 0.45) / 2, y + top_rows * tcell + gap, rng)

    OUT.parent.mkdir(parents=True, exist_ok=True)
    img.resize((SIZE, SIZE), Image.LANCZOS).save(OUT)
    print(f"wrote {OUT}")


if __name__ == "__main__":
    main()
