package ru.playsoftware.j2meloader.applist;

import org.junit.Test;
import static org.junit.Assert.*;

public class LibraryLayoutTest {
    @Test public void tinyHeadersKeepActionsAndSquareGalleryRevealsTwoRows() {
        assertFalse(LibraryLayout.inlineCategory(240,1));
        assertFalse(LibraryLayout.inlineCategory(360,1.5f));
        assertTrue(LibraryLayout.inlineCategory(360,1));
        LibraryLayout square=LibraryLayout.calculate(0,2,456,408,1);
        assertTrue((square.artHeight+74)*2<=408);
        assertEquals(2,square.format);
    }
    @Test public void squareAndNearSquareUseCompactChromeWithoutModelChecks() {
        assertTrue(LibraryLayout.compact(720,720,1));
        assertTrue(LibraryLayout.compact(620,540,1));
        assertTrue(LibraryLayout.compact(800,600,1));
        assertTrue(LibraryLayout.compact(480,302,1));
        assertFalse(LibraryLayout.compact(960,540,1));
        assertTrue(LibraryLayout.compact(960,540,1.3f));
    }
    @Test public void autoAdaptsButExplicitFormatsAlwaysWin() {
        assertEquals(LibraryLayout.PORTRAIT, LibraryLayout.calculate(0,0,456,408,1).format);
        assertEquals(LibraryLayout.WIDE, LibraryLayout.calculate(0,0,936,200,1).format);
        for (int[] size : new int[][]{{456,408},{936,200},{296,550},{696,620}}) {
            for (int format : new int[]{1,2}) {
                LibraryLayout value = LibraryLayout.calculate(0,format,size[0],size[1],1);
                assertEquals(format,value.format);
                assertTrue(value.artWidth + 22 <= size[0] / value.columns);
                assertTrue(value.artHeight + 74 <= size[1]);
                assertEquals(format == 2 ? 2f/3f : 16f/9f, (float)value.artWidth/value.artHeight,.02f);
            }
        }
    }
    @Test public void fontScaleReservesTitleSpaceAndDoesNotIncreaseColumns() {
        LibraryLayout normal = LibraryLayout.calculate(0,2,456,240,1);
        LibraryLayout large = LibraryLayout.calculate(0,2,456,240,1.5f);
        assertTrue(large.columns <= normal.columns);
        assertTrue(large.artHeight + 96 <= 240);
    }
    @Test public void galleryPreferenceDoesNotChangeListOrGridGeometry() {
        for (int mode : new int[]{1,2}) {
            LibraryLayout wide = LibraryLayout.calculate(mode,1,456,400,1);
            LibraryLayout tall = LibraryLayout.calculate(mode,2,456,400,1);
            assertEquals(wide.columns,tall.columns); assertEquals(wide.artHeight,tall.artHeight);
        }
        assertEquals(0, LibraryLayout.format(98));
    }
}
