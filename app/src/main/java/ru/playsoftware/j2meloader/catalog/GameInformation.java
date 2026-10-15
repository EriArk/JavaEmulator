package ru.playsoftware.j2meloader.catalog;

import java.io.*;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import ru.playsoftware.j2meloader.applist.AppItem;
import ru.playsoftware.j2meloader.config.Config;

/** Read-only metadata: the installed descriptor is the installer's resolved JAD/JAR result. */
public final class GameInformation {
    private GameInformation() { }
    public static String describe(AppItem item) {
        if (item instanceof ExternalLibrary.Item)
            return "Platform: " + value(item.getAuthor()) + "\nPhone: " + value(item.getVersion());
        if (AdditionalGames.isManaged(new File(item.getPathExt())))
            return "Platform: Mophun\nVersion: " + value(item.getVersion());
        String version = item.getVersion(), vendor = item.getAuthor();
        File descriptor = new File(item.getPathExt(), Config.MIDLET_MANIFEST_FILE);
        if (descriptor.isFile()) {
            try {
                Attributes fields = read(descriptor);
                version = fields.getValue("MIDlet-Version");
                vendor = fields.getValue("MIDlet-Vendor");
            } catch (IOException | RuntimeException error) {
                return "Platform: Java ME\nVersion: Unavailable\nInstalled game information could not be read.";
            }
        }
        return "Platform: Java ME\nVersion: " + value(version) + "\nVendor: " + value(vendor);
    }
    static Attributes read(File file) throws IOException {
        if (file.length() > 1024 * 1024) throw new IOException("Descriptor is too large");
        try (InputStream input = new BufferedInputStream(new FileInputStream(file))) {
            return new Manifest(input).getMainAttributes();
        }
    }
    private static String value(String value) { return value == null || value.trim().isEmpty() ? "Not provided" : value; }
}
