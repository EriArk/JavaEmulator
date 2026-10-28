// SPDX-License-Identifier: GPL-3.0-only
package io.github.eriark.abyssme.symbian;

import android.content.Context;
import android.content.res.Configuration;
import android.os.LocaleList;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import ru.playsoftware.j2meloader.R;
import static org.junit.Assert.*;

public class SystemRequirementsLocalizationTest {
    private Context locale(String tag) {
        Context base=InstrumentationRegistry.getInstrumentation().getTargetContext();
        Configuration config=new Configuration(base.getResources().getConfiguration());
        config.setLocales(LocaleList.forLanguageTags(tag));return base.createConfigurationContext(config);
    }
    @Test public void translatedRowsPreserveGenerationAndReadiness() {
        Context expanded=locale("en-XA");
        SystemFileCatalog.Entry n95=new SystemFileCatalog.Entry("fixture","N95","RM-320","epoc93fp1");
        List<SystemRequirements.Row> rows=SystemRequirements.rows(Collections.singletonList(n95),false);
        assertNull(rows.get(0).kind);
        assertEquals(GameImport.Kind.CARD,rows.get(1).kind);
        assertEquals(GameImport.Kind.NGAGE2,rows.get(4).kind);
        assertFalse(rows.get(1).phoneReady);assertTrue(rows.get(4).phoneReady);
        for(SystemRequirements.Row row:rows) {
            assertFalse(SystemRequirementsUi.title(expanded,row).isEmpty());
            assertNotEquals(row.status,SystemRequirementsUi.status(expanded,row,false));
            assertNotEquals(row.description,SystemRequirementsUi.description(expanded,row,false));
        }
        assertEquals("Checked on import",SystemRequirementsUi.status(locale("en"),rows.get(4),false));
        assertEquals("Setup files added",SystemRequirementsUi.status(locale("en"),rows.get(4),true));
    }
    @Test public void firmwareHelpPreservesRequiredFileTypesAndDoesNotMixPlatforms() {
        Context expanded=locale("en-XA");
        String formats=expanded.getString(R.string.system_formats_help);
        for(String extension:new String[]{".rom",".rpkg","ZIP/7z","EKA2L1"}) assertTrue(formats.contains(extension));
        assertTrue(SystemRequirementsUi.needed(expanded,GameImport.Kind.NGAGE2).contains("N95"));
        assertTrue(SystemRequirementsUi.needed(expanded,GameImport.Kind.CARD).contains("N-Gage QD"));
        SystemRequirements.Row withoutPhone=SystemRequirements.rows(Collections.emptyList(),true).get(4);
        assertEquals("Phone files needed",SystemRequirementsUi.status(locale("en"),withoutPhone,true));
        assertFalse(withoutPhone.phoneReady);
    }
}
