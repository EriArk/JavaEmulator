package javax.microedition.io;
import java.io.IOException;
public interface HttpConnection extends InputConnection {
    String getHeaderField(String name) throws IOException;
}
