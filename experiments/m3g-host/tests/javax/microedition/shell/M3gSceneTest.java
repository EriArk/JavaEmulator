package javax.microedition.shell;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import javax.microedition.m3g.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Scene graph and picking contracts shared by the native and replacement engines. */
@RunWith(AndroidJUnit4.class)
public class M3gSceneTest {
    private Group scene;
    private Mesh mesh;

    @Before public void setup() {
        Graphics3D.initGraphics3D();
        scene = new Group();
        mesh = triangle();
        scene.addChild(mesh);
    }

    @Test public void rayReturnsNearestMeshAndDistance() {
        Mesh farther = triangle();
        farther.setTranslation(0, 0, -2);
        scene.addChild(farther);
        RayIntersection hit = new RayIntersection();
        assertTrue(scene.pick(-1, 0, 0, 0, 0, 0, -1, hit));
        assertSame(mesh, hit.getIntersected());
        assertEquals(2, hit.getDistance(), 0.001f);
        assertEquals(0, hit.getSubmeshIndex());
        assertEquals(1, hit.getNormalZ(), 0.001f);
    }

    @Test public void pickingRespectsScopeAndParentEnable() {
        mesh.setScope(2);
        assertFalse(scene.pick(1, 0, 0, 0, 0, 0, -1, null));
        assertTrue(scene.pick(2, 0, 0, 0, 0, 0, -1, null));
        scene.setPickingEnable(false);
        assertFalse(scene.pick(2, 0, 0, 0, 0, 0, -1, null));
    }

    @Test public void screenPickUsesTranslatedCamera() {
        Camera camera = new Camera();
        camera.setPerspective(60, 1, 1, 10);
        camera.setTranslation(3, 0, 0);
        mesh.setTranslation(3, 0, 0);
        scene.addChild(camera);
        RayIntersection hit = new RayIntersection();
        assertTrue(scene.pick(-1, 0.5f, 0.5f, camera, hit));
        assertSame(mesh, hit.getIntersected());
        assertFalse(scene.pick(-1, 0, 0, camera, null));
    }

    @Test public void groupDuplicateHasIndependentChildrenAndTransforms() {
        mesh.setUserID(42);
        Group copy = (Group) scene.duplicate();
        assertEquals(1, copy.getChildCount());
        assertNotSame(mesh, copy.getChild(0));
        assertSame(copy, copy.getChild(0).getParent());
        copy.getChild(0).setTranslation(5, 0, 0);
        float[] translation = new float[3];
        mesh.getTranslation(translation);
        assertArrayEquals(new float[3], translation, 0);
        assertSame(copy.getChild(0), copy.find(42));
        copy.removeChild(copy.getChild(0));
        assertEquals(1, scene.getChildCount());
        assertEquals(0, copy.getChildCount());
    }

    @Test public void transformToSiblingIncludesBothParents() {
        Group other = new Group();
        other.setTranslation(5, 2, 0);
        scene.addChild(other);
        mesh.setTranslation(1, 2, 0);
        Transform relative = new Transform();
        assertTrue(mesh.getTransformTo(other, relative));
        float[] point = {0,0,0,1};
        relative.transform(point);
        assertArrayEquals(new float[]{-4,0,0,1}, point, 0.001f);
    }

    @Test public void nonUnitPickingDirectionPreservesRayParameter() {
        RayIntersection hit = new RayIntersection();
        assertTrue(scene.pick(-1, 0, 0, 0, 0, 0, -2, hit));
        assertEquals(1, hit.getDistance(), 0.001f);
    }

    private Mesh triangle() {
        VertexArray positions = new VertexArray(3, 3, 2);
        positions.set(0, 3, new short[]{-1,-1,-2, 1,-1,-2, 0,1,-2});
        VertexBuffer buffer = new VertexBuffer();
        buffer.setPositions(positions, 1, null);
        Appearance style = new Appearance();
        PolygonMode polygon = new PolygonMode();
        polygon.setCulling(PolygonMode.CULL_NONE);
        style.setPolygonMode(polygon);
        return new Mesh(buffer, new TriangleStripArray(0, new int[]{3}), style);
    }
}
