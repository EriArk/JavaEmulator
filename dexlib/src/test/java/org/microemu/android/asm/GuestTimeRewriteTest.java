package org.microemu.android.asm;

import org.junit.Test;
import org.objectweb.asm.MethodVisitor;
import static org.objectweb.asm.Opcodes.*;
import static org.junit.Assert.*;

public class GuestTimeRewriteTest {
    @Test public void redirectsOnlyGuestTimingAndPreservesDescriptors() {
        String[] result = {""};
        AndroidMethodVisitor visitor = new AndroidMethodVisitor(new MethodVisitor(ASM9) {
            @Override public void visitMethodInsn(int op, String owner, String name, String desc, boolean itf) {
                result[0] = op + " " + owner + "." + name + desc;
            }
        });
        visitor.visitMethodInsn(INVOKESTATIC, "java/lang/System", "currentTimeMillis", "()J", false);
        assertEquals(INVOKESTATIC + " javax/microedition/shell/GuestTime.currentTimeMillis()J", result[0]);
        visitor.visitMethodInsn(INVOKESTATIC, "java/lang/Thread", "sleep", "(JI)V", false);
        assertTrue(result[0].endsWith("GuestTime.sleep(JI)V"));
        visitor.visitMethodInsn(INVOKEVIRTUAL, "game/Loop", "wait", "(J)V", false);
        assertEquals(INVOKESTATIC + " javax/microedition/shell/GuestTime.wait(Ljava/lang/Object;J)V", result[0]);
        visitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Object", "wait", "()V", false);
        assertEquals(INVOKEVIRTUAL + " java/lang/Object.wait()V", result[0]);
        visitor.visitMethodInsn(INVOKESTATIC, "java/lang/System", "gc", "()V", false);
        assertTrue(result[0].endsWith("java/lang/System.gc()V"));
    }
}
