package javax.microedition.m3g;

import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;

public class GpuTextureTest {
    @After public void restore() {
        Graphics3D.getInstance().releaseTarget(); GpuRasterizer.close(); GpuRasterizer.enabled=false;
    }

    @Test public void allTextureCombinersPreserveRgbAndAlpha() {
        for(boolean rgba:new boolean[]{false,true}) for(int mode:new int[]{Texture2D.FUNC_REPLACE,
                Texture2D.FUNC_MODULATE,Texture2D.FUNC_ADD,Texture2D.FUNC_BLEND,Texture2D.FUNC_DECAL}) {
            Texture2D texture=solid(rgba,0x9060a0e0); texture.setBlending(mode); texture.setBlendColor(0xc04080);
            Mesh mesh=quad(texture); int expected=combine(0xb0705030,0x9060a0e0,mode,rgba,0xc04080);
            assertEquals("CPU mode="+mode,expected,render(mesh,false));
            assertEquals("GPU mode="+mode,expected,render(mesh,true));
        }
    }

    @Test public void fourLayersAndSparseSlotsUseTheirOwnCoordinatesAndTransforms() {
        for(boolean sparse:new boolean[]{false,true}) {
            Mesh mesh=quad(null); int expected=0xb0705030;
            for(int unit=0;unit<4;unit++) {
                if(sparse && (unit&1)==0) continue;
                Image2D image=new Image2D(Image2D.RGB,2,1,new byte[]{16,32,48, 64,80,96});
                Texture2D texture=new Texture2D(image); texture.setBlending(Texture2D.FUNC_ADD);
                texture.setWrapping(Texture2D.WRAP_REPEAT,Texture2D.WRAP_CLAMP);
                texture.setTranslation(unit*.5f,0,0);
                mesh.getAppearance(0).setTexture(unit,texture);
                mesh.getVertexBuffer().setTexCoords(unit,coords(),0,new float[]{.1f,0,0});
                expected=combine(expected,(unit&1)==0?0xff102030:0xff405060,Texture2D.FUNC_ADD,false,0);
            }
            assertEquals(expected,render(mesh,false)); assertEquals(expected,render(mesh,true));
        }
    }

    @Test public void nearestMipSelectsDetailForLongAndNarrowTextures() {
        for(int[] size:new int[][]{{64,64},{64,1},{1,64},{33,7}}) {
            Texture2D texture=levels(size[0],size[1]); texture.setFiltering(Texture2D.FILTER_NEAREST,Texture2D.FILTER_NEAREST);
            Mesh mesh=quad(texture);
            mesh.getVertexBuffer().setTexCoords(0,coords(),32f/Math.max(size[0],size[1]),null);
            assertEquals("CPU",0xa0604080,render(mesh,false));
            assertEquals("GPU",0xa0604080,render(mesh,true));
            mesh.getVertexBuffer().setTexCoords(0,coords(),0,new float[]{.25f,.25f,0});
            assertEquals("Constant UV CPU",0x80202040,render(mesh,false));
            assertEquals("Constant UV GPU",0x80202040,render(mesh,true));
        }
    }

    @Test public void trilinearInterpolatesColorAndAlphaBeforeCombining() {
        for(int filter:new int[]{Texture2D.FILTER_NEAREST,Texture2D.FILTER_LINEAR}) {
            Texture2D texture=levels(64,8); texture.setFiltering(Texture2D.FILTER_LINEAR,filter);
            Mesh mesh=quad(texture); mesh.getVertexBuffer().setTexCoords(0,coords(),.75f,null);
            int cpu=render(mesh,false),gpu=render(mesh,true);
            assertChannels(cpu,gpu,2);
            // rho=3 texels/pixel: between levels 1 and 2, not a dithered choice of either.
            assertTrue((gpu>>>24)>0xa0 && (gpu>>>24)<0xe0);
            assertTrue((gpu&255)>0x80 && (gpu&255)<0xc0);
        }
    }

    @Test public void mipMutationAndFilterChangesDoNotReuseStaleLevels() {
        Texture2D texture=levels(64,8); Mesh mesh=quad(texture);
        mesh.getVertexBuffer().setTexCoords(0,coords(),2,null);
        for(int filter:new int[]{Texture2D.FILTER_LINEAR,Texture2D.FILTER_BASE_LEVEL,Texture2D.FILTER_NEAREST}) {
            texture.setFiltering(filter,Texture2D.FILTER_LINEAR);
            assertChannels(render(mesh,false),render(mesh,true),2);
        }
        byte[] pixels=new byte[64*8*4];
        for(int i=0;i<pixels.length;i+=4) { pixels[i]=32; pixels[i+1]=64; pixels[i+2]=96; pixels[i+3]=(byte)192; }
        texture.getImage().set(0,0,64,8,pixels);
        assertEquals(0xc0204060,render(mesh,true));
        texture.setImage(new Image2D(Image2D.RGBA,1,1,new byte[]{80,64,32,-1}));
        assertEquals(0xff504020,render(mesh,true));
    }

