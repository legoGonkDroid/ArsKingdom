#!/usr/bin/env python3
"""Generates the Mage Tower blueprints (levels 1-3) in the Archwood Collective style, one set per school.

Every school's tower is the same design; only the school's color changes, and it is used for every colored block:
the archwood logs (posts, bands, roof hips, spire), leaves, beds, carpets, the banner, stained-glass windows and the hut.

Size: a 13x26x13 plot for every level. Archwood's guard tower (9x20x9) is the size reference; ours is a little wider
for up to three battlemages (one per tower level) and tall enough for the upper levels.

  * Level 1: a 7x7 two-storey tower on a stone-brick plinth: sourcestone base course, archwood plank walls, school-log
    corner posts and floor bands, an archwood-stair hip roof with school-log hips, a log spire and a magelight.
    Ground floor: hut on the back wall, one bed, rack, chest, two source jars, sconces, a ladder up, a banner over the
    door. Upper floor: bookshelves, a pedestal on a carpet ring, a jar, windows, sconces.
  * Level 2: a third storey (a barracks floor: second bed, chest, carpet, stained-glass windows) under the same roof.
  * Level 3: a fourth barracks storey (third bed) and, instead of the roof, a lookout: a parapet deck round a small
    central roof and spire.
  * Outside (all levels): a cobble/andesite ring path, a path to the door with sconces, magelight lamp posts on the
    plot corners and school-colored archwood shrubs.

Logs are never stripped: the final modpack makes archwood logs glow, and stripping removes that.
Lights are placed lit; the colony (or building) color applies to them once built.

Run from the project root:  python3 tools/gen_magetower_blueprint.py [school ...]   (default: all four)
"""
import sys
from pathlib import Path

from bpgen import (ANDESITE, BOOKSHELF, CHEST, COBBLE, GRASS, Grid, JAR, LAMP_WALL, MAGELIGHT, PEDESTAL, PLANKS,
                   SMOOTH_SOURCE, SOLID_SUB, SOURCE_BRICKS, SOURCE_MOSAIC, STONE_BRICKS, bed, door, ladder, leaves, log,
                   pane, rack, save, sconce, st, stairs)

OUT_DIR = Path("src/main/resources/blueprints/ars_kingdom/arskingdom/military")
SCHOOLS = ["red", "blue", "green", "purple"]
LEVELS = (1, 2, 3)

SX, SY, SZ = 13, 26, 13
GROUND = 1                  # layer 1 is ground level; layer 0 is foundation
C = SX // 2                 # centre line (6)
T0, T1 = C - 3, C + 3       # tower walls x/z 3..9
ANCHOR = (C, 2, T0 + 1)     # hut block on the back wall, facing the door
LADDER_X, LADDER_Z = T1 - 1, C


def band_y(storey):
    """The log band above storey 1.. (y6, y10, y14, y18): the floor of the next storey."""
    return 2 + 4 * storey


def carpet(color):
    return st(f"minecraft:{color}_carpet")


def glass(color, axis):
    if axis == "x":
        return st(f"minecraft:{color}_stained_glass_pane", east=True, west=True, north=False, south=False, waterlogged=False)
    return st(f"minecraft:{color}_stained_glass_pane", north=True, south=True, east=False, west=False, waterlogged=False)


def fence(**sides):
    return st("ars_nouveau:archwood_fence", waterlogged=False, **{d: sides.get(d, False) for d in ("north", "south", "east", "west")})


