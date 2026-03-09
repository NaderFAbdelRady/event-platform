package com.platform.engine;

import javax.annotation.PostConstruct;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import java.io.*;
import java.net.URL;
import java.util.jar.*;
import java.util.logging.Logger;

/**
 * On startup, extracts all platform SDK and component classes into a real
 * JAR file at COMPONENTS_DIR/platform-classes.jar.
 *
 * WildFly uses a VFS classloader that the standard JavaCompiler cannot
 * walk — writing a concrete JAR on disk solves this permanently.
 * ComponentLoader depends on this bean and uses PLATFORM_JAR as its classpath.
 */
@Singleton
@Startup
public class ClasspathExtractor {

    private static final Logger LOG = Logger.getLogger(ClasspathExtractor.class.getName());

    public static final String COMPONENTS_DIR = System.getProperty(
            "platform.components.dir",
            System.getProperty("user.home") + "/platform-components");

    public static final String PLATFORM_JAR =
            COMPONENTS_DIR + File.separator + "platform-classes.jar";

    /** All platform classes the generated components need to compile against */
    private static final Class<?>[] PLATFORM_CLASSES = {
        com.platform.components.AbstractEventComponent.class,
        com.platform.components.EventComponent.class,
        com.platform.components.ComponentMetadata.class,
        com.platform.events.PlatformEvent.class,
        com.platform.events.EventContext.class,
        com.platform.events.EventSource.class,
        com.platform.sdk.EventBusService.class,
        com.platform.sdk.DataStoreService.class,
        com.platform.sdk.HttpClientService.class,
        com.platform.sdk.WsGatewayService.class
    };

    @PostConstruct
    public void extract() {
        try {
            new File(COMPONENTS_DIR).mkdirs();
            buildPlatformJar();
            LOG.info("Platform JAR ready: " + PLATFORM_JAR);
        } catch (Exception e) {
            LOG.severe("Failed to build platform JAR: " + e.getMessage());
        }
    }

    private void buildPlatformJar() throws Exception {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");

        try (JarOutputStream jar = new JarOutputStream(
                new FileOutputStream(PLATFORM_JAR), manifest)) {

            for (Class<?> clazz : PLATFORM_CLASSES) {
                writeClass(jar, clazz);
            }
        }
    }

    private void writeClass(JarOutputStream jar, Class<?> clazz) throws Exception {
        String resourcePath = clazz.getName().replace('.', '/') + ".class";
        URL url = clazz.getClassLoader().getResource(resourcePath);
        if (url == null) {
            LOG.warning("Could not locate class resource: " + resourcePath);
            return;
        }
        byte[] bytes;
        try (InputStream in = url.openStream()) {
            bytes = in.readAllBytes();
        }
        jar.putNextEntry(new JarEntry(resourcePath));
        jar.write(bytes);
        jar.closeEntry();
    }
}
