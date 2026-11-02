package org.microemu.android.asm;

import org.junit.Test;
import org.objectweb.asm.*;
import static org.objectweb.asm.Opcodes.*;
import static org.junit.Assert.*;

public class BooleanReturnTest {
    private byte[] fixture(String result) {
        ClassWriter out = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        out.visit(V1_5, ACC_PUBLIC, "BooleanFixture", null, "java/lang/Object", null);
        MethodVisitor method = out.visitMethod(ACC_PUBLIC | ACC_STATIC, "value", "(I)" + result, null, null);
        method.visitCode(); method.visitVarInsn(ILOAD, 0); method.visitInsn(I2S);
        method.visitInsn(IRETURN); method.visitMaxs(0,0); method.visitEnd(); out.visitEnd();
        return AndroidProducer.instrument(out.toByteArray(), "BooleanFixture.class");
    }
    @Test public void booleanReturnIsNarrowedButIntegerReturnIsUnchanged() throws Exception {
        for (String result : new String[]{"Z", "I", "S"}) {
            byte[] bytes = fixture(result);
            int[] masks = {0};
            new ClassReader(bytes).accept(new ClassVisitor(ASM9) {
                @Override public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e) {
                    return new MethodVisitor(ASM9) { @Override public void visitInsn(int op) { if(op == IAND) masks[0]++; } };
                }
            }, 0);
            assertEquals(result.equals("Z") ? 1 : 0, masks[0]);
            Class<?> type = new ClassLoader() { Class<?> load() { return defineClass("BooleanFixture", bytes, 0, bytes.length); } }.load();
            for (int value : new int[]{0, 1, 2, 3, -1, -2, 32767, 32768, 65535}) {
                Object actual = type.getMethod("value", int.class).invoke(null, value);
                if (result.equals("Z")) assertEquals((value & 1) != 0, actual);
                else assertEquals((short)value, ((Number) actual).intValue());
            }
        }
    }
}