def build(school, level):
    g = Grid((SX, SY, SZ))
    put = g.put
    accent = lambda axis="y": log(axis, color=school)
    storeys = level + 1

    # ---- ground: grass, a cobble ring path round the tower and a path to the door -------------------------
    for x in range(SX):
        for z in range(SZ):
            put(x, 0, z, SOLID_SUB)
            put(x, GROUND, z, GRASS)
    for x in range(T0 - 1, T1 + 2):
        for z in range(T0 - 1, T1 + 2):
            if x in (T0 - 1, T1 + 1) or z in (T0 - 1, T1 + 1):
                put(x, GROUND, z, COBBLE)
    for z in range(T1 + 1, SZ):
        for x in (C - 1, C, C + 1):
            put(x, GROUND, z, ANDESITE if x == C else COBBLE)

    # ---- plinth and floor ---------------------------------------------------------------------------------
    for x in range(T0, T1 + 1):
        for z in range(T0, T1 + 1):
            wall = x in (T0, T1) or z in (T0, T1)
            centre = abs(x - C) <= 1 and abs(z - C) <= 1
            put(x, GROUND, z, STONE_BRICKS if wall else SOURCE_MOSAIC if centre else SMOOTH_SOURCE)

    corners = {(T0, T0), (T0, T1), (T1, T0), (T1, T1)}

    def walls(y0, y1):
        for y in range(y0, y1 + 1):
            for x in range(T0, T1 + 1):
                for z in range(T0, T1 + 1):
                    if not (x in (T0, T1) or z in (T0, T1)):
                        continue
                    if (x, z) in corners:
                        put(x, y, z, accent())
                    elif y == 2:
                        put(x, y, z, SOURCE_BRICKS)
                    else:
                        put(x, y, z, PLANKS)

    def band(y):
        for x in range(T0, T1 + 1):
            for z in range(T0, T1 + 1):
                if (x, z) in corners:
                    put(x, y, z, accent())
                elif z in (T0, T1):
                    put(x, y, z, accent("x"))
                elif x in (T0, T1):
                    put(x, y, z, accent("z"))
                else:
                    put(x, y, z, PLANKS)

    # ---- storey 1: ground floor (y2..5) ------------------------------------------------------------------
    walls(2, 5)
    for y in (3, 4):                                   # windows: west wall, front either side of the door,
        put(T0, y, C, pane("z"))                       # and the back wall above the hut
        put(C - 2, y, T1, pane("x"))
        put(C + 2, y, T1, pane("x"))
        put(C, y + 1, T0, pane("x"))
    put(C, 2, T1, door("lower"))                       # single door on the centre line under a log lintel
    put(C, 3, T1, door("upper"))
    for x in (C - 1, C, C + 1):
        put(x, 4, T1, accent("x"))
    band(band_y(1))

    ax, ay, az = ANCHOR
    put(ax, ay, az, st(f"ars_kingdom:blockhutmagetower{school}", facing="south"))
    put(T0 + 1, 2, C - 1, bed(school, "north", "head"))   # first battlemage's bed
    put(T0 + 1, 2, C, bed(school, "north", "foot"))
    put(T0 + 1, 2, T0 + 1, JAR)
    put(T1 - 1, 2, T0 + 1, JAR)
    put(T1 - 1, 2, T1 - 1, rack("west"))
    put(T0 + 1, 2, T1 - 1, CHEST)
    put(T0 + 1, 4, T1 - 1, sconce("east"))
    put(T1 - 1, 4, C - 1, sconce("west"))

    # ---- storey 2: study (y7..9) --------------------------------------------------------------------------
    f = band_y(1)
    walls(f + 1, f + 3)
    for y in (f + 1, f + 2):                           # windows; the east wall stays solid for the ladder
        put(C, y, T0, pane("x"))
        put(C, y, T1, pane("x"))
        put(T0, y, C, pane("z"))
    for x in (T0 + 1, T0 + 2):
        put(x, f + 1, T0 + 1, BOOKSHELF)
        put(x, f + 2, T0 + 1, BOOKSHELF)
    for dx in (-1, 0, 1):                              # a school-colored carpet ring round the pedestal
        for dz in (-1, 0, 1):
            if (dx, dz) != (0, 0):
                put(C + dx, f + 1, C + dz, carpet(school))
    put(C, f + 1, C, PEDESTAL)
    put(T1 - 1, f + 1, T0 + 1, JAR)
    put(T0 + 1, f + 2, T1 - 1, sconce("east"))
    put(T1 - 1, f + 2, T1 - 1, sconce("west"))
    band(band_y(2))

    # ---- storeys 3..: barracks, one more battlemage bed per level ------------------------------------------
    for storey in range(3, storeys + 1):
        f = band_y(storey - 1)
        walls(f + 1, f + 3)
        for y in (f + 1, f + 2):                       # school stained-glass windows, east wall solid
            put(C, y, T0, glass(school, "x"))
            put(C, y, T1, glass(school, "x"))
            put(T0, y, C, glass(school, "z"))
        put(T0 + 1, f + 1, C - 1, bed(school, "north", "head"))
        put(T0 + 1, f + 1, C, bed(school, "north", "foot"))
        put(T0 + 1, f + 1, T0 + 1, CHEST)
        for x in range(C - 1, C + 2):                  # carpet runner down the middle
            for z in range(T0 + 1, T1):
                put(x, f + 1, z, carpet(school))
        put(T0 + 1, f + 2, T1 - 1, sconce("east"))
        put(T1 - 1, f + 2, T1 - 1, sconce("west"))
        band(band_y(storey))

    # ---- ladder: against the east wall, through every band, one rung above the top floor -------------------
    top_band = band_y(storeys)
    ladder_top = top_band + 1 if level == 3 else band_y(storeys - 1) + 1
    for y in range(2, ladder_top + 1):
        put(LADDER_X, y, LADDER_Z, ladder("west"))

    # ---- top ---------------------------------------------------------------------------------------------
    def hip_roof(base_y, lo0, hi0):
        ring, y = 0, base_y
        while True:
            lo, hi = lo0 + ring, hi0 - ring
            if lo > hi:
                break
            for x in range(lo, hi + 1):
                for z in range(lo, hi + 1):
                    if not (x in (lo, hi) or z in (lo, hi)):
                        continue
                    if lo == hi:
                        put(x, y, z, PLANKS)
                    elif x in (lo, hi) and z in (lo, hi):
                        put(x, y, z, accent())
                    elif z == lo:
                        put(x, y, z, stairs("south"))
                    elif z == hi:
                        put(x, y, z, stairs("north"))
                    elif x == lo:
                        put(x, y, z, stairs("east"))
                    else:
                        put(x, y, z, stairs("west"))
            ring += 1
            y += 1
        return y - 1

    if level < 3:
        # The top storey is roofed: an overhanging hip roof over the top band.
        top = hip_roof(top_band + 1, T0 - 1, T1 + 1)
    else:
        # Lookout: a parapet deck on the top band (school-log posts, archwood fences; the post by the ladder
        # is solid so the ladder holds) round a small central roof.
        y = top_band + 1
        for x in range(T0, T1 + 1):
            for z in range(T0, T1 + 1):
                if not (x in (T0, T1) or z in (T0, T1)):
                    continue
                if (x, z) in corners or (x, z) == (T1, LADDER_Z) or (x == C or z == C):
                    put(x, y, z, accent())
                else:
                    put(x, y, z, fence(north=x in (T0, T1), south=x in (T0, T1), east=z in (T0, T1), west=z in (T0, T1)))
        put(T1, y + 1, T0, MAGELIGHT)
        put(T0, y + 1, T0, MAGELIGHT)
        put(T0, y + 1, T1, MAGELIGHT)
        put(T1, y + 1, T1, MAGELIGHT)
        top = hip_roof(y, C - 1, C + 1)
    put(C, top + 1, C, accent())
    put(C, top + 2, C, accent())
    put(C, top + 3, C, MAGELIGHT)

    # ---- outside: banner and sconces by the door, lamp posts on the plot corners, school shrubs -----------
    put(C, 5, T1 + 1, st(f"minecraft:{school}_wall_banner", facing="south"))
    put(C - 1, 4, T1 + 1, sconce("south"))
    put(C + 1, 4, T1 + 1, sconce("south"))
    for x, z in [(1, 1), (SX - 2, 1), (1, SZ - 2), (SX - 2, SZ - 2)]:
        put(x, GROUND, z, STONE_BRICKS)
        put(x, 2, z, LAMP_WALL)
        put(x, 3, z, MAGELIGHT)
    for x, z in [(1, C), (SX - 2, C), (C, 1), (C - 2, 1), (C + 2, 1)]:
        put(x, 2, z, leaves(school))
    return g


def main():
    schools = sys.argv[1:] or SCHOOLS
    for school in schools:
        for level in LEVELS:
            name = f"magetower{school}{level}"
            save(OUT_DIR / f"{name}.blueprint", build(school, level), (SX, SY, SZ), ANCHOR, name,
                 f"ars_kingdom:magetower{school}", GROUND)


if __name__ == "__main__":
    main()
