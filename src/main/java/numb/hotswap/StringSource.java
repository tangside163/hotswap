package numb.hotswap;


import javax.tools.SimpleJavaFileObject;
import java.net.URI;

public class StringSource extends SimpleJavaFileObject {
    private final String contents;

    public StringSource(String className, String contents) {
        super(URI.create("string:///" + className.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
        this.contents = contents;
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) {
        return contents;
    }

}
