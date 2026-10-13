/* SPDX-License-Identifier: Apache-2.0 */
package org.microemu.android.asm;

import org.junit.Test;
import org.objectweb.asm.*;
import static org.junit.Assert.*;
import static org.objectweb.asm.Opcodes.*;

public class AndroidProducerTest {
    @Test public void java8HandlerStackSurvivesInstrumentation() { check(V1_8, true); }
    @Test public void java7HandlerStackSurvivesInstrumentation() { check(V1_7, true); }
    @Test public void legacyClassWithoutFramesStillWorks() { check(V1_5, false); }

    private void check(int version, boolean frames) {
        ClassWriter writer = new ClassWriter(frames ? ClassWriter.COMPUTE_FRAMES : ClassWriter.COMPUTE_MAXS);
        writer.visit(version, ACC_PUBLIC, "StackProbe", null, "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(ACC_PUBLIC | ACC_STATIC, "read", "()V", null, null);
        Label start = new Label(), end = new Label(), handler = new Label();
        method.visitCode();
        method.visitTryCatchBlock(start, end, handler, "java/lang/Exception");
        method.visitLabel(start);
        method.visitMethodInsn(INVOKESTATIC, "fixture/MissingOnHost", "run", "()V", false);
        method.visitLabel(end);
        method.visitInsn(RETURN);
        method.visitLabel(handler);
        method.visitVarInsn(ASTORE, 0);
        method.visitLdcInsn("encoding");
        // Instrumentation adds an encoding-property argument, raising peak stack to 2.
        method.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "getBytes", "()[B", false);
        method.visitInsn(POP);
        method.visitInsn(RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd(); writer.visitEnd();
        byte[] transformed = AndroidProducer.instrument(writer.toByteArray(), "StackProbe.class");
        final int[] max = {-1};
        new ClassReader(transformed).accept(new ClassVisitor(ASM9) {
            @Override public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
                return new MethodVisitor(ASM9) {
                    @Override public void visitMaxs(int stack, int locals) { max[0] = stack; }
                };
            }
        }, 0);
        assertEquals("Handler and injected encoding argument must be included", 2, max[0]);
    }
}
