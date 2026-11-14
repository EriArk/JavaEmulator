/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;

/** Null members mean an explicit cleared setting, not a missing/inherited setting. */
public final class SharedValuesAdapter extends TypeAdapter<JsonObject> {
    @Override public JsonObject read(JsonReader in) throws IOException {
        JsonElement value=JsonParser.parseReader(in);
        return value.isJsonNull()?null:value.getAsJsonObject();
    }
    @Override public void write(JsonWriter out,JsonObject value) throws IOException {
        boolean previous=out.getSerializeNulls();out.setSerializeNulls(true);
        try{com.google.gson.internal.Streams.write(value,out);}finally{out.setSerializeNulls(previous);}
    }
}
