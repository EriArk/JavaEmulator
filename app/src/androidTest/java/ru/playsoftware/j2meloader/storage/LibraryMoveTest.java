package ru.playsoftware.j2meloader.storage;
import android.content.*;
import androidx.test.platform.app.InstrumentationRegistry;
import io.github.eriark.abyssme.storage.*;
import org.json.*;
import org.junit.*;
import java.io.*;
import java.nio.file.*;
import static org.junit.Assert.*;
public class LibraryMoveTest {
 private File base,internal,primary,sd;private Context context;
 @Before public void setup()throws Exception{
  Context actual=InstrumentationRegistry.getInstrumentation().getTargetContext();base=new File(actual.getCacheDir(),"move-"+System.nanoTime());internal=new File(base,"private");primary=new File(base,"primary");sd=new File(base,"sd");internal.mkdirs();primary.mkdirs();sd.mkdirs();
  context=new ContextWrapper(actual){@Override public File getFilesDir(){return internal;}@Override public File getExternalFilesDir(String type){return primary;}};
  put(primary,"converted/java/res.jar","game");put(primary,"data/java/save","progress");put(primary,"configs/java/config.json","settings");put(primary,"physical-profiles/default.json","bindings");
  put(internal,"symbian-systems/phone1/runtime/data/drives/c/save","phone1");put(internal,"symbian-systems/phone2/source/phone.rom","firmware");put(internal,"symbian-profiles/phone1/config.json","profile");
 }
 private void put(File dir,String name,String text)throws Exception{File f=new File(dir,name);f.getParentFile().mkdirs();Files.write(f.toPath(),text.getBytes());}
 @After public void cleanup()throws Exception{VerifiedTree.discard(base);}
 @Test public void migratesBothRootsAndMovesBack()throws Exception{
  LibraryMove move=new LibraryMove(context);move.begin(move.plan(sd),s->{});
  assertTrue(StoragePaths.available(context));assertFalse(StoragePaths.moving(context));assertTrue(new File(StoragePaths.phones(context),"symbian-systems/phone2/source/phone.rom").isFile());
  assertFalse(new File(internal,"symbian-systems").exists());assertFalse(new File(primary,"data").exists());
  assertEquals("progress",new String(Files.readAllBytes(new File(StoragePaths.library(context),"data/java/save").toPath())));
  move.begin(move.plan(primary),s->{});assertTrue(StoragePaths.library(context).getPath().startsWith(primary.getPath()));assertTrue(new File(StoragePaths.phones(context),"symbian-profiles/phone1/config.json").exists());
 }
 @Test public void interruptionRecoversOldOrRetries()throws Exception{
  LibraryMove move=new LibraryMove(context);JSONObject plan=move.plan(sd);StoragePaths.write(StoragePaths.journal(context),plan);
  put(new File(plan.getString("root")),"library/partial","partial");
  move.cancelUncommitted();assertEquals(primary,StoragePaths.library(context));assertTrue(new File(primary,"data/java/save").exists());
  plan=move.plan(sd);StoragePaths.write(StoragePaths.journal(context),plan);move.run(plan,s->{});assertTrue(StoragePaths.available(context));
 }
 @Test public void missingCardDoesNotFallbackOrReset()throws Exception{
  LibraryMove move=new LibraryMove(context);move.begin(move.plan(sd),s->{});File selected=StoragePaths.library(context);File gone=new File(base,"removed");assertTrue(sd.renameTo(gone));
  assertFalse(StoragePaths.available(context));assertEquals(selected,StoragePaths.library(context));assertFalse(new File(primary,"data").exists());assertTrue(gone.renameTo(sd));assertTrue(StoragePaths.available(context));
 }
 @Test public void committedRecoveryDoesNotReCopyOrOverwriteNewData()throws Exception{
  LibraryMove move=new LibraryMove(context);JSONObject plan=move.plan(sd);move.begin(plan,s->{});StoragePaths.write(StoragePaths.journal(context),plan);
  put(StoragePaths.library(context),"data/new-save","new");move.run(plan,s->{});assertTrue(new File(StoragePaths.library(context),"data/new-save").exists());assertFalse(StoragePaths.moving(context));
 }
 @Test public void realRemovableVolumeRoundTrip()throws Exception{
  Context actual=InstrumentationRegistry.getInstrumentation().getTargetContext();File card=null;
  for(File f:actual.getExternalFilesDirs(null))if(f!=null&&android.os.Environment.isExternalStorageRemovable(f)&&android.os.Environment.MEDIA_MOUNTED.equals(android.os.Environment.getExternalStorageState(f)))card=f;
  org.junit.Assume.assumeNotNull(card);
  File fixture=new File(card,"storage-test-"+System.nanoTime());assertTrue(fixture.mkdirs());
  try{LibraryMove move=new LibraryMove(context);move.begin(move.plan(fixture),s->{});assertTrue(StoragePaths.library(context).getPath().startsWith(fixture.getPath()));assertTrue(new File(StoragePaths.phones(context),"symbian-systems/phone1/runtime/data/drives/c/save").isFile());move.begin(move.plan(primary),s->{});assertTrue(new File(StoragePaths.library(context),"data/java/save").isFile());}
  finally{VerifiedTree.discard(fixture);}
 }
 @Test public void verificationFailureKeepsOriginalSelectionAndSaves()throws Exception{
  LibraryMove move=new LibraryMove(context);JSONObject plan=move.plan(sd);
  try{move.begin(plan,status->{if(status.startsWith("Verifying")){
   try{Files.write(new File(plan.getString("root"),"library/data/java/save").toPath(),"corrupt copy".getBytes());}catch(Exception e){throw new RuntimeException(e);}
  }});fail();}catch(IOException expected){}
  assertEquals(primary,StoragePaths.library(context));assertEquals("progress",new String(Files.readAllBytes(new File(primary,"data/java/save").toPath())));assertTrue(StoragePaths.moving(context));move.cancelUncommitted();assertFalse(StoragePaths.moving(context));
 }
 @Test public void oldLaunchPathsFollowRepeatedMoves()throws Exception{
  String old=new File(primary,"converted/java").getPath();LibraryMove move=new LibraryMove(context);move.begin(move.plan(sd),s->{});String middle=new File(StoragePaths.library(context),"converted/java").getPath();assertEquals(middle,StoragePaths.resolve(context,old));assertEquals(middle,StoragePaths.resolve(context,middle));
  move.begin(move.plan(primary),s->{});String now=new File(StoragePaths.library(context),"converted/java").getPath();assertEquals(now,StoragePaths.resolve(context,old));assertEquals(now,StoragePaths.resolve(context,middle));assertEquals(now,StoragePaths.resolve(context,now));assertEquals("/unrelated/game.jar",StoragePaths.resolve(context,"/unrelated/game.jar"));
 }
 @Test public void competingMoveCannotReplaceJournal()throws Exception{
  try(java.io.RandomAccessFile f=new java.io.RandomAccessFile(new File(internal,"library-move.lock"),"rw");java.nio.channels.FileLock lock=f.getChannel().lock()){
   LibraryMove move=new LibraryMove(context);try{move.begin(move.plan(sd),s->{});fail();}catch(IOException expected){}assertFalse(StoragePaths.moving(context));assertTrue(new File(primary,"data/java/save").isFile());
  }
 }
 @Test public void invalidPointerIsNotAnEmptyLibrary()throws Exception{
  Files.write(StoragePaths.pointer(context).toPath(),"broken".getBytes());assertFalse(StoragePaths.available(context));
  try{StoragePaths.library(context);fail();}catch(IllegalStateException expected){}
 }
}
