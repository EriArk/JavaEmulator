package ru.playsoftware.j2meloader.catalog;

import java.io.*;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import ru.playsoftware.j2meloader.applist.AppItem;
import ru.playsoftware.j2meloader.config.Config;

/** Read-only metadata: the installed descriptor is the installer's resolved JAD/JAR result. */
public final class GameInformation {
    private GameInformation() { }
    private enum Kind { EXTERNAL, MOPHUN, JAVA, UNREADABLE }
    private static final class Details {
        final Kind kind;final String version,vendor;
        Details(Kind kind,String version,String vendor){this.kind=kind;this.version=version;this.vendor=vendor;}
    }
    public static String describe(AppItem item) {
        Details data=inspect(item);
        switch(data.kind) {
            case EXTERNAL:return "Platform: "+value(data.vendor)+"\nPhone: "+value(data.version);
            case MOPHUN:return "Platform: Mophun\nVersion: "+value(data.version);
            case UNREADABLE:return "Platform: Java ME\nVersion: Unavailable\nInstalled game information could not be read.";
            default:return "Platform: Java ME\nVersion: "+value(data.version)+"\nVendor: "+value(data.vendor);
        }
    }
    public static String describe(android.content.Context context,AppItem item) {
        Details data=inspect(item);
        String version=value(context,data.version),vendor=value(context,data.vendor);
        switch(data.kind) {
            case EXTERNAL:return context.getString(ru.playsoftware.j2meloader.R.string.game_info_external,vendor,version);
            case MOPHUN:return context.getString(ru.playsoftware.j2meloader.R.string.game_info_mophun,version);
            case UNREADABLE:return context.getString(ru.playsoftware.j2meloader.R.string.game_info_unreadable);
            default:return context.getString(ru.playsoftware.j2meloader.R.string.game_info_java,version,vendor);
        }
    }
    private static Details inspect(AppItem item) {
        if (item instanceof ExternalLibrary.Item)
            return new Details(Kind.EXTERNAL,item.getVersion(),item.getAuthor());
        if (AdditionalGames.isManaged(new File(item.getPathExt())))
            return new Details(Kind.MOPHUN,item.getVersion(),item.getAuthor());
        String version = item.getVersion(), vendor = item.getAuthor();
        File descriptor = new File(item.getPathExt(), Config.MIDLET_MANIFEST_FILE);
        if (descriptor.isFile()) {
            try {
                Attributes fields = read(descriptor);
                version = fields.getValue("MIDlet-Version");
                vendor = fields.getValue("MIDlet-Vendor");
            } catch (IOException | RuntimeException error) {
                return new Details(Kind.UNREADABLE,null,null);
            }
        }
        return new Details(Kind.JAVA,version,vendor);
    }
    static Attributes read(File file) throws IOException {
        if (file.length() > 1024 * 1024) throw new IOException("Descriptor is too large");
        try (InputStream input = new BufferedInputStream(new FileInputStream(file))) {
            return new Manifest(input).getMainAttributes();
        }
    }
    private static String value(String value) { return value == null || value.trim().isEmpty() ? "Not provided" : value; }
    private static String value(android.content.Context context,String value) {
        return value==null||value.trim().isEmpty()?context.getString(ru.playsoftware.j2meloader.R.string.game_info_missing):value;
    }
}
