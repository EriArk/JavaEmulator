/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.multiplayer;

import android.content.Context;
import android.content.res.Configuration;
import android.view.*;
import android.widget.*;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.gson.*;
import org.junit.Test;
import ru.playsoftware.j2meloader.ui.AppPalette;
import static org.junit.Assert.*;

public class LobbyPresentationTest {
    private LobbyRoomModel.State state(){
        LobbyRoomModel.State s=new LobbyRoomModel.State();s.account=s.host="11";s.hosting=s.joined=s.available=true;
        s.names.put("11","You");s.names.put("22","Alex with a longer display name");
        s.snapshot=JsonParser.parseString("{\"members\":[{\"id\":\"11\",\"connected\":true,\"prepared\":true,\"ready\":false},{\"id\":\"22\",\"connected\":true,\"prepared\":true,\"ready\":true}]}").getAsJsonObject();return s;
    }
    @Test public void seatStatesActionsAndRoomCapacityAreTruthful(){
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
            LinearLayout parent=new LinearLayout(c);parent.setOrientation(LinearLayout.VERTICAL);
            LobbyRoomModel.State s=state();String[] selected={""};int[] invites={0};
            LobbySeats.add(parent,s,id->selected[0]=id,()->invites[0]++);
            View guest=parent.findViewWithTag("member:22");assertTrue(guest.getContentDescription().toString().contains("Ready"));guest.performClick();assertEquals("22",selected[0]);
            parent.findViewWithTag("empty-seat").performClick();assertEquals(1,invites[0]);
            s.snapshot.add("game",new JsonObject());parent.removeAllViews();LobbySeats.add(parent,s,id->{},()->fail());assertNull(parent.findViewWithTag("empty-seat"));
            s.available=false;parent.removeAllViews();LobbySeats.add(parent,s,id->{},()->fail());
            assertTrue(parent.findViewWithTag("member:22").getContentDescription().toString().contains("Reconnecting"));
        });
    }
    @Test public void compactAndLargeTextSeatsFitWithoutClipping(){
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            Context base=InstrumentationRegistry.getInstrumentation().getTargetContext();
            for(String locale:new String[]{"en","en-XA","ar-XB"})
            for(int width:new int[]{320,393,480,540,720})for(float font:new float[]{1f,1.5f,2f})for(int night:new int[]{Configuration.UI_MODE_NIGHT_NO,Configuration.UI_MODE_NIGHT_YES}){
                Configuration config=new Configuration(base.getResources().getConfiguration());config.screenWidthDp=width;config.fontScale=font;
                config.setLocales(android.os.LocaleList.forLanguageTags(locale));
                Context c=AppPalette.preview(base.createConfigurationContext(config),"classic",night);
                LinearLayout parent=new LinearLayout(c);parent.setOrientation(LinearLayout.VERTICAL);
                LobbySeats.add(parent,state(),id->{},()->{});
                int px=Math.round(width*c.getResources().getDisplayMetrics().density);
                parent.measure(View.MeasureSpec.makeMeasureSpec(px,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));parent.layout(0,0,px,parent.getMeasuredHeight());
                check(parent);
                assertEquals(width>=340&&font<=1.25f?2:1,((ViewGroup)parent.getChildAt(0)).getChildCount());
            }
        });
    }
    private void check(ViewGroup group){
        for(int i=0;i<group.getChildCount();i++){
            View child=group.getChildAt(i);assertTrue(child.getLeft()>=0);assertTrue(child.getRight()<=group.getWidth());assertTrue(child.getBottom()<=group.getHeight());
            if(child instanceof TextView){TextView text=(TextView)child;assertNotNull(text.getLayout());assertTrue(text.getLayout().getHeight()<=text.getHeight()-text.getCompoundPaddingTop()-text.getCompoundPaddingBottom());}
            if(child instanceof ViewGroup)check((ViewGroup)child);
        }
    }
    @Test public void initialsHandleWhitespaceAndSupplementaryCharacters(){
        assertEquals("?",LobbySeats.initials("  "));assertEquals("AB",LobbySeats.initials("  Alex Brown "));
        assertEquals("A",LobbySeats.initials("Alex"));assertEquals(2,LobbySeats.initials("\uD83C\uDFAE").length());
    }
}
