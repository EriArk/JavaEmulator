package javax.microedition.util;
import java.io.InputStream;
public final class ContextHolder {
    public static InputStream getResourceAsStream(Class<?> owner, String name) {
        return ContextHolder.class.getResourceAsStream(name.startsWith("/") ? name : "/" + name);
    }
}
