"""Generate an original SWF7 audio and SharedObject cold-start fixture."""
import math
import struct
import sys
from pathlib import Path


def tag(kind, data=b""):
    short = min(len(data), 63)
    length = struct.pack("<I", len(data)) if short == 63 else b""
    return struct.pack("<H", (kind << 6) + short) + length + data


def push(value):
    data = (b"\0" + value.encode() + b"\0" if isinstance(value, str)
            else b"\x07" + struct.pack("<i", value))
    return b"\x96" + struct.pack("<H", len(data)) + data


# var so = SharedObject.getLocal("progress")
actions = push("so") + push("progress") + push(1) + push("SharedObject") + b"\x1c" + push("getLocal") + b"\x52\x1d"
data = push("so") + b"\x1c" + push("data") + b"\x4e"
# so.data.boots = int(so.data.boots) + 1; so.flush()
actions += data + push("boots") + data + push("boots") + b"\x4e\x18\x50\x4f"
actions += push(0) + push("so") + b"\x1c" + push("flush") + b"\x52\x17\x07\0"
rate = 11025
pcm = b"".join(struct.pack("<h", round(5500 * math.sin(2 * math.pi * 440 * t / rate))) for t in range(rate))
sound = struct.pack("<HBI", 1, 0x36, rate) + pcm
# Signed 13-bit stage bounds in twips, 160 x 120 pixels.
bits = "01101" + "".join(format(v, "013b") for v in (0, 3200, 0, 2400))
rect = int(bits.ljust((len(bits) + 7) // 8 * 8, "0"), 2).to_bytes((len(bits) + 7) // 8, "big")
movie = rect + struct.pack("<HH", 24 << 8, 1) + tag(9, bytes((32, 80, 140)))
movie += tag(14, sound) + tag(15, struct.pack("<HBH", 1, 4, 128)) + tag(12, actions) + tag(1) + tag(0)
Path(sys.argv[1]).write_bytes(b"FWS\x07" + struct.pack("<I", 8 + len(movie)) + movie)
