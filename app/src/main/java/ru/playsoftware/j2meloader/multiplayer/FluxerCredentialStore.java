/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.multiplayer;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** No-backup ciphertext only; the encryption key stays in Android Keystore. */
final class FluxerCredentialStore implements FluxerAuth.Store {
    private static final byte[] AAD = "AbyssME.Fluxer.session.v1".getBytes(StandardCharsets.US_ASCII);
    private final AtomicFile file;
    private final String alias;

    FluxerCredentialStore(Context context) {
        this(new File(context.getNoBackupFilesDir(), "fluxer-session.bin"), "abyssme.fluxer.session.v1");
    }
    FluxerCredentialStore(File path, String alias) { this.file = new AtomicFile(path); this.alias = alias; }
    private SecretKey key(boolean create) throws Exception {
        KeyStore keys = KeyStore.getInstance("AndroidKeyStore"); keys.load(null);
        if (keys.containsAlias(alias)) return (SecretKey) keys.getKey(alias, null);
        if (!create) throw new java.io.IOException("Session key unavailable");
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).setRandomizedEncryptionRequired(true).build());
        return generator.generateKey();
    }
    @Override public String read() throws Exception {
        byte[] packed;
        try (java.io.FileInputStream input = file.openRead()) {
            packed = new byte[16385]; int count = 0, n;
            while (count < packed.length && (n = input.read(packed, count, packed.length - count)) != -1) count += n;
            if (count < 30 || count >= packed.length || packed[0] != 1) throw new java.io.IOException("Invalid session data");
            packed = java.util.Arrays.copyOf(packed, count);
        } catch (java.io.FileNotFoundException e) {
            if (file.getBaseFile().exists()) throw e;
            return null;
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(false), new GCMParameterSpec(128, packed, 1, 12));
        cipher.updateAAD(AAD);
        byte[] plain = cipher.doFinal(packed, 13, packed.length - 13);
        try {
            String token = new String(plain, StandardCharsets.UTF_8);
            if (!FluxerAuth.validToken(token)) throw new java.io.IOException("Invalid session");
            return token;
        } finally { java.util.Arrays.fill(plain, (byte) 0); }
    }
    @Override public void save(String token) throws Exception {
        if (!FluxerAuth.validToken(token)) throw new IllegalArgumentException("Invalid session");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key(true));
        cipher.updateAAD(AAD);
        byte[] plain = token.getBytes(StandardCharsets.UTF_8), encrypted;
        try { encrypted = cipher.doFinal(plain); } finally { java.util.Arrays.fill(plain, (byte)0); }
        if (cipher.getIV().length != 12) throw new java.io.IOException("Invalid IV");
        byte[] packed = ByteBuffer.allocate(13 + encrypted.length).put((byte)1).put(cipher.getIV()).put(encrypted).array();
        FileOutputStream out = null;
        try { out = file.startWrite(); out.write(packed); file.finishWrite(out); }
        catch (Exception e) { if (out != null) file.failWrite(out); throw e; }
    }
    @Override public void clear() throws Exception {
        file.delete();
        if (file.getBaseFile().exists() || new File(file.getBaseFile() + ".bak").exists()
                || new File(file.getBaseFile() + ".new").exists()) throw new java.io.IOException("Could not remove session");
        KeyStore keys = KeyStore.getInstance("AndroidKeyStore"); keys.load(null); keys.deleteEntry(alias);
    }
}
