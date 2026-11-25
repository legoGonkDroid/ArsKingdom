#!/usr/bin/env python3
"""Generates the level-1 Source Sanctum blueprint in the Archwood Collective style.

Size: a 29x23x29 plot, a step below Archwood Collective's town hall (35x27x36). Like the town hall, level 1 uses
only part of the plot, so levels 2 and 3 can grow into it (the plot size must stay the same for every level):
  * a central 11x11 hall: sourcestone base course, purple-archwood log frame, archwood plank walls, tall
    windows, an archwood-stair hip roof with purple log hip lines and a magelight spire;
  * a front porch with a slab canopy on log posts;
  * four corner "source wells" (stone-brick plinths with a log post, magelight and jars), the footings for
    the corner towers of later levels;
  * a fenced courtyard with stone-brick fence pillars, a gate, paths and purple archwood trees.

Logs are never stripped: the final modpack makes archwood logs glow, and stripping removes that.
All four archwood colours are used, one per corner (red NW, blue NE, green SW, purple SE): the hall's corner
posts and roof hip lines, the four corner source wells, and the four garden trees. Purple stays the main
accent (undyed source is purple). Lights are placed lit (light level 15 and a lit block entity, as Archwood
does), so the colony colour applies to them once built.

Run from the project root:  python3 tools/gen_sanctum_blueprint.py
"""
from pathlib import Path

from bpgen import (AIR, ANDESITE, BOOKSHELF, COBBLE, GRASS, JAR, MAGELIGHT, PLANKS, SMOOTH_SOURCE, SOLID_SUB,
                   SOURCE_BRICKS, SOURCE_MOSAIC, STONE_BRICKS, door, leaves, log, pane, save, sconce, slab, st, stairs)

OUT = Path("src/main/resources/blueprints/ars_kingdom/arskingdom/mystic/sourcesanctum1.blueprint")
NAME = "sourcesanctum1"

SX, SY, SZ = 29, 23, 29
GROUND = 1                      # layer 1 is ground level; layer 0 is foundation
C = SX // 2                     # centre line (14)
H0, H1 = C - 5, C + 5           # hall walls x 9..19
Z0, Z1 = C - 6, C + 4           # hall walls z 8..18 (pushed back to leave a front courtyard)
WALL_TOP = 7                    # walls y2..7
BAND = WALL_TOP + 1             # log band / ceiling at y8
ANCHOR = (C, 2, Z0 + 1)         # hut block, against the back wall, facing the door

# One archwood colour per corner of the plot.
NW, NE, SW, SE = "red", "blue", "green", "purple"






def corner_color(x, z, cx, cz):
    """The archwood colour of the plot corner nearest (x, z), seen from (cx, cz)."""
    return {(False, False): NW, (True, False): NE, (False, True): SW, (True, True): SE}[(x > cx, z > cz)]












