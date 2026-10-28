// SPDX-License-Identifier: GPL-3.0-only
package io.github.eriark.abyssme.symbian;

import android.content.Context;
import android.content.res.Configuration;
import android.os.LocaleList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.File;
import java.io.FileOutputStream;
import org.json.*;
import org.junit.Test;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.ui.AppPalette;
import static org.junit.Assert.*;

public class SystemTransferLocalizationTest {
    private Context locale(String tag) {
        Context base=InstrumentationRegistry.getInstrumentation().getTargetContext();
        Configuration config=new Configuration(base.getResources().getConfiguration());
        config.setLocales(LocaleList.forLanguageTags(tag));
        return base.createConfigurationContext(config);
    }
    static Object[] arguments(SystemFailure.Code code) {
        if(code==SystemFailure.Code.COMPONENT_REQUIRED_RANGE)return new Object[]{"Component 100%","1.2.3","2.0.0"};
        if(code==SystemFailure.Code.COMPONENT_REQUIRED_MINIMUM)return new Object[]{"Component 100%","1.2.3"};
        if(code.english.contains("%1$d"))return new Object[]{128L};
        if(code.english.contains("%1$s"))return new Object[]{"runtime/100% game.dat"};
        return new Object[0];
    }
    @Test public void everyOwnedFailureHasLocalizedPresentationAndExactEnglishFallback() {
        for(SystemFailure.Code code:SystemFailure.Code.values()) {
            SystemFailure error=new SystemFailure(code,arguments(code));
            assertEquals(code.name(),error.getMessage(),SystemFailureText.message(locale("en"),error));
            for(String tag:new String[]{"es","pt-BR","ru","en-XA","ar-XB"}) {
                String translated=SystemFailureText.message(locale(tag),error);
                assertNotEquals(code.name(),error.getMessage(),translated);
                if(arguments(code).length>0 && arguments(code)[0] instanceof String)
                    assertTrue(translated.contains((String)arguments(code)[0]));
            }
        }
    }
    @Test public void unknownDiagnosticsAndFailureArgumentsRemainUnmodified() {
        IOException raw=new IOException("Unsafe backup path");
        assertEquals(raw.getMessage(),SystemFailureText.message(locale("en-XA"),raw));
        Object[] args={"runtime/User file 100%.dat"};
        SystemFailure failure=new SystemFailure(SystemFailure.Code.DAMAGED_FILE,raw,args);
        args[0]="changed";failure.arguments()[0]="also changed";
        assertSame(raw,failure.getCause());
        assertTrue(SystemFailureText.message(locale("en-XA"),failure).contains("runtime/User file 100%.dat"));
    }
    @Test public void backupVersionsKeepTheirCompleteWarningsAndUserNames() throws Exception {
        String hash=new String(new char[64]).replace('\0','a');
        for(int version=1;version<=3;version++) {
            JSONObject phone=new JSONObject().put("version",1).put("id",hash).put("firmware","RH-29")
                    .put("model","Owner phone 100%").put("os","epoc6").put("romSha256",hash).put("rpkgSha256","");
            JSONObject manifest=new JSONObject().put("version",version).put("format","AbyssME phone backup")
                    .put("phone",phone).put("bytes",3L*1024*1024).put("files",new JSONArray().put(new JSONObject()))
                    .put("bindings",new JSONArray());
            PhoneBackup.Preview preview=new PhoneBackup.Preview(manifest);
            assertEquals(preview.description(),SystemTransferText.backup(locale("en"),preview));
            String expanded=SystemTransferText.backup(locale("en-XA"),preview);
            assertNotEquals(preview.description(),expanded);
            assertTrue(expanded.contains(preview.phone));
            assertEquals(version,manifest.getInt("version"));
        }
    }
    @Test public void removalAndComponentRequirementsKeepIdentityAndSafetyMeaning() throws Exception {
        GameRemoval.Plan plan=new GameRemoval.Plan(123,new String[]{"123","0","My package 100%","1.2.3","2",
                "123","First game","124","","end"});
        assertEquals(plan.description("Owner phone"),SystemTransferText.removal(locale("en"),plan,"Owner phone"));
        String translated=SystemTransferText.removal(locale("en-XA"),plan,"Owner phone");
        for(String name:new String[]{"Owner phone","My package 100%","1.2.3","First game","124"})assertTrue(translated.contains(name));
        assertEquals(2,plan.affected.size());assertTrue(plan.affected.contains(123L));
        SisMetadata.Version min=new SisMetadata.Version(1,2,3),max=new SisMetadata.Version(2,0,0);
        for(SisMetadata.Version ceiling:new SisMetadata.Version[]{null,max}) {
            SisMetadata.Need need=new SisMetadata.Need(42,"Component 100%",min,ceiling);
            assertEquals(need.description(),SystemTransferText.component(locale("en"),need));
            assertTrue(SystemTransferText.component(locale("ar-XB"),need).contains(need.name));
            assertTrue(need.accepts(min));assertFalse(need.accepts(new SisMetadata.Version(1,2,2)));
        }
    }
    @Test public void typedProgressPreservesLegacyCallbacksAndCancellation() throws Exception {
        String[] seen={null};PreparedSystemArchive.Progress legacy=text->seen[0]=text;
        for(SystemProgress step:SystemProgress.values()) {
            Object[] args=step==SystemProgress.READ_ENTRY_SIZE?new Object[]{"File 100%",9L}:new Object[]{4L,9L,"File 100%"};legacy.update(step,args);
            assertEquals(step.fallback(args),seen[0]);
            assertEquals(seen[0],SystemTransferText.progress(locale("en"),step,args));
            assertNotEquals(seen[0],SystemTransferText.progress(locale("en-XA"),step,args));
        }
        PreparedSystemArchive.Progress cancelled=text->{throw new InterruptedIOException("cancel");};
        try {cancelled.update(SystemProgress.BACKUP,1,2);fail("Cancellation swallowed");}
        catch(InterruptedIOException expected) {assertEquals("cancel",expected.getMessage());}
    }
    @Test public void importSummaryAndQueueStatesDoNotTranslateRawDetails() {
        SystemFileCatalog.ImportResult result=new SystemFileCatalog.ImportResult();
        result.entries.add(new SystemFileCatalog.Entry("id","Phone","RH-29","epoc6"));
        for(int reused:new int[]{0,1}) {
            result.reused=reused;
            assertEquals(result.summary(),SystemTransferText.imported(locale("en"),result));
        }
        result.failures.add("owner/archive.zip: native diagnostic 100%");
        assertEquals(result.summary(),SystemTransferText.imported(locale("en"),result));
        assertTrue(SystemTransferText.imported(locale("ar-XB"),result).contains(result.failures.get(0)));
        for(ArchiveBatch.State state:ArchiveBatch.State.values())
            assertNotEquals(SystemTransferText.archiveState(locale("en"),state),SystemTransferText.archiveState(locale("en-XA"),state));
        assertEquals(2,locale("en-XA").getResources().getStringArray(R.array.native_queue_roles).length);
    }
    private ArchiveBatch.Batch batch() {
        ArchiveBatch.Batch batch=new ArchiveBatch.Batch();batch.id="fixture";batch.name="User collection 100%.zip";
        for(int i=0;i<3;i++) {
            ArchiveBatch.Item item=new ArchiveBatch.Item();item.id=i;item.name="Original game "+i+".sis";
            item.kind=GameImport.Kind.SIS_MODERN;item.canSave=true;batch.items.add(item);
        }
        return batch;
    }
    private static final class Host implements ArchiveBatchView.Host {
        int changed,starts,finishes,removed,closed;
        public void changed(){changed++;}public void row(int id){}public void start(){starts++;}
        public void others(){}public void close(){closed++;}public void remove(){removed++;}public void done(){finishes++;}
    }
    private View labelled(View view,String label) {
        if(view instanceof TextView && label.contentEquals(((TextView)view).getText()))return view;
        if(label.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++) {
            View found=labelled(((ViewGroup)view).getChildAt(i),label);if(found!=null)return found;
        }
        return null;
    }
    @Test public void queueCallbacksAndDisabledStateSurviveTranslation() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            Context context=locale("en-XA");ArchiveBatch.Batch batch=batch();Host host=new Host();
            ArchiveBatchView view=new ArchiveBatchView(context,batch,host);
            View start=labelled(view,context.getString(R.string.native_queue_import_count,3));assertNotNull(start);
            start.performClick();assertEquals(1,host.starts);
            view.working(true);assertFalse(start.isEnabled());
            CheckBox all=(CheckBox)labelled(view,context.getString(R.string.native_queue_select_all));
            all.setChecked(false);all.setChecked(true);assertFalse(start.isEnabled());
            view.working(false);assertTrue(start.isEnabled());
            labelled(view,context.getString(R.string.native_queue_select_all)).performClick();
            assertFalse(start.isEnabled());for(ArchiveBatch.Item item:batch.items)assertFalse(item.selected);
            labelled(view,context.getString(R.string.native_queue_remove)).performClick();assertEquals(1,host.removed);
            labelled(view,context.getString(R.string.system_back)).performClick();assertEquals(1,host.closed);
            batch.started=true;for(ArchiveBatch.Item item:batch.items)item.state=ArchiveBatch.State.DONE;
            ArchiveBatchView done=new ArchiveBatchView(context,batch,host);
            labelled(done,context.getString(R.string.native_queue_finish)).performClick();assertEquals(1,host.finishes);
            assertEquals("Original game 0.sis",batch.items.get(0).name);
        });
    }
    @Test public void queueLayoutFitsExpandedAndRtlLabelsAtPhoneAndHandheldSizes() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            for(String tag:new String[]{"en","es","pt-BR","ru","en-XA","ar-XB"})for(float font:new float[]{1f,1.5f,2f})
            for(int[] size:new int[][]{{320,640},{393,780},{540,540},{720,400}})
            for(int night:new int[]{Configuration.UI_MODE_NIGHT_NO,Configuration.UI_MODE_NIGHT_YES})
            for(boolean started:new boolean[]{false,true}) {
                Context base=locale(tag);Configuration config=new Configuration(base.getResources().getConfiguration());
                config.screenWidthDp=size[0];config.screenHeightDp=size[1];config.fontScale=font;
                Context context=AppPalette.preview(base.createConfigurationContext(config),"classic",night);
                ArchiveBatch.Batch batch=batch();batch.started=started;
                if(started) {batch.items.get(0).state=ArchiveBatch.State.WAITING;batch.items.get(1).state=ArchiveBatch.State.DONE;}
                ArchiveBatchView view=new ArchiveBatchView(context,batch,new Host());
                view.setLayoutDirection(config.getLayoutDirection());
                float density=context.getResources().getDisplayMetrics().density;
                int width=Math.round(size[0]*density),height=Math.round(size[1]*density);
                view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));
                view.layout(0,0,width,height);
                check(view,tag+" "+size[0]+"x"+size[1]+" font="+font+" started="+started);
                if(night==Configuration.UI_MODE_NIGHT_YES && size[0]==540 && font==1f && !started
                        || night==Configuration.UI_MODE_NIGHT_NO && size[0]==320 && font==2f && started && tag.equals("en-XA"))
                    capture(view,"queue-"+tag+"-"+size[0]+"-font"+font+"-"+started+".png");
            }
        });
    }
    private void capture(View view,String name) {
        File folder=new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir(null),"localization-transfer-fixtures");
        assertTrue(folder.isDirectory() || folder.mkdirs());
        Bitmap bitmap=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);
        settleDrawables(view);
        view.draw(new Canvas(bitmap));
        try(FileOutputStream stream=new FileOutputStream(new File(folder,name))) {assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,stream));}
        catch(IOException error) {throw new AssertionError(error);}
        finally {bitmap.recycle();}
    }
    private void settleDrawables(View view) {
        view.jumpDrawablesToCurrentState();
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)
            settleDrawables(((ViewGroup)view).getChildAt(i));
    }
    private void check(ViewGroup group,String label) {
        for(int i=0;i<group.getChildCount();i++) {
            View child=group.getChildAt(i);if(child.getVisibility()==View.GONE)continue;
            // RecyclerView may keep a partly visible row at the viewport edge.
            if(!(group instanceof RecyclerView)) {
                assertTrue(label+" left",child.getLeft()>=0);assertTrue(label+" right",child.getRight()<=group.getWidth());
                assertTrue(label+" bottom "+child.getClass().getSimpleName(),child.getBottom()<=group.getHeight());
            }
            if(child instanceof RecyclerView)assertTrue(label+" list usable",child.getHeight()>=Math.round(72*child.getResources().getDisplayMetrics().density));
            if(child instanceof TextView) {
                TextView text=(TextView)child;assertNotNull(label,text.getLayout());
                assertTrue(label+" text height: "+text.getText(),text.getLayout().getHeight()<=text.getHeight()-text.getCompoundPaddingTop()-text.getCompoundPaddingBottom());
            }
            if(child instanceof ViewGroup)check((ViewGroup)child,label);
        }
    }
}
