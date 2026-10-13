package javax.microedition.m3g;

import javax.microedition.shell.M3gSceneContractTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/** The same API scenes with GPU enabled, plus full-frame CPU/GPU comparisons. */
public class GpuSceneContractTest extends M3gSceneContractTest {
    @Before @Override public void setup() { super.setup(); GpuRasterizer.enabled=true; }
    @After @Override public void release() { super.release(); GpuRasterizer.close(); GpuRasterizer.enabled=false; }

    @Test public void combinedScenesMatchCpuAndActuallyDrawOnGpu() throws Exception {
        for(int scene=0;scene<8;scene++) {
            int[] cpu=frame(scene,false), gpu=frame(scene,true);
            save(scene,"cpu",cpu); save(scene,"gpu",gpu);
            for(int i=0;i<cpu.length;i++) for(int shift=0;shift<32;shift+=8) {
                assertTrue("scene="+scene+" pixel="+i+" cpu="+Integer.toHexString(cpu[i])+" gpu="+Integer.toHexString(gpu[i])+" channel="+shift,
                        Math.abs(((cpu[i]>>>shift)&255)-((gpu[i]>>>shift)&255))<=3);
            }
        }
    }
    private static void save(int scene,String backend,int[] pixels) throws Exception {
        java.io.File dir=new java.io.File(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getExternalFilesDir(null),"m3g-scene-contract");
        assertTrue(dir.isDirectory() || dir.mkdirs());
        android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(pixels,32,32,android.graphics.Bitmap.Config.ARGB_8888);
        try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(dir,scene+"-"+backend+".png"))) {
            assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out));
        }
    }
    private int[] frame(int scene,boolean gpu) {
        Graphics3D.initGraphics3D(); g=Graphics3D.getInstance(); GpuRasterizer.enabled=gpu;
        Image2D target=new Image2D(Image2D.RGBA,32,32); g.bindTarget(target,true,0); g.setViewport(0,0,32,32);
        World w=world(); Mesh m=litMesh(); Material material=m.getAppearance(0).getMaterial();
        material.setColor(Material.AMBIENT,0x204060); material.setColor(Material.DIFFUSE,0xffd09050);
        Light light=ambient();
        if(scene==1 || scene==2) { light.setMode(scene==1?Light.DIRECTIONAL:Light.SPOT); light.setSpotAngle(60); }
        if(scene==3) { Fog f=new Fog(); f.setMode(Fog.LINEAR); f.setLinear(1,4); f.setColor(0x90c0f0); m.getAppearance(0).setFog(f); }
        if(scene==4) { material.setColor(Material.DIFFUSE,0x80ffffff); CompositingMode c=new CompositingMode(); c.setBlending(CompositingMode.ALPHA); m.getAppearance(0).setCompositingMode(c); }
        if(scene==5) {
            VertexArray uv=new VertexArray(4,2,1); uv.set(0,4,new byte[]{0,0,0,1,1,0,1,1});
            for(int unit=0;unit<2;unit++) {
                m.getVertexBuffer().setTexCoords(unit,uv,1,null);
                Texture2D t=new Texture2D(new Image2D(Image2D.RGBA,1,1,new byte[]{-1,(byte)128,(byte)192,(byte)192}));
                t.setBlending(Texture2D.FUNC_MODULATE); m.getAppearance(0).setTexture(unit,t);
            }
        }
        if(scene==6) m.getVertexBuffer().getPositions(null).set(0,4,new short[]{-1,1,0,-1,-1,-2,1,1,-2,1,-1,-2});
        w.addChild(m); w.addChild(light);
        if(scene==7) for(int i=0;i<140;i++) { Mesh other=mesh(0xffabcdef); other.setTranslation(20+i,0,0); w.addChild(other); }
        long before=GpuRasterizer.draws; g.render(w); g.releaseTarget();
        if(gpu) assertTrue("GPU path must execute: "+scene,GpuRasterizer.draws>before);
        int[] pixels=target.image.clone(); GpuRasterizer.close(); return pixels;
    }
}
