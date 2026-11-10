package ru.playsoftware.j2meloader.catalog;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.XmlResourceParser;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.xmlpull.v1.XmlPullParser;
import java.util.Locale;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.ui.AppPalette;
import static org.junit.Assert.*;

/** Extraction checks, not translation or runtime language-switch acceptance. */
public class SettingsResourcesTest {
    private Context context(String locale) {
        Context base = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Configuration config = new Configuration(base.getResources().getConfiguration());
        config.setLocale(Locale.forLanguageTag(locale));
        return base.createConfigurationContext(config);
    }

    @Test public void preferenceTitlesAllReferenceResources() throws Exception {
        try (XmlResourceParser xml = context("en").getResources().getXml(R.xml.preferences)) {
            while (xml.next() != XmlPullParser.END_DOCUMENT) {
                if (xml.getEventType() != XmlPullParser.START_TAG) continue;
                assertTrue(xml.getName(), xml.getAttributeResourceValue(
                        "http://schemas.android.com/apk/res/android", "title", 0) != 0);
            }
        }
    }

    @Test public void folderPluralFormsAndCountsAreCompleteSentences() {
        Context c = context("en");
        assertEquals("1 folder", c.getResources().getQuantityString(R.plurals.folders_selected, 1, 1));
        assertEquals("2 folders", c.getResources().getQuantityString(R.plurals.folders_selected, 2, 2));
        GameFolderIndexer.Result r = new GameFolderIndexer.Result();
        r.skippedFolders = 1; r.limited = true;
        assertEquals("No supported games found\n1 folder could not be read\n"
                + "Scan limit reached. Select a smaller folder for the remaining files.", r.summary(c, 0));
    }

    @Test public void translatedStringsAndUnknownLocaleFallbackPreserveGuestAndSystemLocale() {
        Locale original = Locale.getDefault();
        String[] locales={"en", "es", "pt-BR", "ru", "fr", "de", "ja", "zh-CN", "zz-ZZ"};
        String[] titles={"System files", "Archivos del sistema", "Arquivos de sistema", "Системные файлы",
                "Fichiers système", "Systemdateien", "システムファイル", "系统文件", "System files"};
        for (int i=0;i<locales.length;i++) {
            String locale=locales[i];
            Context c = context(locale);
            assertEquals(titles[i], c.getString(R.string.settings_system_files));
            assertFalse(c.getString(R.string.settings_system_files_summary).isEmpty());
            if(locale.equals("zz-ZZ")) assertEquals("Required files for Symbian and N-Gage", c.getString(R.string.settings_system_files_summary));
            String file = "Games/RH-29/%20 archive.zip";
            assertTrue(c.getString(R.string.folders_add_failed, file).contains(file));
            if(locale.equals("zz-ZZ")) assertEquals("Could not add folder: " + file, c.getString(R.string.folders_add_failed, file));
            assertEquals("Opacity: 75%", context("en").getString(R.string.menu_percentage, "Opacity", 75));
            assertEquals(AppPalette.VALUES.length, c.getResources().getStringArray(R.array.palette_names).length);
        }
        assertEquals(original, Locale.getDefault());
    }
}
