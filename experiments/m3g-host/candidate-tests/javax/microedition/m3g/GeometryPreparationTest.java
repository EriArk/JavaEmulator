package javax.microedition.m3g;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import javax.microedition.lcdui.Image;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;

public class GeometryPreparationTest {
    @After public void restore() {
        Triangle.cacheLighting = true;
        Graphics3D.getInstance().releaseTarget();
    }

    @Test public void packedTransformPreservesUnusedCapacity() {
        Transform transform = new Transform();
        transform.set(new float[]{2,.25f,-1,3, -2,1,.5f,-4, 1,-1,3,2, .5f,.25f,-.5f,2});
        for (int type : new int[]{1,2}) for (int dims : new int[]{2,3}) {
            VertexArray input = new VertexArray(3,dims,type);
            short[] values = new short[3*dims];
            byte[] bytes = new byte[values.length];
            for (int i=0; i<values.length; i++) { values[i]=(short)(i*11-40); bytes[i]=(byte)values[i]; }
            if (type==1) input.set(0,3,bytes); else input.set(0,3,values);
            for (boolean w : new boolean[]{false,true}) {
                float[] expected = new float[12];
                for (int i=0; i<3; i++) {
                    for (int c=0; c<dims; c++) expected[i*4+c]=values[i*dims+c];
                    expected[i*4+3]=w ? 1 : 0;
                }
                transform.transform(expected);
                for (int capacity : new int[]{12,13,256}) {
                    float[] out = new float[capacity]; Arrays.fill(out,12345.25f);
                    transform.transform(input,out,w);
                    assertArrayEquals(expected,Arrays.copyOf(out,12),0);
                    for (int i=12; i<out.length; i++) assertEquals("Unused tail",12345.25f,out[i],0);
                }
            }
        }
    }

    @Test public void packedTransformStillRejectsInvalidInput() {
        Transform transform = new Transform();
        VertexArray input = new VertexArray(3,3,1);
        try { transform.transform(input,new float[11],true); fail(); }
        catch (IllegalArgumentException expected) { }
        try { transform.transform(new VertexArray(3,4,1),new float[12],true); fail(); }
        catch (IllegalArgumentException expected) { }
        try { transform.transform(null,new float[12],true); fail(); }
        catch (NullPointerException expected) { }
    }

    @Test public void oppositeFacesDoNotShareLighting() {
        Fixture f = new Fixture();
        int[] indices={0,1,2, 0,2,1};
        int[] expected=f.colors(indices,false,PolygonMode.SHADE_SMOOTH);
        int[] actual=f.colors(indices,true,PolygonMode.SHADE_SMOOTH);
        assertArrayEquals(expected,actual);
        assertNotEquals("Front/back must differ",actual[0],actual[3]);
        assertTrue("One side is lit",(actual[0]&255)>240 || (actual[3]&255)>240);
        assertTrue("Other side is dark",(actual[0]&255)==0 || (actual[3]&255)==0);
    }

    @Test public void nonAdjacentVerticesAndFlatProvokingColorsAreReused() {
        Fixture f = new Fixture();
        int[] indices={0,1,2, 3,4,5, 0,1,2};
        for (int mode : new int[]{PolygonMode.SHADE_SMOOTH,PolygonMode.SHADE_FLAT}) {
            for (boolean tracking : new boolean[]{false,true}) {
                f.material.setVertexColorTrackingEnable(tracking);
                long start=Triangle.shadedVertices;
                int[] expected=f.colors(indices,false,mode);
                assertEquals(mode==PolygonMode.SHADE_FLAT ? 3 : 9,Triangle.shadedVertices-start);
                start=Triangle.shadedVertices;
                int[] actual=f.colors(indices,true,mode);
                assertArrayEquals(expected,actual);
                assertEquals(mode==PolygonMode.SHADE_FLAT ? 2 : 6,Triangle.shadedVertices-start);
                if (mode==PolygonMode.SHADE_FLAT) for (int i=0; i<actual.length; i+=3) {
                    assertEquals(actual[i],actual[i+1]); assertEquals(actual[i],actual[i+2]);
                }
            }
        }
    }

    @Test public void lightingChangesAndEpochRolloverInvalidateColors() throws Exception {
        Fixture f = new Fixture(); int[] indices={0,1,2};
        int[] previous=f.colors(indices,true,PolygonMode.SHADE_SMOOTH);
        for (int change=0; change<5; change++) {
            if (change==0) f.light.setColor(0x40ff60);
            if (change==1) f.material.setColor(Material.DIFFUSE,0x80804020);
            if (change==2) f.normal.postScale(1,1,-1);
            if (change==3) f.material.setColor(Material.EMISSIVE,0x305070);
            if (change==4) {
                Field epoch=Triangle.class.getDeclaredField("lightingEpoch"); epoch.setAccessible(true);
                epoch.setInt(null,Integer.MAX_VALUE);
                f.material.setColor(Material.EMISSIVE,0x604020);
            }
            int[] actual=f.colors(indices,true,PolygonMode.SHADE_SMOOTH);
            assertFalse("Changed lighting must be visible: "+change,Arrays.equals(previous,actual));
            assertArrayEquals(f.colors(indices,false,PolygonMode.SHADE_SMOOTH),actual);
            previous=actual;
        }
    }