def build():
    grid = {}

    def put(x, y, z, state):
        if 0 <= x < SX and 0 <= y < SY and 0 <= z < SZ:
            grid[(x, y, z)] = state

    def filled(x, y, z):
        return grid.get((x, y, z), AIR) != AIR

    # ---- ground ------------------------------------------------------------------------------------------
    for x in range(SX):
        for z in range(SZ):
            put(x, 0, z, SOLID_SUB)
            put(x, GROUND, z, GRASS)

    # Hall plinth: andesite ring, stone bricks under the walls, sourcestone floor with a mosaic ring.
    for x in range(H0 - 1, H1 + 2):
        for z in range(Z0 - 1, Z1 + 2):
            edge = x in (H0 - 1, H1 + 1) or z in (Z0 - 1, Z1 + 1)
            wall = x in (H0, H1) or z in (Z0, Z1)
            put(x, GROUND, z, ANDESITE if edge else STONE_BRICKS if wall else SMOOTH_SOURCE)
    for x in range(H0 + 2, H1 - 1):
        for z in range(Z0 + 2, Z1 - 1):
            if x in (H0 + 2, H1 - 2) or z in (Z0 + 2, Z1 - 2):
                put(x, GROUND, z, SOURCE_MOSAIC)

    # Paths: from the gate to the porch, and a ring round the hall linking the four source wells.
    for z in range(Z1 + 2, SZ):
        for x in (C - 1, C, C + 1):
            put(x, GROUND, z, ANDESITE if x == C else COBBLE)
    ring_lo, ring_hi = 4, SX - 5
    for i in range(ring_lo, ring_hi + 1):
        for a, b in [(i, ring_lo), (i, ring_hi), (ring_lo, i), (ring_hi, i)]:
            if grid[(a, GROUND, b)] == GRASS:
                put(a, GROUND, b, COBBLE)

    # ---- front porch -------------------------------------------------------------------------------------
    pz0, pz1 = Z1 + 1, Z1 + 3
    for x in range(C - 2, C + 3):
        for z in range(pz0, pz1 + 1):
            put(x, GROUND, z, STONE_BRICKS)
    for x in (C - 2, C + 2):
        for y in range(2, 6):
            put(x, y, pz1, log())
    for x in range(C - 3, C + 4):
        for z in range(pz0, pz1 + 2):
            put(x, 6, z, slab())
    # Sconces on the hall wall either side of the door, under the canopy.
    put(C - 1, 4, Z1 + 1, sconce("south"))
    put(C + 1, 4, Z1 + 1, sconce("south"))

    # ---- hall walls --------------------------------------------------------------------------------------
    corners = {(H0, Z0), (H0, Z1), (H1, Z0), (H1, Z1)}
    posts = corners | {(C, Z0), (H0, C - 1), (H1, C - 1), (C - 2, Z1), (C + 2, Z1)}
    for y in range(2, WALL_TOP + 1):
        for x in range(H0, H1 + 1):
            for z in range(Z0, Z1 + 1):
                if not (x in (H0, H1) or z in (Z0, Z1)):
                    continue
                if (x, z) in corners:
                    put(x, y, z, log(color=corner_color(x, z, C, (Z0 + Z1) // 2)))
                elif (x, z) in posts:
                    put(x, y, z, log())
                elif y == 2:
                    put(x, y, z, SOURCE_BRICKS)
                else:
                    put(x, y, z, PLANKS)
        if 3 <= y <= 5:  # tall windows
            for x in (H0 + 2, C - 3 + 1, C + 2, H1 - 2):
                if x not in (C - 2, C + 2):
                    put(x, y, Z0, pane("x"))
            for x in (H0 + 2, H1 - 2):
                put(x, y, Z1, pane("x"))
            for z in (Z0 + 2, C - 3, C + 1, Z1 - 2):
                put(H0, y, z, pane("z"))
                put(H1, y, z, pane("z"))
    # Single door on the centre line, framed by plank jambs under a log lintel.
    put(C, 2, Z1, door("lower"))
    put(C, 3, Z1, door("upper"))
    for x in (C - 1, C + 1):
        put(x, 2, Z1, SOURCE_BRICKS)
        put(x, 3, Z1, PLANKS)
    for x in range(C - 1, C + 2):
        put(x, 4, Z1, log("x"))

    # Log band and plank ceiling.
    for x in range(H0, H1 + 1):
        for z in range(Z0, Z1 + 1):
            if (x, z) in corners:
                put(x, BAND, z, log(color=corner_color(x, z, C, (Z0 + Z1) // 2)))
            elif z in (Z0, Z1):
                put(x, BAND, z, log("x"))
            elif x in (H0, H1):
                put(x, BAND, z, log("z"))
            else:
                put(x, BAND, z, PLANKS)

    # ---- hip roof ----------------------------------------------------------------------------------------
    ring, y = 0, BAND + 1
    while True:
        lx, hx, lz, hz = H0 - 1 + ring, H1 + 1 - ring, Z0 - 1 + ring, Z1 + 1 - ring
        if lx > hx or lz > hz:
            break
        for x in range(lx, hx + 1):
            for z in range(lz, hz + 1):
                if not (x in (lx, hx) or z in (lz, hz)):
                    continue
                if lx == hx or lz == hz:
                    put(x, y, z, PLANKS)
                elif x in (lx, hx) and z in (lz, hz):
                    put(x, y, z, log(color=corner_color(x, z, C, (Z0 + Z1) // 2)))
                elif z == lz:
                    put(x, y, z, stairs("south"))
                elif z == hz:
                    put(x, y, z, stairs("north"))
                elif x == lx:
                    put(x, y, z, stairs("east"))
                else:
                    put(x, y, z, stairs("west"))
        ring += 1
        y += 1
    top = y - 1
    put(C, top + 1, Z0 + 5, log())
    put(C, top + 2, Z0 + 5, log())
    put(C, top + 3, Z0 + 5, MAGELIGHT)

    # ---- interior (floor y2) -----------------------------------------------------------------------------
    ax, ay, az = ANCHOR
    put(ax, ay, az, st("ars_kingdom:blockhutsourcesanctum", facing="south"))
    for x in (H0 + 1, H0 + 2, H1 - 2, H1 - 1):          # bookshelves along the back wall
        put(x, 2, Z0 + 1, BOOKSHELF)
        put(x, 3, Z0 + 1, BOOKSHELF)
    for z in (Z0 + 3, Z0 + 5, Z0 + 7):                  # source jars along both side walls
        put(H0 + 1, 2, z, JAR)
        put(H1 - 1, 2, z, JAR)
    cx, cz = C, Z0 + 5                                  # pedestal circle round a ritual brazier
    put(cx, 2, cz, st("ars_nouveau:ritual_brazier"))
    for dx, dz in [(0, -2), (0, 2), (-2, 0), (2, 0)]:
        put(cx + dx, 2, cz + dz, st("ars_nouveau:arcane_pedestal", facing="up", waterlogged=False))
    put(H0 + 1, 2, Z1 - 1, st("minecolonies:blockminecoloniesrack", facing="east", variant="blockrackempty"))
    put(H0 + 2, 2, Z1 - 1, st("minecolonies:blockminecoloniesrack", facing="north", variant="blockrackempty"))
    put(H1 - 1, 2, Z1 - 1, st("ars_nouveau:archwood_chest"))
    for z in (Z0 + 3, Z0 + 7):
        put(H0 + 1, 5, z, sconce("east"))
        put(H1 - 1, 5, z, sconce("west"))
    put(C - 3, 5, Z0 + 1, sconce("south"))
    put(C + 3, 5, Z0 + 1, sconce("south"))

    # ---- four corner source wells (footings for later towers) --------------------------------------------
    for wx, wz in [(4, 4), (SX - 5, 4), (4, SZ - 5), (SX - 5, SZ - 5)]:
        for x in range(wx - 2, wx + 3):
            for z in range(wz - 2, wz + 3):
                edge = abs(x - wx) == 2 or abs(z - wz) == 2
                put(x, GROUND, z, STONE_BRICKS if edge else SOURCE_BRICKS)
        for y in range(2, 6):
            put(wx, y, wz, log(color=corner_color(wx, wz, C, C)))
        put(wx, 6, wz, MAGELIGHT)
        for dx, dz in [(-1, 0), (1, 0), (0, -1), (0, 1)]:
            put(wx + dx, 2, wz + dz, JAR)
        for dx, dz in [(-2, -2), (2, -2), (-2, 2), (2, 2)]:
            put(wx + dx, 2, wz + dz, st("minecraft:stone_brick_wall", up=True, north="none", south="none",
                                         east="none", west="none", waterlogged=False))
            put(wx + dx, 3, wz + dz, MAGELIGHT)

    # ---- courtyard fence with stone-brick pillars and a gate ---------------------------------------------
    ring_cells = [(x, z) for x in range(SX) for z in range(SZ) if x in (0, SX - 1) or z in (0, SZ - 1)]
    gate = {(x, SZ - 1) for x in range(C - 2, C + 3)}
    pillars = {(x, z) for x, z in ring_cells if (x % 7 == 0 and z in (0, SZ - 1)) or (z % 7 == 0 and x in (0, SX - 1))}
    pillars |= {(C - 3, SZ - 1), (C + 3, SZ - 1)}      # gate posts
    fence_cells = set(ring_cells) - gate
    for x, z in fence_cells:
        if (x, z) in pillars:
            put(x, 2, z, STONE_BRICKS)
            put(x, 3, z, STONE_BRICKS)
            put(x, 4, z, MAGELIGHT)
        else:
            n, s_, e, w = ((x, z - 1) in fence_cells, (x, z + 1) in fence_cells,
                           (x + 1, z) in fence_cells, (x - 1, z) in fence_cells)
            put(x, 2, z, st("ars_nouveau:archwood_fence", north=n, south=s_, east=e, west=w, waterlogged=False))
    for x in range(C - 2, C + 3):
        put(x, GROUND, SZ - 1, ANDESITE)

    # ---- purple archwood trees in the side gardens --------------------------------------------------------
    def tree(tx, tz, color, height=6, radius=3):
        for y in range(2, 2 + height):
            put(tx, y, tz, log(color=color))
        for y, r in [(height - 1, radius), (height, radius), (height + 1, radius - 1), (height + 2, 1)]:
            yy = y + 1
            for dx in range(-r, r + 1):
                for dz in range(-r, r + 1):
                    if dx * dx + dz * dz > r * r + 1:
                        continue
                    if dx == 0 and dz == 0 and yy < 2 + height:
                        continue
                    if not filled(tx + dx, yy, tz + dz):
                        put(tx + dx, yy, tz + dz, leaves(color))
    tree(4, C - 3, NW)                      # side gardens, back half: red (west), blue (east)
    tree(SX - 5, C - 3, NE)
    tree(H0 - 2, Z1 + 4, SW, height=4, radius=2)   # flanking the front courtyard: green, purple
    tree(H1 + 2, Z1 + 4, SE, height=4, radius=2)
    for x, z in [(H0 - 2, Z0 - 2), (H1 + 2, Z0 - 2)]:
        put(x, 2, z, leaves(corner_color(x, z, C, C)))
    return grid


def main():
    save(OUT, build(), (SX, SY, SZ), ANCHOR, NAME, "ars_kingdom:sourcesanctum", GROUND)


if __name__ == "__main__":
    main()
