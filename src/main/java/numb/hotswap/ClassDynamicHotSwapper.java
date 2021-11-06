package numb.hotswap;

import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.agent.ByteBuddyAgent;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.instrument.Instrumentation;

/**
 * @Author qingzhu
 * @Date 2021/7/31
 * @Desciption
 */
@Slf4j
public class ClassDynamicHotSwapper {

    private static Instrumentation instrumentation;

    private static AbstractCompiler compiler;

    static {
        compiler = new JdkComplier(Thread.currentThread().getContextClassLoader());
    }


    public static String hotSwapClass(String sourceCode) {
        CompliedInfo compiledInfo = compiler.compile(sourceCode);
        byte[] byteCode = compiledInfo.getByteCode();

        String fullClassName = compiledInfo.getFullClassName();
        log.info("complied success.bytecode.length={},fullClassName={}", byteCode.length, fullClassName);

        try {
            Class.forName(compiledInfo.getFullClassName());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("该类jvm未加载.无法执行替换", e);
        }

        //swapClassIfOracleJdk(byteCode, fullClassName);

        if (instrumentation == null) {
            instrumentation = ByteBuddyAgent.install();
        }

        try {
            ClassHotSwapAgent.redefine(fullClassName, byteCode, instrumentation);
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }

        return fullClassName;
    }


    /**
     * @param byteCode
     * @param fullClassName
     */
    /*private static void swapClassIfOracleJdk(byte[] byteCode, String fullClassName) {
        File byteCodeTempFile = null;
        try {
            byteCodeTempFile = File.createTempFile(String.format("dynamicComplied_class_byteCode%s", fullClassName), ".tempclass",
                    new ApplicationHome(ClassDynamicHotSwapper.class).getSource().getParentFile());
            if (byteCodeTempFile.exists()) {
                byteCodeTempFile.delete();
            }
            log.info("编译后字节文件路径为{}", byteCodeTempFile.getAbsolutePath());
            byteCodeTempFile.deleteOnExit();
            FileOutputStream fileOutputStream = new FileOutputStream(byteCodeTempFile);
            fileOutputStream.write(byteCode);
            fileOutputStream.flush();
            fileOutputStream.close();
        } catch (IOException e) {
            throw new IllegalStateException("文件写入字节码失败", e);
        }

        startHotSwapAgent(fullClassName, byteCodeTempFile);
    }*/


    /**
     * @param fullClassName
     * @param byteCodeTempFile
     */
   /* private static void startHotSwapAgent(String fullClassName, File byteCodeTempFile) {
        String agentArg = String.format("%s&%s", fullClassName, byteCodeTempFile.getAbsolutePath());
        try {
            ClassPool pool = new ClassPool(true);
            ClassLoader classLoader = ClassHotSwapAgent.class.getClassLoader();
            pool.appendClassPath(new LoaderClassPath(classLoader));
            CtClass agentClass = pool.get(ClassHotSwapAgent.class.getName());
    }

    */

}