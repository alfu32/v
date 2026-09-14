package org.vlang;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/** Loads a packaged native program and forwards Java arguments to jar_main. */
public final class NativeMain {
    private static boolean nativeLoaded;

    private NativeMain() {
    }

    public static void main(String[] args) throws Exception {
        System.exit(runResource("program", args));
    }

    private static native int runNative(String library, String[] args);

    private static native void setEnvironmentNative(String name, String value);

    /** Runs a native library stored beside the launcher resources. */
    public static int runResource(String name, String[] args) throws IOException {
        String target = target();
        loadNative(target);
        String extension = extension(target);
        Path directory = Files.createTempDirectory("v-native-");
        Path library = extract("/native/" + target + "/" + name + extension,
                directory.resolve(name + extension));
        return runNative(library.toString(), args);
    }

    /** Loads the JNI bridge used by the native launcher. */
    public static synchronized void loadNative(String target) throws IOException {
        if (nativeLoaded) {
            return;
        }
        String extension = extension(target);
        Path directory = Files.createTempDirectory("v-jni-");
        Path bridge = extract("/native/" + target + "/v_jni" + extension,
                directory.resolve("v_jni" + extension));
        System.load(bridge.toString());
        nativeLoaded = true;
    }

    /** Sets an environment variable for the native compiler process. */
    public static void setEnvironment(String name, String value) {
        setEnvironmentNative(name, value);
    }

    public static Path extract(String resource, Path destination) throws IOException {
        try (InputStream input = NativeMain.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IOException("missing resource " + resource);
            }
            Files.createDirectories(destination.getParent());
            Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING);
        }
        destination.toFile().deleteOnExit();
        try {
            destination.toFile().setExecutable(true, false);
        } catch (SecurityException ignored) {
            // Windows and restricted JVMs may not support POSIX executable bits.
        }
        return destination;
    }

    public static String target() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        String osName;
        if (os.contains("win")) {
            osName = "windows";
        } else if (os.contains("mac") || os.contains("darwin")) {
            osName = "macos";
        } else if (os.contains("linux")) {
            osName = "linux";
        } else {
            throw new IllegalStateException("unsupported operating system: " + os);
        }
        String archName;
        if (arch.equals("amd64") || arch.equals("x86_64") || arch.equals("x64")) {
            archName = "x86_64";
        } else if (arch.equals("aarch64") || arch.equals("arm64")) {
            archName = "aarch64";
        } else {
            throw new IllegalStateException("unsupported architecture: " + arch);
        }
        return osName + "-" + archName;
    }

    public static String extension(String target) {
        if (target.startsWith("windows-")) {
            return ".dll";
        }
        if (target.startsWith("macos-")) {
            return ".dylib";
        }
        return ".so";
    }
}
