/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.multiplayer;

import com.google.gson.JsonParser;
import org.junit.Test;
import static org.junit.Assert.*;

public class FluxerHttpTransportTest {
    @Test public void errorsRetainOnlyBoundedMachineCodes() {
        assertEquals("{\"code\":\"ACCESS_DENIED\"}", FluxerHttpTransport.errorCode(JsonParser.parseString(
                "{\"code\":\"ACCESS_DENIED\",\"message\":\"private\",\"token\":\"secret\"}")).toString());
        assertEquals("50007", FluxerHttpTransport.errorCode(JsonParser.parseString("{\"code\":50007}")).get("code").getAsString());
        for (String value : new String[]{"null", "[]", "{}", "{\"code\":true}",
                "{\"code\":{\"private\":1}}", "{\"code\":\"private text\"}",
                "{\"code\":\"https://example.org/secret\"}"}) {
            assertEquals(0, FluxerHttpTransport.errorCode(JsonParser.parseString(value)).size());
        }
    }
}
