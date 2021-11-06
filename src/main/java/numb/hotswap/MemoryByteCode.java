package numb.hotswap;


import javax.tools.SimpleJavaFileObject;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.URI;


public class MemoryByteCode extends SimpleJavaFileObject {
    private static final char PKG_SEPARATOR = '.';
    private static final char DIR_SEPARATOR = '/';

    private ByteArrayOutputStream byteArrayOutputStream;

    public MemoryByteCode(String className) {
        super(URI.create("byte:///" + className.replace(PKG_SEPARATOR, DIR_SEPARATOR)
                + Kind.CLASS.extension), Kind.CLASS);
    }

    @Override
    public OutputStream openOutputStream() {
        if (byteArrayOutputStream == null) {
            byteArrayOutputStream = new ByteArrayOutputStream();
        }
        return byteArrayOutputStream;
    }

    public byte[] getByteCode() {
        return byteArrayOutputStream.toByteArray();
    }

    public String getClassName() {
        String className = getName();
        className = className.replace(DIR_SEPARATOR, PKG_SEPARATOR);
        className = className.substring(1, className.indexOf(Kind.CLASS.extension));
        return className;
    }

}
