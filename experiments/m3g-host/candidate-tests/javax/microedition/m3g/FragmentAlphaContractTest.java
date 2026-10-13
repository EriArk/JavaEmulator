package javax.microedition.m3g;

import javax.microedition.shell.M3gSceneContractTest;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;

public class FragmentAlphaContractTest {
    @After public void restore() {
        Graphics3D.getInstance().releaseTarget(); GpuRasterizer.close(); GpuRasterizer.enabled=false; NativeRasterizer.enabled=true;
    }
    @Test public void replacePreservesZeroAlphaOnAllBackends() {
        for(int backend=0;backend<3;backend++) assertEquals(0x002080ef,render(backend,0x002080ef,1));
    }
    @Test public void replaceAppliesNodeAlphaOnAllBackends() {
        for(int backend=0;backend<3;backend++) assertEquals(0x7f2080ef,render(backend,0xff2080ef,.5f));
    }
    private int render(int backend,int color,float alpha) {
        Graphics3D.initGraphics3D(); Graphics3D g=Graphics3D.getInstance();
        GpuRasterizer.enabled=backend==2; NativeRasterizer.enabled=backend==1;
        Image2D target=new Image2D(Image2D.RGBA,32,32); g.bindTarget(target,true,0); g.setViewport(0,0,32,32);
        g.clear(null); g.setCamera(M3gSceneContractTest.camera(),null);
        Mesh m=M3gSceneContractTest.mesh(color); m.setAlphaFactor(alpha);
        long nativeBefore=NativeRasterizer.draws, gpuBefore=GpuRasterizer.draws;
        g.render(m,new Transform()); g.releaseTarget();
        if(backend==1) assertTrue(NativeRasterizer.draws>nativeBefore);
        if(backend==2) assertTrue(GpuRasterizer.draws>gpuBefore);
        int pixel=target.image[17*32+16]; GpuRasterizer.close(); return pixel;
    }
}
