"""Shared helpers for our blueprint generators: block states in the Archwood Collective palette and the
Structurize blueprint writer (palette indices packed two shorts per int in y, z, x order)."""
import nbt
from nbt import Byte, Int, IntArray, List, Short

PACK = "Ars Kingdom (Archwood Collective)"
AIR = ("minecraft:air", {})


def st(name, **props):
    return name, {k: str(v).lower() for k, v in props.items()}


SOLID_SUB = st("structurize:blocksolidsubstitution")
GRASS = st("minecraft:grass_block", snowy=False)
ANDESITE = st("minecraft:polished_andesite")
COBBLE = st("minecraft:cobblestone")
STONE_BRICKS = st("minecraft:stone_bricks")
SOURCE_BRICKS = st("ars_nouveau:sourcestone_large_bricks")
SMOOTH_SOURCE = st("ars_nouveau:smooth_sourcestone")
SOURCE_MOSAIC = st("ars_nouveau:sourcestone_mosaic")
PLANKS = st("ars_nouveau:archwood_planks")
BOOKSHELF = st("minecraft:bookshelf")
MAGELIGHT = st("ars_nouveau:magelight_torch", facing="north", floor=True, roof=False, level=15)
JAR = st("ars_nouveau:source_jar", fill=0)
CHEST = st("ars_nouveau:archwood_chest")
PEDESTAL = st("ars_nouveau:arcane_pedestal", facing="up", waterlogged=False)
LAMP_WALL = st("minecraft:stone_brick_wall", up=True, north="none", south="none", east="none", west="none", waterlogged=False)


def log(axis="y", color="purple"):
    """Archwood log, bark on (never stripped: the pack makes archwood logs glow)."""
    return st(f"ars_nouveau:{color}_archwood_log", axis=axis)


def leaves(color="purple"):
    return st(f"ars_nouveau:{color}_archwood_leaves", persistent=True)


def stairs(facing, half="bottom"):
    return st("ars_nouveau:archwood_stairs", facing=facing, half=half, shape="straight", waterlogged=False)


def slab(kind="bottom"):
    return st("ars_nouveau:archwood_slab", type=kind, waterlogged=False)


def pane(axis):
    if axis == "x":  # wall runs along x: connect east/west
        return st("minecraft:glass_pane", east=True, west=True, north=False, south=False, waterlogged=False)
    return st("minecraft:glass_pane", north=True, south=True, east=False, west=False, waterlogged=False)


def door(half, hinge="left", facing="north"):
    return st("ars_nouveau:archwood_door", facing=facing, half=half, hinge=hinge, open=False, powered=False)


def sconce(facing):
    return st("ars_nouveau:archwood_sconce", facing=facing, level=15)


def rack(facing):
    return st("minecolonies:blockminecoloniesrack", facing=facing, variant="blockrackempty")


def ladder(facing):
    return st("minecraft:ladder", facing=facing, waterlogged=False)


def bed(color, facing, part):
    return st(f"minecraft:{color}_bed", facing=facing, part=part, occupied=False)


class Grid(dict):
    """(x, y, z) -> block state, clipped to the plot."""

    def __init__(self, size):
        super().__init__()
        self.size = size

    def put(self, x, y, z, state):
        sx, sy, sz = self.size
        if 0 <= x < sx and 0 <= y < sy and 0 <= z < sz:
            self[(x, y, z)] = state

    def filled(self, x, y, z):
        return self.get((x, y, z), AIR) != AIR