    @Test public void sparseTexturesRetainScratchButReleaseInactiveReferences() {
        Graphics3D.initGraphics3D(); Graphics3D g=Graphics3D.getInstance();
        Image image=Image.createImage(32,32); g.bindTarget(image.getGraphics(),true,0);
        Camera camera=new Camera(); camera.setParallel(2,1,1,10); g.setCamera(camera,new Transform());
        VertexBuffer vertices=quad();
        VertexArray uv=new VertexArray(4,2,1); uv.set(0,4,new byte[]{0,0,0,1,1,0,1,1});
        vertices.setTexCoords(0,uv,1,null); vertices.setTexCoords(3,uv,1,null);
        Appearance style=new Appearance();
        Texture2D texture=new Texture2D(new Image2D(Image2D.RGB,1,1,new byte[]{-1,0,0}));
        texture.setBlending(Texture2D.FUNC_REPLACE); style.setTexture(0,texture); style.setTexture(3,texture);
        Mesh mesh=new Mesh(vertices,new TriangleStripArray(0,new int[]{4}),style);
        Background bg=new Background(); g.clear(bg); g.render(mesh,new Transform());
        float[] first=g.texVerts[0], second=g.texVerts[1];
        g.releaseTarget();
        assertNotNull(second); assertEquals(0xffff0000,image.getBitmap().getPixel(16,16));
        g.bindTarget(image.getGraphics(),true,0); g.clear(bg);
        g.render(mesh,new Transform()); assertSame(first,g.texVerts[0]); assertSame(second,g.texVerts[1]);
        style.setTexture(0,null); style.setTexture(3,null); g.clear(bg); g.render(mesh,new Transform());
        for (float[] coords : g.texVerts) assertNull(coords);
        for (Texture2D active : Graphics3D.textures) assertNull(active);
        g.releaseTarget();
        assertEquals(0xffffffff,image.getBitmap().getPixel(16,16));
        g.bindTarget(image.getGraphics(),true,0); g.clear(bg);
        style.setTexture(3,texture); g.render(mesh,new Transform()); assertSame(first,g.texVerts[0]);
        style.setTexture(0,texture); g.render(mesh,new Transform()); assertSame(second,g.texVerts[1]);
    }

    @Test public void unlitDrawDoesNotPrepareLightsAndNextLitDrawDoes() throws Exception {
        Graphics3D.initGraphics3D(); Graphics3D g=Graphics3D.getInstance();
        Image image=Image.createImage(32,32); g.bindTarget(image.getGraphics(),true,0);
        Camera camera=new Camera(); camera.setParallel(2,1,1,10); g.setCamera(camera,new Transform());
        Light light=new Light(); light.setMode(Light.DIRECTIONAL); g.addLight(light,new Transform());
        Field field=Graphics3D.class.getDeclaredField("lightEyeDir"); field.setAccessible(true);
        float[] sentinel={19,20,21,22}; field.set(g,sentinel);
        Field pos=Graphics3D.class.getDeclaredField("lightEyePos"); pos.setAccessible(true); pos.set(g,new float[4]);
        VertexBuffer vertices=quad(); Appearance style=new Appearance();
        Mesh mesh=new Mesh(vertices,new TriangleStripArray(0,new int[]{4}),style);
        g.render(mesh,new Transform()); assertArrayEquals(new float[]{19,20,21,22},sentinel,0);
        VertexArray normals=new VertexArray(4,3,1); normals.set(0,4,new byte[]{0,0,127,0,0,127,0,0,127,0,0,127});
        vertices.setNormals(normals); style.setMaterial(new Material());
        g.render(mesh,new Transform()); assertTrue(((float[])field.get(g))[2]<-.99f);
    }

    private static VertexBuffer quad() {
        VertexArray points=new VertexArray(4,3,1);
        points.set(0,4,new byte[]{-1,1,-2,-1,-1,-2,1,1,-2,1,-1,-2});
        VertexBuffer vertices=new VertexBuffer(); vertices.setPositions(points,1,null);
        vertices.setDefaultColor(0xffffffff); return vertices;
    }

    private static class Fixture {
        final Material material=new Material(); final Light light=new Light();
        final Transform normal=new Transform(); final VertexArray normals=new VertexArray(6,3,1);
        final VertexBuffer vertices=new VertexBuffer(); final ArrayList<Light> lights=new ArrayList<>();
        final float[] clip={-.8f,-.8f,0,1, .8f,-.8f,0,1, 0,.8f,0,1,
                -.6f,-.6f,0,1, .6f,-.6f,0,1, 0,.6f,0,1};
        Fixture() {
            normals.set(0,6,new byte[]{0,0,127,0,0,127,0,0,127,0,0,127,0,0,127,0,0,127});
            material.setColor(Material.AMBIENT|Material.SPECULAR|Material.EMISSIVE,0);
            material.setColor(Material.DIFFUSE,0xffffffff);
            light.setMode(Light.DIRECTIONAL); lights.add(light);
            VertexArray colors=new VertexArray(6,4,1);
            colors.set(0,6,new byte[]{-1,0,0,-1,0,-1,0,-128,0,0,-1,64, -1,-1,0,-1,0,-1,-1,-1,-1,0,-1,-1});
            vertices.setColors(colors);
        }
        int[] colors(int[] indices, boolean cache, int mode) {
            Triangle.cacheLighting=cache; int[] count={0};
            Triangle[] triangles=Triangle.fromVertAndTris(clip,new float[4][],material,mode,true,false,
                    clip,normals,normal,lights,new float[4],new float[]{0,0,-1,0},-1,
                    indices,count,PolygonMode.CULL_NONE,vertices,false,false,false);
            assertEquals(indices.length/3,count[0]);
            int[] out=new int[count[0]*3];
            for (int i=0; i<count[0]; i++) {
                out[i*3]=triangles[i].colorA(); out[i*3+1]=triangles[i].colorB(); out[i*3+2]=triangles[i].colorC();
            }
            return out;
        }
    }
}
