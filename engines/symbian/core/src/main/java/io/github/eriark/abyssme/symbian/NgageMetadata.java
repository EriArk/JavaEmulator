// SPDX-License-Identifier: GPL-3.0-only
package io.github.eriark.abyssme.symbian;

import android.util.Log;
import android.util.Xml;
import org.xmlpull.v1.XmlPullParser;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

/** Compatibility for legacy language tags in N-Gage's temporary achievement import. */
public final class NgageMetadata {
    static final int MAX_BYTES = 1024 * 1024;
    private NgageMetadata() { }

    static byte[] normalized(byte[] original) throws Exception {
        if (original.length > MAX_BYTES) throw new IOException("Metadata is too large");
        String xml = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(original)).toString();
        if (xml.startsWith("\uFEFF")) xml = xml.substring(1);
        // Reject DTDs before parsing, including internal entity expansion.
        if (xml.contains("<!DOCTYPE") || xml.contains("<!ENTITY")) throw new IOException("DTD not allowed");
        // Bound tree complexity before constructing or serializing a DOM.
        XmlPullParser limits = Xml.newPullParser();
        limits.setInput(new StringReader(xml));
        int elements = 0;
        while (limits.next() != XmlPullParser.END_DOCUMENT) {
            if (limits.getEventType() == XmlPullParser.START_TAG
                    && (++elements > 10000 || limits.getDepth() > 32))
                throw new IOException("Metadata structure is too complex");
        }
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setExpandEntityReferences(false);
        javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setEntityResolver((publicId, systemId) -> { throw new SAXException("External entity not allowed"); });
        builder.setErrorHandler(new DefaultHandler() {
            @Override public void error(org.xml.sax.SAXParseException e) throws SAXException { throw e; }
            @Override public void fatalError(org.xml.sax.SAXParseException e) throws SAXException { throw e; }
        });
        Document document = builder.parse(new InputSource(new StringReader(xml)));
        Element root = document.getDocumentElement();
        if (!"GameAchievements".equals(root.getTagName()) || root.getNamespaceURI() != null
                || !root.getAttribute("gameid").matches("[0-9a-fA-F]{8}")) return null;
        boolean changed = false;
        for (Node achievement = root.getFirstChild(); achievement != null; achievement = achievement.getNextSibling()) {
            if (!(achievement instanceof Element) || !"Achievement".equals(achievement.getNodeName())
                    || achievement.getNamespaceURI() != null) continue;
            Set<String> languages = new HashSet<>();
            for (Node child = achievement.getFirstChild(); child != null; child = child.getNextSibling()) {
                if (!(child instanceof Element) || child.getNamespaceURI() != null) continue;
                Element value = (Element) child;
                String name = value.getTagName();
                if (!name.equals("Title") && !name.equals("Description") && !name.equals("IconName")) continue;
                String lang = value.getAttribute("lang");
                String canonical = lang.equals("sp") ? "es" : lang;
                if (!languages.add(name + ":" + canonical)) throw new IOException("Conflicting translations");
                if (!lang.equals(canonical)) { value.setAttribute("lang", canonical); changed = true; }
            }
        }
        if (!changed) return null;
        javax.xml.transform.Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        transformer.transform(new DOMSource(document), new StreamResult(output));
        if (output.size() > MAX_BYTES) throw new IOException("Normalized metadata is too large");
        return output.toByteArray();
    }

    static boolean repair(File file, File backups) throws Exception {
        if (Files.isSymbolicLink(file.toPath()) || !file.isFile() || file.length() > MAX_BYTES) return false;
        byte[] original;
        try (java.io.InputStream input = Files.newInputStream(file.toPath())) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            for (int count; (count = input.read(buffer)) != -1;) {
                if (bytes.size() + count > MAX_BYTES) throw new IOException("Metadata is too large");
                bytes.write(buffer, 0, count);
            }
            original = bytes.toByteArray();
        }
        byte[] replacement = normalized(original);
        if (replacement == null) return false;
        StringBuilder hash = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(original)) hash.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
        if (Files.isSymbolicLink(backups.toPath())) throw new IOException("Invalid backup directory");
        Files.createDirectories(backups.toPath());
        File backup = new File(backups, "achievements.abyssme-original-" + hash + ".xml");
        if (!backup.exists()) Files.write(backup.toPath(), original, java.nio.file.StandardOpenOption.CREATE_NEW);
        if (Files.isSymbolicLink(backup.toPath()) || backup.length() != original.length
                || !Arrays.equals(original, Files.readAllBytes(backup.toPath()))) throw new IOException("Metadata backup differs");
        File temporary = File.createTempFile("achievements-", ".tmp", file.getParentFile());
        try {
            Files.write(temporary.toPath(), replacement);
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary.toPath()); }
        return true;
    }

    public static void prepare(File file, File backups) {
        try {
            if (repair(file, backups)) Log.i("AbyssSymbian", "Normalized legacy Spanish achievement language; original XML retained");
        } catch (Exception error) {
            Log.w("AbyssSymbian", "Achievement metadata left unchanged: " + error.getMessage());
        }
    }
}
