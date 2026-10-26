package ru.playsoftware.j2meloader.input;

import org.junit.Test;
import static org.junit.Assert.*;
import static ru.playsoftware.j2meloader.input.MenuPlacement.Position.*;

public class MenuPlacementTest {
    @Test public void confirmationsAlwaysCenter() {
        for (int[] size : new int[][]{{393,851},{540,620},{620,540},{960,540},{480,320}})
            for (boolean controller : new boolean[]{false,true})
                for (boolean preview : new boolean[]{false,true})
                    assertEquals(CENTER,MenuPlacement.resolve(size[0],size[1],controller,true,preview));
    }
    @Test public void squareAndShortMenusCenterWithEitherInputMode() {
        for (int[] size : new int[][]{{540,620},{620,540},{720,720},{480,320}})
            for (boolean controller : new boolean[]{false,true})
                assertEquals(CENTER,MenuPlacement.resolve(size[0],size[1],controller,false,false));
    }
    @Test public void phonePortraitKeepsBottomSheet() {
        assertEquals(BOTTOM,MenuPlacement.resolve(393,851,false,false,false));
        assertEquals(BOTTOM,MenuPlacement.resolve(393,851,false,false,true));
        assertEquals(CENTER,MenuPlacement.resolve(393,851,true,false,false));
    }
    @Test public void sidePanelRequiresWideShapeAndUsefulRemainingPicture() {
        assertEquals(SIDE,MenuPlacement.resolve(960,540,true,false,false));
        assertEquals(SIDE,MenuPlacement.resolve(851,393,false,false,true));
        assertEquals(CENTER,MenuPlacement.resolve(600,360,true,false,false));
        assertEquals(SIDE,MenuPlacement.resolve(600,360,true,false,true));
        assertEquals(CENTER,MenuPlacement.resolve(520,320,true,false,true));
        assertEquals(CENTER,MenuPlacement.resolve(800,700,true,false,true));
    }
}
