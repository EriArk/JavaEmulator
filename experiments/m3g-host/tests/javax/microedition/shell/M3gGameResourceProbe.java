package javax.microedition.shell;

import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.zip.*;
import javax.microedition.m3g.Loader;
import javax.microedition.m3g.Object3D;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Opt-in private corpus probe. No game assets are bundled or copied to reports. */
public class M3gGameResourceProbe {
    @Test public void loadStandaloneM3gResourcesFromSuppliedArchives() throws Exception {
        android.content.Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String path=InstrumentationRegistry.getArguments().getString("m3gCorpus");
        assertNotNull("Pass -e m3gCorpus <app-accessible directory>",path);
        File[] jars=new File(path).listFiles((dir,name)->name.toLowerCase(Locale.ROOT).endsWith(".jar"));
        assertNotNull(jars); assertTrue(jars.length>0); Arrays.sort(jars);
        JSONArray games=new JSONArray(); int errors=0,resources=0;
        for(int i=0;i<jars.length;i++) {
            File root=new File(context.getCacheDir(),"m3g-corpus/converted/game-"+i);
            assertTrue(root.isDirectory() || root.mkdirs());
            File copy=new File(root,"res.jar"); Files.copy(jars[i].toPath(),copy.toPath(),StandardCopyOption.REPLACE_EXISTING);
            new AppClassLoader("",context.getCodeCacheDir().getPath(),getClass().getClassLoader(),root);
            JSONArray results=new JSONArray();
            try(ZipFile zip=new ZipFile(copy)) {
                Enumeration<? extends ZipEntry> entries=zip.entries();
                while(entries.hasMoreElements()) {
                    ZipEntry entry=entries.nextElement(); if(entry.isDirectory() || entry.getSize()<12 || entry.getName().endsWith(".class")) continue;
                    byte[] header=new byte[12]; try(DataInputStream input=new DataInputStream(zip.getInputStream(entry))) { input.readFully(header); }
                    if(!Arrays.equals(header,new byte[]{(byte)0xab,74,83,82,49,56,52,(byte)0xbb,13,10,26,10})) continue;
                    JSONObject result=new JSONObject().put("resource",entry.getName()); resources++;
                    try {
                        Object3D[] roots=Loader.load("/"+entry.getName());
                        JSONArray types=new JSONArray(); for(Object3D object:roots) types.put(object.getClass().getSimpleName());
                        result.put("roots",types).put("loaded",true);
                    } catch(Exception failure) { errors++; result.put("loaded",false).put("error",failure.toString()); }
                    results.put(result);
                }
            }
            games.put(new JSONObject().put("archive",jars[i].getName()).put("resources",results));
        }
        JSONObject report=new JSONObject().put("resources",resources).put("failures",errors).put("games",games);
        try(FileOutputStream out=new FileOutputStream(new File(context.getExternalFilesDir(null),"m3g-corpus-results.json"))) {
            out.write(report.toString(2).getBytes("UTF-8"));
        }
        assertTrue("No standalone M3G resources found",resources>0); assertEquals("Failed resources; see report",0,errors);
    }
}
