"""Prepare pinned Classic hardware modules and an empty, partitioned FAT16 card."""
import struct
import subprocess
import sys
import tempfile
from pathlib import Path

revision = "9088327dd0168c19bf2abe6d6db720f6a19ef962"
checkout = sys.argv[1]
target = Path(__file__).resolve().parents[2] / "build/gamebuino/assets/gamebuino"
core = target / "core"
core.mkdir(parents=True, exist_ok=True)
modules = "class MemoryMappedRegister AtmelRegisters FlagsRegister AtmelIO AtmelContext AtmelProcessor ADC Buttons SPI USART EEPROM Lcd SdDevice AudioPlayer HexDecoder Bootloader"
for name in modules.split():
    source = revision + ":webemulator/js/" + name + ".js"
    (core / (name + ".js")).write_bytes(subprocess.check_output(["git", "-C", checkout, "show", source]))

# A single FAT16 partition works with both the Petit FatFs and GB_Fat guest drivers.
with tempfile.TemporaryDirectory() as tmp:
    volume = Path(tmp) / "volume"
    subprocess.run(["mkfs.fat", "--invariant", "-C", "-F", "16", "-s", "2", "-h", "1", "-n", "GAMEBUINO", str(volume), "8192"], check=True)
    mbr = bytearray(512)
    mbr[450] = 0x06
    struct.pack_into("<II", mbr, 454, 1, volume.stat().st_size // 512)
    mbr[510:] = bytes.fromhex("55aa")
    # Unallocated sectors read as zero. The game-specific card grows as sectors are written.
    raw = volume.read_bytes().rstrip(b"\0")
    (target / "blank.img").write_bytes(mbr + raw.ljust((len(raw) + 511) // 512 * 512, b"\0"))
