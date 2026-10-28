// SPDX-License-Identifier: GPL-3.0-only
package ru.playsoftware.j2meloader.info;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.LocaleList;
import android.text.Spanned;
import android.text.style.URLSpan;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import org.junit.Test;
import ru.playsoftware.j2meloader.BuildConfig;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.applist.AppItem;
import ru.playsoftware.j2meloader.catalog.GameInformation;
import ru.playsoftware.j2meloader.config.Config;
import ru.playsoftware.j2meloader.settings.SettingsActivity;
import static org.junit.Assert.*;

public class InfoContentTest {
    private Context context(String tag,float font) {
        Context base=InstrumentationRegistry.getInstrumentation().getTargetContext();
        Configuration config=new Configuration();config.setLocales(LocaleList.forLanguageTags(tag));
        config.fontScale=font;config.uiMode=Configuration.UI_MODE_NIGHT_YES;
        ContextThemeWrapper wrapper=new ContextThemeWrapper(base,R.style.AppTheme);wrapper.applyOverrideConfiguration(config);
        return wrapper;
    }
    @Test public void supportLinksAndAttributionRemainIntactInEveryLocale() {
        for(String tag:new String[]{"en","en-XA","ar-XB","ru","ja","zh-CN"}) {
            Spanned about=(Spanned)InfoContent.about(context(tag,1));
            Set<String> links=new HashSet<>();for(URLSpan link:about.getSpans(0,about.length(),URLSpan.class))links.add(link.getURL());
            assertEquals(new HashSet<>(Arrays.asList("https://github.com/EriArk/JavaEmulator",
                    "https://github.com/EriArk/JavaEmulator/issues",
                    "https://github.com/EriArk/JavaEmulator/blob/main/docs/LOCALIZATION.md",
                    "https://github.com/nikita36078/J2ME-Loader")),links);
            assertTrue(about.toString().contains(BuildConfig.VERSION_NAME));
            if(tag.equals("en"))assertTrue(about.toString().contains("Copyright 2017-2024 Nikita Shakarun"));
            if(tag.equals("ja") || tag.equals("zh-CN")) {
                assertTrue(about.toString().contains("2017-2024 Nikita Shakarun"));
                assertTrue(about.toString().contains("GPL-3.0-only"));
            }
            assertFalse(about.toString().contains("j2me.loader@mail.ru"));
        }
    }
    @Test public void helpUsesCurrentProductResourcesAndKeepsBuildBoundary() {
        Context english=context("en",1),expanded=context("en-XA",1);
        String help=InfoContent.help(english);
        for(int key:new int[]{R.string.product_help_library,R.string.product_help_controls,R.string.product_help_picture,
                R.string.product_help_transfer,R.string.product_help_online})assertTrue(help.contains(english.getString(key)));
        assertEquals(BuildConfig.SYMBIAN_EXPERIMENT,help.contains(english.getString(R.string.product_help_systems)));
        assertNotEquals(help,InfoContent.help(expanded));assertNotEquals(InfoContent.about(english).toString(),InfoContent.about(expanded).toString());
        assertFalse(help.contains("Immediate processing mode"));
    }
    @Test public void informationKeepsRawMetadataAndDoesNotRewriteDescriptors() throws Exception {
        File root=Files.createTempDirectory(context("en",1).getCacheDir().toPath(),"info-locale-").toFile();
        File manifest=new File(root,Config.MIDLET_MANIFEST_FILE);
        try {
            AppItem item=new AppItem("unused","Owner title","Not provided","100% / 01.02") {
                @Override public String getPathExt(){return root.getPath();}
            };
            String english=GameInformation.describe(context("en",1),item);
            assertEquals(GameInformation.describe(item),english);
            String expanded=GameInformation.describe(context("en-XA",1),item);
            assertTrue(expanded.contains("Not provided"));assertTrue(expanded.contains("100% / 01.02"));assertNotEquals(english,expanded);
            byte[] data="MIDlet-Version: 01.02\r\nMIDlet-Vendor: Owner 100%\r\n".getBytes(StandardCharsets.UTF_8);
            Files.write(manifest.toPath(),data);
            assertTrue(GameInformation.describe(context("ar-XB",1),item).contains("Owner 100%"));
            assertArrayEquals(data,Files.readAllBytes(manifest.toPath()));
            Files.write(manifest.toPath(),"broken\r\n".getBytes(StandardCharsets.UTF_8));
            assertEquals(context("en-XA",1).getString(R.string.game_info_unreadable),GameInformation.describe(context("en-XA",1),item));
        } finally {Files.deleteIfExists(manifest.toPath());Files.delete(root.toPath());}
    }
    @Test public void longHelpAndAboutScrollAcrossCompactAndExpandedLayouts() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            for(String tag:new String[]{"en","en-XA","ar-XB","ja","zh-CN"})for(float font:new float[]{1,2})
                for(int[] size:new int[][]{{320,640},{540,540},{720,400}})for(boolean about:new boolean[]{false,true}) {
                    Context context=context(tag,font);float density=context.getResources().getDisplayMetrics().density;
                    ScrollView scroll=InfoContent.view(context,about?InfoContent.about(context):InfoContent.help(context));
                    scroll.setBackgroundColor(0xff161a1e);scroll.setLayoutDirection(tag.equals("ar-XB")?View.LAYOUT_DIRECTION_RTL:View.LAYOUT_DIRECTION_LTR);
                    int w=Math.round(size[0]*density),h=Math.round(size[1]*density);
                    scroll.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));
                    scroll.layout(0,0,w,h);
                    TextView text=(TextView)scroll.getChildAt(0);assertNotNull(text.getLayout());
                    assertTrue(text.getMeasuredWidth()<=w);
                    for(int line=0;line<text.getLayout().getLineCount();line++)assertEquals(0,text.getLayout().getEllipsisCount(line));
                    if(font==2&&!about){assertTrue(text.getHeight()>h);scroll.scrollTo(0,text.getHeight());assertTrue(scroll.getScrollY()>0);scroll.scrollTo(0,0);}
                    if(size[0]==540&&font==1&&tag.equals("en"))capture(scroll,(about?"about":"help")+"-dark-square.png");
                    if(size[0]==320&&font==1&&(tag.equals("ja")||tag.equals("zh-CN")))
                        capture(scroll,tag+"-"+(about?"about":"help")+"-dark-portrait.png");
                    if(size[0]==320&&font==2&&tag.equals("en-XA")&&!about)capture(scroll,"help-expanded-portrait.png");
                }
        });
    }
    @Test public void aboutMoreAndHelpRemainScrollableDialogsWithCloseActions() {
        try(ActivityScenario<SettingsActivity> scenario=ActivityScenario.launch(SettingsActivity.class)) {
            scenario.onActivity(activity->{
                AboutDialogFragment about=new AboutDialogFragment();about.showNow(activity.getSupportFragmentManager(),"info-test");
                AlertDialog dialog=(AlertDialog)about.requireDialog();assertTrue(dialog.isShowing());
                assertTrue(dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled());
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).performClick();
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity->{
                activity.getSupportFragmentManager().executePendingTransactions();
                InfoDialogFragment more=(InfoDialogFragment)activity.getSupportFragmentManager().findFragmentByTag("more");
                assertNotNull(more);((AlertDialog)more.requireDialog()).getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity->{
                activity.getSupportFragmentManager().executePendingTransactions();
                assertNull(activity.getSupportFragmentManager().findFragmentByTag("more"));
                HelpDialogFragment help=new HelpDialogFragment();help.showNow(activity.getSupportFragmentManager(),"help-test");
                AlertDialog helpDialog=(AlertDialog)help.requireDialog();assertTrue(helpDialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled());
                helpDialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity->{
                activity.getSupportFragmentManager().executePendingTransactions();
                assertNull(activity.getSupportFragmentManager().findFragmentByTag("help-test"));
            });
        }
    }
    private void capture(View view,String name) {
        File root=new File(view.getContext().getExternalFilesDir(null),"localization-info-fixtures");assertTrue(root.isDirectory()||root.mkdirs());
        Bitmap bitmap=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);view.draw(new Canvas(bitmap));
        try(FileOutputStream out=new FileOutputStream(new File(root,name))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}
        catch(IOException error){throw new AssertionError(error);}finally{bitmap.recycle();}
    }
}
