package numb.hotswap;

import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.boot.system.ApplicationHome;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @Author qingzhu
 * @Date 2021/8/5
 * @Desciption
 */
@Slf4j
public class JdkComplier extends AbstractCompiler {

    private final JavaCompiler javaCompiler;
    private final StandardJavaFileManager standardFileManager;
    private final List<String> options = new ArrayList<>();
    private final DynamicClassLoader dynamicClassLoader;

    private final List<Diagnostic<? extends JavaFileObject>> errors = new ArrayList<>();
    private final List<Diagnostic<? extends JavaFileObject>> warnings = new ArrayList<>();

    private AtomicBoolean exportedLombok = new AtomicBoolean(false);

    public JdkComplier(ClassLoader parent) {
        javaCompiler = ToolProvider.getSystemJavaCompiler();
        if(javaCompiler==null){
            throw new IllegalStateException("获取编译器失败.请确认运行在jdk而不是jre");
        }
        this.dynamicClassLoader = new DynamicClassLoader(parent);
        standardFileManager = javaCompiler.getStandardFileManager(null, null, null);
    }

    @Override
    protected CompliedInfo doCompile(String name, String source) {
        String simpleClassName = name.substring(name.lastIndexOf(".") + 1);

        byte[] bytes = null;
        try {
            bytes = compile(Lists.newArrayList(new StringSource(simpleClassName, source))).get(name);
        } catch (Exception e) {
            throw new IllegalStateException( e.getMessage(), e);
        }

        return new CompliedInfo(name, bytes);

    }


    public synchronized Map<String, byte[]> compile(List<StringSource> compilationUnits) {
        initCompiler();

        JavaFileManager fileManager = new DynamicJavaFileManager(standardFileManager, dynamicClassLoader);

        DiagnosticCollector<JavaFileObject> collector = new DiagnosticCollector<JavaFileObject>();
        JavaCompiler.CompilationTask task = javaCompiler.getTask(null, fileManager, collector, options, null,
                compilationUnits);

        try {
            boolean result = task.call();

            if (!result || collector.getDiagnostics().size() > 0) {

                for (Diagnostic<? extends JavaFileObject> diagnostic : collector.getDiagnostics()) {
                    switch (diagnostic.getKind()) {
                        case NOTE:
                        case MANDATORY_WARNING:
                        case WARNING:
                            warnings.add(diagnostic);
                            break;
                        case OTHER:
                        case ERROR:
                        default:
                            errors.add(diagnostic);
                            break;
                    }

                }

                if (!errors.isEmpty()) {
                    throw new IllegalStateException("Compilation Error" + buildErrorMessage(errors));
                }
            }

            return dynamicClassLoader.getByteCodes();
        } catch (Exception e) {
            log.warn("编译失败.", e);
            throw new IllegalStateException( buildErrorMessage(errors), e);
        } finally {
            compilationUnits.clear();
        }

    }

    private void initCompiler() {
        if (exportedLombok.compareAndSet(false, true)) {
            try {
                String packageName = Slf4j.class.getPackage().getName().replace(".", "/");
                String tempJarPath = dynamicClassLoader.getResource(packageName).toString();
                String lombokJarPathInJar = StringUtils.substringBefore(tempJarPath, packageName);
                String jarName = StringUtils.substringBetween(lombokJarPathInJar, "lib/", "/");
                File lombokJar = new File(new ApplicationHome(getClass()).getSource().getParentFile().getAbsolutePath() + File.separator + jarName);
                if (lombokJar.exists()) {
                    lombokJar.delete();
                }
                lombokJar.deleteOnExit();

                URL url = new URL(lombokJarPathInJar);
                FileUtils.copyURLToFile(url, lombokJar);
                log.info("导出lombokjar:{}", lombokJar.getAbsolutePath());

                standardFileManager.setLocation(StandardLocation.ANNOTATION_PROCESSOR_PATH, Lists.newArrayList(lombokJar));
            } catch (Exception e) {
                throw new IllegalStateException( "设置注解处理器失败", e);
            }

            this.options.add("-source");
            this.options.add("1.8");
            this.options.add("-target");
            this.options.add("1.8");

        }

        errors.clear();
        warnings.clear();
    }

    private String buildErrorMessage(List<Diagnostic<? extends JavaFileObject>> errors) {
        StringBuilder errorsMsg = new StringBuilder();

        for (Map<String, Object> message : getErrorList(errors)) {
            for (Map.Entry<String, Object> entry : message.entrySet()) {
                Object value = entry.getValue();
                if (value != null && !value.toString().isEmpty()) {
                    errorsMsg.append(entry.getKey());
                    errorsMsg.append(": ");
                    errorsMsg.append(value);
                }
                errorsMsg.append(" , ");
            }

            errorsMsg.append("\n");
        }

        return errorsMsg.toString();
    }

    private List<Map<String, Object>> getErrorList(List<Diagnostic<? extends JavaFileObject>> errors) {
        List<Map<String, Object>> messages = new ArrayList<>();
        if (errors != null) {
            for (Diagnostic<? extends JavaFileObject> diagnostic : errors) {
                Map<String, Object> message = new HashMap<>();
                message.put("line", diagnostic.getLineNumber());
                message.put("message", diagnostic.getMessage(Locale.US));
                messages.add(message);
            }

        }
        return messages;
    }



}
