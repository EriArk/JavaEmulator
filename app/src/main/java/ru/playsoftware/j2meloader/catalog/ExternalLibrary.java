package ru.playsoftware.j2meloader.catalog;

import android.app.Activity;
import android.content.Context;
import java.io.IOException;
import java.util.*;
import ru.playsoftware.j2meloader.BuildConfig;
import ru.playsoftware.j2meloader.applist.AppItem;

/** Engine-owned entries. They must never enter the Java install/backup database. */
public final class ExternalLibrary {
    public interface Provider { List<AppItem> read(Context context) throws IOException; }
    public interface PackageProvider extends Provider {
        Item install(Context context, android.net.Uri uri) throws IOException;
        default void installSystemFile(Context context, android.net.Uri uri, String model) throws IOException {
            install(context, uri);
        }
    }
    public static PackageProvider packageProvider(int platform) throws IOException {
        String provider;
        switch (platform) {
            case 5: provider = "io.github.eriark.abyssme.wipi.WipiLibrary"; break;
            case 6: case 7: provider = "io.github.eriark.abyssme.dingoo.DingooLibrary"; break;
            case 8: provider = "io.github.eriark.abyssme.piece.PieceLibrary"; break;
            case 10: provider = "io.github.eriark.abyssme.supervision.SupervisionLibrary"; break;
            case 11: provider = "io.github.eriark.abyssme.megaduck.MegaDuckLibrary"; break;
            case 12: provider = "io.github.eriark.abyssme.cybiko.CybikoLibrary"; break;
            case 13: provider = "io.github.eriark.abyssme.flash.FlashLibrary"; break;
            case 14: provider = "io.github.eriark.abyssme.gamate.GamateLibrary"; break;
            case 15: provider = "io.github.eriark.abyssme.gameking.GameKingLibrary"; break;
            case 25: provider = "io.github.eriark.abyssme.arduboy.ArduboyLibrary"; break;
            case 26: provider = "io.github.eriark.abyssme.gamebuino.GamebuinoLibrary"; break;
            case 27: provider = "io.github.eriark.abyssme.pokitto.PokittoLibrary"; break;
            case 30: provider = "io.github.eriark.abyssme.thumby.ThumbyLibrary"; break;
            case 32: provider = "io.github.eriark.abyssme.espboy.EspboyLibrary"; break;
            case 31: provider = "io.github.eriark.abyssme.thumby.ThumbyLibrary$Color"; break;
            case 28: provider = "io.github.eriark.abyssme.gameking.HartungLibrary"; break;
            case 29: provider = "io.github.eriark.abyssme.gameking.EpochLibrary"; break;
            case 24: provider = "io.github.eriark.abyssme.iphone.IphoneLibrary"; break;
            case 23: provider = "io.github.eriark.abyssme.pocketpc.GizmondoLibrary"; break;
            case 22: provider = "io.github.eriark.abyssme.pocketpc.PocketPcLibrary"; break;
            case 21: provider = "io.github.eriark.abyssme.portfolio.PortfolioLibrary"; break;
            case 19: provider = "io.github.eriark.abyssme.b2g.B2gLibrary$Firefox"; break;
            case 20: provider = "io.github.eriark.abyssme.b2g.B2gLibrary$Kai"; break;
            case 18: provider = "io.github.eriark.abyssme.webos.WebOsLibrary"; break;
            case 17: provider = "io.github.eriark.abyssme.mre.MreLibrary"; break;
            case 16: provider = "io.github.eriark.abyssme.palm.PalmLibrary"; break;
            case 9: provider = "io.github.eriark.abyssme.gp32.Gp32Library"; break;
            default: throw new IOException("Unknown handheld platform");
        }
        try {
            return Class.forName(provider)
                    .asSubclass(PackageProvider.class).getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) { throw new IOException("Cannot load handheld library", e); }
    }
    public abstract static class Item extends AppItem {
        protected Item(String path, String title, String platform, String phone) { super(path, title, platform, phone); }
        public int platformId() { return getAuthor() != null && getAuthor().toLowerCase(Locale.ROOT).contains("n-gage") ? 4 : 3; }
        public abstract void launch(Activity activity, boolean systemSettings);
        public void removePackage() throws IOException { throw new IOException("Use the system uninstall screen"); }
        public abstract void favorite();
        public abstract void rename(String title) throws java.io.IOException;
        public void uninstall(Activity activity) { }
        public boolean supportsUninstallReview() { return false; }
        public boolean supportsGameSettings() { return false; }
        /** Engine-owned identity and storage; never derive these from the launcher path. */
        public ru.playsoftware.j2meloader.config.ProfileModel loadGameSettings() throws IOException { return null; }
        public void displaySettings(ru.playsoftware.j2meloader.config.GameSettingsPages pages) { pages.display(); }
        /** Optional host-owned artwork overrides, separate from engine-generated images. */
        public java.io.File getArtworkDirectory() { return null; }
        @Override public String getPathExt() { return getPath(); }
        @Override public String getCoverPathExt() { return null; }
    }
    private ExternalLibrary() { }
    public static List<AppItem> read(Context context) throws IOException {
        List<AppItem> games = new ArrayList<>();
        if (BuildConfig.WIPI_PROBE) games.addAll(packageProvider(5).read(context));
        if (BuildConfig.DINGOO_PROBE) games.addAll(packageProvider(6).read(context));
        if (BuildConfig.PIECE_PROBE) games.addAll(packageProvider(8).read(context));
        if (BuildConfig.SUPERVISION_PROBE) games.addAll(packageProvider(10).read(context));
        if (BuildConfig.MEGADUCK_PROBE) games.addAll(packageProvider(11).read(context));
        if (BuildConfig.CYBIKO_PROBE) games.addAll(packageProvider(12).read(context));
        if (BuildConfig.FLASH_PROBE) games.addAll(packageProvider(13).read(context));
        if (BuildConfig.GAMATE_PROBE) games.addAll(packageProvider(14).read(context));
        if (BuildConfig.GAMEKING_PROBE) games.addAll(packageProvider(15).read(context));
        if (BuildConfig.ARDUBOY_PROBE) games.addAll(packageProvider(25).read(context));
        if (BuildConfig.GAMEBUINO_PROBE) games.addAll(packageProvider(26).read(context));
        if (BuildConfig.POKITTO_PROBE) games.addAll(packageProvider(27).read(context));
        if (BuildConfig.THUMBY_PROBE) games.addAll(packageProvider(30).read(context));
        if (BuildConfig.ESPBOY_PROBE) games.addAll(packageProvider(32).read(context));
        if (BuildConfig.THUMBY_COLOR_PROBE) games.addAll(packageProvider(31).read(context));
        if (BuildConfig.GAMEKING_PROBE) games.addAll(packageProvider(28).read(context));
        if (BuildConfig.GAMEKING_PROBE) games.addAll(packageProvider(29).read(context));
        if (BuildConfig.IPHONE_PROBE) games.addAll(packageProvider(24).read(context));
        if (BuildConfig.GIZMONDO_PROBE) games.addAll(packageProvider(23).read(context));
        if (BuildConfig.POCKETPC_PROBE) games.addAll(packageProvider(22).read(context));
        if (BuildConfig.PORTFOLIO_PROBE) games.addAll(packageProvider(21).read(context));
        if (BuildConfig.B2G_PROBE) {
            games.addAll(packageProvider(19).read(context));
            games.addAll(packageProvider(20).read(context));
        }
        if (BuildConfig.WEBOS_PROBE) games.addAll(packageProvider(18).read(context));
        if (BuildConfig.MRE_PROBE) games.addAll(packageProvider(17).read(context));
        if (BuildConfig.PALM_PROBE) games.addAll(packageProvider(16).read(context));
        if (BuildConfig.GP32_PROBE) games.addAll(packageProvider(9).read(context));
        if (!BuildConfig.SYMBIAN_EXPERIMENT) return games;
        try {
            games.addAll(Class.forName("io.github.eriark.abyssme.symbian.SymbianLauncherLibrary")
                    .asSubclass(Provider.class).getDeclaredConstructor().newInstance().read(context));
            return games;
        } catch (ReflectiveOperationException error) { throw new IOException("Cannot read Symbian library", error); }
    }
}
