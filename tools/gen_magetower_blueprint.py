#!/usr/bin/env python3
"""Generates the Mage Tower blueprints in the Archwood Collective style.

The design is the same for every school; only the school's color changes (the archwood logs, the bed and the hut).

Level 1: a round tower, 11 blocks across, two storeys of 8 blocks each (floor block + 7 blocks of headroom).
  * Foundation: a disc of sourcestone large bricks at ground level (also the ground-floor floor).
  * Frame: school-colored archwood log posts round the wall and log bands at the upper floor and the top;
    walls of archwood planks.
  * Roof: a flat, open-air deck of archwood planks with a school-log railing round the edge (magelights on the
    four diagonal posts), reached by the ladder.
  * Ground floor (entering through the south door): the hut on the left (west) wall, a ladder straight across
    from the door (north wall), and on the right (east), from the door inwards: a source jar, a scribe's table
    and an arcane pedestal.
  * Upper floor: the battlemage's bed in the school color.
  * Lighting (both floors): sconces on both interior faces of the four diagonal posts, 3 and 6 blocks above the floor.

Level 2: level 1 plus
  * Sourcestone large bricks start replacing the archwood planks: the ground-floor walls.
  * An outside staircase, two blocks wide, hugs the wall: sourcestone slabs rising half a block per step from just
    right of the door path (seen from outside), counter-clockwise almost all the way round, to a gap in the deck's
    railing. Its outer edge has a railing of sourcestone blocks with Ars Additions sourcestone walls on top.
  * Carpet in the school color on both floors, a school banner above the door outside.
  * Magelights on the staircase railing every 6 steps.

Plot: 19x20x19 at every level (the 11-wide tower, the staircase and its railing round it, and a block of ground).

Logs are never stripped: the final modpack makes archwood logs glow, and stripping removes that.
Lights are placed lit; the colony (or building) color applies to them once built.

Run from the project root:  python3 tools/gen_magetower_blueprint.py [school ...]   (default: red)
"""
import math
import sys
from pathlib import Path

from bpgen import (AIR, ANDESITE, COBBLE, Grid, JAR, MAGELIGHT, PEDESTAL, PLANKS, SOLID_SUB, SOURCE_BRICKS, bed,
                   door, ladder, log, pane, save, sconce, st)

OUT_DIR = Path("src/main/resources/blueprints/ars_kingdom/arskingdom/military")
SCHOOLS = ["red"]
LEVELS = (1, 2)

SX, SY, SZ = 19, 20, 19
GROUND = 1                  # layer 1 is ground level; layer 0 is foundation fill
C = SX // 2                 # centre (9)
RADIUS = 5.5                # 11 blocks across
STOREY = 8                  # floor block + 7 blocks of headroom
FLOOR1 = GROUND             # y1: the sourcestone foundation is the ground floor
FLOOR2 = FLOOR1 + STOREY    # y9
TOP = FLOOR2 + STOREY       # y17: the top band and the roof deck
ANCHOR = (C - 4, FLOOR1 + 1, C)   # hut against the west (left) wall, facing into the room

SOURCESTONE = st("ars_nouveau:sourcestone")

# Log posts round the ring (the 8 corners of the circle's outline and its 4 diagonals), as offsets from the centre.
# The south door sits between the posts at (-2, 5) and (2, 5).
DIAGONAL_POSTS = {(C + dx, C + dz) for dx, dz in ((-3, -3), (3, -3), (3, 3), (-3, 3))}
POSTS = {(C + dx, C + dz) for dx, dz in ((-2, -5), (2, -5), (5, -2), (5, 2), (2, 5), (-2, 5), (-5, 2), (-5, -2))} \
    | DIAGONAL_POSTS


def dist2(x, z):
    return (x - C) ** 2 + (z - C) ** 2


def inside(x, z, r=RADIUS):
    return dist2(x, z) <= r * r


def is_wall(x, z):
    """The tower's outer ring: inside the circle with an outside block among its 8 neighbours (no diagonal gaps)."""
    return inside(x, z) and not all(inside(x + a, z + b) for a in (-1, 0, 1) for b in (-1, 0, 1))


def is_room(x, z):
    return inside(x, z) and not is_wall(x, z)


def band_axis(x, z):
    """A horizontal log in the ring runs along the wall: along z on the east/west sides, along x on north/south."""
    dx, dz = x - C, z - C
    return "z" if abs(dx) > abs(dz) else "x"


