package io.github.eriark.abyssme.symbian;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.*;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.junit.*;
import ru.playsoftware.j2meloader.BuildConfig;
import ru.playsoftware.j2meloader.MainActivity;
import ru.playsoftware.j2meloader.input.TouchDeck;
import static org.junit.Assert.*;

public final class SymbianTouchTest {
    @Test public void letterboxIsIgnoredAndDragIsClampedAndReleased() {
        List<String> events = new ArrayList<>();
        SymbianTouch touch = new SymbianTouch((x,y,z,a,p) -> events.add(x+","+y+","+z+","+a+","+p));
        RectF bounds = new RectF(20,40,100,200);
        assertFalse(touch.down(7,19,80,1,bounds));
        assertFalse(touch.down(7,100,80,1,bounds));
        assertFalse(touch.down(7,50,200,1,bounds));
        assertFalse(touch.down(7,50,80,1,null));
        touch.move(7,50,80,1); touch.up(7);
        assertTrue(events.isEmpty());
        assertTrue(touch.down(7,50,80,.5f,bounds));
        touch.move(7,50,80,1);
        touch.move(7,-50,500,1);
        touch.cancel(); touch.cancel();
        assertEquals(Arrays.asList("50,80,50,0,0","20,199,100,1,0","20,199,0,2,0"),events);
    }
    @Test public void androidIdsUseBoundedGuestSlotsWithoutPromotingIgnoredFingers() {
        List<Integer> slots = new ArrayList<>();
        List<Integer> actions = new ArrayList<>();
        SymbianTouch touch = new SymbianTouch((x,y,z,a,p) -> {slots.add(p);actions.add(a);});
        RectF bounds = new RectF(0,0,100,100);
        for (int i=0;i<8;i++) assertTrue(touch.down(31-i,50,50,1,bounds));
        assertFalse(touch.down(4,50,50,1,bounds));
        touch.up(31); touch.move(4,60,60,1);
        assertTrue(touch.down(3,50,50,1,bounds));
        assertEquals(Arrays.asList(0,1,2,3,4,5,6,7,0,0),slots);
        touch.cancel();
        assertEquals(9,Collections.frequency(actions,SymbianTouch.DOWN));
        assertEquals(9,Collections.frequency(actions,SymbianTouch.UP));
    }
    @Test public void viewportIsFrozenForTheGesture() {
        List<Integer> xs = new ArrayList<>();
        SymbianTouch touch = new SymbianTouch((x,y,z,a,p) -> xs.add(x));
        RectF bounds = new RectF(10,10,100,100);
        assertTrue(touch.down(1,20,20,1,bounds));
        bounds.set(0,0,500,500);
        touch.move(1,200,20,1); touch.up(1);
        assertEquals(Arrays.asList(20,99,99),xs);
    }

