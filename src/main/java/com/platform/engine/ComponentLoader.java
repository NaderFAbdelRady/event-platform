package com.platform.engine;

import com.platform.components.EventComponent;
import com.platform.sdk.DataStoreService;
import com.platform.sdk.EventBusService;
import com.platform.sdk.HttpClientService;
import com.platform.sdk.WsGatewayService;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.ejb.DependsOn;
import javax.ejb.EJB;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.inject.Inject;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Watches the components directory for new or updated .java/.class files.
 * Compiles .java files, loads .class files, injects SDK services,
 * and registers the component in the registry.
 *
 * Each component gets its own URLClassLoader for true hot-reload.
 *
 * DependsOn ClasspathExtractor ensures platform-classes.jar exists
 * before any compilation is attempted.
 */
@Singleton
@Startup
@DependsOn("ClasspathExtractor")
public class ComponentLoader {

    private static final Logger LOG = Logger.getLogger(ComponentLoader.class.getName());

    // Shared with ClasspathExtractor — single source of truth
    private static final String COMPONENTS_DIR = ClasspathExtractor.COMPONENTS_DIR;

    @EJB private ComponentRegistry registry;
    @EJB private LifecycleManager  lifecycleManager;

    @Inject private EventBusService   eventBusService;
    @Inject private DataStoreService  dataStoreService;
    @Inject private HttpClientService httpClientService;
    @Inject private WsGatewayService  wsGatewayService;

    private Thread       watcherThread;
    private WatchService watchService;

    @PostConstruct
    public void init() {
        ensureComponentsDirectory();
        loadExistingComponents();
        startFileWatcher();
        LOG.info("ComponentLoader started. Watching: " + COMPONENTS_DIR);
    }

    @PreDestroy
    public void shutdown() {
        if (watcherThread != null) watcherThread.interrupt();
        try { if (watchService != null) watchService.close(); } catch (Exception ignored) {}
    }

    // ── Load existing .class files on startup ─────────────────

    private void loadExistingComponents() {
        File dir = new File(COMPONENTS_DIR);
        File[] classFiles = dir.listFiles((d, n) ->
                n.endsWith(".class") && !n.equals("platform-classes.jar"));
        if (classFiles == null) return;

        for (File classFile : classFiles) {
            try {
                loadClassFile(classFile);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Failed to load: " + classFile.getName(), e);
            }
        }
    }

    // ── File Watcher ──────────────────────────────────────────

    private void startFileWatcher() {
        try {
            watchService = FileSystems.getDefault().newWatchService();
            Path path = Paths.get(COMPONENTS_DIR);
            path.register(watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY);

            watcherThread = new Thread(this::watchLoop, "ComponentFileWatcher");
            watcherThread.setDaemon(true);
            watcherThread.start();

        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to start file watcher", e);
        }
    }

