/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.symbian;
import android.content.*;
import androidx.test.platform.app.InstrumentationRegistry;
import com.github.eka2l1.emu.Emulator;
import io.github.eriark.abyssme.storage.*;
import ru.playsoftware.j2meloader.storage.LibraryMove;
import ru.playsoftware.j2meloader.appsdb.AppDatabase;
import ru.playsoftware.j2meloader.applist.AppItem;
import org.junit.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.Assert.*;

/** Opt-in private phone/card fixtures; no commercial payloads in the test APK. */
public class StoragePhoneTest {
 @Test public void nativePhoneDatabaseAndBackupSurviveSdMove()throws Exception{
  Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("storagePhone")));
  Context actual=InstrumentationRegistry.getInstrumentation().getTargetContext();assertTrue(actual.getPackageName().endsWith(".debug"));
  File inputs=new File(actual.getExternalFilesDir(null),"storage-input");
  File card=null;for(File f:actual.getExternalFilesDirs(null))if(f!=null&&android.os.Environment.isExternalStorageRemovable(f))card=f;
  assertNotNull("A mounted SD card is required",card);
  File base=new File(actual.getCacheDir(),"phone-storage-"+System.nanoTime()),internal=new File(base,"private"),primary=new File(base,"library"),sd=new File(card,"phone-storage-test-"+System.nanoTime());
  assertTrue(internal.mkdirs());assertTrue(primary.mkdirs());assertTrue(sd.mkdirs());
  Context context=new ContextWrapper(actual){@Override public File getFilesDir(){return internal;}@Override public File getExternalFilesDir(String type){return primary;}};
  try{
   SystemFileCatalog catalog=new SystemFileCatalog(context);
   SystemFileCatalog.ImportResult imported=catalog.addArchive(context,new File(inputs,"ngage.zip"),s->{});
   assertNotNull(imported);assertTrue(imported.summary(),imported.failures.isEmpty());assertEquals(1,imported.entries.size());
   SystemFileCatalog.Entry phone=imported.entries.get(0);
   Emulator.prepare(catalog.runtime(phone).getPath());
   try(GameImport game=GameImport.inspect(new File(inputs,"snakes.zip"))){assertEquals(0,Emulator.installNGageGame(game.file.getPath()));catalog.bind(game.hash,phone);}
   Emulator.prepare(catalog.runtime(phone).getPath());assertTrue(Arrays.asList(Emulator.getCatalogApps()).contains("Snakes"));
   new SymbianLibraryIndex(context).publish(phone,Emulator.getLibraryApps(),false);catalog.activate(phone);Emulator.stopSession();
   File marker=new File(catalog.runtime(phone),"data/drives/c/storage-progress.dat");Files.createDirectories(marker.getParentFile().toPath());Files.write(marker.toPath(),"retained progress".getBytes());
   // A real SQLite catalog, including favorites, is moved with its application files.
   try(AppDatabase db=AppDatabase.open(context,primary.getPath())){AppItem item=new AppItem("storage-fixture","Storage fixture","Maintainer","1");item.setFavorite(true);db.appItemDao().insert(item);}
   LibraryMove move=new LibraryMove(context);move.begin(move.plan(sd),s->{});
   assertTrue(StoragePaths.phones(context).getPath().startsWith(sd.getPath()));
   catalog=new SystemFileCatalog(context);phone=catalog.read(phone.id);
   assertEquals("retained progress",new String(Files.readAllBytes(new File(catalog.runtime(phone),"data/drives/c/storage-progress.dat").toPath())));
   Emulator.prepare(catalog.runtime(phone).getPath());assertTrue(Arrays.asList(Emulator.getCatalogApps()).contains("Snakes"));Emulator.stopSession();
   try(AppDatabase db=AppDatabase.open(context,StoragePaths.library(context).getPath())){assertTrue(db.appItemDao().getByPath("storage-fixture").isFavorite());}
   File backup=new File(base,"phone.zip");PhoneBackup transfer=new PhoneBackup(StoragePaths.phones(context));
   transfer.exportPhone(phone,backup,Emulator.portablePhoneSettings(catalog.runtime(phone).getPath()),s->{});
   SystemFileCatalog.Entry restored=transfer.restore(backup,(runtime,meta)->{SymbianFiles.prepareAssets(context,runtime);Emulator.prepare(runtime.getPath());assertTrue(Arrays.asList(Emulator.getCatalogApps()).contains("Snakes"));Emulator.stopSession();},s->{});
   assertNotEquals(phone.id,restored.id);assertEquals(2,new SystemFileCatalog(context).list().size());
   // New native phone installation on selected storage, followed by a two-phone return.
   move.begin(move.plan(primary),s->{});catalog=new SystemFileCatalog(context);assertEquals(2,catalog.list().size());
   for(SystemFileCatalog.Entry entry:catalog.list()){Emulator.prepare(catalog.runtime(entry).getPath());assertTrue(Arrays.asList(Emulator.getCatalogApps()).contains("Snakes"));Emulator.stopSession();}
  }finally{Emulator.stopSession();VerifiedTree.discard(base);VerifiedTree.discard(sd);}
 }
}
