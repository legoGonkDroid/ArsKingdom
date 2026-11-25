"""Minimal NBT reader/writer for Structurize .blueprint files (gzipped NBT).

Typed values are kept explicit so files round-trip exactly:
  Byte(v), Short(v), Int(v), Long(v), Float(v), Double(v), str, ByteArray(bytes), IntArray([...]),
  LongArray([...]), List(element_tag_id, [...]), dict (compound).
"""
import gzip
import struct
from dataclasses import dataclass, field


@dataclass
class Byte:
    v: int


@dataclass
class Short:
    v: int


@dataclass
class Int:
    v: int


@dataclass
class Long:
    v: int


@dataclass
class Float:
    v: float


@dataclass
class Double:
    v: float


@dataclass
class ByteArray:
    v: bytes


@dataclass
class IntArray:
    v: list


@dataclass
class LongArray:
    v: list


@dataclass
class List:
    elem: int  # tag id of the elements (0 = empty)
    v: list = field(default_factory=list)


END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BYTE_ARRAY, STRING, LIST, COMPOUND, INT_ARRAY, LONG_ARRAY = range(13)


def _read(f, t):
    r = f.read
    if t == BYTE:
        return Byte(struct.unpack(">b", r(1))[0])
    if t == SHORT:
        return Short(struct.unpack(">h", r(2))[0])
    if t == INT:
        return Int(struct.unpack(">i", r(4))[0])
    if t == LONG:
        return Long(struct.unpack(">q", r(8))[0])
    if t == FLOAT:
        return Float(struct.unpack(">f", r(4))[0])
    if t == DOUBLE:
        return Double(struct.unpack(">d", r(8))[0])
    if t == BYTE_ARRAY:
        n = struct.unpack(">i", r(4))[0]
        return ByteArray(r(n))
    if t == STRING:
        n = struct.unpack(">H", r(2))[0]
        return r(n).decode("utf-8")
    if t == LIST:
        et = r(1)[0]
        n = struct.unpack(">i", r(4))[0]
        return List(et, [_read(f, et) for _ in range(n)])
    if t == COMPOUND:
        d = {}
        while True:
            tt = r(1)[0]
            if tt == END:
                return d
            k = _read(f, STRING)
            d[k] = _read(f, tt)
    if t == INT_ARRAY:
        n = struct.unpack(">i", r(4))[0]
        return IntArray(list(struct.unpack(">%di" % n, r(4 * n))))
    if t == LONG_ARRAY:
        n = struct.unpack(">i", r(4))[0]
        return LongArray(list(struct.unpack(">%dq" % n, r(8 * n))))
    raise ValueError(f"bad tag {t}")


def _tag_id(v):
    return {Byte: BYTE, Short: SHORT, Int: INT, Long: LONG, Float: FLOAT, Double: DOUBLE, ByteArray: BYTE_ARRAY,
            str: STRING, List: LIST, dict: COMPOUND, IntArray: INT_ARRAY, LongArray: LONG_ARRAY}[type(v)]


def _write(out, v):
    t = _tag_id(v)
    if t == BYTE:
        out += struct.pack(">b", v.v)
    elif t == SHORT:
        out += struct.pack(">h", v.v)
    elif t == INT:
        out += struct.pack(">i", v.v)
    elif t == LONG:
        out += struct.pack(">q", v.v)
    elif t == FLOAT:
        out += struct.pack(">f", v.v)
    elif t == DOUBLE:
        out += struct.pack(">d", v.v)
    elif t == BYTE_ARRAY:
        out += struct.pack(">i", len(v.v)) + v.v
    elif t == STRING:
        b = v.encode("utf-8")
        out += struct.pack(">H", len(b)) + b
    elif t == LIST:
        et = v.elem if v.v else (v.elem or END)
        out += bytes([et]) + struct.pack(">i", len(v.v))
        for x in v.v:
            _write(out, x)
    elif t == COMPOUND:
        for k, x in v.items():
            out += bytes([_tag_id(x)])
            _write(out, k)
            _write(out, x)
        out += bytes([END])
    elif t == INT_ARRAY:
        out += struct.pack(">i", len(v.v)) + struct.pack(">%di" % len(v.v), *v.v)
    elif t == LONG_ARRAY:
        out += struct.pack(">i", len(v.v)) + struct.pack(">%dq" % len(v.v), *v.v)


def load(path):
    with gzip.open(path, "rb") as f:
        t = f.read(1)[0]
        _read(f, STRING)  # root name
        return _read(f, t)


def save(path, root: dict):
    out = bytearray([COMPOUND])
    _write(out, "")
    _write(out, root)
    with gzip.open(path, "wb") as f:
        f.write(bytes(out))


def plain(v):
    """Typed NBT → plain Python, for printing."""
    if isinstance(v, dict):
        return {k: plain(x) for k, x in v.items()}
    if isinstance(v, List):
        return [plain(x) for x in v.v]
    if isinstance(v, (Byte, Short, Int, Long, Float, Double, IntArray, LongArray)):
        return v.v
    if isinstance(v, ByteArray):
        return f"<{len(v.v)} bytes>"
    return v
