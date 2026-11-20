"""Adapt the pinned source in the build directory, leaving the checkout untouched."""
from pathlib import Path
import sys

src = Path(sys.argv[1]) / "src"
card = src / "smc_direct.c"
text = card.read_text()
# Encrypted retail card boot belongs to the user-supplied BIOS.
first = text.index("static void basename_no_ext")
last = text.index("static int move_files_to_assets", first)
text = text[:first] + text[last:]
first = text.index("        const smc_file_buf_t *gxc = find_matching_gxc")
last = text.index("    } else if", first)
card.write_text(text[:first] + '''        file_list_free(&list);
        serr(err, err_len, "This card requires an installed GP32 BIOS");
        return 0;
''' + text[last:])

# Export the NAND dirty bit without exposing hardware structs to the Android host.
with (src / "s3c2400.c").open("a") as file:
    file.write("\nint abyss_smc_dirty(s3c2400_t *s) { return smc_is_dirty(s->smc); }\n")
    file.write("int abyss_smc_present(s3c2400_t *s) { return smc_is_present(s->smc); }\n")
with (src / "gp32.c").open("a") as file:
    file.write("\nextern int abyss_smc_dirty(s3c2400_t *);\n"
               "int abyss_card_dirty(gp32_t *g) { return abyss_smc_dirty(g->soc); }\n")

    file.write("\nextern int abyss_smc_present(s3c2400_t *);\n"
               "int abyss_card_present(gp32_t *g) { return abyss_smc_present(g->soc); }\n")

# Check the upper bound before subtracting unsigned guest RAM addresses.
fxe = src / "fxe.c"
text = fxe.read_text()
check = "addr >= GP32_RAM_BASE && len <="
assert text.count(check) == 1
text = text.replace('strcpy(out->title, label && label[0] ? label : "raw GXB");',
                    'snprintf(out->title, sizeof(out->title), "%s", label && label[0] ? label : "raw GXB");')
fxe.write_text(text.replace(check, "addr >= GP32_RAM_BASE && addr <= GP32_RAM_BASE + 0x00800000u && len <="))
