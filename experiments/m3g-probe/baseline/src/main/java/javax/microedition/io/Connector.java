package javax.microedition.io;
import java.io.IOException;
public final class Connector {
    public static InputConnection open(String uri) throws IOException {
        throw new IOException("Network access is outside the standalone probe");
    }
}
