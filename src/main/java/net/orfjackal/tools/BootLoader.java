package net.orfjackal.tools;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Properties;

/**
 * @author Esko Luontola
 * @since 24.2.2008
 */
public class BootLoader {

    // see also http://www.jroller.com/ssourcery/entry/get_rid_of_the_classpath

    public static final String PROPERTIES_PATH = "/bootloader.properties";
    public static final String LIBRARY_DIR_KEY = "libraryDir";
    public static final String MAIN_CLASS_KEY = "mainClass";

    private static String libraryDir;
    private static String mainClass;

    public static void main(String[] args) throws Exception {
        readProperties();
        URL[] libs = urlsFrom(listJarsIn(libraryDir()));
        ClassLoader classLoader = new URLClassLoader(libs, Thread.currentThread().getContextClassLoader());
        start(mainClass, args, classLoader);
    }

    private static void readProperties() throws IOException {
        Properties p = new Properties();
        p.load(BootLoader.class.getResourceAsStream(PROPERTIES_PATH));
        libraryDir = getRequiredProperty(p, LIBRARY_DIR_KEY);
        mainClass = getRequiredProperty(p, MAIN_CLASS_KEY);
    }

    private static String getRequiredProperty(Properties p, String key) {
        String value = p.getProperty(key);
        if (value == null) {
            throw new IllegalArgumentException(
                    "Required key \"" + key + "\" not found from file \"" + PROPERTIES_PATH + "\"");
        }
        return value;
    }

    private static File libraryDir() {
        File dir = new File(libraryDir);
        if (!dir.isDirectory()) {
            throw new IllegalStateException("No such directory: " + dir);
        }
        return dir;
    }

    private static File[] listJarsIn(File dir) {
        return dir.listFiles(new FileFilter() {
            public boolean accept(File file) {
                return file.getName().toLowerCase().endsWith(".jar");
            }
        });
    }

    private static URL[] urlsFrom(File[] files) throws MalformedURLException {
        URL[] urls = new URL[files.length];
        for (int i = 0; i < files.length; i++) {
            urls[i] = new URL("file", null, files[i].getAbsolutePath());
        }
        return urls;
    }

    private static void start(String mainClass, String[] args, ClassLoader classLoader) throws ClassNotFoundException, NoSuchMethodException, InterruptedException {
        Method main = classLoader.loadClass(mainClass).getMethod("main", String[].class);
        Thread t = new Thread(new MainRunner(main, args), Thread.currentThread().getName());
        t.start();
        t.join(1000);
    }

    private static class MainRunner implements Runnable {

        private final Method main;
        private final String[] args;

        public MainRunner(Method main, String[] args) {
            this.main = main;
            this.args = args;
        }

        public void run() {
            try {
                main.invoke(null, new Object[]{args});
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
