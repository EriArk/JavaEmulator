package javax.microedition.m3g;

import javax.microedition.lcdui.Image;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;

/** Exact pixels against the retained Java path, including mixed execution. */
public class NativeRasterizerTest {
    @After public void restore() { NativeRasterizer.enabled = true; Graphics3D.getInstance().releaseTarget(); }

    @Test public void nativePixelsMatchJavaAcrossPipelineStates() {
        for (int mode : new int[]{CompositingMode.REPLACE, CompositingMode.ALPHA,
                CompositingMode.ALPHA_ADD, CompositingMode.MODULATE, CompositingMode.MODULATE_X2}) {
            for (int variant = 0; variant < 8; variant++) {
                int[] javaPixels = scene(false, mode, variant, false);
                int[] nativePixels = scene(true, mode, variant, false);
                assertArrayEquals("mode " + mode + ", variant " + variant, javaPixels, nativePixels);
            }
        }
    }

    @Test public void softwareFallbackSharesDepthAndColorWithNativeDraws() {
        for (int variant = 0; variant < 8; variant++) {
            assertArrayEquals("mixed variant " + variant,
                    scene(false, CompositingMode.ALPHA, variant, true),
                    scene(true, CompositingMode.ALPHA, variant, true));
        }
    }

    @Test public void malformedNativePacketsDoNotTouchBuffers() {
        assertTrue("Native library must be present", NativeRasterizer.available());
        int[] pixels = {0xff123456, 0xffabcdef, 0xff987654, 0xff102030};
        short[] depth = {1,2,3,4};
        int[] before = pixels.clone(); short[] depthBefore = depth.clone();
        float[] state = new float[51]; int[] flags = new int[45];
        flags[1] = 2; flags[3] = 2; flags[5] = 2; flags[14] = 256; flags[24] = 15;
        flags[2] = 100;
        assertFalse(NativeRasterizer.draw(pixels, depth, null, state, flags));
        flags[2] = 0; state[0] = Float.NaN;
        assertFalse(NativeRasterizer.draw(pixels, depth, null, state, flags));
        state[0] = 0; flags[6] = 2; flags[25] = 1; flags[16] = 2; flags[17] = 2;
        assertFalse(NativeRasterizer.draw(pixels, depth, new int[][]{new int[1]}, state, flags));
        assertFalse(NativeRasterizer.draw(pixels, new short[1], null, state, flags));
        int[][] images = {new int[4], new int[4]};
        flags[25] = 2; flags[27] = 2; flags[28] = 2; flags[31] = 12;
        assertFalse("Invalid second blend", NativeRasterizer.draw(pixels, depth, images, state, flags));
        flags[31] = 0; images[1] = pixels;
        assertFalse("Target/texture alias", NativeRasterizer.draw(pixels, depth, images, state, flags));
        flags[25] = 5;
        assertFalse("Too many layers", NativeRasterizer.draw(pixels, depth, images, state, flags));
        assertArrayEquals(before, pixels);
        assertArrayEquals(depthBefore, depth);
    }

