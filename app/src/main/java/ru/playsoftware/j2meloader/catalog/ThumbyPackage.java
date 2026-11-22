/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.catalog;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/** Maps a single arcade game folder to the paths its MicroPython code expects. */
public final class ThumbyPackage {
    public final String title, main;
    public final SortedMap<String, ZipEntry> files = new TreeMap<>();
    public ZipEntry movie;

    private ThumbyPackage(String title, String main) { this.title = title; this.main = main; }

    public static boolean recognizes(ZipFile zip) throws IOException {
        ThumbyPackage game;
        try { game = read(zip); } catch (IllegalArgumentException invalid) { return false; }
        for (ZipEntry entry : game.files.values()) {
            if (!entry.getName().endsWith(".py")) continue;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8))) {
                char[] prefix = new char[8192];
                int n = reader.read(prefix);
                if (n > 0 && new String(prefix, 0, n).matches("(?s).*\\b(?:import|from)\\s+thumby\\w*\\b.*")) return true;
            }
        }
        return game.movie != null;
    }

    public static ThumbyPackage read(ZipFile zip) throws IOException {
        List<String> python = new ArrayList<>(), named = new ArrayList<>();
        for (Enumeration<? extends ZipEntry> it = zip.entries(); it.hasMoreElements();) {
            String path = it.nextElement().getName();
            if (path.startsWith("__MACOSX/") || !path.endsWith(".py")) continue;
            python.add(path);
            String filename = path.substring(path.lastIndexOf('/') + 1);
            String dir = path.substring(0, path.length() - filename.length());
            if (dir.endsWith("/" + filename.substring(0, filename.length()-3) + "/")
                    || dir.equals(filename.substring(0, filename.length()-3) + "/")) named.add(path);
        }
        if (named.isEmpty() && python.size() == 1) named.add(python.get(0));
        if (named.isEmpty()) for (String path : python) {
            if (path.equals("main.py") || path.endsWith("/main.py")) named.add(path);
        }
        if (named.isEmpty() && python.size() > 1) {
            // Arcade ZIPs can place the entry script and its helper modules side by side.
            Set<String> helpers = new HashSet<>();
            java.util.regex.Pattern imports = java.util.regex.Pattern.compile("(?m)^\\s*(?:from|import)\\s+([a-zA-Z_][a-zA-Z_0-9]*)");
            for (String path : python) {
                ZipEntry entry = zip.getEntry(path);
                if (entry.getSize() < 0 || entry.getSize() > 0x160000) continue;
                String dir = path.substring(0, path.lastIndexOf('/') + 1);
                try (BufferedReader in = new BufferedReader(new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = in.readLine()) != null) {
                        java.util.regex.Matcher match = imports.matcher(line);
                        if (match.find()) helpers.add(dir + match.group(1) + ".py");
                    }
                }
            }
            for (String path : python) if (!helpers.contains(path)) named.add(path);
        }
        if (named.size() != 1) throw new IllegalArgumentException("Choose one Thumby game folder with its main Python script");
        String entry = named.get(0), root = entry.substring(0, entry.lastIndexOf('/') + 1);
        String main = entry.substring(root.length()), title = main.substring(0, main.length()-3);
        if ("main".equals(title) && !root.isEmpty()) {
            String folder = root.substring(0, root.length()-1);
            title = folder.substring(folder.lastIndexOf('/')+1);
        }
        if (title.isEmpty() || title.equals(".") || title.equals("..") || title.contains("\\"))
            throw new IllegalArgumentException("Invalid game directory");
        ThumbyPackage game = new ThumbyPackage(title, main);
        long total = 0;
        for (Enumeration<? extends ZipEntry> it = zip.entries(); it.hasMoreElements();) {
            ZipEntry e = it.nextElement();
            if (e.isDirectory() || !e.getName().startsWith(root)) continue;
            String name = e.getName().substring(root.length());
            if (name.startsWith("__MACOSX/") || name.equals("arcade_description.txt")) continue;
            if (name.startsWith("arcade_") && name.endsWith(".webm")) { game.movie = e; continue; }
            if (name.contains("\\") || Arrays.stream(name.split("/", -1)).anyMatch(s -> s.isEmpty() || s.equals(".") || s.equals("..")))
                throw new IllegalArgumentException("Invalid game file path");
            if (game.files.put(name, e) != null) throw new IllegalArgumentException("Duplicate game file");
            total += e.getSize();
            if (e.getSize() < 0 || total > 0x160000) throw new IllegalArgumentException("Game exceeds Thumby flash capacity");
        }
        return game;
    }
}
