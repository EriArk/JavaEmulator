package javax.microedition.io;
import java.io.IOException;
import java.io.InputStream;
public interface InputConnection {
    InputStream openInputStream() throws IOException;
    void close() throws IOException;
}
