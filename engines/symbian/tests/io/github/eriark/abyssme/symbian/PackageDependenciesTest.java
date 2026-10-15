package io.github.eriark.abyssme.symbian;

import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.nio.file.Files;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

public class PackageDependenciesTest {
    @Test public void readsSizePrefixedBodiesIncludingEmbeddedPrerequisites() throws Exception {
        File file=File.createTempFile("stored-controller-",".ctl",InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir());
        try {
            byte[] controller=SisComponentsTest.controller(100,1,new byte[][]{SisComponentsTest.need(200,0,-1)},
                    SisComponentsTest.controller(300,1,new byte[][]{SisComponentsTest.need(400,0,-1)}));
            byte[] body=Arrays.copyOfRange(controller,8,controller.length);
            for(byte[] payload:new byte[][]{body,SisComponentsTest.join(SisComponentsTest.ints(body.length),body)}) {
                Files.write(file.toPath(),SisComponentsTest.join(SisComponentsTest.ints(payload.length),payload));
                assertTrue(PackageDependencies.needs(file.getPath(),100,200));
                assertTrue(PackageDependencies.needs(file.getPath(),100,400));
                assertFalse(PackageDependencies.needs(file.getPath(),100,500));
                try { PackageDependencies.needs(file.getPath(),999,200); fail(); } catch(IOException expected) { }
            }
        } finally { Files.deleteIfExists(file.toPath()); }
    }
    @Test public void rejectsTruncatedOversizedAndWrongControllerFormat() throws Exception {
        File file=File.createTempFile("bad-controller-",".ctl",InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir());
        try {
            byte[] controller=SisComponentsTest.controller(100,1,new byte[0][]);
            for(byte[] invalid:new byte[][]{new byte[0],SisComponentsTest.ints(Integer.MAX_VALUE,0,0),controller,
                    SisComponentsTest.join(SisComponentsTest.ints(controller.length),controller),
                    SisComponentsTest.join(SisComponentsTest.ints(controller.length-8),Arrays.copyOfRange(controller,8,controller.length-1))}) {
                Files.write(file.toPath(),invalid);
                try { PackageDependencies.needs(file.getPath(),100,200); fail("Invalid controller accepted"); } catch(IOException expected) { }
            }
            try(RandomAccessFile out=new RandomAccessFile(file,"rw")) { out.setLength(SisMetadata.LIMIT+5L); }
            try { PackageDependencies.needs(file.getPath(),100,200); fail(); } catch(IOException expected) { }
        } finally { Files.deleteIfExists(file.toPath()); }
    }
}