    private void watchLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                WatchKey key = watchService.take();
                for (WatchEvent<?> event : key.pollEvents()) {
                    Path   changed  = (Path) event.context();
                    String filename = changed.toString();

                    // Ignore the platform JAR itself
                    if (filename.equals("platform-classes.jar")) continue;

                    if (filename.endsWith(".java")) {
                        compileAndLoad(new File(COMPONENTS_DIR, filename));
                    } else if (filename.endsWith(".class")) {
                        loadClassFile(new File(COMPONENTS_DIR, filename));
                    }
                }
                key.reset();

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                LOG.log(Level.WARNING, "File watcher error", e);
            }
        }
    }

    // ── Compilation ───────────────────────────────────────────

    public CompileResult compileSource(String className, String sourceCode) {
        try {
            Path sourceFile = Paths.get(COMPONENTS_DIR, className + ".java");
            Files.writeString(sourceFile, sourceCode);
            return compileAndLoad(sourceFile.toFile());
        } catch (Exception e) {
            return CompileResult.failure("Failed to write source: " + e.getMessage());
        }
    }

    private CompileResult compileAndLoad(File javaFile) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            return CompileResult.failure(
                    "JavaCompiler not available — ensure WildFly is launched with a JDK, not a JRE.");
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

        try (var fm = compiler.getStandardFileManager(diagnostics, null, null)) {
            var units = fm.getJavaFileObjects(javaFile);

            JavaCompiler.CompilationTask task = compiler.getTask(
                    null, fm, diagnostics,
                    Arrays.asList(
                            "-classpath", buildClasspath(),
                            "--release",  "11",
                            "-d",         COMPONENTS_DIR),
                    null, units);

            if (!task.call()) {
                StringBuilder errors = new StringBuilder();
                diagnostics.getDiagnostics().forEach(d ->
                        errors.append("ERROR at line ").append(d.getLineNumber())
                              .append(": ").append(d.getMessage(null))
                              .append("\n"));
                return CompileResult.failure(errors.toString());
            }

            // Compilation succeeded — load the .class
            String className = javaFile.getName().replace(".java", "");
            return loadClassFile(new File(COMPONENTS_DIR, className + ".class"));

        } catch (Exception e) {
            return CompileResult.failure("Compilation exception: " + e.getMessage());
        }
    }

    // ── Class Loading ─────────────────────────────────────────

    private CompileResult loadClassFile(File classFile) {
        String className = classFile.getName().replace(".class", "");

        try {
            // Each component gets its own URLClassLoader for hot-reload
            URL[] urls = { new File(COMPONENTS_DIR).toURI().toURL() };
            URLClassLoader classLoader = new URLClassLoader(
                    urls, Thread.currentThread().getContextClassLoader());

            Class<?> clazz = classLoader.loadClass("com.platform.components." + className);

            if (!EventComponent.class.isAssignableFrom(clazz)) {
                classLoader.close();
                return CompileResult.failure(className + " does not implement EventComponent");
            }

            EventComponent component =
                    (EventComponent) clazz.getDeclaredConstructor().newInstance();

            // Inject SDK services before onLoad()
            component.injectEventBus(eventBusService);
            component.injectDataStore(dataStoreService);
            component.injectHttpClient(httpClientService);
            component.injectWsGateway(wsGatewayService);

            lifecycleManager.load(component, classLoader);
            return CompileResult.success(className);

        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to load class: " + className, e);
            return CompileResult.failure("Class loading failed: " + e.getMessage());
        }
    }

    // ── Classpath builder ─────────────────────────────────────

    /**
     * Builds the compiler classpath:
     *   1. platform-classes.jar  — platform SDK/interface classes (extracted at startup)
     *   2. System classpath      — JDK classes (java.util, javax.*, etc.)
     *   3. Components directory  — previously compiled component classes
     */
    private String buildClasspath() {
        StringBuilder cp = new StringBuilder();

        // 1. Platform classes JAR — always a real file, always findable
        cp.append(ClasspathExtractor.PLATFORM_JAR);

        // 2. JDK / system classpath
        String sysCp = System.getProperty("java.class.path", "");
        if (!sysCp.isBlank()) {
            cp.append(File.pathSeparator).append(sysCp);
        }

        // 3. Components dir for cross-component references
        cp.append(File.pathSeparator).append(COMPONENTS_DIR);

        return cp.toString();
    }

    // ── Helpers ───────────────────────────────────────────────

    private void ensureComponentsDirectory() {
        new File(COMPONENTS_DIR).mkdirs();
    }

    public String getComponentsDir() {
        return COMPONENTS_DIR;
    }

    // ── CompileResult ─────────────────────────────────────────

    public static class CompileResult {
        private final boolean success;
        private final String  componentName;
        private final String  errorMessage;

        private CompileResult(boolean success, String name, String error) {
            this.success       = success;
            this.componentName = name;
            this.errorMessage  = error;
        }

        public static CompileResult success(String name)  { return new CompileResult(true,  name, null); }
        public static CompileResult failure(String error) { return new CompileResult(false, null, error); }

        public boolean isSuccess()       { return success; }
        public String getComponentName() { return componentName; }
        public String getErrorMessage()  { return errorMessage; }
    }
}
