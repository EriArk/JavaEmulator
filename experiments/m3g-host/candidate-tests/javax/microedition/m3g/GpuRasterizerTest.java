package javax.microedition.m3g;

import android.opengl.*;
import java.io.*;
import javax.microedition.lcdui.Image;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;
import static android.opengl.EGL14.*;

public class GpuRasterizerTest {
    @After public void restore() {
        Graphics3D.getInstance().releaseTarget(); GpuRasterizer.close(); GpuRasterizer.enabled=false;
    }

    @Test public void solidColorsAndDepthSurviveCpuGpuSwitches() {
        for (boolean firstGpu : new boolean[]{false,true}) for (int width : new int[]{32,47}) {
            Graphics3D.initGraphics3D(); Graphics3D g=Graphics3D.getInstance();
            Image target=bind(g,width,35); Background bg=new Background(); bg.setColor(0xff123456); g.clear(bg);
            long before=GpuRasterizer.draws;
            GpuRasterizer.enabled=firstGpu; g.render(quad(0xff00ff00,null),position(-3));
            GpuRasterizer.enabled=!firstGpu; g.render(quad(0xffff0000,null),position(-2));
            GpuRasterizer.enabled=firstGpu; g.render(quad(0xff0000ff,null),position(-4));
            g.releaseTarget();
            assertTrue(GpuRasterizer.failure,GpuRasterizer.draws>before);
            assertEquals(0xffff0000,target.getBitmap().getPixel(width/2,17));
            assertEquals(0xff123456,target.getBitmap().getPixel(0,0));
        }
    }

    @Test public void clippingMasksDisabledDepthAndTargetResize() {
        for (int[] size : new int[][]{{32,32},{47,35},{32,32}}) {
            Graphics3D g=Graphics3D.getInstance(); Image target=bind(g,size[0],size[1]);
            Background bg=new Background(); bg.setColor(0xff123456); g.clear(bg);
            g.setViewport(4,3,size[0]-8,size[1]-6);
            Mesh mesh=quad(0x8060a0e0,null); CompositingMode mode=new CompositingMode();
            mode.setColorWriteEnable(false); mode.setDepthTestEnable(false); mesh.getAppearance(0).setCompositingMode(mode);
            GpuRasterizer.enabled=true; long before=GpuRasterizer.draws;
            g.render(mesh,position(-2)); g.releaseTarget();
            assertEquals(GpuRasterizer.failure,before+1,GpuRasterizer.draws);
            assertEquals(0x80123456,target.getBitmap().getPixel(size[0]/2,size[1]/2));
            assertEquals(0xff123456,target.getBitmap().getPixel(0,0));
        }
    }

    @Test public void textureOrientationMutationAndAlphaRejection() {
        Graphics3D.initGraphics3D(); Graphics3D g=Graphics3D.getInstance();
        Image2D image=new Image2D(Image2D.RGBA,2,2);
        image.set(0,0,2,2,new byte[]{-1,0,0,-1, 0,-1,0,-1, 0,0,-1,-1, -1,-1,-1,0});
        Texture2D tex=new Texture2D(image); tex.setBlending(Texture2D.FUNC_REPLACE);
        tex.setWrapping(Texture2D.WRAP_CLAMP,Texture2D.WRAP_CLAMP);
        for (int iteration=0; iteration<2; iteration++) {
            Image target=bind(g,32,32); Background bg=new Background(); bg.setColor(0xff123456); g.clear(bg);
            Mesh mesh=quad(0xffffffff,tex); CompositingMode mode=new CompositingMode(); mode.setAlphaThreshold(.5f);
            mesh.getAppearance(0).setCompositingMode(mode); GpuRasterizer.enabled=true;
            long before=GpuRasterizer.draws; g.render(mesh,position(-2));
            GpuRasterizer.enabled=false; g.render(quad(0xffe0c020,null),position(-3)); g.releaseTarget();
            assertEquals(GpuRasterizer.failure,before+1,GpuRasterizer.draws);
            assertEquals(iteration==0?0xffff0000:0xff00ffff,target.getBitmap().getPixel(10,10));
            assertEquals(0xff00ff00,target.getBitmap().getPixel(22,10));
            assertEquals(0xff0000ff,target.getBitmap().getPixel(10,22));
            assertEquals("Rejected alpha must not write depth",0xffe0c020,target.getBitmap().getPixel(22,22));
            image.set(0,0,1,1,new byte[]{0,-1,-1,-1});
        }
    }

    @Test public void unsupportedStateFallsBackWithoutDroppingGeometry() {
        Graphics3D.initGraphics3D(); Graphics3D g=Graphics3D.getInstance(); Image target=bind(g,32,32);
        Background bg=new Background(); bg.setColor(0xff000000); g.clear(bg);
        Mesh mesh=quad(0xffff0000,null); Fog fog=new Fog(); fog.setMode(Fog.LINEAR);
        fog.setColor(0x0000ff); fog.setLinear(1,3); mesh.getAppearance(0).setFog(fog);
        GpuRasterizer.enabled=true; long before=GpuRasterizer.draws;
        g.render(mesh,position(-2)); g.releaseTarget(); assertEquals(before,GpuRasterizer.draws);
        int pixel=target.getBitmap().getPixel(16,16);
        assertTrue((pixel&255)>60); assertTrue(((pixel>>16)&255)>60);
    }

