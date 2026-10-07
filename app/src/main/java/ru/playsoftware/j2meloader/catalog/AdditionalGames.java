package ru.playsoftware.j2meloader.catalog;

import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import java.io.File;
import java.io.IOException;
import ru.playsoftware.j2meloader.applist.AppItem;
import ru.woesss.j2me.installer.InstallerDialog;

/** Optional in-APK engine. Ordinary builds contain no implementation. */
public final class AdditionalGames {
    public static final String MANIFEST = "additional-game.json";
    private static Engine engine;
    private static boolean loaded;

    public interface Engine {
        boolean accepts(Context context, Uri uri);
        DialogFragment installer(Uri uri, boolean autoStart, boolean finishHost);
        AppItem readInstalled(File directory) throws IOException;
        void launch(Context context, String title, File directory, boolean controls);
    }

    private AdditionalGames() { }

    public static synchronized Engine get(Context context) {
        if (!loaded) {
            loaded = true;
            try {
                android.os.Bundle metadata = context.getPackageManager().getApplicationInfo(
                        context.getPackageName(), PackageManager.GET_META_DATA).metaData;
                String implementation = metadata == null ? null : metadata.getString("abyssme.additionalGameEngine");
                if (implementation != null) {
                    engine = Class.forName(implementation).asSubclass(Engine.class).getDeclaredConstructor().newInstance();
                }
            } catch (ReflectiveOperationException | PackageManager.NameNotFoundException error) {
                Log.e("AdditionalGames", "Cannot load optional engine", error);
            }
        }
        return engine;
    }

    public static boolean isManaged(File directory) {
        return new File(directory, MANIFEST).exists();
    }

    public static void install(Context context, FragmentManager fragments, Uri uri,
                               boolean autoStart, boolean finishHost) {
        Engine extra = get(context);
        DialogFragment dialog = extra != null && extra.accepts(context, uri)
                ? extra.installer(uri, autoStart, finishHost)
                : InstallerDialog.newInstance(uri, autoStart, finishHost);
        if (fragments.findFragmentByTag("installer") == null) dialog.show(fragments, "installer");
    }

    public static boolean launch(Context context, String title, File directory, boolean controls) {
        if (!isManaged(directory)) return false;
        Engine extra = get(context);
        if (extra == null) {
            Toast.makeText(context, "This build does not include the engine for this game", Toast.LENGTH_LONG).show();
        } else {
            extra.launch(context, title, directory, controls);
        }
        return true;
    }
}