    @Test public void textureCacheTracksBaseAndIndividualMipRevisions() {
        Texture2D texture=levels(64,8); texture.setFiltering(Texture2D.FILTER_NEAREST,Texture2D.FILTER_NEAREST);
        Mesh mesh=quad(texture); mesh.getVertexBuffer().setTexCoords(0,coords(),.5f,null);
        long before=GpuRasterizer.textureUploads;
        assertEquals(0xa0604080,render(mesh,true)); long first=GpuRasterizer.textureUploads;
        assertEquals(before+7,first);
        assertEquals(0xa0604080,render(mesh,true)); assertEquals(first,GpuRasterizer.textureUploads);
        Image2D mip=texture.getImageForLOD(1); byte[] data=new byte[mip.getWidth()*mip.getHeight()*4];
        for(int i=0;i<data.length;i+=4) { data[i]=32; data[i+1]=96; data[i+2]=64; data[i+3]=-1; }
        mip.set(0,0,mip.getWidth(),mip.getHeight(),data);
        assertEquals(0xff206040,render(mesh,true)); assertEquals(first+1,GpuRasterizer.textureUploads);
    }

    private static int render(Mesh mesh,boolean gpu) {
        Graphics3D g=Graphics3D.getInstance(); Image2D target=new Image2D(Image2D.RGBA,32,32);
        g.bindTarget(target,true,0); Camera camera=new Camera(); camera.setParallel(4,1,1,10);
        g.setCamera(camera,new Transform()); g.clear(null);
        GpuRasterizer.enabled=gpu; long before=GpuRasterizer.draws;
        Transform t=new Transform(); t.postTranslate(0,0,-2); g.render(mesh,t); g.releaseTarget();
        if(gpu) assertEquals(GpuRasterizer.failure,before+1,GpuRasterizer.draws);
        return target.image[15*32+16];
    }
    private static Texture2D solid(boolean rgba,int color) {
        byte[] pixel=rgba?new byte[]{(byte)(color>>16),(byte)(color>>8),(byte)color,(byte)(color>>>24)}
                :new byte[]{(byte)(color>>16),(byte)(color>>8),(byte)color};
        return new Texture2D(new Image2D(rgba?Image2D.RGBA:Image2D.RGB,1,1,pixel));
    }
    private static Texture2D levels(int w,int h) {
        Texture2D texture=new Texture2D(new Image2D(Image2D.RGBA,w,h)); texture.setBlending(Texture2D.FUNC_REPLACE);
        int[] colors={0x80202040,0xa0604080,0xe02080c0,0xffc02040,0xff204060,0xff608020,0xff406080};
        for(int level=0;level<32-Integer.numberOfLeadingZeros(Math.max(w,h));level++)
            java.util.Arrays.fill(texture.getImageForLOD(level).image,colors[level]);
        return texture;
    }
    private static VertexArray coords() {
        VertexArray uv=new VertexArray(4,2,1); uv.set(0,4,new byte[]{0,0,0,1,1,0,1,1}); return uv;
    }
    private static Mesh quad(Texture2D texture) {
        VertexArray pos=new VertexArray(4,3,1); pos.set(0,4,new byte[]{-1,1,0,-1,-1,0,1,1,0,1,-1,0});
        VertexBuffer vertices=new VertexBuffer(); vertices.setPositions(pos,1,null); vertices.setDefaultColor(0xb0705030);
        vertices.setTexCoords(0,coords(),1,null); Appearance style=new Appearance(); style.setTexture(0,texture);
        PolygonMode poly=new PolygonMode(); poly.setCulling(PolygonMode.CULL_NONE); poly.setPerspectiveCorrectionEnable(true);
        style.setPolygonMode(poly); return new Mesh(vertices,new TriangleStripArray(0,new int[]{4}),style);
    }
    private static int combine(int c,int t,int mode,boolean rgba,int blendColor) {
        int a=c>>>24,ta=t>>>24;
        if(rgba) a=mode==Texture2D.FUNC_REPLACE?ta:mode==Texture2D.FUNC_DECAL?a:
                (a*ta+(mode==Texture2D.FUNC_MODULATE?a:128))/256;
        int result=a<<24;
        for(int shift=0;shift<24;shift+=8) {
            int v=(c>>>shift)&255,s=(t>>>shift)&255,b=(blendColor>>>shift)&255;
            if(mode==Texture2D.FUNC_REPLACE) v=s;
            else if(mode==Texture2D.FUNC_ADD) v=Math.min(255,v+s);
            else if(mode==Texture2D.FUNC_MODULATE) v=(v*s+(rgba?128:0))/256;
            else if(mode==Texture2D.FUNC_DECAL) v=rgba?v+(((s-v)*ta)>>8):s;
            else v+=((b-v)*s+128)>>8;
            result|=v<<shift;
        }
        return result;
    }
    private static void assertChannels(int a,int b,int tolerance) {
        for(int shift=0;shift<32;shift+=8) assertTrue(Integer.toHexString(a)+" vs "+Integer.toHexString(b),
                Math.abs(((a>>>shift)&255)-((b>>>shift)&255))<=tolerance);
    }
}
