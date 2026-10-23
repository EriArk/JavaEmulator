package ru.playsoftware.j2meloader.ui;

import org.junit.Test;
import static org.junit.Assert.*;

public class PictureScaleTest {
    @Test public void largestWholeMultipleFitsPortraitLandscapeAndSquareAreas() {
        assertEquals(3, PictureScale.integerFit(240,320,1080,1080),0);
        assertEquals(4, PictureScale.integerFit(128,160,601,701),0);
        assertEquals(3, PictureScale.integerFit(160,128,601,701),0);
        assertEquals(1, PictureScale.integerFit(480,800,600,900),0);
    }
    @Test public void smallAreasFallBackToFitWithoutCropping() {
        assertEquals(.5f, PictureScale.integerFit(240,320,120,200),0);
        assertEquals(.5f, PictureScale.integerFit(320,240,200,120),0);
        assertEquals(0, PictureScale.integerFit(240,320,0,200),0);
        assertEquals(0, PictureScale.integerFit(0,320,120,200),0);
        assertEquals(0, PictureScale.integerFit(240,320,Float.NaN,200),0);
    }
    @Test public void fractionalAvailablePixelsNeverRoundUpAnExtraMultiple() {
        assertEquals(2, PictureScale.integerFit(128,160,383.9f,640),0);
        assertEquals(3, PictureScale.integerFit(128,160,384,640),0);
    }
    @Test public void centeredEdgesAreIntegralForOddAndEvenSizes() {
        for(float center:new float[]{100,100.5f,101.25f}) for(int extent:new int[]{31,32,97,128}) {
            float pivot=PictureScale.center(center,extent,true);
            assertEquals(Math.floor(pivot-extent/2f),pivot-extent/2f,0);
            assertEquals(Math.floor(pivot+extent/2f),pivot+extent/2f,0);
            assertEquals(center,PictureScale.center(center,extent,false),0);
        }
    }
}
