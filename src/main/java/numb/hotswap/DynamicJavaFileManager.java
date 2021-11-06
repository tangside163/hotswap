package numb.hotswap;

import com.google.common.collect.Sets;
import org.springframework.boot.loader.LaunchedURLClassLoader;

import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import java.io.File;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;

public class DynamicJavaFileManager extends ForwardingJavaFileManager<JavaFileManager> {
    private static final Set<Location> superLocationNames = Sets.newHashSet(
            StandardLocation.PLATFORM_CLASS_PATH, StandardLocation.ANNOTATION_PROCESSOR_PATH);

    private final DynamicClassLoader classLoader;
    private final Map<String, MemoryByteCode> byteCodes = new HashMap<>();


    public DynamicJavaFileManager(JavaFileManager fileManager, DynamicClassLoader classLoader) {
        super(fileManager);
        this.classLoader = classLoader;
    }

    @Override
    public JavaFileObject getJavaFileForOutput(Location location, String className,
                                               JavaFileObject.Kind kind, FileObject sibling) throws IOException {
        if (kind == JavaFileObject.Kind.CLASS) {
            return byteCodes.computeIfAbsent(className, key -> {
                MemoryByteCode byteCode = new MemoryByteCode(className);
                classLoader.registerCompiledSource(byteCode);
                return byteCode;
            });
        } else {
            return super.getJavaFileForOutput(location, className, kind, sibling);
        }

    }

    @Override
    public ClassLoader getClassLoader(Location location) {
        if (location == StandardLocation.ANNOTATION_PROCESSOR_PATH) {
            return super.getClassLoader(location);
        }
        return classLoader;
    }

    @Override
    public String inferBinaryName(Location location, JavaFileObject file) {
        if (file instanceof CustomJavaFileObject) {
            return ((CustomJavaFileObject) file).binaryName();
        } else {
            return super.inferBinaryName(location, file);
        }
    }


    @Override
    public Iterable<JavaFileObject> list(Location location, String packageName, Set<JavaFileObject.Kind> kinds,
                                         boolean recurse) throws IOException {
        if(superLocationNames.contains(location)){
            return super.list(location, packageName, kinds, recurse);
        }

        if (location == StandardLocation.CLASS_PATH && kinds.contains(JavaFileObject.Kind.CLASS)) {
            Set<JavaFileObject> javaFileObjects = this.find(packageName);
            if (javaFileObjects!=null && javaFileObjects.size()>0) {
                return javaFileObjects;
            }
        }

        return super.list(location, packageName, kinds, recurse);
    }


    public Set<JavaFileObject> find(String packageName) throws IOException {
        String javaPackageName = packageName.replaceAll("\\.", "/");

        Set<JavaFileObject> result = new HashSet<>();

        Enumeration<URL> urlEnumeration = classLoader.getResources(javaPackageName);
        while (urlEnumeration.hasMoreElements()) {
            URL packageFolderURL = urlEnumeration.nextElement();
            result.addAll(listUnder(packageName, packageFolderURL));
        }

        return result;
    }

    private Collection<JavaFileObject> listUnder(String packageName, URL packageFolderURL) {
        File directory = new File(packageFolderURL.getFile());
        if (directory.isDirectory()) {
            return processDir(packageName, directory);
        } else {
            return processJar(packageFolderURL);
        }
    }

    private Set<JavaFileObject> processJar(URL packageFolderURL) {
        Set<JavaFileObject> result = new HashSet<>();
        boolean runInSpringBootJar = this.getClass().getClassLoader().getClass() == LaunchedURLClassLoader.class;
        try {
            String jarUri = packageFolderURL.toExternalForm().substring(0, packageFolderURL.toExternalForm().lastIndexOf("!/"));

            JarURLConnection jarConn = (JarURLConnection) packageFolderURL.openConnection();
            jarConn.connect();
            String rootEntryName = runInSpringBootJar ? jarConn.getEntryName() : "";
            int rootEnd = rootEntryName.length() + 1;

            Enumeration<JarEntry> entryEnum = jarConn.getJarFile().entries();
            while (entryEnum.hasMoreElements()) {
                JarEntry jarEntry = entryEnum.nextElement();
                String name = jarEntry.getName();
                if (runInSpringBootJar) {
                    if (name.startsWith(rootEntryName) && name.indexOf('/', rootEnd) == -1 && name.endsWith(JavaFileObject.Kind.CLASS.extension)) {
                        result.add(buildSourceJavaObject(jarUri, name));
                    }
                } else {
                    if (name.endsWith(JavaFileObject.Kind.CLASS.extension)) {
                        result.add(buildSourceJavaObject(jarUri, name));
                    }
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("无法打开jar包" + packageFolderURL, e);
        }
        return result;
    }

    private CustomJavaFileObject buildSourceJavaObject(String jarUri, String name) {
        URI uri = URI.create(jarUri + "!/" + name);
        String binaryName = name.replaceAll("/", ".");
        binaryName = binaryName.replaceAll(JavaFileObject.Kind.CLASS.extension + "$", "");

        return new CustomJavaFileObject(binaryName, uri, JavaFileObject.Kind.CLASS);
    }

    private Set<JavaFileObject> processDir(String packageName, File directory) {
        Set<JavaFileObject> result = new HashSet<>();

        File[] childFiles = directory.listFiles();
        for (File childFile : childFiles) {
            if (childFile.isFile()) {
                if (childFile.getName().endsWith(JavaFileObject.Kind.CLASS.extension)) {
                    String binaryName = packageName + "." + childFile.getName();
                    binaryName = binaryName.replaceAll(JavaFileObject.Kind.CLASS.extension + "$", "");

                    result.add(new CustomJavaFileObject(binaryName, childFile.toURI(), JavaFileObject.Kind.CLASS));
                }
            }
        }

        return result;
    }
}
