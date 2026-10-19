package javax.microedition.shell;

import android.os.SystemClock;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.jar.*;
import org.junit.Test;
import org.objectweb.asm.*;
import static org.junit.Assert.*;
import static org.objectweb.asm.Opcodes.*;

public class GuestTimeDeviceTest {
    @Test public void installedJarUsesNewClockWithoutModifyingOriginalFiles() throws Exception {
        File root = Files.createTempDirectory(InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir().toPath(), "speed-test").toFile();
        File jar = new File(root, ru.playsoftware.j2meloader.config.Config.MIDLET_RES_FILE);
        ClassWriter writer = new ClassWriter(0);
        writer.visit(V1_5, ACC_PUBLIC, "ClockFixture", null, "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(ACC_PUBLIC | ACC_STATIC, "read", "()J", null, null);
        method.visitCode(); method.visitMethodInsn(INVOKESTATIC, "java/lang/System", "currentTimeMillis", "()J", false);
        method.visitInsn(LRETURN); method.visitMaxs(2,0); method.visitEnd(); writer.visitEnd();
        try (JarOutputStream output = new JarOutputStream(new FileOutputStream(jar))) {
            output.putNextEntry(new JarEntry("ClockFixture.class")); output.write(writer.toByteArray()); output.closeEntry();
        }
        byte[] original = Files.readAllBytes(jar.toPath());
        File oldDex = new File(root, "old.dex"); Files.write(oldDex.toPath(), new byte[]{1,2,3});
        try {
            File dex = GuestDex.prepare(root, root, oldDex);
            assertNotEquals(oldDex, dex); assertEquals(dex, GuestDex.prepare(root, root, oldDex));
            assertArrayEquals(original, Files.readAllBytes(jar.toPath()));
            assertArrayEquals(new byte[]{1,2,3}, Files.readAllBytes(oldDex.toPath()));
            assertTrue(dex.setReadOnly());
            Class<?> fixture = new dalvik.system.DexClassLoader(dex.getPath(), root.getPath(), null, getClass().getClassLoader()).loadClass("ClockFixture");
            for (int speed : new int[]{50,100,200,300,400}) {
                GuestTime.speed(speed);
                long guest = (Long)fixture.getMethod("read").invoke(null), real = SystemClock.elapsedRealtime();
                SystemClock.sleep(300);
                long delta = (Long)fixture.getMethod("read").invoke(null) - guest;
                long elapsed = SystemClock.elapsedRealtime() - real;
                assertTrue("Converted guest clock at " + speed, Math.abs(delta - elapsed * speed / 100) < 40);
            }
        } finally { GuestTime.speed(100); ru.playsoftware.j2meloader.util.FileUtils.deleteDirectory(root); }
    }
    @Test public void sleepsTimersAndInterruptsHonorGuestTime() throws Exception {
        try {
            for (int speed : new int[]{50,200,400}) {
                GuestTime.speed(speed);
                long real = SystemClock.elapsedRealtime(); GuestTime.sleep(200);
                long elapsed = SystemClock.elapsedRealtime() - real;
                assertTrue(elapsed >= 20000 / speed - 10 && elapsed < 20000 / speed + 200);
                CountDownLatch fired = new CountDownLatch(1);
                javax.microedition.shell.custom.Timer timer = new javax.microedition.shell.custom.Timer(true);
                real = SystemClock.elapsedRealtime();
                try {
                    timer.schedule(new javax.microedition.shell.custom.TimerTask() { public void run() { fired.countDown(); } }, 200);
                    assertTrue(fired.await(2,TimeUnit.SECONDS));
                    elapsed = SystemClock.elapsedRealtime() - real;
                    assertTrue(elapsed >= 20000 / speed - 10 && elapsed < 20000 / speed + 200);
                } finally { timer.cancel(); }
            }
            Thread.currentThread().interrupt();
            try { GuestTime.sleep(1); fail("Expected interrupt"); } catch (InterruptedException expected) { }
        } finally { Thread.interrupted(); GuestTime.speed(100); }
    }
}
