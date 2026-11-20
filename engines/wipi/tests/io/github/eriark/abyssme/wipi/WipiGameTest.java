/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.wipi;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.SystemClock;
import android.view.*;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import java.io.*;
import static org.junit.Assert.*;

public class WipiGameTest {
    private final Instrumentation instr=InstrumentationRegistry.getInstrumentation();
    private final Context context=instr.getTargetContext();
    private int basket() {
        Bitmap image=instr.getUiAutomation().takeScreenshot();
        if(image==null)return -1;
        try {
            int[] row=new int[image.getWidth()];
            for(int y=image.getHeight()*9/10;y>image.getHeight()/2;y-=3) {
                image.getPixels(row,0,row.length,0,y,row.length,1);
                int start=-1;
                for(int x=0;x<row.length;x++) {
                    int c=row[x];
                    if(Color.red(c)>235 && Color.green(c)>112 && Color.green(c)<164 && Color.blue(c)<85) {
                        if(start<0)start=x;
                    } else {
                        if(start>=0 && x-start>row.length/16)return (x+start)/2;
                        start=-1;
                    }
                }
            }
            return -1;
        } finally {image.recycle();}
    }
    private int awaitBasket(int initial,boolean moved) {
        long end=SystemClock.uptimeMillis()+15000;
        do {
            int x=basket();
            if(x>=0 && (!moved || x>initial+25))return x;
            SystemClock.sleep(100);
        }while(SystemClock.uptimeMillis()<end);
        throw new AssertionError("Could not see "+(moved?"basket movement":"initial basket"));
    }
    private static WipiPackage readArchive(String... names) throws IOException {
        return readArchive(java.nio.charset.StandardCharsets.UTF_8, names);
    }

    private static WipiPackage readArchive(java.nio.charset.Charset charset, String... names) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(bytes, charset)) {
            for (String name : names) {
                zip.putNextEntry(new java.util.zip.ZipEntry(name));
                zip.write(1);
                zip.closeEntry();
            }
        }
        return WipiPackage.read(new ByteArrayInputStream(bytes.toByteArray()));
    }

    @Test public void recognizesCarrierPackageAndLeavesMidletAlone() throws IOException {
        assertEquals("KTF", readArchive("games/test/__adf__", "games/test/app.jar").carrier);
        assertEquals("LGT", readArchive("app_info", "app.jar").carrier);
        assertEquals("SKT", readArchive("app.msd", "app.jar").carrier);
        try {
            readArchive("META-INF/MANIFEST.MF", "DemoGame.class", "__adf__");
            fail("A MIDlet JAR with a descriptor-like resource must stay on Java ME");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("carrier ZIP"));
        }
    }

    @Test public void opensUnflaggedKoreanGameDirectory() throws IOException {
        assertEquals("KTF", readArchive(java.nio.charset.Charset.forName("EUC-KR"),
                "게임/__adf__", "게임/app.jar").carrier);
    }

    @Test public void importsOneLibraryCardAndPersistsEdits() throws IOException {
        File zip = File.createTempFile("wipi-library-", ".zip", context.getCacheDir());
        ru.playsoftware.j2meloader.catalog.ExternalLibrary.Item card = null;
        try {
            try (java.util.zip.ZipOutputStream out = new java.util.zip.ZipOutputStream(new FileOutputStream(zip))) {
                for (String entry : new String[]{"title.msd", "title.jar"}) {
                    out.putNextEntry(new java.util.zip.ZipEntry(entry));
                    out.write(zip.getName().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    out.closeEntry();
                }
            }
            WipiLibrary library = new WipiLibrary();
            library.install(context, Uri.fromFile(zip));
            library.install(context, Uri.fromFile(zip));
            String title = zip.getName().replace(".zip", "");
            int matches = 0;
            for (ru.playsoftware.j2meloader.applist.AppItem item : library.read(context)) {
                if (title.equals(item.getTitle())) {
                    card = (ru.playsoftware.j2meloader.catalog.ExternalLibrary.Item)item;
                    matches++;
                }
            }
            assertEquals(1, matches);
            assertEquals(5, ru.playsoftware.j2meloader.applist.LibraryQuery.platform(card));
            assertEquals("SKVM", card.getAuthor());
            card.rename(title+" renamed");
            card.favorite();
            boolean found = false;
            for (ru.playsoftware.j2meloader.applist.AppItem item : library.read(context)) {
                if (item.getPath().equals(card.getPath())) {
                    assertEquals(card.getId(), item.getId());
                    assertEquals(title+" renamed", item.getTitle());
                    assertTrue(item.isFavorite());
                    found = true;
                }
            }
            assertTrue(found);
        } finally {
            if (card != null) card.removePackage();
            zip.delete();
        }
    }

    private void clickAction(int labelId) {
        String label = context.getString(labelId);
        long until = SystemClock.uptimeMillis() + 4000;
        do {
            android.view.accessibility.AccessibilityNodeInfo root = instr.getUiAutomation().getRootInActiveWindow();
            if (root != null) for (android.view.accessibility.AccessibilityNodeInfo item : root.findAccessibilityNodeInfosByText(label)) {
                if (label.equals(String.valueOf(item.getText())) && item.performAction(
                        android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)) return;
            }
            SystemClock.sleep(120);
        } while (SystemClock.uptimeMillis() < until);
        fail("Missing game menu action: " + label);
    }

    @Test public void gameRespondsToRightAcrossRelaunch() {
        String fixture=InstrumentationRegistry.getArguments().getString("fixture");
        Assume.assumeNotNull(fixture);
        File zip=new File(context.getExternalFilesDir(null),fixture);
        assertTrue(zip.isFile());
        Intent launch=new Intent(context,WipiGameActivity.class).setData(Uri.fromFile(zip))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        for(int pass=0;pass<2;pass++){
            context.startActivity(launch);
            int initial=awaitBasket(0,false);
            long now=SystemClock.uptimeMillis();
            try {
                instr.getUiAutomation().injectInputEvent(new KeyEvent(now,now,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_RIGHT,0),true);
                int moved=awaitBasket(initial,true);
                android.util.Log.i("AbyssWipiTest",fixture+" basket "+initial+" -> "+moved);
            } finally {
                instr.getUiAutomation().injectInputEvent(new KeyEvent(now,SystemClock.uptimeMillis(),KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_RIGHT,0),true);
                instr.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
                clickAction(ru.playsoftware.j2meloader.R.string.game_exit);
                clickAction(ru.playsoftware.j2meloader.R.string.game_leave_without_saving);
                SystemClock.sleep(600);
            }
        }
    }
}
