package ru.playsoftware.j2meloader.catalog;

import android.app.Activity;
import android.content.Context;
import java.io.IOException;
import java.util.*;
import ru.playsoftware.j2meloader.BuildConfig;
import ru.playsoftware.j2meloader.applist.AppItem;

/** Read-only engine-owned entries. They must never enter the Java install/backup database. */
public final class ExternalLibrary {
    public interface Provider { List<AppItem> read(Context context) throws IOException; }
    public abstract static class Item extends AppItem {
        protected Item(String path, String title, String platform, String phone) { super(path, title, platform, phone); }
        public abstract void launch(Activity activity, boolean systemSettings);
        public abstract void favorite();
        @Override public String getPathExt() { return getPath(); }
        @Override public String getCoverPathExt() { return null; }
    }
    private ExternalLibrary() { }
    public static List<AppItem> read(Context context) throws IOException {
        if (!BuildConfig.SYMBIAN_EXPERIMENT) return Collections.emptyList();
        try {
            return Class.forName("io.github.eriark.abyssme.symbian.SymbianLauncherLibrary")
                    .asSubclass(Provider.class).getDeclaredConstructor().newInstance().read(context);
        } catch (ReflectiveOperationException error) { throw new IOException("Cannot read Symbian library", error); }
    }
}
