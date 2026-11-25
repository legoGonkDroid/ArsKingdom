#!/usr/bin/env python3
"""Generates the Arcanist Workshop blueprint (level 1) in the Archwood Collective style.

A 9x9 workshop on a 13x13 plot:
  * Foundation: sourcestone large bricks round the edge (and a base course on the walls), archwood plank floor.
  * Frame: purple archwood log corner and mid posts and a log top plate; archwood plank walls; glass windows.
  * Roof: an archwood-stair gable along x with a 1-block overhang, plank gable ends, a log ridge with a magelight.
  * Inside (entering through the south door): the hut on the back (north) wall facing the door, a source jar in each
    back corner (the Arcanist pays source from jars inside the workshop), a scribe's table on the right (east),
    bookshelves on the left (west), a crafting table and a chest by the door, lit sconces.
  * Outside: solid placeholders on the ground, a path to the door with sconces either side.

Logs are never stripped: the final modpack makes archwood logs glow, and stripping removes that.

Run from the project root:  python3 tools/gen_arcanist_blueprint.py
"""
from pathlib import Path

from bpgen import (ANDESITE, BOOKSHELF, CHEST, COBBLE, Grid, JAR, MAGELIGHT, PLANKS, SOLID_SUB, SOURCE_BRICKS, door,
                   log, pane, save, sconce, st, stairs)

OUT = Path("src/main/resources/blueprints/ars_kingdom/arskingdom/mystic/arcanistworkshop1.blueprint")
SX, SY, SZ = 13, 13, 13
GROUND = 1
C = SX // 2                      # 6
X0, X1, Z0, Z1 = 2, 10, 2, 10    # walls
WALL_TOP = GROUND + 5            # y6: log top plate
ANCHOR = (C, GROUND + 1, Z0 + 1)  # hut against the back wall, facing the door
COLOR = "purple"                 # the Workshop serves every school: Ars's own purple


def scribes_table(facing, part):
    return st("ars_nouveau:scribes_table", facing=facing, part=part)


def build():
    g = Grid((SX, SY, SZ))
    put = g.put
    accent = lambda axis="y": log(axis, color=COLOR)

    # ---- ground, foundation, floor --------------------------------------------------------------------------
    for x in range(SX):
        for z in range(SZ):
            put(x, 0, z, SOLID_SUB)
            inside = X0 <= x <= X1 and Z0 <= z <= Z1
            edge = inside and (x in (X0, X1) or z in (Z0, Z1))
            put(x, GROUND, z, SOURCE_BRICKS if edge else PLANKS if inside else SOLID_SUB)
    for z in range(Z1 + 1, SZ):
        for x in (C - 1, C, C + 1):
            put(x, GROUND, z, ANDESITE if x == C else COBBLE)

    # ---- walls: posts at the corners and mid-points, base course, planks, top plate ------------------------
    posts = {(x, z) for x in (X0, C, X1) for z in (Z0, C, Z1)} - {(C, C)}
    for y in range(GROUND + 1, WALL_TOP):
        for x in range(X0, X1 + 1):
            for z in range(Z0, Z1 + 1):
                if x in (X0, X1) or z in (Z0, Z1):
                    put(x, y, z, accent() if (x, z) in posts else SOURCE_BRICKS if y == GROUND + 1 else PLANKS)
    for x in range(X0, X1 + 1):
        for z in range(Z0, Z1 + 1):
            if (x, z) in posts and (x in (X0, X1) and z in (Z0, Z1)):
                put(x, WALL_TOP, z, accent())
            elif z in (Z0, Z1):
                put(x, WALL_TOP, z, accent("x"))
            elif x in (X0, X1):
                put(x, WALL_TOP, z, accent("z"))

    # Windows: either side of the door, and the middle of the side walls' halves.
    for y in (GROUND + 2, GROUND + 3):
        for x in (C - 2, C + 2):
            put(x, y, Z1, pane("x"))
            put(x, y, Z0, pane("x"))
        for z in (C - 2, C + 2):
            put(X1, y, z, pane("z"))

    # Door, under a log lintel.
    put(C, GROUND + 1, Z1, door("lower"))
    put(C, GROUND + 2, Z1, door("upper"))
    put(C, GROUND + 3, Z1, accent("x"))

    # ---- roof: a gable along x, stairs stepping in from both eaves, plank gable ends, a log ridge ----------
    for step in range(0, C - Z0 + 2):
        y = WALL_TOP + step
        zn, zs = Z0 - 1 + step, Z1 + 1 - step
        if zn >= zs:
            for x in range(X0 - 1, X1 + 2):
                put(x, y, zn, accent("x"))
            break
        for x in range(X0 - 1, X1 + 2):
            put(x, y, zn, stairs("south"))
            put(x, y, zs, stairs("north"))
        if step >= 1:                                   # gable ends between the roof slopes
            for z in range(zn + 1, zs):
                put(X0, y, z, PLANKS)
                put(X1, y, z, PLANKS)
    ridge_y = max(y for (x, y, z) in g if g[(x, y, z)] == accent("x") and z == C)
    put(C, ridge_y + 1, C, MAGELIGHT)

    # ---- inside -----------------------------------------------------------------------------------------------
    f = GROUND + 1
    ax, ay, az = ANCHOR
    put(ax, ay, az, st("ars_kingdom:blockhutarcanistworkshop", facing="south"))
    put(X0 + 1, f, Z0 + 1, JAR)                         # back corners: the jars the Arcanist pays from
    put(X1 - 1, f, Z0 + 1, JAR)
    put(X1 - 1, f, C, scribes_table("north", "foot"))   # right (east) wall
    put(X1 - 1, f, C - 1, scribes_table("north", "head"))
    for z in range(C - 1, C + 2):                       # left (west) wall: bookshelves, two high
        put(X0 + 1, f, z, BOOKSHELF)
        put(X0 + 1, f + 1, z, BOOKSHELF)
    put(X1 - 1, f, Z1 - 1, st("minecraft:crafting_table"))
    put(X0 + 1, f, Z1 - 1, CHEST)
    put(C, f + 2, Z1 - 1, sconce("north"))              # over the door
    put(X0 + 1, f + 2, Z0 + 1, sconce("south"))         # over the jars
    put(X1 - 1, f + 2, Z0 + 1, sconce("south"))
    put(X0 + 1, f + 3, C, sconce("east"))
    put(X1 - 1, f + 3, C, sconce("west"))

    # ---- outside: sconces by the door ------------------------------------------------------------------------
    put(C - 1, f + 2, Z1 + 1, sconce("south"))
    put(C + 1, f + 2, Z1 + 1, sconce("south"))
    return g


def main():
    save(OUT, build(), (SX, SY, SZ), ANCHOR, "arcanistworkshop1", "ars_kingdom:arcanistworkshop", GROUND)


if __name__ == "__main__":
    main()
