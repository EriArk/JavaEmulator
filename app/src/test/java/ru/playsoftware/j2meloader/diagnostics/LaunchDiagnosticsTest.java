package ru.playsoftware.j2meloader.diagnostics;

import java.lang.reflect.InvocationTargetException;
import org.junit.Test;
import static org.junit.Assert.*;

public class LaunchDiagnosticsTest {
    @Test public void constructorFailureShowsGuestTypeAndMessage() {
        Throwable cause = new ArrayIndexOutOfBoundsException("length=348; index=348");
        assertEquals(cause.toString(), LaunchDiagnostics.describeFailure(
                new InvocationTargetException(new InvocationTargetException(cause))));
    }

    @Test public void ordinaryFailureKeepsItsContext() {
        Throwable error = new IllegalStateException("Could not read save", new Exception("disk"));
        assertEquals(error.toString(), LaunchDiagnostics.describeFailure(error));
    }

    @Test public void emptyReflectionWrapperIsStillReportable() {
        Throwable error = new InvocationTargetException(null);
        assertEquals(error.toString(), LaunchDiagnostics.describeFailure(error));
    }
}