def to_blueprint(grid, size, anchor, name, hut_type, ground, hut_te_extra=None):
    """Structurize blueprint NBT. The hut block entity at `anchor` carries the building type, pack, corners and a
    `groundlevel` tag at layer `ground`. Sconces and magelights get lit block entities with Ars's default colour."""
    sx, sy, sz = size
    palette, index = [], {}

    def pid(state):
        key = (state[0], tuple(sorted(state[1].items())))
        if key not in index:
            index[key] = len(palette)
            entry = {"Name": state[0]}
            if state[1]:
                entry = {"Properties": dict(sorted(state[1].items())), "Name": state[0]}
            palette.append(entry)
        return index[key]

    pid(AIR)
    shorts = []
    for y in range(sy):
        for z in range(sz):
            for x in range(sx):
                shorts.append(pid(grid.get((x, y, z), AIR)))
    if len(shorts) % 2:
        shorts.append(0)
    ints = []
    for i in range(0, len(shorts), 2):
        v = (shorts[i] << 16) | shorts[i + 1]
        ints.append(v - (1 << 32) if v >= 1 << 31 else v)

    ax, ay, az = anchor
    rel = lambda x, y, z: {"x": Int(x), "y": Int(y), "z": Int(z)}
    default_color = {"r": Int(255), "g": Int(25), "b": Int(180), "type": "ars_nouveau:constant"}
    light_tes = []
    for (x, y, z), state in sorted(grid.items()):
        if state[0].endswith("sconce"):
            light_tes.append({"color": dict(default_color), "lit": Byte(1), "x": Short(x), "y": Short(y), "z": Short(z),
                              "id": "ars_nouveau:sconce"})
        elif state[0] == "ars_nouveau:magelight_torch":
            light_tes.append({"color": dict(default_color), "lit": Byte(1), "horizontalFire": Byte(0),
                              "x": Short(x), "y": Short(y), "z": Short(z), "id": "ars_nouveau:magelight_torch"})
    hut_te = {
        "id": "ars_kingdom:colony_building",
        "type": hut_type,
        "colony": Int(0),
        "rotMir": Byte(0),
        "pack": PACK,
        "path": name + ".blueprint",
        "blueprintDataProvider": {
            "corner1": rel(-ax, -ay, -az),
            "corner2": rel(sx - 1 - ax, sy - 1 - ay, sz - 1 - az),
            "path": name + ".blueprint",
            "schematicName": name,
            "posTagMap": List(nbt.COMPOUND, [{
                "tagPos": rel(0, ground - ay, 0),
                "tagNameList": List(nbt.COMPOUND, [{"tagName": "groundlevel"}]),
            }]),
            "pack": PACK,
        },
        "x": Short(ax), "y": Short(ay), "z": Short(az),
    }
    if hut_te_extra:
        hut_te.update(hut_te_extra)

    def nbt_palette(p):
        out = {}
        if "Properties" in p:
            out["Properties"] = {k: v for k, v in p["Properties"].items()}
        out["Name"] = p["Name"]
        return out

    mods = sorted({p["Name"].split(":")[0] for p in palette} - {"minecraft", "structurize"})
    return {
        "size_x": Short(sx), "size_y": Short(sy), "size_z": Short(sz),
        "required_mods": List(nbt.STRING, mods),
        "entities": List(nbt.COMPOUND, []),
        "blocks": IntArray(ints),
        "tile_entities": List(nbt.COMPOUND, [hut_te] + light_tes),
        "name": name + ".blueprint",
        "palette": List(nbt.COMPOUND, [nbt_palette(p) for p in palette]),
        "version": Byte(1),
        "mcversion": Int(3955),  # 1.21.1 data version
        "architects": List(nbt.STRING, ["legoGonkDroid"]),
        "optional_data": {"structurize": {"primary_offset": rel(ax, ay, az)}},
    }


def save(path, grid, size, anchor, name, hut_type, ground):
    stripped = [s for s in grid.values() if "stripped" in s[0]]
    assert not stripped, "no stripped logs: the pack makes archwood logs glow"
    bp = to_blueprint(grid, size, anchor, name, hut_type, ground)
    path.parent.mkdir(parents=True, exist_ok=True)
    nbt.save(path, bp)
    print(f"wrote {path}: {size[0]}x{size[1]}x{size[2]}, {len(bp['palette'].v)} palette entries, mods {bp['required_mods'].v}")
    return bp
