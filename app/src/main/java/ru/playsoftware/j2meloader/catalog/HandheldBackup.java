/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.catalog;

import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Managed handheld packages are already portable. Publish each card after its dependencies. */
final class HandheldBackup {
    static final List<String> ENGINES = Arrays.asList("wipi", "dingoo", "piece", "gp32", "supervision", "megaduck", "cybiko", "flash", "gamate", "gameking", "palm");
    final String engine, id, title, format;
    final File root;
    HandheldBackup(File root, String engine, String id, Properties metadata) {
        this.root = root; this.engine = engine; this.id = id;
        title = metadata.getProperty("title", id);
        format = metadata.getProperty("format", metadata.getProperty("carrier", "")).toUpperCase(Locale.ROOT);
    }
    String card() { return engine + "/packages/" + id; }
    String data() { return engine + "/data/" + id; }
    String config() { return "configs/" + engine + "-" + id; }
    static boolean handheldPath(String[] p) {
        return p.length >= 3 && ENGINES.contains(p[0]) && (p[1].equals("system") ||
                p.length >= 4 && (p[1].equals("packages") || p[1].equals("data")) && p[2].matches("[0-9a-f]{64}"));
    }
    static List<HandheldBackup> list(File root) throws IOException {
        List<HandheldBackup> result = new ArrayList<>();
        for (String engine : ENGINES) {
            File packages = new File(root, engine + "/packages");
            if (!packages.exists()) continue;
            File[] cards = packages.listFiles();
            if (cards == null) throw new IOException("Cannot read " + packages);
            for (File card : cards) {
                if (!card.getName().matches("[a-f0-9]{64}")) continue;
                Properties meta = new Properties();
                try (InputStream in = new FileInputStream(new File(card,"card.properties"))) { meta.load(in); }
                result.add(new HandheldBackup(root,engine,card.getName(),meta));
            }
        }
        return result;
    }
    static void migratePieceBase(Context c, File root) throws IOException {
        File marker = new File(root, "piece/system/base");
        String id = c.getSharedPreferences("piece",0).getString("base", "");
        if (marker.exists() || !id.matches("[0-9a-f]{64}") || !new File(root,"piece/packages/"+id+"/image.pfi").isFile()) return;
        Files.createDirectories(marker.getParentFile().toPath());
        try { Files.write(marker.toPath(), id.getBytes(StandardCharsets.US_ASCII), StandardOpenOption.CREATE_NEW); }
        catch (FileAlreadyExistsException ignored) { }
    }
    static void merge(File source, File target, AtomicBoolean cancelled) throws IOException {
        if (!source.exists()) return;
        if (cancelled.get() || Thread.currentThread().isInterrupted()) throw new CancellationException();
        if (!target.getCanonicalFile().equals(target.getAbsoluteFile().toPath().normalize().toFile()))
            throw new IOException("Linked restore destination: " + target.getName());
        if (source.isDirectory()) {
            Files.createDirectories(target.toPath());
            File[] files = source.listFiles();
            if (files == null) throw new IOException("Cannot read " + source);
            for (File f : files) merge(f, new File(target,f.getName()), cancelled);
        } else if (!target.exists()) {
            Files.createDirectories(target.getParentFile().toPath());
            File tmp = File.createTempFile(".restore-", ".tmp",target.getParentFile());
            try {
                Files.copy(source.toPath(),tmp.toPath(),StandardCopyOption.REPLACE_EXISTING);
                if (!target.exists()) Files.move(tmp.toPath(),target.toPath());
            } finally { tmp.delete(); }
        }
    }
    void restore(File destination, AtomicBoolean cancelled) throws IOException {
        destination = destination.getCanonicalFile();
        File system = new File(root, engine + "/system");
        if (engine.equals("piece") && !new File(destination, "piece/system/base").exists()) {
            File marker = new File(system, "base");
            if (marker.isFile()) {
                if (marker.length() != 64) throw new IOException("Invalid P/ECE base selection");
                String base = new String(Files.readAllBytes(marker.toPath()), StandardCharsets.US_ASCII);
                if (!base.matches("[0-9a-f]{64}")) throw new IOException("Invalid P/ECE base identity");
                String dependency = "piece/packages/" + base;
                if (new File(root, dependency + "/image.pfi").isFile())
                    restoreCard(new File(root, dependency), new File(destination, dependency), cancelled);
                // A base card can be removed independently of games that already contain its flash.
                if (new File(destination, dependency + "/image.pfi").isFile())
                    merge(marker, new File(destination, "piece/system/base"), cancelled);
            }
        } else if (!engine.equals("piece")) {
            merge(system, new File(destination, engine + "/system"), cancelled);
        }
        merge(new File(root, data()), new File(destination, data()), cancelled);
        restoreCard(new File(root, card()), new File(destination, card()), cancelled);
    }

    private static void restoreCard(File source, File target, AtomicBoolean cancelled) throws IOException {
        if (!target.getCanonicalFile().equals(target))
            throw new IOException("Linked package destination: " + target.getName());
        if (target.isDirectory()) {
            merge(source, target, cancelled);
            return;
        }
        Files.createDirectories(target.getParentFile().toPath());
        File staging = Files.createTempDirectory(target.getParentFile().toPath(), ".restore-").toFile();
        try {
            merge(source, staging.getCanonicalFile(), cancelled);
            if (!staging.renameTo(target) && !target.isDirectory()) throw new IOException("Cannot restore " + target.getName());
        } finally { LibraryArchive.remove(staging); }
    }
}
