/* SPDX-License-Identifier: Apache-2.0 */
package javax.microedition.lcdui;

import com.google.gson.Gson;
import java.io.File;
import org.junit.After;
import org.junit.Test;
import ru.playsoftware.j2meloader.config.ProfileModel;
import static org.junit.Assert.*;

public class FontProfileTest {
    private ProfileModel profile(int width,int height) {
        ProfileModel model=new ProfileModel(new File("font-test"));
        model.screenWidth=width; model.screenHeight=height;
        return model;
    }

    @After public void restore() { Font.applySettings(profile(240,320)); }

    private void sizes(ProfileModel model,int small,int medium,int large) {
        Font.applySettings(model);
        int[] types={Font.SIZE_SMALL,Font.SIZE_MEDIUM,Font.SIZE_LARGE};
        int[] heights={small,medium,large};
        for(int i=0;i<types.length;i++) {
            Font expected=new Font(Font.FACE_SYSTEM,Font.STYLE_PLAIN,types[i],heights[i]);
            Font actual=Font.getFont(Font.FACE_SYSTEM,Font.STYLE_PLAIN,types[i]);
            assertEquals(expected.getHeight(),actual.getHeight());
            assertEquals(expected.stringWidth("Pick up building pieces"),actual.stringWidth("Pick up building pieces"));
        }
    }

    @Test public void newProfilesFollowPreparedResolution() {
        ProfileModel model=profile(240,320);
        assertEquals(0,model.fontSizeSmall); assertEquals(0,model.fontSizeMedium); assertEquals(0,model.fontSizeLarge);
        sizes(model,18,22,26);
        model.screenWidth=128; model.screenHeight=160;
        sizes(model,13,15,20);
        model.screenWidth=128; model.screenHeight=128;
        sizes(model,9,13,15);
    }

    @Test public void orientationKeepsSameVirtualFontSize() {
        sizes(profile(128,160),13,15,20);
        sizes(profile(160,128),13,15,20);
        sizes(profile(176,220),15,18,22);
        sizes(profile(320,240),18,22,26);
    }

    @Test public void existingExplicitSizesSurviveLoadingAndResolutionChanges() {
        ProfileModel model=new Gson().fromJson("{\"FontSizeSmall\":11,\"FontSizeMedium\":17,\"FontSizeLarge\":23}",ProfileModel.class);
        model.screenWidth=128; model.screenHeight=160;
        sizes(model,11,17,23);
        model.screenWidth=640; model.screenHeight=480;
        sizes(model,11,17,23);
        assertEquals(11,model.fontSizeSmall); assertEquals(17,model.fontSizeMedium); assertEquals(23,model.fontSizeLarge);
    }

    @Test public void automaticSizesPersistAndCanMixWithExplicitOverrides() {
        ProfileModel model=profile(128,160); model.fontSizeMedium=12;
        model=new Gson().fromJson(new Gson().toJson(model),ProfileModel.class);
        sizes(model,13,12,20);
        assertEquals(0,model.fontSizeSmall); assertEquals(12,model.fontSizeMedium); assertEquals(0,model.fontSizeLarge);
    }
}