    private int[] scene(boolean nativePath, int blending, int variant, boolean mixed) {
        NativeRasterizer.enabled = nativePath;
        long start = NativeRasterizer.draws;
        Graphics3D.initGraphics3D();
        Graphics3D g = Graphics3D.getInstance();
        Image target = Image.createImage(64, 48);
        g.bindTarget(target.getGraphics(), true, (variant & 1) == 0 ? 0 : Graphics3D.DITHER);
        try {
            // Offset and clipped viewports retain physical buffer indices.
            g.setViewport((variant & 2) == 0 ? 0 : -5, (variant & 2) == 0 ? 0 : 3, 64, 48);
            Background bg = new Background(); bg.setColor(0xff406080); g.clear(bg);
            Camera cam = new Camera();
            if ((variant & 4) == 0) cam.setParallel(2, 64f/48, 1, 10);
            else cam.setPerspective(60, 64f/48, 1, 10);
            g.setCamera(cam, new Transform());
            Appearance style = new Appearance();
            PolygonMode poly = new PolygonMode(); poly.setCulling(PolygonMode.CULL_NONE);
            poly.setPerspectiveCorrectionEnable(true); style.setPolygonMode(poly);
            CompositingMode blend = new CompositingMode(); blend.setBlending(blending);
            blend.setColorWriteEnable(variant != 2); blend.setAlphaWriteEnable(variant != 3);
            blend.setDepthWriteEnable(variant != 4); blend.setDepthTestEnable(variant != 5);
            blend.setAlphaThreshold(variant == 6 ? 0.6f : 0);
            blend.setDepthOffset(variant == 7 ? 0.25f : 0, 0);
            style.setCompositingMode(blend);
            Texture2D texture = new Texture2D(new Image2D(Image2D.RGBA, 2, 2,
                    new byte[]{-1,0,0,-128, 0,-1,0,-1, 0,0,-1,32, -1,-1,0,-64}));
            texture.setBlending((variant & 1)==0 ? Texture2D.FUNC_MODULATE : Texture2D.FUNC_REPLACE);
            texture.setFiltering(Texture2D.FILTER_BASE_LEVEL,
                    (variant & 2)==0 ? Texture2D.FILTER_NEAREST : Texture2D.FILTER_LINEAR);
            texture.setWrapping(Texture2D.WRAP_REPEAT, Texture2D.WRAP_CLAMP);
            style.setTexture(0, texture);
            Fog fog = new Fog(); fog.setMode(Fog.LINEAR); fog.setLinear(1, 6); fog.setColor(0x305070);
            style.setFog(fog);
            VertexArray pos = new VertexArray(4, 3, 2);
            pos.set(0,4,new short[]{-10,9,-21, -10,-9,-21, 10,9,-27, 10,-9,-27});
            VertexBuffer vertices = new VertexBuffer(); vertices.setPositions(pos, 0.1f, null);
            VertexArray uv = new VertexArray(4, 2, 1);
            uv.set(0,4,new byte[]{-2,-1, -2,4, 5,-1, 5,4});
            vertices.setTexCoords(0,uv,0.5f,null);
            VertexArray colors = new VertexArray(4,4,1);
            colors.set(0,4,new byte[]{-1,-128,64,-1, 32,-1,64,-128, -1,32,-1,-64, 96,64,-1,-1});
            vertices.setColors(colors);
            Mesh mesh = new Mesh(vertices, new TriangleStripArray(0,new int[]{4}), style);
            mesh.setAlphaFactor(0.8f);
            g.render(mesh, new Transform());
            if (mixed) {
                // Half-resolution mode remains on Java and shares both buffers.
                fog.setMode(Fog.EXPONENTIAL); fog.setDensity(0.2f);
                texture.setFiltering(Texture2D.FILTER_NEAREST, Texture2D.FILTER_LINEAR);
                Transform front = new Transform(); front.postTranslate(0.15f, 0, 0.3f);
                long beforeFallback = NativeRasterizer.draws;
                org.recompile.mobile.Mobile.halfResM3GRaster=true;
                try { g.render(mesh, front); }
                finally { org.recompile.mobile.Mobile.halfResM3GRaster=false; }
                assertEquals("Unexpected native half-resolution draw", beforeFallback, NativeRasterizer.draws);
                style.setFog(null); style.setTexture(0, null);
                front.postTranslate(-0.3f,0,0.2f);
                g.render(mesh,front);
            }
        } finally { g.releaseTarget(); }
        if (nativePath) assertTrue("No native draw for variant " + variant, NativeRasterizer.draws > start);
        int[] pixels = new int[64*48];
        target.getBitmap().getPixels(pixels,0,64,0,0,64,48);
        return pixels;
    }

    @Test public void layeredTexturesAndFogMatchJava() {
        for (int layers=1; layers<=4; layers++) {
            for (int mode : new int[]{Texture2D.FUNC_REPLACE, Texture2D.FUNC_MODULATE,
                    Texture2D.FUNC_ADD, Texture2D.FUNC_BLEND, Texture2D.FUNC_DECAL}) {
                for (int format : new int[]{Image2D.RGB, Image2D.RGBA, Image2D.ALPHA,
                        Image2D.LUMINANCE, Image2D.LUMINANCE_ALPHA}) {
                    for (int variant=0; variant<4; variant++) {
                        assertArrayEquals("layers="+layers+", blend="+mode+", format="+format+", variant="+variant,
                                layered(false,layers,mode,format,variant,false),
                                layered(true,layers,mode,format,variant,false));
                    }
                }
            }
        }
    }

    @Test public void repeatedImageReferencesMatchJava() {
        for (int layers=2; layers<=4; layers++) for (int variant=0; variant<4; variant++) {
            assertArrayEquals("shared image, layers="+layers+", variant="+variant,
                    layered(false,layers,Texture2D.FUNC_BLEND,Image2D.RGBA,variant,true),
                    layered(true,layers,Texture2D.FUNC_BLEND,Image2D.RGBA,variant,true));
        }
    }

