package ru.playsoftware.j2meloader.input;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.RectF;
import com.google.gson.Gson;
import org.junit.Test;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.graphics.CanvasWrapper;
import javax.microedition.lcdui.graphics.ScreenRotation;
import ru.playsoftware.j2meloader.config.ProfileModel;
import static org.junit.Assert.*;

public class ScreenRotationTest {
    @Test public void clockwiseCornersMatchTouchAndTextureCoordinates() {
        int[][] expected = {{0,1,2,3},{2,0,3,1},{3,2,1,0},{1,3,0,2}};
        float[][] corners = {{0,0},{1,0},{0,1},{1,1}};
        for (int turn=0;turn<4;turn++) for (int i=0;i<4;i++) {
            float x=ScreenRotation.sourceX(corners[i][0],corners[i][1],turn*90);
            float y=ScreenRotation.sourceY(corners[i][0],corners[i][1],turn*90);
            assertEquals(expected[turn][i], Math.round(x)+2*Math.round(y));
            // Invert again with the opposite rotation, including non-corner touches.
            float sx=ScreenRotation.sourceX(.21f,.68f,turn*90);
            float sy=ScreenRotation.sourceY(.21f,.68f,turn*90);
            assertEquals(.21f,ScreenRotation.sourceX(sx,sy,-turn*90),.00001f);
            assertEquals(.68f,ScreenRotation.sourceY(sx,sy,-turn*90),.00001f);
        }
    }

    @Test public void softwareRenderingRotatesInsideItsOwnViewport() {
        int[] colors={Color.RED,Color.GREEN,Color.BLUE,Color.YELLOW};
        Image source=Image.createImage(8,12);
        for(int y=0;y<12;y++) for(int x=0;x<8;x++)
            source.getBitmap().setPixel(x,y,colors[(x>=4?1:0)+(y>=6?2:0)]);
        int[][] expected={{0,1,2,3},{2,0,3,1},{3,2,1,0},{1,3,0,2}};
        CanvasWrapper wrapper=new CanvasWrapper(false);
        for(int turn=0;turn<4;turn++) {
            Bitmap out=Bitmap.createBitmap(100,100,Bitmap.Config.ARGB_8888);
            out.eraseColor(Color.MAGENTA);
            android.graphics.Canvas canvas=new android.graphics.Canvas(out);
            wrapper.bind(canvas);
            RectF box=turn%2==0?new RectF(10,10,50,70):new RectF(10,10,70,50);
            wrapper.drawImage(source,box,turn*90);
            int[][] samples={{12,12},{(int)box.right-3,12},{12,(int)box.bottom-3},
                    {(int)box.right-3,(int)box.bottom-3}};
            for(int i=0;i<4;i++) assertEquals(colors[expected[turn][i]],out.getPixel(samples[i][0],samples[i][1]));
            assertEquals(Color.MAGENTA,out.getPixel(0,0));
            assertEquals(Color.MAGENTA,out.getPixel(90,90));
            // Drawing later UI is not rotated with the game image.
            android.graphics.Paint paint=new android.graphics.Paint(); paint.setColor(Color.WHITE);
            canvas.drawRect(80,80,90,90,paint);
            assertEquals(Color.WHITE,out.getPixel(85,85));
            out.recycle();
        }
    }

    @Test public void oldProfilesDefaultToZeroAndNewProfilesKeepRotation() {
        Gson gson=new Gson();
        assertEquals(0,gson.fromJson("{}",ProfileModel.class).screenRotation);
        ProfileModel profile=new ProfileModel(); profile.screenRotation=180;
        assertEquals(180,gson.fromJson(gson.toJson(profile),ProfileModel.class).screenRotation);
        assertEquals(270,ScreenRotation.normalize(-90));
        assertEquals(0,ScreenRotation.normalize(360));
        assertEquals(0,ScreenRotation.normalize(13));
        assertTrue(ScreenRotation.swapsAxes(270));
        assertFalse(ScreenRotation.swapsAxes(180));
    }
}
