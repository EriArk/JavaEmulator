"""Convert GNU Unifont BMP hex glyphs to the MRE renderer's indexed bitmap format."""
import gzip
from pathlib import Path
import struct
import sys

with gzip.open(sys.argv[1], "rt", encoding="ascii") as source:
    offsets = bytearray(4 * 65536)
    records = bytearray()
    for line in source:
        code, bitmap = line.strip().split(":")
        index = int(code, 16)
        if index >= 65536:
            continue
        rows = bytes.fromhex(bitmap)
        if len(rows) not in (16, 32):
            raise ValueError(f"Unexpected glyph width at U+{index:04X}")
        struct.pack_into("<I", offsets, index * 4, len(offsets) + len(records))
        records.extend((len(rows) // 2 - 1, 0))
        records.extend(rows)
Path(sys.argv[2]).write_bytes(offsets + records)
