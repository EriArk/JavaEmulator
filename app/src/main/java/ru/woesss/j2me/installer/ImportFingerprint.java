package ru.woesss.j2me.installer;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.TreeMap;
import ru.woesss.j2me.jar.Descriptor;

final class ImportFingerprint {
    static String of(String jarHash, Descriptor manifest, Descriptor descriptor) {
        try {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            add(digest,jarHash);
            Map<String,String> attributes=new TreeMap<>();
            if(manifest!=null) attributes.putAll(manifest.getAttrs());
            attributes.putAll(descriptor.getAttrs());
            for(Map.Entry<String,String> entry:attributes.entrySet()) {
                add(digest,entry.getKey()); add(digest,entry.getValue());
            }
            StringBuilder result=new StringBuilder(64);
            for(byte b:digest.digest()) result.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
            return result.toString();
        } catch(NoSuchAlgorithmException error) { throw new AssertionError(error); }
    }
    private static void add(MessageDigest digest,String value) {
        byte[] bytes=value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(4).putInt(bytes.length).array()); digest.update(bytes);
    }
}
