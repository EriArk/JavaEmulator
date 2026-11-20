/* SPDX-License-Identifier: Apache-2.0 */
package org.microemu.android.asm;

import java.io.*;
import java.util.zip.*;
import java.util.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.objectweb.asm.*;
import static org.junit.Assert.*;
import static org.objectweb.asm.Opcodes.*;

public class CldcThreadMethodsTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private static void add(ZipOutputStream zip, String name, String parent, String contract, String... methods) throws IOException {
        ClassWriter w = new ClassWriter(0);
        w.visit(V1_4, ACC_PUBLIC | ACC_ABSTRACT | (name.equals("Contract") ? ACC_INTERFACE : 0),
                name, null, parent, contract == null ? null : new String[]{contract});
        for (String method : methods) w.visitMethod(ACC_PUBLIC | ACC_ABSTRACT, method, "()V", null, null).visitEnd();
        w.visitEnd();
        zip.putNextEntry(new ZipEntry(name + ".class"));
        zip.write(w.toByteArray());
        zip.closeEntry();
    }

    @Test public void changesOnlyConflictingGuestDispatchFamilies() throws Exception {
        File source = tmp.newFile("game.jar"), out = tmp.newFile("input.jar");
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(source))) {
            add(zip, "Parent", "java/lang/Thread", null);
            add(zip, "Worker", "Parent", null, "resume");
            add(zip, "Contract", "java/lang/Object", null, "resume");
            add(zip, "NamedWorker", "Worker", "Contract");
            add(zip, "AnotherWorker", "java/lang/Object", "Contract", "resume");
            add(zip, "Other", "java/lang/Object", null, "resume", "abyssme$resume");
            ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            w.visit(V1_4, ACC_PUBLIC, "Calls", null, "java/lang/Object", null);
            MethodVisitor m = w.visitMethod(ACC_PUBLIC | ACC_STATIC, "run", "()V", null, null);
            m.visitCode();
            for (String owner : Arrays.asList("NamedWorker", "Contract", "Other")) {
                m.visitInsn(ACONST_NULL);
                boolean iface = owner.equals("Contract");
                m.visitMethodInsn(iface ? INVOKEINTERFACE : INVOKEVIRTUAL, owner, "resume", "()V", iface);
            }
            m.visitInsn(RETURN); m.visitMaxs(0, 0); m.visitEnd(); w.visitEnd();
            zip.putNextEntry(new ZipEntry("Calls.class")); zip.write(w.toByteArray()); zip.closeEntry();
        }
        assertEquals(out, CldcThreadMethods.prepare(source,out));
        try (ZipFile zip = new ZipFile(out)) {
            List<String> calls = new ArrayList<>(), declarations = new ArrayList<>();
            for (ZipEntry entry : Collections.list(zip.entries())) {
                try (InputStream in = zip.getInputStream(entry)) {
                    ClassReader r = new ClassReader(in);
                    r.accept(new ClassVisitor(ASM9) {
                        public MethodVisitor visitMethod(int access,String name,String desc,String sig,String[] ex) {
                            declarations.add(r.getClassName()+"."+name);
                            return new MethodVisitor(ASM9) {
                                public void visitMethodInsn(int op,String owner,String name,String desc,boolean itf) {
                                    calls.add(owner+"."+name);
                                }
                            };
                        }
                    },0);
                }
            }
            assertEquals(Arrays.asList("NamedWorker.abyssme$resume$", "Contract.abyssme$resume$", "Other.resume"), calls);
            assertTrue(declarations.containsAll(Arrays.asList("Worker.abyssme$resume$", "Contract.abyssme$resume$",
                    "AnotherWorker.abyssme$resume$", "Other.resume")));
        }
    }

    @Test public void ordinaryGameKeepsOriginalArchive() throws Exception {
        File source=tmp.newFile("ordinary.jar"), out=tmp.newFile("unused.jar");
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(source))) {
            add(zip, "Worker", "java/lang/Thread", null, "run");
            add(zip, "Other", "java/lang/Object", null, "resume");
        }
        assertEquals(source,CldcThreadMethods.prepare(source,out));
    }
}
