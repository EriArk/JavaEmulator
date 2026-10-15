package ru.playsoftware.j2meloader.catalog;

import org.junit.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

public class LauncherLinkTest {
    private final String key = "symbian:" + new String(new char[64]).replace('\0','a') + "/4294967295";
    private String read(String payload, String app) throws Exception {
        return LauncherLink.decode(new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8)), app);
    }
    @Test public void identityRoundTripsWithoutFilesystemPaths() throws Exception {
        String value = LauncherLink.encode("test.phone", key);
        assertEquals(key, read(value,"test.phone"));
        assertFalse(value.contains("/data/"));
    }
    @Test public void variantsCannotAccidentallyOpenAnotherLibrary() throws Exception {
        try { read(LauncherLink.encode("test.phone",key),"test.handheld"); fail(); }
        catch(IOException expected) { assertTrue(expected.getMessage().contains("another AbyssME")); }
    }
    @Test public void badVersionMissingFieldsOversizeAndUnsafeIdentityAreRejected() throws Exception {
        String value = LauncherLink.encode("test",key);
        for(String payload:new String[]{"{}", "invalid", value.replace("\"version\":1","\"version\":2"),
                new org.json.JSONObject(value).put("game","../../escape").toString(), value.replace("4294967295","4294967296"),
                new String(new char[4097]).replace('\0','x')}) {
            try {read(payload,"test");fail(payload);} catch(IOException expected) { }
        }
    }
}
