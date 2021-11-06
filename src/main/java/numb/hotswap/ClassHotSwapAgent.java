
package numb.hotswap;

import com.sun.tools.attach.VirtualMachine;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.Attributes;
import java.util.jar.Attributes.Name;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

public class ClassHotSwapAgent {

    /**
     * 全限定类名-->字节码
     */
    @Deprecated
    private static final Map<String, byte[]> className2CompliedByteCode = new ConcurrentHashMap();

    /**
     * 全限定类名->Class
     */
    @Deprecated
    private static final Map<String, Class<?>> loadedClassName2Class = new ConcurrentHashMap();

    private static Instrumentation instrumentation = null;

    /**
     * 缓存字节码
     *
     * @param className
     * @param byteCode
     */
    @Deprecated
    public static void cacheClassByte(String className, byte[] byteCode) {
        className2CompliedByteCode.put(className, byteCode);
    }


    /**
     * 缓存Class
     *
     * @param className
     * @param clazz
     */
    @Deprecated
    public static void cacheLoadedClass(String className, Class<?> clazz) {
        loadedClassName2Class.put(className, clazz);
    }


    public ClassHotSwapAgent() {
    }

    public static void agentmain(String agentArgs, Instrumentation inst) throws Throwable {
        if (!inst.isRedefineClassesSupported()) {
            throw new RuntimeException("this JVM does not support redefinition of classes");
        } else {
            instrumentation = inst;
            String fullClassName = agentArgs.split("&")[0];
            String byteCodeFilePath = agentArgs.split("&")[1];
            FileInputStream fileInputStream = new FileInputStream(byteCodeFilePath);
            byte[] byteCode = new byte[fileInputStream.available()];
            fileInputStream.read(byteCode);
            fileInputStream.close();

            redefine(fullClassName, byteCode, instrumentation);
        }
    }

    /**
     *
     * @param fullClassName
     * @param byteCode
     */
    public static void redefine(String fullClassName, byte[] byteCode, Instrumentation instrumentation) {
        Class clazz = Arrays.stream(instrumentation.getAllLoadedClasses()).filter(item -> item.getName().equals(fullClassName)).findFirst().orElse(null);
        if (Objects.isNull(clazz)) {
            throw new IllegalStateException(String.format("该类%s未被jvm加载,无法热替换", fullClassName));
        }

        ClassDefinition classDefinition = new ClassDefinition(clazz, byteCode);
        try {
            instrumentation.redefineClasses(classDefinition);
        } catch (Exception e) {
            throw new IllegalStateException(String.format("热替换类文件失败.fullClassName:%s", clazz.getName()), e);
        }
    }

    @Deprecated
    public static void redefine(String fullClassName, byte[] byteCode) {
        Class clazz = loadedClassName2Class.get(fullClassName);
        byte[] classByte = className2CompliedByteCode.get(fullClassName);

        ClassDefinition classDefinition = new ClassDefinition(clazz, classByte);
        System.out.println("redefine.instrumentation" + ClassHotSwapAgent.instrumentation + ClassHotSwapAgent.class.getClassLoader());
        try {
            ClassHotSwapAgent.instrumentation.redefineClasses(classDefinition);
        } catch (Exception e) {
            throw new IllegalStateException(String.format("热替换类文件失败.fullClassName:%s", fullClassName), e);
        }
    }


    /**
     * @param byteCode4AgentMainClass
     * @param agentArg
     */
    public static void startAgent(byte[] byteCode4AgentMainClass, String agentArg) {
        try {
            File agentJar = createAgentJarFile(byteCode4AgentMainClass);
            RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
            String pid = runtimeMXBean.getName().split("@")[0];
            VirtualMachine vm = VirtualMachine.attach(pid);
            vm.loadAgent(agentJar.getAbsolutePath(), agentArg);
            vm.detach();
        } catch (Exception e) {
            throw new IllegalStateException("启动agent失败了^_^", e);
        }
    }


    private static File createAgentJarFile(byte[] byteCode4AgentMainClass) throws IOException {
        File jar = File.createTempFile("agent", ".jar");
        jar.deleteOnExit();
        return createAgentJar(jar, byteCode4AgentMainClass);
    }

    private static File createAgentJar(File jar, byte[] byteCode4AgentMainClass) throws IOException {
        Manifest manifest = new Manifest();
        Attributes attrs = manifest.getMainAttributes();
        attrs.put(Name.MANIFEST_VERSION, "1.0");
        attrs.put(new Name("Agent-Class"), ClassHotSwapAgent.class.getName());
        attrs.put(new Name("Can-Retransform-Classes"), "true");
        attrs.put(new Name("Can-Redefine-Classes"), "true");
        //attrs.put(new Name("Class-Path"), new ApplicationHome());
        JarOutputStream jos = null;

        try {
            jos = new JarOutputStream(new FileOutputStream(jar), manifest);
            String cname = ClassHotSwapAgent.class.getName();
            JarEntry e = new JarEntry(cname.replace('.', '/') + ".class");
            jos.putNextEntry(e);
            jos.write(byteCode4AgentMainClass);
            jos.closeEntry();
        } finally {
            if (jos != null) {
                jos.close();
            }
        }

        return jar;
    }

}
