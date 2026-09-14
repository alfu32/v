package org.vlang;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/** Loads one native V payload and invokes its exported jar_main entry point. */
public final class NativeMain {
    private NativeMain() {
    }

    /** Returns the host target name used by the V JAR resource layout. */
    public static String target() {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
        String platform;
        if (os.contains("win")) {
            platform = "windows";
        } else if (os.contains("mac") || os.contains("darwin")) {
            platform = "macos";
        } else if (os.contains("linux")) {
            platform = "linux";
        } else {
            throw new IllegalStateException("unsupported operating system: " + os);
        }
        String cpu;
        if (arch.equals("amd64") || arch.equals("x86_64") || arch.equals("x64")) {
            cpu = "x86_64";
        } else if (arch.equals("aarch64") || arch.equals("arm64")) {
            cpu = "aarch64";
        } else {
            throw new IllegalStateException("unsupported CPU architecture: " + arch);
        }
        return platform + "-" + cpu;
    }

    /** Returns the native library suffix for the current host. */
    public static String librarySuffix() {
        return target().startsWith("windows-") ? ".dll"
                : target().startsWith("macos-") ? ".dylib" : ".so";
    }

    /** Extracts a resource into a temporary file and returns its path. */
    public static Path extract(String resource, String prefix) throws IOException {
        Path file = Files.createTempFile(prefix, librarySuffix());
        file.toFile().deleteOnExit();
        try (InputStream input = resource(resource)) {
            Files.copy(input, file, StandardCopyOption.REPLACE_EXISTING);
        }
        if (!target().startsWith("windows-")) {
            file.toFile().setExecutable(true);
        }
        return file;
    }

    /** Invokes jar_main in a native library. */
    public static native int runNative(String library, String[] args);

    /** Sets an environment variable for the V process hosted by this JVM. */
    public static native void setEnv(String name, String value);

    /** Checks whether a resource is present in the running JAR. */
    public static boolean hasResource(String name) {
        return NativeMain.class.getClassLoader().getResource(name) != null;
    }

    private static InputStream resource(String name) throws IOException {
        InputStream input = NativeMain.class.getClassLoader().getResourceAsStream(name);
        if (input == null) {
            throw new IOException("missing JAR resource: " + name);
        }
        return input;
    }
}
