package io.github.eriark.abyssme.symbian;

import java.util.*;

/** Readiness means usable inputs are present, not that every game has been tested. */
final class SystemRequirements {
    static final class Row {
        final String title, status, description;
        final boolean platform, phoneReady;
        Row(String title, String status, String description, boolean platform, boolean phoneReady) {
            this.title = title; this.status = status; this.description = description; this.platform = platform;
            this.phoneReady = phoneReady;
        }
    }
    static List<Row> rows(List<SystemFileCatalog.Entry> systems, boolean platformSaved) {
        List<Row> rows = new ArrayList<>();
        rows.add(new Row("Java / Mophun", "No extra files", "Java games (.jar / .jad) and Mophun games (.mpn) do not need phone firmware.", false, true));
        rows.add(row("Original N-Gage", systems, GameImport.Kind.CARD,
                "Phone: Nokia N-Gage or N-Gage QD.\n\nNeeded: a phone dump prepared for EKA2L1, as a ZIP/7z archive or a .rom file with its matching .rpkg when required.\n\nN-Gage 2.0 installers are for a different platform and will not replace these files."));
        rows.add(row("Symbian S60 1st / 2nd edition", systems, GameImport.Kind.SIS_LEGACY,
                "Phone: an S60 1st/2nd edition model matching the game, such as Nokia 3650 or 6600.\n\nNeeded: an EKA2L1 phone dump, as a ZIP/7z archive or .rom with its matching .rpkg when required. A game .sis is not phone firmware."));
        rows.add(row("Symbian S60 3rd edition and later", systems, GameImport.Kind.SIS_MODERN,
                "Phone: the Symbian generation required by the game, for example Nokia N95 for S60 3rd edition.\n\nNeeded: an EKA2L1 phone dump, as a ZIP/7z archive or .rom with its matching .rpkg when required. Different Symbian generations are not interchangeable."));
        boolean phone = systems.stream().anyMatch(e -> e.supports(GameImport.Kind.NGAGE2));
        rows.add(new Row("N-Gage 2.0", !phone ? "Phone files needed" : platformSaved ? "Setup files added" : "Checked on import",
                "Phone: Nokia N95 recommended (S60 3rd edition).\n\nPhone dump: "
                + (phone ? "added." : "needed (.rom + matching .rpkg, or prepared ZIP/7z).")
                + "\n\nN-Gage 2.0 app: " + (platformSaved ? "installer saved." : "installer needed if not already installed on the phone.")
                + " Use its .sis or ZIP, not a .n-gage game.\n\nSaved files are reused; platform installation is checked when adding a game.", true, phone));
        return rows;
    }
    private static Row row(String name, List<SystemFileCatalog.Entry> systems, GameImport.Kind kind, String help) {
        long count = systems.stream().filter(e -> e.supports(kind)).count();
        return new Row(name, count == 0 ? "Phone files needed" : "Phone files added", help, false, count > 0);
    }
    static String needed(GameImport.Kind kind) {
        switch (kind) {
            case CARD: return "This game needs original Nokia N-Gage / N-Gage QD system files.";
            case NGAGE2: return "This game needs S60 3rd edition system files (N95 recommended) and the N-Gage 2.0 platform installer.";
            case SIS_LEGACY: return "This game needs system files for a supported Symbian S60 1st/2nd edition phone.";
            default: return "This game needs system files for its Symbian generation (S60 3rd edition or later).";
        }
    }
    static String formats() {
        return "An EKA2L1 phone dump contains the phone's operating system, not games. Add a ZIP/7z containing one .rom and its matching .rpkg, or select those files together. Subfolders and uppercase extensions are supported. Both files must be from the same phone dump; do not mix several phones or games in this archive. A prepared ROM + Z-folder archive is also supported.\n\nUse files you have the right to use. Firmware and games are not included.";
    }
}