    @Test public void zeroDensityFogDoesNotTintMeshesOrSprites() {
        for (boolean nativePath : new boolean[]{false,true}) for (boolean sprite : new boolean[]{false,true}) {
            NativeRasterizer.enabled=nativePath;
            Graphics3D.initGraphics3D();
            Graphics3D g=Graphics3D.getInstance();
            Image target=Image.createImage(32,32);
            g.bindTarget(target.getGraphics(),true,0);
            try {
                Camera camera=new Camera(); camera.setParallel(2,1,1,10);
                g.setCamera(camera,new Transform());
                Background bg=new Background(); bg.setColor(0xff000000); g.clear(bg);
                Appearance style=new Appearance();
                Fog fog=new Fog(); fog.setMode(Fog.EXPONENTIAL); fog.setDensity(0); fog.setColor(0x0000ff);
                style.setFog(fog);
                Transform position=new Transform(); position.postTranslate(0,0,-2);
                if (sprite) {
                    Sprite3D node=new Sprite3D(true,new Image2D(Image2D.RGB,1,1,new byte[]{-1,0,0}),style);
                    g.render(node,position);
                } else {
                    VertexArray pos=new VertexArray(4,3,1);
                    pos.set(0,4,new byte[]{-1,1,0,-1,-1,0,1,1,0,1,-1,0});
                    VertexBuffer vertices=new VertexBuffer(); vertices.setPositions(pos,1,null);
                    vertices.setDefaultColor(0xffff0000);
                    g.render(new Mesh(vertices,new TriangleStripArray(0,new int[]{4}),style),position);
                }
            } finally { g.releaseTarget(); }
            assertEquals("Zero-density fog must leave solid red unchanged",0xffff0000,target.getBitmap().getPixel(16,16));
        }
    }

    private int[] layered(boolean nativePath, int layers, int mode, int format, int variant, boolean shared) {
        return layered(nativePath,layers,mode,format,variant,shared,0);
    }

    @Test public void mipmappedTexturesMatchJava() {
        for (int layers=1; layers<=4; layers++) for (int filter=1; filter<=2; filter++) {
            for (int variant=0; variant<8; variant++) {
                assertArrayEquals("mip layers="+layers+", filter="+filter+", variant="+variant,
                        layered(false,layers,Texture2D.FUNC_BLEND,Image2D.RGBA,variant,true,filter),
                        layered(true,layers,Texture2D.FUNC_BLEND,Image2D.RGBA,variant,true,filter));
            }
        }
    }

    @Test public void magnifiedMipmapsKeepFractionalCoordinates() {
        for (boolean nativePath : new boolean[]{false,true}) for (int variant=0; variant<4; variant++) {
            assertArrayEquals("Base-level mipmap must preserve bilinear subtexel sampling",
                    layered(nativePath,1,Texture2D.FUNC_REPLACE,Image2D.RGBA,variant,false,0),
                    layered(nativePath,1,Texture2D.FUNC_REPLACE,Image2D.RGBA,variant,false,3));
        }
    }

    @Test public void mipmapsRefreshAfterImageChanges() {
        Image2D image=new Image2D(Image2D.RGB,4,8);
        Texture2D texture=new Texture2D(image);
        int[] original=texture.getImageForLOD(3).image.clone();
        byte[] data=new byte[4*8*3]; java.util.Arrays.fill(data,(byte)255);
        image.set(0,0,4,8,data);
        assertFalse(java.util.Arrays.equals(original,texture.getImageForLOD(3).image));
        assertEquals(0xffffffff,texture.getImageForLOD(3).image[0]);
        Graphics3D.initGraphics3D();
        Graphics3D g=Graphics3D.getInstance();
        g.bindTarget(image,false,0);
        Background bg=new Background(); bg.setColor(0xff00ff00); g.clear(bg); g.releaseTarget();
        assertEquals("Render-to-image invalidates mipmaps",0xff00ff00,texture.getImageForLOD(3).image[0]);
        Image2D replacement=new Image2D(Image2D.RGB,1,1,new byte[]{-1,0,0});
        texture.setImage(replacement);
        assertEquals(0xffff0000,texture.getImageForLOD(8).image[0]);
    }

    @Test public void spritesMatchJavaAcrossPipelineStates() {
        for (int mode : new int[]{CompositingMode.REPLACE,CompositingMode.ALPHA,
                CompositingMode.ALPHA_ADD,CompositingMode.MODULATE,CompositingMode.MODULATE_X2}) {
            for (int variant=0; variant<32; variant++) {
                assertArrayEquals("sprite blend="+mode+", variant="+variant,
                        sprites(false,mode,variant),sprites(true,mode,variant));
            }
        }
    }

