// SPDX-License-Identifier: GPL-3.0-only
package ru.playsoftware.j2meloader.multiplayer;

import android.content.Context;
import android.content.res.Configuration;
import android.os.LocaleList;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;
import ru.playsoftware.j2meloader.R;
import static org.junit.Assert.*;

/** Resource/state checks are offline and never touch the installed account vault. */
public class SocialLocalizationTest {
    private Context locale(String tag) {
        Context base=InstrumentationRegistry.getInstrumentation().getTargetContext();
        Configuration config=new Configuration(base.getResources().getConfiguration());
        config.setLocales(LocaleList.forLanguageTags(tag));
        return base.createConfigurationContext(config);
    }
    @Test public void everyAuthNoticeHasEnglishAndExpandedResource() {
        Context english=locale("en"), expanded=locale("en-XA");
        for(FluxerAuth.Notice key:FluxerAuth.Notice.values()) {
            assertEquals(key.name(),key.english,SocialText.auth(english,key));
            assertNotEquals(key.name(),key.english,SocialText.auth(expanded,key));
        }
    }
    @Test public void allChatAndDirectoryNoticesAreResourceBacked() {
        Context english=locale("en"), expanded=locale("en-XA");
        for(SocialNotice key:SocialNotice.values()) {
            assertEquals(key.name(),key.english,SocialText.notice(english,key));
            if(key!=SocialNotice.NONE&&key!=SocialNotice.EXTERNAL)
                assertNotEquals(key.name(),key.english,SocialText.notice(expanded,key));
        }
    }
    @Test public void userTextThatLooksLikeAnAppPlaceholderIsNeverTranslated() {
        Context expanded=locale("en-XA");
        for(String text:new String[]{"Game session message","[Attachment]","[Sticker]","[Conversation event]","100% / RH-29"}) {
            FluxerChats.Message message=new FluxerChats.Message("1","You",text,"",SocialNotice.NONE,false,false,FluxerChats.Message.ContentKind.TEXT);
            assertEquals(text,SocialText.content(expanded,message));
        }
        FluxerChats.Message placeholder=new FluxerChats.Message("1","You","[Attachment]","",SocialNotice.NONE,false,false,FluxerChats.Message.ContentKind.ATTACHMENT);
        assertNotEquals("[Attachment]",SocialText.content(expanded,placeholder));
    }
    @Test public void userConversationNamesAreNotConfusedWithFallbackTitles() {
        Context expanded=locale("en-XA");
        JsonObject custom=JsonParser.parseString("{\"id\":\"12\",\"type\":3,\"name\":\"Group conversation\"}").getAsJsonObject();
        assertEquals("Group conversation",SocialText.channel(expanded,new FluxerChats.Channel(custom,"1")));
        custom.remove("name");
        assertNotEquals("Group conversation",SocialText.channel(expanded,new FluxerChats.Channel(custom,"1")));
    }
    @Test public void statusTranslationDoesNotMutateActorsOrStartNetwork() {
        FluxerChats chats=new FluxerChats((method,path,body,token,done)->fail("Unexpected request"),()->0,s->{});
        FluxerChats.State state=chats.state();
        assertEquals(SocialNotice.CHATS_SIGNIN,state.statusCode);
        String english=state.status;
        assertNotEquals(english,SocialText.notice(locale("en-XA"),state.statusCode));
        assertEquals(english,chats.state().status);
        assertFalse(state.quiet(false));
        assertTrue(locale("en-XA").getString(R.string.social_host_value,"@name#0042").contains("@name#0042"));
        chats.close();
    }
    @Test public void displayArgumentsAndProtocolIdentifiersRemainIntact() {
        Context expanded=locale("en-XA");
        assertTrue(expanded.getString(R.string.social_host_account,"1234567890123456789").contains("1234567890123456789"));
        assertTrue(expanded.getString(R.string.social_no_browser,FluxerAuth.LOGIN_URL).contains(FluxerAuth.LOGIN_URL));
        assertTrue(expanded.getString(R.string.social_invitation_from,"Name 50% / [Attachment]").contains("Name 50% / [Attachment]"));
        assertEquals("Expires in 2:05",locale("en").getString(R.string.social_expires,2L,5L));
    }
}