    @Test public void screenTransformsAndVirtualKeysKeepIndependentOwnership() throws Exception {
        Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
        Context context=instrumentation.getTargetContext();
        Activity activity=instrumentation.startActivitySync(new Intent(context,MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        String seed=UUID.randomUUID().toString().replace("-",""); String system=seed+seed;
        SymbianSessionUi[] current=new SymbianSessionUi[1];
        try {
            instrumentation.runOnMainSync(() -> {
                TextureView surface=new TextureView(activity);
                List<int[]> touches=new ArrayList<>(); List<Integer> keys=new ArrayList<>();
                SymbianSessionUi.Host host=new SymbianSessionUi.Host() {
                    public RectF touchBounds() {
                        int w=surface.getWidth(),h=surface.getHeight();
                        int scale=current[0].scale();
                        float width=scale==0?176:scale==1?Math.min(w,h*176f/208):w;
                        float height=scale==0?208:scale==1?width*208/176:h;
                        return new RectF((w-width)/2,(h-height)/2,(w+width)/2,(h+height)/2);
                    }
                    public void touch(int x,int y,int z,int a,int p){touches.add(new int[]{x,y,z,a,p});}
                    public void key(int scan,boolean down){keys.add(down?scan:-scan);}
                    public void pause(boolean paused){}
                    public void display(int scale){}
                    public void leave(){}
                    public void restart(){}
                    public Bitmap capture(){return null;}
                };
                try { current[0]=new SymbianSessionUi(activity,surface,system,1,"Touch fixture",host); }
                catch(IOException error){throw new AssertionError(error);}
                SymbianSessionUi session=current[0];
                boolean cameraVisible=ru.playsoftware.j2meloader.util.Screenshots.isButtonVisible(activity);
                try {
                    for (boolean show:new boolean[]{false,true,false}) {
                        session.visible(false);
                        ru.playsoftware.j2meloader.util.Screenshots.setButtonVisible(activity,show);
                        session.visible(true);
                        assertEquals(show?View.VISIBLE:View.GONE,session.root.findViewById(ru.playsoftware.j2meloader.R.id.game_screenshot_button).getVisibility());
                        assertEquals(View.VISIBLE,session.root.findViewById(ru.playsoftware.j2meloader.R.id.game_menu_button).getVisibility());
                    }
                } finally {ru.playsoftware.j2meloader.util.Screenshots.setButtonVisible(activity,cameraVisible);session.visible(true);}
                View deck=session.root.getChildAt(1);
                for (int[] size:new int[][]{{480,800},{800,480}}) for (int layout=0;layout<2;layout++)
                    for (int scale=0;scale<3;scale++) for (int rotation=0;rotation<4;rotation++) {
                        session.profile.touchLayout=layout; session.profile.screenScaleType=scale;
                        session.profile.screenRotation=rotation*90;
                        measure(session.root,size[0],size[1]); session.apply(); measure(session.root,size[0],size[1]);
                        touches.clear(); keys.clear();
                        float[] point=at(surface,surface.getWidth()*.5f,surface.getHeight()*.5f);
                        send(deck,MotionEvent.ACTION_DOWN,new int[]{31},new float[][]{point});
                        assertEquals("One down at rotation "+rotation,1,touches.size());
                        assertEquals(surface.getWidth()/2,touches.get(0)[0],1);
                        assertEquals(surface.getHeight()/2,touches.get(0)[1],1);
                        assertEquals(0,touches.get(0)[4]);
                        RectF bounds=host.touchBounds();
                        float x=Math.max(0,bounds.left)+(Math.min(surface.getWidth(),bounds.right)-Math.max(0,bounds.left))*.7f;
                        float y=Math.max(0,bounds.top)+(Math.min(surface.getHeight(),bounds.bottom)-Math.max(0,bounds.top))*.6f;
                        float[] moved=at(surface,x,y);
                        send(deck,MotionEvent.ACTION_MOVE,new int[]{31},new float[][]{moved});
                        assertEquals((int)x,touches.get(1)[0],1); assertEquals((int)y,touches.get(1)[1],1);
                        session.focused(false); session.focused(true);
                        assertEquals(SymbianTouch.UP,touches.get(2)[3]); assertEquals(0,touches.get(2)[2]);
                        assertTrue(keys.isEmpty());
                        send(deck,MotionEvent.ACTION_MOVE,new int[]{31},new float[][]{point});
                        send(deck,MotionEvent.ACTION_UP,new int[]{31},new float[][]{point});
                        assertEquals("No revived pointer after focus loss",3,touches.size());

                        if (!BuildConfig.HANDHELD_MODE) {
                            TouchDeck phone=new TouchDeck(session.profile,activity.getResources().getDisplayMetrics().density,new TouchDeck.Sink() {
                                public void press(int k){} public void release(int k){} public void repeat(int k){} public void invalidate(){}
                                public void feedback(){}
                            });
                            phone.layout(size[0],size[1]);
                            RectF button=phone.getKeys().get(0).bounds;
                            float[] buttonPoint={button.centerX(),button.centerY()};
                            touches.clear(); keys.clear();
                            send(deck,MotionEvent.ACTION_DOWN,new int[]{31},new float[][]{buttonPoint});
                            assertFalse(keys.isEmpty()); assertTrue(touches.isEmpty());
                            send(deck,MotionEvent.ACTION_POINTER_DOWN | (1<<8),new int[]{31,23},new float[][]{buttonPoint,point});
                            assertEquals(1,touches.size()); assertEquals(0,touches.get(0)[4]);
                            send(deck,MotionEvent.ACTION_MOVE,new int[]{31,23},new float[][]{point,buttonPoint});
                            assertEquals("Button finger never becomes a guest finger",1,touches.stream().filter(t->t[3]==0).count());
                            session.clear();
                            assertEquals(SymbianTouch.UP,touches.get(touches.size()-1)[3]);
                            assertEquals("Button released on leaving key area",0,keys.stream().mapToInt(Integer::intValue).sum());
                        }
                    }
                session.close();
            });
        } finally {
            instrumentation.runOnMainSync(() -> {if(current[0]!=null)current[0].close();activity.finish();});
            Path directory=new File(context.getFilesDir(),"symbian-profiles/"+system).toPath();
            if(Files.exists(directory))try(java.util.stream.Stream<Path> paths=Files.walk(directory)) {
                for(Path path:(Iterable<Path>)paths.sorted(Comparator.reverseOrder())::iterator)Files.delete(path);
            }
        }
    }
    private static void measure(View view,int w,int h) {
        view.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));
        view.layout(0,0,w,h);
    }
    private static float[] at(View surface,float x,float y) {
        float[] point={x,y}; surface.getMatrix().mapPoints(point);
        point[0]+=surface.getLeft(); point[1]+=surface.getTop(); return point;
    }
    private static void send(View view,int action,int[] ids,float[][] points) {
        MotionEvent.PointerProperties[] properties=new MotionEvent.PointerProperties[ids.length];
        MotionEvent.PointerCoords[] coords=new MotionEvent.PointerCoords[ids.length];
        for(int i=0;i<ids.length;i++) {
            properties[i]=new MotionEvent.PointerProperties(); properties[i].id=ids[i]; properties[i].toolType=MotionEvent.TOOL_TYPE_FINGER;
            coords[i]=new MotionEvent.PointerCoords();coords[i].x=points[i][0];coords[i].y=points[i][1];coords[i].pressure=1;coords[i].size=1;
        }
        long now=SystemClock.uptimeMillis();
        MotionEvent event=MotionEvent.obtain(now,now,action,ids.length,properties,coords,0,0,1,1,0,0,InputDevice.SOURCE_TOUCHSCREEN,0);
        try {assertTrue(view.dispatchTouchEvent(event));} finally {event.recycle();}
    }
}
