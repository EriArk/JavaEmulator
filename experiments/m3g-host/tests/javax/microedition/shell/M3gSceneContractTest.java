package javax.microedition.shell;

import javax.microedition.lcdui.Image;
import javax.microedition.m3g.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/** Public API scenes shared with the unchanged native reference. */
public class M3gSceneContractTest {
    protected Graphics3D g;
    @Before public void setup() { Graphics3D.initGraphics3D(); g = Graphics3D.getInstance(); }
    @After public void release() { g.releaseTarget(); }

    @Test public void perspectivePreservesLargeDepthRange() {
        Camera c = new Camera(); c.setPerspective(60, 1.5f, .1f, 50000);
        float[] values = new float[4]; c.getProjection(values);
        assertArrayEquals(new float[]{60, 1.5f, .1f, 50000}, values, 0);
    }
    @Test public void perspectiveCanReverseNearAndFar() {
        Camera c = new Camera(); c.setPerspective(60, 1, 10, 1);
        Transform t = new Transform(); c.getProjection(t);
        float[] points = {0,0,-10,1, 0,0,-1,1}; t.transform(points);
        assertEquals(-1, points[2]/points[3], .0001f);
        assertEquals(1, points[6]/points[7], .0001f);
    }
    @Test public void genericProjectionCopiesItsMatrix() {
        Camera c = new Camera(); Transform t = new Transform(); t.postScale(2,3,4);
        c.setGeneric(t); t.setIdentity(); Transform out = new Transform(); c.getProjection(out);
        float[] p = {1,1,1,1}; out.transform(p); assertArrayEquals(new float[]{2,3,4,1}, p, 0);
    }
    @Test public void collapsedCameraVolumeRendersNothing() {
        for (boolean perspective : new boolean[]{false,true}) {
            Camera c = camera();
            if (perspective) c.setPerspective(60,1,2,2); else c.setParallel(2,1,2,2);
            assertEquals(0xff000000, render(mesh(0xffffffff), c));
        }
    }
    @Test public void collapsedCameraAlsoClipsSprites() {
        Camera c = camera(); c.setParallel(2,1,2,2);
        Sprite3D sprite = new Sprite3D(true, new Image2D(Image2D.RGB,1,1,new byte[]{-1,-1,-1}), new Appearance());
        Group group = new Group(); sprite.setTranslation(0,0,-2); group.addChild(sprite);
        assertEquals(0xff000000, render(group,c));
    }
    @Test public void largeRetainedQueueKeepsEarlierMeshes() {
        Group group = new Group(); group.addChild(mesh(0xffef6020));
        for (int i=0;i<140;i++) { Mesh m=mesh(0xff2040ef); m.setTranslation(100+i,0,0); group.addChild(m); }
        assertEquals(0xffef6020,render(group,camera()));
    }
    @Test public void disabledParentExcludesLights() {
        World w = world(); Group hidden = new Group(); hidden.setRenderingEnable(false);
        hidden.addChild(ambient()); w.addChild(hidden); w.addChild(litMesh());
        assertEquals(0xff000000,render(w,null));
    }
    @Test public void enabledParentIncludesLights() {
        World w=world(); Group branch=new Group(); branch.addChild(ambient()); w.addChild(branch); w.addChild(litMesh());
        assertEquals(0xffffffff,render(w,null));
    }
    @Test public void disabledWorldExcludesLights() {
        World w=world(); w.addChild(ambient()); w.addChild(litMesh()); w.setRenderingEnable(false);
        assertEquals(0xff000000,render(w,null)); assertEquals(0,g.getLightCount());
    }
    @Test public void lightScopesDoNotLeakAcrossMeshes() {
        Light l=ambient(); l.setScope(2); g.addLight(l,null);
        Mesh m=litMesh(); m.setScope(1); assertEquals(0xff000000,render(m,camera()));
        m.setScope(2); assertEquals(0xffffffff,render(m,camera()));
    }
    @Test public void parentScopeDoesNotHideChildGeometry() {
        Group parent=new Group(); parent.setScope(0); parent.addChild(mesh(0xff2080ef));
        assertEquals(0xff2080ef,render(parent,camera()));
    }
    @Test public void cameraScopeClipsMesh() {
        Camera c=camera(); c.setScope(2); Mesh m=mesh(0xff2080ef); m.setScope(1);
        assertEquals(0xff000000,render(m,c));
    }
    @Test public void removedLightSlotDoesNotCrashRendering() {
        g.addLight(ambient(),null); g.addLight(ambient(),null); g.setLight(0,null,null);
        assertEquals(2,g.getLightCount()); assertNull(g.getLight(0,null));
        assertEquals(0xffffffff,render(litMesh(),camera()));
    }
    @Test public void setLightCopiesCallerTransform() {
        g.addLight(ambient(),null); Transform t=new Transform(); t.postTranslate(1,2,3);
        g.setLight(0,ambient(),t); t.setIdentity(); Transform out=new Transform(); g.getLight(0,out);
        float[] point={0,0,0,1}; out.transform(point); assertArrayEquals(new float[]{1,2,3,1},point,0);
    }
    @Test public void defaultSpotMatchesExplicitFortyFiveDegrees() {
        Light a=new Light(); a.setMode(Light.SPOT);
        Light b=new Light(); b.setMode(Light.SPOT); b.setSpotAngle(45);
        assertEquals(spotPixel(b),spotPixel(a));
    }
    @Test public void animatedSpotMatchesSetter() {
        Light a=new Light(); a.setMode(Light.SPOT); a.setSpotAngle(90);
        animate(a,AnimationTrack.SPOT_ANGLE,10);
        Light b=new Light(); b.setMode(Light.SPOT); b.setSpotAngle(10);
        assertEquals(spotPixel(b),spotPixel(a));
    }
    @Test public void duplicateSpotKeepsItsCone() {
        Light a=new Light(); a.setMode(Light.SPOT); a.setSpotAngle(10);
        assertEquals(spotPixel(a),spotPixel((Light)a.duplicate()));
    }
    @Test public void nearPlaneClipsCrossingTriangleWithoutDroppingVisiblePart() {
        Mesh m=mesh(0xff20ef80); VertexArray p=m.getVertexBuffer().getPositions(null);
        p.set(0,4,new short[]{-1,1,0, -1,-1,-2, 1,1,-2, 1,-1,-2});
        assertEquals(0xff20ef80,render(m,camera()));
    }
    @Test public void oppositeWindingCanBeCulled() {
        Mesh m=mesh(0xff20ef80); PolygonMode p=m.getAppearance(0).getPolygonMode();
        p.setCulling(PolygonMode.CULL_BACK); p.setWinding(PolygonMode.WINDING_CCW);
        int front=render(m,camera()); p.setWinding(PolygonMode.WINDING_CW); int back=render(m,camera());
        assertNotEquals(front,back); assertTrue(front==0xff20ef80 || back==0xff20ef80);
    }
    @Test public void animatedMaterialColorsClampAndRound() {
        Material m=new Material();
        for(int[] pair:new int[][]{{AnimationTrack.AMBIENT_COLOR,Material.AMBIENT},{AnimationTrack.DIFFUSE_COLOR,Material.DIFFUSE},
                {AnimationTrack.EMISSIVE_COLOR,Material.EMISSIVE},{AnimationTrack.SPECULAR_COLOR,Material.SPECULAR}}) {
            animate(m,pair[0],1.2f,-.2f,.5f); assertEquals(0xff0080,m.getColor(pair[1])&0xffffff);
        }
    }
    @Test public void animatedMaterialAlphaClamps() {
        Material m=new Material(); animate(m,AnimationTrack.ALPHA,1.2f); assertEquals(255,m.getColor(Material.DIFFUSE)>>>24);
    }
    @Test public void animatedLightColorClampsAndRounds() {
        Light l=new Light(); animate(l,AnimationTrack.COLOR,1.2f,-.2f,.5f); assertEquals(0xff0080,l.getColor());
    }
    @Test public void animatedFogColorClampsAndRounds() {
        Fog f=new Fog(); animate(f,AnimationTrack.COLOR,1.2f,-.2f,.5f); assertEquals(0xff0080,f.getColor());
    }
    @Test public void animatedTextureColorClampsAndRounds() {
        Texture2D t=new Texture2D(new Image2D(Image2D.RGB,1,1,new byte[3]));
        animate(t,AnimationTrack.COLOR,1.2f,-.2f,.5f); assertEquals(0xff0080,t.getBlendColor());
    }
    @Test public void animatedBackgroundColorClampsAndRounds() {
        Background b=new Background(); animate(b,AnimationTrack.COLOR,1.2f,-.2f,.5f); assertEquals(0xff0080,b.getColor()&0xffffff);
    }
    @Test public void animatedVertexColorClampsAndRounds() {
        VertexBuffer b=new VertexBuffer(); animate(b,AnimationTrack.COLOR,1.2f,-.2f,.5f); assertEquals(0xff0080,b.getDefaultColor()&0xffffff);
    }
    private int spotPixel(Light light) {
        g.resetLights(); Transform t=new Transform(); t.postTranslate(4,0,0); g.addLight(light,t);
        Mesh m=litMesh(); Material mat=m.getAppearance(0).getMaterial(); mat.setColor(Material.AMBIENT,0);
        mat.setColor(Material.DIFFUSE,0xffffffff); return render(m,camera());
    }
    public static void animate(Object3D object,int property,float... values) {
        KeyframeSequence keys=new KeyframeSequence(1,values.length,KeyframeSequence.STEP);
        keys.setKeyframe(0,0,values); keys.setDuration(1); AnimationTrack track=new AnimationTrack(keys,property);
        track.setController(new AnimationController()); object.addAnimationTrack(track); object.animate(0);
    }
    protected int render(Node node,Camera camera) {
        Image image=Image.createImage(32,32); g.bindTarget(image.getGraphics(),true,0); g.setViewport(0,0,32,32);
        if(node instanceof World) g.render((World)node);
        else { Background bg=new Background(); bg.setColor(0xff000000); g.clear(bg); g.setCamera(camera,null); g.render(node,new Transform()); }
        g.releaseTarget(); return image.getBitmap().getPixel(16,17);
    }
    public static Camera camera() { Camera c=new Camera(); c.setParallel(2,1,1,10); return c; }
    public static World world() {
        World w=new World(); Camera c=camera(); w.addChild(c); w.setActiveCamera(c);
        Background bg=new Background(); bg.setColor(0xff000000); w.setBackground(bg); return w;
    }
    public static Light ambient() { Light l=new Light(); l.setMode(Light.AMBIENT); return l; }
    public static Mesh litMesh() {
        Mesh m=mesh(0xffffffff); VertexArray normals=new VertexArray(4,3,1);
        normals.set(0,4,new byte[]{0,0,127,0,0,127,0,0,127,0,0,127}); m.getVertexBuffer().setNormals(normals);
        Material mat=new Material(); mat.setColor(Material.AMBIENT,0xffffff); mat.setColor(Material.DIFFUSE,0xff000000);
        m.getAppearance(0).setMaterial(mat); return m;
    }
    public static Mesh mesh(int color) {
        VertexArray p=new VertexArray(4,3,2); p.set(0,4,new short[]{-1,1,-2,-1,-1,-2,1,1,-2,1,-1,-2});
        VertexBuffer b=new VertexBuffer(); b.setPositions(p,1,null); b.setDefaultColor(color);
        Appearance a=new Appearance(); PolygonMode pm=new PolygonMode(); pm.setCulling(PolygonMode.CULL_NONE); a.setPolygonMode(pm);
        return new Mesh(b,new TriangleStripArray(0,new int[]{4}),a);
    }
}
