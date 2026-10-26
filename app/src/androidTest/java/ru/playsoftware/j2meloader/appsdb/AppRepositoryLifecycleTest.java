package ru.playsoftware.j2meloader.appsdb;

import android.app.Application;
import androidx.preference.PreferenceManager;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import ru.playsoftware.j2meloader.applist.AppItem;
import ru.playsoftware.j2meloader.applist.AppListModel;
import ru.playsoftware.j2meloader.config.Config;
import static org.junit.Assert.*;

public class AppRepositoryLifecycleTest {
    @Test public void closingDrainsAcceptedWritesAndFencesLatePreferenceCallbacks() throws Exception {
        Application app=(Application)InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
        String original=Config.getEmulatorDir();
        Method init=Config.class.getDeclaredMethod("initDirs",String.class);init.setAccessible(true);
        Field executorField=AppRepository.class.getDeclaredField("databaseExecutor");executorField.setAccessible(true);
        try {
            for(int round=0;round<12;round++) {
                File root=Files.createTempDirectory(app.getCacheDir().toPath(),"repository-close-").toFile();
                init.invoke(null,root.getPath());
                AppRepository[] repository={null};
                InstrumentationRegistry.getInstrumentation().runOnMainSync(()->repository[0]=new AppListModel(app).getAppRepository());
                ExecutorService executor=(ExecutorService)executorField.get(repository[0]);
                CountDownLatch started=new CountDownLatch(1), release=new CountDownLatch(1);
                executor.execute(()->{started.countDown();try{release.await(10,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}});
                try {
                    assertTrue(started.await(10,TimeUnit.SECONDS));
                    AppItem item=new AppItem("accepted","Accepted write","Fixture","1");
                    item.setPreparationState("indexed");
                    repository[0].insert(item);
                    repository[0].close();repository[0].close();
                    repository[0].onSharedPreferenceChanged(PreferenceManager.getDefaultSharedPreferences(app),
                            ru.playsoftware.j2meloader.util.Constants.PREF_APP_SORT);
                } finally {release.countDown();repository[0].close();}
                assertTrue("Database worker did not finish",executor.awaitTermination(15,TimeUnit.SECONDS));
                try(AppDatabase check=AppDatabase.open(app,root.getPath())) {
                    assertNotNull("Accepted write lost on close",check.appItemDao().getByPath("accepted"));
                }
            }
        } finally {init.invoke(null,original);}
    }
}