    private int[] sprites(boolean nativePath, int mode, int variant) {
        NativeRasterizer.enabled=nativePath;
        long before=NativeRasterizer.spriteDraws;
        Graphics3D.initGraphics3D();
        Graphics3D g=Graphics3D.getInstance();
        Image target=Image.createImage(64,48);
        g.bindTarget(target.getGraphics(),(variant&16)==0,0);
        try {
            g.setViewport(-3,2,64,48); g.setDepthRange(0.2f,0.8f);
            Background bg=new Background(); bg.setColor(0xff406080); g.clear(bg);
            Camera camera=new Camera();
            if ((variant&1)==0) camera.setParallel(2,64f/48,1,10);
            else camera.setPerspective(60,64f/48,1,10);
            g.setCamera(camera,new Transform());
            Appearance style=new Appearance();
            CompositingMode blend=new CompositingMode(); blend.setBlending(mode);
            blend.setColorWriteEnable(variant%8!=2); blend.setAlphaWriteEnable(variant%8!=3);
            blend.setDepthTestEnable(variant%8!=4); blend.setDepthWriteEnable(variant%8!=5);
            blend.setAlphaThreshold(variant%8==6 ? 0.6f : 0); style.setCompositingMode(blend);
            Fog fog=new Fog(); fog.setMode((variant&2)==0 ? Fog.LINEAR : Fog.EXPONENTIAL);
            fog.setLinear(1,6); fog.setDensity(0.2f); fog.setColor(0x905020); style.setFog(fog);
            byte[] data=new byte[19*25*4];
            for (int i=0; i<data.length; i++) data[i]=(byte)(i*37+11);
            for (int i=3; i<data.length; i+=20) data[i]=0;
            Sprite3D sprite=new Sprite3D((variant&4)==0,new Image2D(Image2D.RGBA,19,25,data),style);
            sprite.setCrop(-2,1,(variant&8)==0 ? 18 : -18,(variant&2)==0 ? 23 : -23);
            sprite.setAlphaFactor(0.7f);
            Transform position=new Transform(); position.postTranslate(0,0,-2);
            g.render(sprite,position);
            position.postTranslate(0.15f,0,-0.5f); g.render(sprite,position);
            position.postTranslate(-0.3f,0,0.9f); g.render(sprite,position);
        } finally { g.releaseTarget(); }
        if (nativePath) assertTrue("No native sprite draw",NativeRasterizer.spriteDraws>before);
        int[] pixels=new int[64*48]; target.getBitmap().getPixels(pixels,0,64,0,0,64,48);
        boolean visible=false;
        for (int pixel : pixels) visible |= pixel!=0xff406080;
        assertTrue("Sprite scene is blank",visible);
        return pixels;
    }

    @Test public void malformedSpriteAndMipPacketsAreAtomic() {
        int[] pixels={1,2,3,4}; short[] depth={5,6,7,8};
        int[] before=pixels.clone(); short[] zBefore=depth.clone();
        float[] f={0,1,0,1}; int[] p=new int[20];
        p[1]=2; p[3]=2; p[5]=2; p[6]=2; p[7]=2; p[10]=2; p[11]=2; p[14]=256;
        assertFalse(NativeRasterizer.sprite(pixels,depth,pixels,f,p));
        p[4]=10;
        assertFalse(NativeRasterizer.sprite(pixels,depth,new int[4],f,p));
        p[4]=0; p[10]=3;
        assertFalse(NativeRasterizer.sprite(pixels,depth,new int[4],f,p));
        p[10]=2; f[0]=Float.NaN;
        assertFalse(NativeRasterizer.sprite(pixels,depth,new int[4],f,p));
        float[] state=new float[51]; int[] flags=new int[45];
        flags[1]=2; flags[3]=2; flags[5]=2; flags[6]=2; flags[14]=256;
        flags[16]=2; flags[17]=2; flags[25]=1;
        int[] mipFlags={2,1,0,0,0,0,0,0}; float[] mipState=new float[18];
        assertFalse(NativeRasterizer.mipmapped(pixels,depth,new int[][]{new int[4],pixels},state,flags,mipState,mipFlags));
        assertFalse(NativeRasterizer.mipmapped(pixels,depth,new int[][]{new int[4],new int[0]},state,flags,mipState,mipFlags));
        mipFlags[0]=17;
        assertFalse(NativeRasterizer.mipmapped(pixels,depth,new int[][]{new int[4]},state,flags,mipState,mipFlags));
        assertArrayEquals(before,pixels); assertArrayEquals(zBefore,depth);
    }

