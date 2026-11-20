package ru.playsoftware.j2meloader.storage;
import io.github.eriark.abyssme.storage.VerifiedTree;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.nio.file.*;
import static org.junit.Assert.*;
public class VerifiedTreeTest {
 @Rule public TemporaryFolder tmp=new TemporaryFolder();
 private File file(File root,String name,String value)throws Exception{File f=new File(root,name);f.getParentFile().mkdirs();Files.write(f.toPath(),value.getBytes());return f;}
 @Test public void preservesNestedPhoneSavesAndEmptyFolders()throws Exception{
  File from=tmp.newFolder(),to=new File(tmp.getRoot(),"to");file(from,"phones/a/drives/c/save","save");file(from,"phones/b/rom","rom");new File(from,"empty").mkdir();
  VerifiedTree.copy(from,to,n->{});VerifiedTree.verify(from,to);VerifiedTree.deleteVerified(from,to);
  assertFalse(from.exists());assertTrue(new File(to,"empty").isDirectory());assertEquals("save",new String(Files.readAllBytes(new File(to,"phones/a/drives/c/save").toPath())));
 }
 @Test public void corruptedCopyNeverDeletesSource()throws Exception{
  File from=tmp.newFolder(),to=new File(tmp.getRoot(),"to");File save=file(from,"save","original");VerifiedTree.copy(from,to,n->{});file(to,"save","damaged!");
  try{VerifiedTree.deleteVerified(from,to);fail();}catch(IOException expected){}assertTrue(save.isFile());
 }
 @Test public void interruptedCopyRetainsSource()throws Exception{
  File from=tmp.newFolder(),to=new File(tmp.getRoot(),"to");File save=file(from,"save","keep");
  try{VerifiedTree.copy(from,to,n->{throw new InterruptedIOException("cancel");});fail();}catch(InterruptedIOException expected){}
  assertEquals("keep",new String(Files.readAllBytes(save.toPath())));
 }
 @Test public void missingDestinationNeverDeletesSource()throws Exception{
  File from=tmp.newFolder();File save=file(from,"save","keep");try{VerifiedTree.deleteVerified(from,new File(tmp.getRoot(),"gone"));fail();}catch(IOException expected){}assertTrue(save.exists());
 }
 @Test public void addedFilesFailVerification()throws Exception{
  File from=tmp.newFolder(),to=new File(tmp.getRoot(),"to");file(from,"a","a");VerifiedTree.copy(from,to,n->{});file(from,"b","b");try{VerifiedTree.verify(from,to);fail();}catch(IOException expected){}
 }
 @Test public void symbolicLinksDoNotEscapeMigration()throws Exception{
  File from=tmp.newFolder(),outside=tmp.newFile();Files.createSymbolicLink(new File(from,"link").toPath(),outside.toPath());try{VerifiedTree.size(from);fail();}catch(IOException expected){}assertTrue(outside.exists());
 }
}
