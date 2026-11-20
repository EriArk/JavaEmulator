/* SPDX-License-Identifier: Apache-2.0 */
package org.microemu.android.asm;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;

/** CLDC omits Thread.resume(). A MIDlet may use that name for its own virtual method. */
public final class CldcThreadMethods {
    private static final String METHOD = "resume", DESCRIPTOR = "()V";
    private static final class Type extends ClassVisitor {
        final String parent;
        final String[] interfaces;
        final Set<String> names;
        boolean declares;
        Type(ClassReader reader, Set<String> names) {
            super(Opcodes.ASM9);
            parent = reader.getSuperName();
            interfaces = reader.getInterfaces();
            this.names = names;
        }
        @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            if (DESCRIPTOR.equals(descriptor)) {
                names.add(name);
                if (METHOD.equals(name) && (access & Opcodes.ACC_STATIC) == 0) declares = true;
            }
            return null;
        }
    }

    /** Only guest classes enter this graph. Track descendants and guest interface contracts. */
    private static boolean inherits(String name, Set<String> targets, Map<String, Type> classes) {
        ArrayDeque<String> pending = new ArrayDeque<>();
        Set<String> seen = new HashSet<>();
        pending.add(name);
        while (!pending.isEmpty()) {
            String current = pending.remove();
            if (!seen.add(current)) continue;
            if (targets.contains(current)) return true;
            Type type = classes.get(current);
            if (type == null) continue;
            if (type.parent != null) pending.add(type.parent);
            Collections.addAll(pending, type.interfaces);
        }
        return false;
    }

    /** Returns the original file unless guest bytecode actually conflicts with Android. */
    public static File prepare(File source, File converted) throws IOException {
        Map<String, Type> classes = new HashMap<>();
        Set<String> names = new HashSet<>(), rename = new HashSet<>();
        try (ZipFile zip = new ZipFile(source)) {
            List<? extends ZipEntry> entries = Collections.list(zip.entries());
            for (ZipEntry entry : entries) {
                if (!entry.getName().endsWith(".class")) continue;
                try (InputStream in = zip.getInputStream(entry)) {
                    ClassReader reader = new ClassReader(in);
                    Type type = new Type(reader, names);
                    reader.accept(type, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    classes.put(reader.getClassName(), type);
                }
            }
            for (Map.Entry<String, Type> entry : classes.entrySet()) {
                if (entry.getValue().declares && inherits(entry.getKey(), Collections.singleton("java/lang/Thread"), classes))
                    rename.add(entry.getKey());
            }
            if (rename.isEmpty()) return source;
            // Include declaring ancestors (including interfaces) and every receiver that
            // inherits their implementation. A subclass may introduce another interface.
            boolean changed;
            do {
                changed = false;
                for (Map.Entry<String,Type> entry : classes.entrySet()) {
                    String name = entry.getKey();
                    if (!inherits(name, rename, classes)) continue;
                    changed |= rename.add(name);
                    ArrayDeque<String> parents = new ArrayDeque<>();
                    parents.add(name);
                    Set<String> seen = new HashSet<>();
                    while (!parents.isEmpty()) {
                        String owner = parents.remove();
                        Type parent = classes.get(owner);
                        if (parent == null || !seen.add(owner)) continue;
                        if (parent.declares) changed |= rename.add(owner);
                        if (parent.parent != null) parents.add(parent.parent);
                        Collections.addAll(parents, parent.interfaces);
                    }
                }
            } while (changed);
            String replacement = "abyssme$resume";
            while (names.contains(replacement)) replacement += "$";
            try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(converted.toPath()))) {
                for (ZipEntry entry : entries) {
                    if (!entry.getName().endsWith(".class")) continue;
                    try (InputStream in = zip.getInputStream(entry)) {
                        out.putNextEntry(new ZipEntry(entry.getName()));
                        out.write(rewrite(new ClassReader(in), rename, replacement));
                        out.closeEntry();
                    }
                }
            }
        }
        return converted;
    }

    private static byte[] rewrite(ClassReader reader, Set<String> owners, String replacement) {
        ClassWriter writer = new ClassWriter(reader, 0);
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                if (METHOD.equals(name) && DESCRIPTOR.equals(desc) && owners.contains(reader.getClassName()))
                    name = replacement;
                return new MethodVisitor(Opcodes.ASM9, super.visitMethod(access, name, desc, signature, exceptions)) {
                    @Override public void visitMethodInsn(int opcode, String owner, String method, String descriptor, boolean itf) {
                        if (METHOD.equals(method) && DESCRIPTOR.equals(descriptor) && owners.contains(owner))
                            method = replacement;
                        super.visitMethodInsn(opcode, owner, method, descriptor, itf);
                    }
                };
            }
        }, 0);
        return writer.toByteArray();
    }

    private CldcThreadMethods() {}
}
