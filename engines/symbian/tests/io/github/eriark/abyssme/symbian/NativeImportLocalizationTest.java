// SPDX-License-Identifier: GPL-3.0-only
package io.github.eriark.abyssme.symbian;

import android.content.Context;
import android.content.res.Configuration;
import android.os.LocaleList;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;
import org.junit.*;
import static org.junit.Assert.*;
import static io.github.eriark.abyssme.symbian.SisComponentsTest.*;

public class NativeImportLocalizationTest {
    private File root;
    private Context locale(String tag) {
        Context base=InstrumentationRegistry.getInstrumentation().getTargetContext();
        Configuration config=new Configuration(base.getResources().getConfiguration());
        config.setLocales(LocaleList.forLanguageTags(tag));return base.createConfigurationContext(config);
    }
    @Before public void setup() throws Exception {root=Files.createTempDirectory(locale("en").getCacheDir().toPath(),"import-locale-").toFile();}
    @After public void cleanup() throws Exception {PhoneBackupTest.remove(root);}
    private ArchiveBatch queue() throws Exception {
        File file=sis(root,"game.sisx",100,controller(100,1,new byte[0][]),true);
        ArchiveBatch store=new ArchiveBatch(new File(root,"queue"));
        store.beginFiles(Collections.singletonList(file),Collections.singletonList("Owner game 100%.sisx"),text->{});
        return store;
    }
    private File journal() {return new File(root,"queue/current/batch.json");}
    private JSONObject read() throws Exception {return new JSONObject(new String(Files.readAllBytes(journal().toPath()),StandardCharsets.UTF_8));}
    private void write(JSONObject json) throws Exception {Files.write(journal().toPath(),json.toString().getBytes(StandardCharsets.UTF_8));}
    @Test public void everyErrorRecordRoundTripsWithoutUsingDisplayTextAsIdentity() throws Exception {
        for(SystemFailure.Code code:SystemFailure.Code.values()) {
            SystemFailure original=new SystemFailure(code,SystemTransferLocalizationTest.arguments(code));
            SystemFailure restored=SystemFailure.recorded(new JSONObject(original.record().toString()));
            assertNotNull(code.name(),restored);assertEquals(code,restored.code);
            assertEquals(original.getMessage(),restored.getMessage());
            assertEquals(SystemFailureText.message(locale("en-XA"),original),SystemFailureText.message(locale("en-XA"),restored));
        }
        assertNull(SystemFailure.recorded(new JSONObject().put("code","FUTURE_ERROR").put("args",new JSONArray())));
        assertNull(SystemFailure.recorded(new JSONObject().put("code","SPACE_REQUIRED").put("args",new JSONArray().put("not a number"))));
        assertNull(SystemFailure.recorded(new JSONObject().put("code","SPACE_REQUIRED").put("args",new JSONArray())));
        assertNull(SystemFailure.recorded(new JSONObject().put("code","UNSAFE_PATH").put("args",new JSONArray().put(new JSONObject()))));
    }
    @Test public void queuedNoticesSurviveReloadSnapshotAndLocaleChange() throws Exception {
        ArchiveBatch store=queue();ArchiveBatch.Batch batch=store.read();
        assertTrue(batch.selectedFiles);
        String hash=batch.items.get(0).hash;
        for(ArchiveNotice notice:ArchiveNotice.values()) {
            ArchiveBatch.Item item=batch.items.get(0);item.detail(notice,"Owner component 100%");store.save(batch);
            batch=store.read();item=batch.items.get(0);
            assertEquals(notice,item.notice);assertEquals(notice.fallback("Owner component 100%"),item.detail);
            assertEquals(item.detail,SystemTransferText.detail(locale("en"),item));
            assertNotEquals(item.detail,SystemTransferText.detail(locale("en-XA"),item));
            for(String tag:new String[]{"ja","zh-CN"})
                assertNotEquals(tag+":"+notice,item.detail,SystemTransferText.detail(locale(tag),item));
            ArchiveBatch.Batch copy=ArchiveBatch.snapshot(batch);
            assertEquals(SystemTransferText.detail(locale("ar-XB"),item),SystemTransferText.detail(locale("ar-XB"),copy.items.get(0)));
            assertTrue(copy.selectedFiles);assertEquals(hash,item.hash);assertEquals("Owner game 100%.sisx",item.name);
        }
    }
    @Test public void rawOldAndUnknownRecordsRemainReadableWithoutGuessing() throws Exception {
        ArchiveBatch store=queue();ArchiveBatch.Batch batch=store.read();ArchiveBatch.Item item=batch.items.get(0);
        item.detail="Needs phone files";item.problem="Unsafe backup path";store.save(batch);
        JSONObject old=read();old.remove("selectedFiles");JSONObject row=old.getJSONArray("items").getJSONObject(0);
        for(String field:new String[]{"notice","noticeArgument","problemFailure","detailFailure"})row.remove(field);
        write(old);item=store.read().items.get(0);
        assertFalse(store.read().selectedFiles);assertEquals("Needs phone files",SystemTransferText.detail(locale("en-XA"),item));
        assertEquals("Unsafe backup path",SystemTransferText.problem(locale("en-XA"),item));
        row.put("notice","FUTURE_NOTICE").put("problemFailure",new JSONObject().put("code","FUTURE_ERROR").put("args",new JSONArray()));
        write(old);item=store.read().items.get(0);
        assertEquals("Needs phone files",SystemTransferText.detail(locale("ar-XB"),item));
        assertEquals("Unsafe backup path",SystemTransferText.problem(locale("ar-XB"),item));
    }
    @Test public void storedFailuresResetOnSuccessAndUnknownExceptionsStayRaw() throws Exception {
        ArchiveBatch store=queue();ArchiveBatch.Batch batch=store.read();ArchiveBatch.Item item=batch.items.get(0);
        item.problem(new SystemFailure(SystemFailure.Code.INPUT_GB_LIMIT,4L));
        item.failure(new SystemFailure(SystemFailure.Code.COMPONENT_CYCLE,"User component"));store.save(batch);
        item=store.read().items.get(0);assertEquals(SystemFailure.Code.INPUT_GB_LIMIT,item.problemFailure.code);
        assertTrue(SystemTransferText.detail(locale("en-XA"),item).contains("User component"));
        assertNotEquals(item.problem,SystemTransferText.problem(locale("en-XA"),item));
        item.detail(ArchiveNotice.INSTALLED,"");assertNull(item.detailFailure);
        item.failure(new IOException("Installed"));assertNull(item.notice);
        assertEquals("Installed",SystemTransferText.detail(locale("en-XA"),item));
        item.detail(null,"");assertEquals("",SystemTransferText.detail(locale("en-XA"),item));
    }
    @Test public void malformedImportRecordsAffectPresentationOnly() throws Exception {
        ArchiveBatch store=queue();JSONObject data=read();JSONObject row=data.getJSONArray("items").getJSONObject(0);
        String hash=row.getString("hash");
        row.put("detail","Exact diagnostic").put("detailFailure",new JSONObject().put("code","SPACE_REQUIRED").put("args",new JSONArray().put("bad")));
        row.put("notice","COMPONENT").put("noticeArgument",new String(new char[2049]).replace('\0','x'));
        write(data);ArchiveBatch.Item item=store.read().items.get(0);
        assertEquals("Exact diagnostic",SystemTransferText.detail(locale("en-XA"),item));
        assertEquals(hash,item.hash);assertTrue(item.selected);assertEquals(ArchiveBatch.State.READY,item.state);
        store.verify(item);
    }
    @Test public void inspectionStateIsRenderedInTheCurrentLocale() {
        SymbianImportDialog.Result ready=new SymbianImportDialog.Result(ImportKind.Kind.EXISTING,null,null,null);
        assertEquals("Ready",ready.message(locale("en")));assertNotEquals("Ready",ready.message(locale("en-XA")));
        SymbianImportDialog.Result progress=new SymbianImportDialog.Result(null,null,null,SystemProgress.READ_INPUT,123L);
        assertEquals("Reading selected file: 123 MB",progress.message(locale("en")));
        assertNotEquals(progress.message(locale("en")),progress.message(locale("en-XA")));
        SymbianImportDialog.Result failed=new SymbianImportDialog.Result(null,null,new SystemFailure(SystemFailure.Code.INPUT_GB_LIMIT,4L),null);
        assertNotEquals(failed.message(locale("en")),failed.message(locale("en-XA")));
        assertEquals("Raw 100%",new SymbianImportDialog.Result(null,null,new IOException("Raw 100%"),null).message(locale("ar-XB")));
    }
    @Test public void oversizedMetadataFallsBackAndOversizedJournalKeepsLastReadableQueue() throws Exception {
        ArchiveBatch store=queue();ArchiveBatch.Batch batch=store.read();ArchiveBatch.Item item=batch.items.get(0);
        item.failure(new SystemFailure(SystemFailure.Code.COMPONENT_CYCLE,new String(new char[2049]).replace('\0','x')));
        store.save(batch);item=store.read().items.get(0);
        assertNull(item.detailFailure);assertFalse(item.detail.isEmpty());assertTrue(item.detail.length()<=400);
        byte[] before=Files.readAllBytes(journal().toPath());
        batch.name=new String(new char[1024*1024]).replace('\0','x');
        try{store.save(batch);fail("Oversized queue must be rejected before replacing the journal");}
        catch(IOException expected){}
        assertArrayEquals(before,Files.readAllBytes(journal().toPath()));
        store.verify(store.read().items.get(0));
    }
    @Test public void collectionErrorsRetainSourceAndTranslateOnlyOwnedReason() {
        SystemFileCatalog.ImportResult result=new SystemFileCatalog.ImportResult();
        result.failure("Phone 100%",new SystemFailure(SystemFailure.Code.INPUT_GB_LIMIT,4L));
        result.failure("Owner archive",new IOException("Raw 100%"));
        assertEquals(result.summary(),SystemTransferText.imported(locale("en"),result));
        String expanded=SystemTransferText.imported(locale("en-XA"),result);
        assertTrue(expanded.contains("Phone 100%"));assertTrue(expanded.contains("Raw 100%"));
        assertFalse(expanded.contains("Input exceeds 4 GB"));
    }
    @Test public void progressAdapterAndBackendKeepCancellationAndLegacyFallback() throws Exception {
        String[] displayed={null};
        PreparedSystemArchive.Progress adapter=SystemTransferText.localize(locale("en-XA"),text->displayed[0]=text);
        adapter.update(SystemProgress.IMPORT_ITEM,1,2,"Owner 100%");assertTrue(displayed[0].contains("Owner 100%"));
        assertNotEquals(SystemProgress.IMPORT_ITEM.fallback(1,2,"Owner 100%"),displayed[0]);
        adapter.update("Raw 100%");assertEquals("Raw 100%",displayed[0]);
        adapter=SystemTransferText.localize(locale("en-XA"),text->{throw new InterruptedIOException("cancel");});
        try{adapter.update(SystemProgress.CONTINUE_QUEUE);fail();}catch(InterruptedIOException expected){assertEquals("cancel",expected.getMessage());}
        GameInstallFlow.Backend backend=new GameInstallFlow.Backend() {
            public void open(SystemFileCatalog.Entry entry){}public boolean hasPlatform(){return false;}
            public void install(File file,GameImport.Kind kind){}public void stage(File file,String hash){}
            public void status(String text){displayed[0]=text;}
        };
        backend.status(SystemProgress.INSTALL_COMPONENT,"Owner 100%");assertEquals("Installing component: Owner 100%",displayed[0]);
    }
}