    private int[] layered(boolean nativePath, int layers, int mode, int format, int variant, boolean shared, int mip) {
        NativeRasterizer.enabled = nativePath;
        long start = NativeRasterizer.draws;
        Graphics3D.initGraphics3D();
        Graphics3D g = Graphics3D.getInstance();
        Image target = Image.createImage(64,48);
        g.bindTarget(target.getGraphics(),true,0);
        try {
            Background bg = new Background(); bg.setColor(0xff406080); g.clear(bg);
            Camera camera = new Camera();
            if ((variant&1)==0) camera.setParallel(2,64f/48,1,10);
            else camera.setPerspective(60,64f/48,1,10);
            g.setCamera(camera,new Transform());
            Appearance style = new Appearance();
            PolygonMode poly = new PolygonMode(); poly.setCulling(PolygonMode.CULL_NONE);
            poly.setPerspectiveCorrectionEnable(true); style.setPolygonMode(poly);
            CompositingMode blend = new CompositingMode(); blend.setBlending(CompositingMode.ALPHA);
            style.setCompositingMode(blend);
            Fog fog = new Fog(); fog.setMode((variant&2)==0 ? Fog.EXPONENTIAL : Fog.LINEAR);
            fog.setDensity(0.35f); fog.setLinear(1,6); fog.setColor(0x507030); style.setFog(fog);
            VertexArray pos = new VertexArray(4,3,2);
            pos.set(0,4,new short[]{-10,9,-21,-10,-9,-21,10,9,-27,10,-9,-27});
            VertexBuffer vertices = new VertexBuffer(); vertices.setPositions(pos,0.1f,null);
            vertices.setDefaultColor(0xc0708090);
            int channels = format==Image2D.RGBA ? 4 : format==Image2D.RGB ? 3 : format==Image2D.LUMINANCE_ALPHA ? 2 : 1;
            Image2D image = null;
            for (int i=0; i<layers; i++) {
                // Sparse source slots, NPOT sizes and a repeated image must all keep their own state.
                int slot = i==layers-1 ? 3 : i;
                if (!shared || image==null) {
                    int w=mip==1 || mip==2 ? ((variant&4)==0 ? 7 : 1) : 3+i;
                    int h=mip==1 || mip==2 ? 32 : 5;
                    byte[] data = new byte[w*h*channels];
                    for (int j=0; j<data.length; j++) data[j]=(byte)(27+j*37+i*19);
                    image = new Image2D(format,w,h,data);
                }
                Texture2D texture = new Texture2D(image);
                texture.setBlending(i==0 && layers>1 ? Texture2D.FUNC_REPLACE : mode);
                texture.setBlendColor(0x205080+i*0x102010);
                texture.setFiltering(mip==0 || (i>0 && (i&1)==0) ? Texture2D.FILTER_BASE_LEVEL
                                : mip==2 ? Texture2D.FILTER_LINEAR : Texture2D.FILTER_NEAREST,
                        ((variant+i)&1)==0 ? Texture2D.FILTER_NEAREST : Texture2D.FILTER_LINEAR);
                texture.setWrapping((i&1)==0 ? Texture2D.WRAP_REPEAT : Texture2D.WRAP_CLAMP,
                        (i&1)==0 ? Texture2D.WRAP_CLAMP : Texture2D.WRAP_REPEAT);
                texture.setTranslation(0.125f*i,-0.25f*i,0);
                style.setTexture(slot,texture);
                VertexArray uv = new VertexArray(4,2,1);
                uv.set(0,4,new byte[]{-2,-1,-2,4,5,-1,5,4});
                vertices.setTexCoords(slot,uv,mip==1 || mip==2 ? 8+i*2 : 0.5f+i*0.125f,null);
            }
            Mesh mesh = new Mesh(vertices,new TriangleStripArray(0,new int[]{4}),style);
            mesh.setAlphaFactor(0.8f);
            g.render(mesh,new Transform());
        } finally { g.releaseTarget(); }
        if (nativePath) assertTrue("No native layered draw", NativeRasterizer.draws>start);
        int[] pixels = new int[64*48];
        target.getBitmap().getPixels(pixels,0,64,0,0,64,48);
        return pixels;
    }
}