    @Test public void perspectiveTextureHasBoundedImageDifference() throws Exception {
        int[] cpu=sloped(false), gpu=sloped(true); int max=0, sum=0, samples=0;
        // Interior excludes coverage-rule differences along hardware triangle edges.
        for (int y=20;y<44;y++) for(int x=24;x<40;x++) for(int shift=0;shift<24;shift+=8) {
            int error=Math.abs(((cpu[y*64+x]>>shift)&255)-((gpu[y*64+x]>>shift)&255));
            max=Math.max(max,error); sum+=error; samples++;
        }
        assertTrue("Perspective max error "+max,max<=12);
        assertTrue("Perspective mean error "+(float)sum/samples,(float)sum/samples<3);
    }

    @Test public void callerEglContextIsRestored() {
        EGLDisplay display=eglGetDisplay(EGL_DEFAULT_DISPLAY); int[] version=new int[2];
        assertTrue(eglInitialize(display,version,0,version,1));
        EGLConfig[] config=new EGLConfig[1]; int[] count=new int[1];
        assertTrue(eglChooseConfig(display,new int[]{EGL_RENDERABLE_TYPE,EGL_OPENGL_ES2_BIT,EGL_SURFACE_TYPE,EGL_PBUFFER_BIT,EGL_NONE},0,config,0,1,count,0));
        EGLContext context=eglCreateContext(display,config[0],EGL_NO_CONTEXT,new int[]{EGL_CONTEXT_CLIENT_VERSION,2,EGL_NONE},0);
        EGLSurface surface=eglCreatePbufferSurface(display,config[0],new int[]{EGL_WIDTH,1,EGL_HEIGHT,1,EGL_NONE},0);
        try {
            assertTrue(eglMakeCurrent(display,surface,surface,context));
            GLES20.glClearColor(.25f,.5f,.75f,1);
            Graphics3D g=Graphics3D.getInstance(); bind(g,32,32); GpuRasterizer.enabled=true;
            long before=GpuRasterizer.draws; g.render(quad(0xff80a0c0,null),position(-2)); g.releaseTarget();
            assertEquals(GpuRasterizer.failure,before+1,GpuRasterizer.draws);
            assertEquals(context,eglGetCurrentContext()); assertEquals(surface,eglGetCurrentSurface(EGL_DRAW));
            float[] color=new float[4]; GLES20.glGetFloatv(GLES20.GL_COLOR_CLEAR_VALUE,color,0);
            assertArrayEquals(new float[]{.25f,.5f,.75f,1},color,0);
        } finally {
            GpuRasterizer.close(); eglMakeCurrent(display,EGL_NO_SURFACE,EGL_NO_SURFACE,EGL_NO_CONTEXT);
            eglDestroySurface(display,surface); eglDestroyContext(display,context);
        }
    }

    private int[] sloped(boolean gpu) throws Exception {
        Graphics3D.initGraphics3D(); Graphics3D g=Graphics3D.getInstance(); Image target=bind(g,64,64);
        Camera camera=new Camera(); camera.setPerspective(60,1,1,10); g.setCamera(camera,new Transform());
        byte[] pixels=new byte[16*16*3];
        for (int y=0;y<16;y++) for(int x=0;x<16;x++) { int p=(y*16+x)*3; pixels[p]=(byte)(x*16); pixels[p+1]=(byte)(y*16); pixels[p+2]=64; }
        Texture2D texture=new Texture2D(new Image2D(Image2D.RGB,16,16,pixels)); texture.setBlending(Texture2D.FUNC_REPLACE);
        texture.setFiltering(Texture2D.FILTER_BASE_LEVEL,Texture2D.FILTER_LINEAR); texture.setWrapping(Texture2D.WRAP_CLAMP,Texture2D.WRAP_CLAMP);
        Mesh mesh=quad(0xffffffff,texture); VertexArray points=new VertexArray(4,3,1);
        points.set(0,4,new byte[]{-1,1,-2,-1,-1,-2,1,1,-3,1,-1,-3}); mesh.getVertexBuffer().setPositions(points,1,null);
        GpuRasterizer.enabled=gpu; long before=GpuRasterizer.draws; g.clear(null); g.render(mesh,new Transform()); g.releaseTarget();
        if(gpu) assertEquals(GpuRasterizer.failure,before+1,GpuRasterizer.draws);
        File dir=new File(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir(null),"m3g-raster");
        dir.mkdirs(); try(FileOutputStream out=new FileOutputStream(new File(dir,gpu?"gpu-perspective.png":"cpu-perspective.png"))) {
            target.getBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);
        }
        int[] result=new int[64*64]; target.getBitmap().getPixels(result,0,64,0,0,64,64); return result;
    }
    private static Image bind(Graphics3D g,int w,int h) {
        Image target=Image.createImage(w,h); g.bindTarget(target.getGraphics(),true,0);
        Camera camera=new Camera(); camera.setParallel(4,(float)w/h,1,10); g.setCamera(camera,new Transform()); return target;
    }
    private static Transform position(float z) { Transform t=new Transform(); t.postTranslate(0,0,z); return t; }
    private static Mesh quad(int color,Texture2D texture) {
        VertexArray points=new VertexArray(4,3,1); points.set(0,4,new byte[]{-1,1,0,-1,-1,0,1,1,0,1,-1,0});
        VertexBuffer vertices=new VertexBuffer(); vertices.setPositions(points,1,null); vertices.setDefaultColor(color);
        Appearance style=new Appearance(); PolygonMode poly=new PolygonMode(); poly.setCulling(PolygonMode.CULL_NONE);
        poly.setPerspectiveCorrectionEnable(true); style.setPolygonMode(poly);
        if (texture!=null) {
            VertexArray coords=new VertexArray(4,2,1); coords.set(0,4,new byte[]{0,0,0,1,1,0,1,1});
            vertices.setTexCoords(0,coords,1,null); style.setTexture(0,texture);
        }
        return new Mesh(vertices,new TriangleStripArray(0,new int[]{4}),style);
    }
}
