package wolphun.vm;

import java.util.Arrays;
import org.junit.Test;
import wolphun.Screen;
import static org.junit.Assert.*;

public class MophunSceneTest {
    private Sdk sdk() {
        byte[] executable=new byte[40];
        executable[0]='V'; executable[1]='M'; executable[2]='G'; executable[3]='P';
        return new Sdk(new Pip2(new Vmgp(executable),4096,1024),new Screen(128,160,1));
    }

    private void call(Sdk sdk,String name,int... args) {
        for(int i=0;i<args.length;i++) sdk.vm.setReg(Pip2.P0+4*i,args[i]);
        sdk.invoke(name);
    }

    @Test public void disposingSpritesClearsCollisionsAndDrawingAndAllowsReinitialization() {
        Sdk sdk=sdk();
        int sprite=sdk.vm.heapBase;
        sdk.vm.write16(sprite+6,4); sdk.vm.write16(sprite+8,4);
        call(sdk,"vSpriteInit",2);
        call(sdk,"vSpriteSet",0,sprite,10,10);
        call(sdk,"vSpriteSet",1,sprite,11,11);
        assertEquals(1,sdk.spriteCollision(0,1,1));
        call(sdk,"vSpriteDispose");
        assertEquals(0,sdk.spriteCount);
        assertNull(sdk.slotSprite); assertNull(sdk.slotX); assertNull(sdk.slotY);
        assertEquals(-1,sdk.spriteCollision(0,1,1));
        Arrays.fill(sdk.scr.fb,0xff123456);
        call(sdk,"vUpdateSprite");
        for(int pixel:sdk.scr.fb) assertEquals(0xff123456,pixel);
        call(sdk,"vSpriteDispose");
        call(sdk,"vSpriteInit",3);
        assertEquals(3,sdk.slotSprite.length);
        assertEquals(0,sdk.slotSprite[0]);
    }

    private void mapWrites(int flags) {
        Sdk sdk=sdk();
        int header=sdk.vm.heapBase, data=header+24, stride=flags==0?1:2;
        sdk.vm.write8(header,flags);
        sdk.vm.write8(header+2,3); sdk.vm.write8(header+3,2);
        sdk.vm.write32(header+16,data);
        for(int i=0;i<6*stride+1;i++) sdk.vm.write8(data+i,0x45);
        call(sdk,"vMapInit",header);
        call(sdk,"vMapSetTile",1,1,0x123);
        assertEquals(0x23,sdk.mapTile(1,1));
        if(flags!=0) assertEquals(0x45,sdk.mapAttr(1,1));
        assertEquals(0x45,sdk.mapTile(0,1));
        assertEquals(0x45,sdk.mapTile(2,1));
        call(sdk,"vMapSetTile",3,1,9);
        call(sdk,"vMapSetTile",-1,0,9);
        call(sdk,"vMapSetTile",0,2,9);
        assertEquals(0x45,sdk.vm.read8(data+6*stride));
        call(sdk,"vMapDispose");
        call(sdk,"vMapSetTile",1,1,9);
        assertEquals(0x23,sdk.vm.read8(data+4*stride));
        assertFalse(sdk.loggedMissing.contains("vMapSetTile"));
    }

    @Test public void tileWritesRespectAttributesAndMapBounds() { mapWrites(11); }
    @Test public void tileWritesSupportMapsWithoutAttributes() { mapWrites(0); }
}