SLAB_STEPS_PER_BLOCK = 2           # the outside staircase rises half a block per step
STAIR_LANE = (RADIUS, RADIUS + 2)   # two blocks wide, hugging the wall
STAIR_RAIL = RADIUS + 3             # the railing: one more block out
STAIR_START = math.atan2(6.3, 1.5)  # just right (east) of the door path, seen from outside
STAIR_STEP = math.radians(10.6)     # angle per step: adjacent lane blocks (>= 5.5 out) never differ by > 1 step


def stone_slab(kind):
    return st("ars_nouveau:sourcestone_slab", type=kind, waterlogged=False)


def stone_wall(**sides):
    return st("ars_additions:sourcestone_wall", up=True, waterlogged=False,
              **{d: sides.get(d, "none") for d in ("north", "south", "east", "west")})


def carpet(color):
    return st(f"minecraft:{color}_carpet")


def stair_step(x, z):
    """The outside staircase's step index at a block: counter-clockwise on screen (x east, z south: decreasing
    angle) from the start line just right of the door path."""
    a = math.atan2(z - C, x - C)
    return int(((STAIR_START - a) % (2 * math.pi)) // STAIR_STEP)


def step_height(i):
    """(y, slab type) of step i: half a block per step from the first step above the ground."""
    return GROUND + 1 + i // SLAB_STEPS_PER_BLOCK, ("bottom" if i % SLAB_STEPS_PER_BLOCK == 0 else "top")


def scribes_table(facing, part):
    """Ars's two-block table: the foot at a position, the head one block towards `facing`."""
    return st("ars_nouveau:scribes_table", facing=facing, part=part)


def build(school, level):
    g = Grid((SX, SY, SZ))
    put = g.put
    accent = lambda axis="y": log(axis, color=school)
    cells = [(x, z) for x in range(SX) for z in range(SZ)]
    ring = [(x, z) for x, z in cells if is_wall(x, z)]
    room = [(x, z) for x, z in cells if is_room(x, z)]

    # ---- ground: fill, solid placeholders round the tower, a path to the door, the sourcestone foundation ---------------
    for x, z in cells:
        put(x, 0, z, SOLID_SUB)
        put(x, GROUND, z, SOURCE_BRICKS if inside(x, z) else SOLID_SUB)   # outside: solid placeholder
    for z in range(C + 6, SZ):
        for x in (C - 1, C, C + 1):
            put(x, GROUND, z, ANDESITE if x == C else COBBLE)

    # ---- walls: planks, log posts, log bands at the upper floor and the top ---------------------------------
    for y in range(FLOOR1 + 1, TOP):
        wall = SOURCE_BRICKS if level >= 2 and y < FLOOR2 else PLANKS   # level 2: sourcestone ground floor
        for x, z in ring:
            put(x, y, z, accent() if (x, z) in POSTS else wall)
    for y in (FLOOR2, TOP):
        for x, z in ring:
            put(x, y, z, accent() if (x, z) in POSTS else accent(band_axis(x, z)))

    # Upper floor: archwood planks inside the band.
    for x, z in room:
        put(x, FLOOR2, z, PLANKS)

    # ---- door (south), with a log lintel and sconces outside ------------------------------------------------
    dz = C + 5
    put(C, FLOOR1 + 1, dz, door("lower"))
    put(C, FLOOR1 + 2, dz, door("upper"))
    for x in (C - 1, C, C + 1):
        put(x, FLOOR1 + 3, dz, accent("x"))
    put(C - 1, FLOOR1 + 3, dz + 1, sconce("south"))
    put(C + 1, FLOOR1 + 3, dz + 1, sconce("south"))

    # ---- windows: west and east on both floors, plus the front upstairs. The back (north) wall stays solid planks:
    # the ladder hangs on it all the way up, and a ladder can't hang on glass -------------------------
    for y in (FLOOR1 + 3, FLOOR1 + 4):
        put(C - 5, y, C, pane("z"))
        put(C + 5, y, C, pane("z"))
    for y in (FLOOR2 + 3, FLOOR2 + 4):
        put(C - 5, y, C, pane("z"))
        put(C + 5, y, C, pane("z"))
        put(C, y, C + 5, pane("x"))

    # ---- ground floor ---------------------------------------------------------------------------------------
    f = FLOOR1 + 1
    ax, ay, az = ANCHOR
    put(ax, ay, az, st(f"ars_kingdom:blockhutmagetower{school}", facing="east"))   # left wall
    put(C + 4, f, C + 1, JAR)                                                       # right wall, door end
    put(C + 4, f, C, scribes_table("north", "foot"))
    put(C + 4, f, C - 1, scribes_table("north", "head"))
    put(C + 3, f, C - 2, PEDESTAL)                                                  # right wall, back nook

    # ---- ladder: straight across from the door, up through the upper floor ----------------------------------
    for y in range(f, TOP + 2):                        # up through the upper floor and onto the roof deck
        put(C, y, C - 4, ladder("south"))

    # ---- upper floor: the battlemage's bed --------------------------------------------------------------------
    u = FLOOR2 + 1
    put(C - 4, u, C, bed(school, "west", "head"))
    put(C - 3, u, C, bed(school, "west", "foot"))

    # ---- sconces: on both interior faces of every post that stands into the room (the four diagonal posts),
    # at 3 and 6 blocks above each floor: 4 posts x 2 faces x 2 heights = 16 per floor --------------------------
    for floor in (FLOOR1, FLOOR2):
        for px, pz in POSTS:
            for name, (ddx, ddz) in (("east", (1, 0)), ("west", (-1, 0)), ("south", (0, 1)), ("north", (0, -1))):
                if is_room(px + ddx, pz + ddz):
                    for h in (3, 6):
                        put(px + ddx, floor + h, pz + ddz, sconce(name))

    # ---- roof deck: flat, open air, a log railing round the edge, reached by the ladder --------------------
    for x, z in room:
        if (x, z) != (C, C - 4):                       # the ladder comes up through the deck
            put(x, TOP, z, PLANKS)
    for x, z in ring:
        put(x, TOP + 1, z, accent() if (x, z) in POSTS else accent(band_axis(x, z)))
    for x, z in DIAGONAL_POSTS:                         # magelights on the diagonal posts keep the deck lit
        put(x, TOP + 2, z, MAGELIGHT)

    if level >= 2:

        # Carpet in the school color on every free floor cell of both floors.
        for y in (FLOOR1 + 1, FLOOR2 + 1):
            for x, z in room:
                if not g.filled(x, y, z):
                    put(x, y, z, carpet(school))

        # School banner above the door, outside.
        put(C, FLOOR1 + 5, C + 6, st(f"minecraft:{school}_wall_banner", facing="south"))

        # Outside staircase: two blocks wide round the wall, half a block per step (sourcestone slabs) from just
        # right of the door path, counter-clockwise up to the deck's height; a railing of sourcestone blocks with
        # sourcestone walls on top along its outer edge; a capped end, and a gap in the deck railing to step through.
        steps = (TOP - GROUND) * SLAB_STEPS_PER_BLOCK          # 32: the top step's surface is level with the deck
        lane = [(x, z) for x, z in cells if STAIR_LANE[0] ** 2 < dist2(x, z) <= STAIR_LANE[1] ** 2]
        rail = [(x, z) for x, z in cells if STAIR_LANE[1] ** 2 < dist2(x, z) <= STAIR_RAIL ** 2]
        top_lane = []
        for x, z in lane:
            i = stair_step(x, z)
            if i < steps:
                y, kind = step_height(i)
                put(x, y, z, stone_slab(kind))
                if i == steps - 1:
                    top_lane.append((x, z))
            elif i == steps:                                    # end cap, level with the top step
                put(x, TOP, z, SOURCESTONE)
                put(x, TOP + 1, z, stone_wall())
        walls = {}
        for x, z in rail:
            i = stair_step(x, z)
            if i <= steps:
                y = step_height(min(i, steps - 1))[0]
                put(x, y, z, SOURCESTONE)
                walls[(x, z)] = y + 1
        lit = {}                                                # magelights on the railing every 6 steps
        for (x, z), y in walls.items():
            i = stair_step(x, z)
            if i % 6 == 0 and i < steps:
                if i not in lit or dist2(x, z) < dist2(*lit[i]):
                    lit[i] = (x, z)
        for x, z in lit.values():
            put(x, walls[(x, z)] + 1, z, MAGELIGHT)
        for (x, z), y in walls.items():                         # connect neighbouring walls at the same height
            sides = {d: "low" for d, (a, b) in (("east", (1, 0)), ("west", (-1, 0)), ("south", (0, 1)), ("north", (0, -1)))
                     if walls.get((x + a, z + b)) == y}
            put(x, y, z, stone_wall(**sides))
        for x, z in ring:                                       # gap in the deck railing beside the top step
            if (x, z) not in POSTS and any((x + a, z + b) in top_lane for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                put(x, TOP + 1, z, AIR)
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
