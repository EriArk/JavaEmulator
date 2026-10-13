package ru.playsoftware.j2meloader.mophun;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import androidx.fragment.app.DialogFragment;
import java.io.File;
import java.io.IOException;
import java.util.Locale;
import ru.playsoftware.j2meloader.applist.AppItem;
import ru.playsoftware.j2meloader.catalog.AdditionalGames;

public final class MophunEngine implements AdditionalGames.Engine {
    @Override public boolean accepts(Context context, Uri uri) {
        return MophunStore.name(context, uri).toLowerCase(Locale.ROOT).endsWith(".mpn");
    }
    @Override public DialogFragment installer(Uri uri, boolean autoStart, boolean finishHost) {
        return MophunInstallerDialog.create(uri, autoStart, finishHost);
    }
    @Override public AppItem readInstalled(File directory) throws IOException { return MophunStore.read(directory); }
    @Override public void launch(Context context, String title, File directory, boolean controls) {
        if (controls) {
            context.startActivity(new Intent(context, ru.playsoftware.j2meloader.config.GameSettingsActivity.class)
                    .setData(Uri.parse(directory.getPath())).putExtra("native_settings", true)
                    .putExtra(ru.playsoftware.j2meloader.util.Constants.KEY_MIDLET_NAME, title));
            return;
        }
        context.startActivity(new Intent(context, MophunGameActivity.class)
                .putExtra("id", directory.getName()).putExtra("title", title).putExtra("controls", controls));
    }
}
