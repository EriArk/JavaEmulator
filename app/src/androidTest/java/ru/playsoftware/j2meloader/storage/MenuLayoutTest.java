package ru.playsoftware.j2meloader.storage;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.view.*;
import androidx.preference.PreferenceManager;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import static org.junit.Assert.*;
import ru.playsoftware.j2meloader.ui.MenuLayout;
import ru.playsoftware.j2meloader.settings.SettingsActivity;
import ru.playsoftware.j2meloader.input.*;
import ru.playsoftware.j2meloader.config.ProfileModel;
public class MenuLayoutTest {
 private SharedPreferences prefs;private String oldOrientation;private int oldMargin;
 @Before public void setup(){prefs=PreferenceManager.getDefaultSharedPreferences(InstrumentationRegistry.getInstrumentation().getTargetContext());oldOrientation=prefs.getString(MenuLayout.ORIENTATION,"auto");oldMargin=prefs.getInt(MenuLayout.MARGIN,0);prefs.edit().putString(MenuLayout.ORIENTATION,"portrait").putInt(MenuLayout.MARGIN,160).commit();}
 @After public void cleanup(){prefs.edit().putString(MenuLayout.ORIENTATION,oldOrientation).putInt(MenuLayout.MARGIN,oldMargin).commit();}
 private void idle(){InstrumentationRegistry.getInstrumentation().waitForIdleSync();android.os.SystemClock.sleep(450);}
 @Test public void orientationAndMarginPersistAndLandscapeClearsInset()throws Exception{
  try(ActivityScenario<SettingsActivity> scenario=ActivityScenario.launch(SettingsActivity.class)){
   idle();scenario.onActivity(a->{assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT,a.getRequestedOrientation());assertTrue(a.findViewById(android.R.id.content).getPaddingBottom()>0);});
   scenario.recreate();idle();scenario.onActivity(a->assertTrue(a.findViewById(android.R.id.content).getPaddingBottom()>0));
   scenario.onActivity(a->{prefs.edit().putString(MenuLayout.ORIENTATION,"landscape").commit();MenuLayout.apply(a);});idle();
   scenario.onActivity(a->{assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,a.getRequestedOrientation());assertEquals(0,MenuLayout.inset(a));assertEquals(0,a.findViewById(android.R.id.content).getPaddingBottom());});
  }
 }
 @Test public void settingsExposeUsableSliderAndReset()throws Exception{
  try(ActivityScenario<SettingsActivity> scenario=ActivityScenario.launch(SettingsActivity.class)){
   idle();scenario.onActivity(a->{ru.playsoftware.j2meloader.settings.SettingsFragment f=(ru.playsoftware.j2meloader.settings.SettingsFragment)a.getSupportFragmentManager().findFragmentById(ru.playsoftware.j2meloader.R.id.settings_content);a.onPreferenceStartScreen(f,f.findPreference("layout"));});idle();
   scenario.onActivity(a->{android.widget.SeekBar bar=a.findViewById(androidx.preference.R.id.seekbar);assertNotNull("Margin must be adjustable in the actual settings screen",bar);assertTrue(bar.isShown());
    ru.playsoftware.j2meloader.settings.SettingsFragment f=(ru.playsoftware.j2meloader.settings.SettingsFragment)a.getSupportFragmentManager().findFragmentById(ru.playsoftware.j2meloader.R.id.settings_content);
    androidx.preference.Preference reset=f.findPreference("pref_reset_menu_margin");assertTrue(reset.getOnPreferenceClickListener().onPreferenceClick(reset));assertEquals(0,MenuLayout.margin(a));
   });
  }
 }
 @Test public void menuMapperAndConfirmationsStayAboveMargin()throws Exception{
  try(ActivityScenario<SettingsActivity> scenario=ActivityScenario.launch(SettingsActivity.class)){
   idle();final GameMenuDialog[] menu={null};final MapperDialog[] mapper={null};final androidx.appcompat.app.AlertDialog[] alert={null};
   scenario.onActivity(a->{menu[0]=new GameMenuDialog(a,"Safe area");menu[0].useEdgePanel();menu[0].show();menu[0].page("Safe area");for(int i=0;i<25;i++)menu[0].action("Action "+i,0,()->{});});idle();
   scenario.onActivity(a->{within(menu[0].getWindow(),a);menu[0].dismiss();mapper[0]=new MapperDialog(a,"Mapping",new ProfileModel(new java.io.File(a.getCacheDir(),"layout-test")),()->{});mapper[0].show();});idle();
   scenario.onActivity(a->{within(mapper[0].getWindow(),a);mapper[0].dismiss();alert[0]=new ru.playsoftware.j2meloader.ui.MenuAlertBuilder(a).setTitle("Confirm").setMessage("Keep menu actions visible").setPositiveButton("Apply",null).show();});idle();
   scenario.onActivity(a->{within(alert[0].getWindow(),a);alert[0].dismiss();});
  }
 }
 private void within(Window w,android.app.Activity a){int[] xy=new int[2];View decor=w.getDecorView();decor.getLocationOnScreen(xy);assertTrue("Dialog bottom="+(xy[1]+decor.getHeight()),xy[1]+decor.getHeight()<=a.getResources().getDisplayMetrics().heightPixels-MenuLayout.inset(a)+5);}
}
