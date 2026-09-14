package org.vlang.cli;

import org.vlang.NativeMain;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/** Java launcher for the universal V compiler JAR. */
public final class Main {
    private Main() {
    }

    public static void main(String[] args) throws Exception {
        PackageRequest request = PackageRequest.parse(args);
        if (request != null) {
            int result = packageProgram(request);
            System.exit(result);
            return;
        }

        Path root = extractVroot();
        String target = NativeMain.target();
        NativeMain.loadNative(target);
        installTcc(root, target);
        NativeMain.setEnvironment("V_PACKAGED_ROOT", root.toString());
        System.exit(NativeMain.runResource("v", noFallback(args)));
    }

    private static int packageProgram(PackageRequest request) throws Exception {
        Path root = extractVroot();
        String target = NativeMain.target();
        NativeMain.loadNative(target);
        installTcc(root, target);
        NativeMain.setEnvironment("V_PACKAGED_ROOT", root.toString());

        Path temporary = Files.createTempDirectory("v-package-");
        Path nativeOutput = temporary.resolve("program");
        List<String> compilerArgs = new ArrayList<>();
        compilerArgs.add("-shared");
        compilerArgs.add("-d");
        compilerArgs.add("jar");
        compilerArgs.add("-new-compiler");
        for (int i = 0; i < request.compilerArgs.size(); i++) {
            String arg = request.compilerArgs.get(i);
            if (arg.equals("-o") || arg.equals("-output")) {
                i++;
                continue;
            }
            compilerArgs.add(arg);
        }
        compilerArgs.add("-o");
        compilerArgs.add(nativeOutput.toString());

        int result = NativeMain.runResource("v", compilerArgs.toArray(new String[0]));
        if (result != 0) {
            return result;
        }

        String extension = NativeMain.extension(target);
        Path library = nativeOutput.resolveSibling(nativeOutput.getFileName() + extension);
        if (!Files.isRegularFile(library)) {
            throw new IOException("V did not produce " + library);
        }
        writeProgramJar(request.output, target, extension, library);
        System.out.println("created " + request.output);
        return 0;
    }

    private static String[] noFallback(String[] args) {
        String[] result = new String[args.length + 1];
        result[0] = "-new-compiler";
        System.arraycopy(args, 0, result, 1, args.length);
        return result;
    }

    private static Path extractVroot() throws IOException {
        Path root = Files.createTempDirectory("v-root-");
        try (JarFile jar = ownJar()) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!name.startsWith("vroot/") || entry.isDirectory()) {
                    continue;
                }
                Path output = root.resolve(name.substring("vroot/".length()));
                Files.createDirectories(output.getParent());
                try (InputStream input = jar.getInputStream(entry)) {
                    Files.copy(input, output, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        return root;
    }

    private static void installTcc(Path root, String target) throws IOException {
        Path nested = NativeMain.extract("/tcc-cli.jar",
                Files.createTempDirectory("v-tcc-").resolve("tcc-cli.jar"));
        Path tccRoot = root.resolve("thirdparty/tcc");
        Files.createDirectories(tccRoot);
        String prefix = "native/" + target + "/tinycc/";
        try (JarFile jar = new JarFile(nested.toFile())) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!name.startsWith(prefix) || entry.isDirectory()) {
                    continue;
                }
                Path output = tccRoot.resolve(name.substring(prefix.length()));
                Files.createDirectories(output.getParent());
                try (InputStream input = jar.getInputStream(entry)) {
                    Files.copy(input, output, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }

        Path binary = firstExisting(tccRoot.resolve("bin/tcc.exe"),
                tccRoot.resolve("bin/tcc"), tccRoot.resolve("bin/tcc-bin"));
        if (binary == null) {
            throw new IOException("TCC runtime has no executable for " + target);
        }
        Files.copy(binary, tccRoot.resolve("tcc.exe"), StandardCopyOption.REPLACE_EXISTING);
        tccRoot.resolve("tcc.exe").toFile().setExecutable(true, false);
        try (DirectoryStream<Path> files = Files.newDirectoryStream(binary.getParent())) {
            for (Path file : files) {
                if (Files.isRegularFile(file)) {
                    Files.copy(file, tccRoot.resolve(file.getFileName().toString()),
                            StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }

        // The published Unix TCC binary uses an $ORIGIN/../lib RPATH. Keep a
        // matching top-level lib directory after relocating it to tcc.exe.
        if (!target.startsWith("windows-")) {
            Path lib = tccRoot.resolve("lib");
            Path relocatedLib = root.resolve("lib");
            if (Files.isDirectory(lib)) {
                copyTree(lib, relocatedLib);
            }
        }
    }

    private static Path firstExisting(Path... paths) {
        for (Path path : paths) {
            if (Files.isRegularFile(path)) {
                return path;
            }
        }
        return null;
    }

    private static void copyTree(Path source, Path destination) throws IOException {
        Files.walk(source).forEach(path -> {
            try {
                Path output = destination.resolve(source.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(output);
                } else {
                    Files.createDirectories(output.getParent());
                    Files.copy(path, output, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException exception) {
                throw new RuntimeException(exception);
            }
        });
    }

    private static void writeProgramJar(Path output, String target, String extension,
            Path library) throws IOException {
        if (output.getParent() != null) {
            Files.createDirectories(output.getParent());
        }
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, "org.vlang.NativeMain");
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(output), manifest)) {
            addResource(jar, "org/vlang/NativeMain.class", "/org/vlang/NativeMain.class");
            addResource(jar, "native/" + target + "/v_jni" + extension,
                    "/native/" + target + "/v_jni" + extension);
            jar.putNextEntry(new JarEntry("native/" + target + "/program" + extension));
            Files.copy(library, jar);
            jar.closeEntry();
        }
    }

    private static void addResource(JarOutputStream jar, String name, String resource)
            throws IOException {
        jar.putNextEntry(new JarEntry(name));
        try (InputStream input = Main.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IOException("missing resource " + resource);
            }
            input.transferTo(jar);
        }
        jar.closeEntry();
    }

    private static JarFile ownJar() throws IOException {
        try {
            Path location = Path.of(Main.class.getProtectionDomain().getCodeSource()
                    .getLocation().toURI());
            if (Files.isDirectory(location)) {
                throw new IOException("v.jar must be run from a JAR file");
            }
            return new JarFile(location.toFile());
        } catch (URISyntaxException exception) {
            throw new IOException("cannot locate v.jar", exception);
        }
    }

    private static final class PackageRequest {
        private final List<String> compilerArgs;
        private final Path output;

        private PackageRequest(List<String> compilerArgs, Path output) {
            this.compilerArgs = compilerArgs;
            this.output = output;
        }

        private static PackageRequest parse(String[] args) {
            List<String> compiler = new ArrayList<>();
            Path output = null;
            boolean requested = false;
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                if (arg.equals("--package")) {
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("--package requires a package type");
                    }
                    String kind = args[++i];
                    if (!kind.equals("java") && !kind.equals("jar")) {
                        throw new IllegalArgumentException("unsupported package type: " + kind);
                    }
                    requested = true;
                } else if (arg.startsWith("--package=")) {
                    String kind = arg.substring("--package=".length());
                    if (!kind.equals("java") && !kind.equals("jar")) {
                        throw new IllegalArgumentException("unsupported package type: " + kind);
                    }
                    requested = true;
                } else if ((arg.equals("-o") || arg.equals("-output")) && i + 1 < args.length) {
                    output = Path.of(args[++i]);
                } else {
                    compiler.add(arg);
                }
            }
            if (!requested) {
                return null;
            }
            if (output == null) {
                for (String arg : compiler) {
                    if (arg.endsWith(".v")) {
                        String name = Path.of(arg).getFileName().toString();
                        output = Path.of(name.substring(0, name.length() - 2) + ".jar");
                        break;
                    }
                }
            }
            if (output == null) {
                throw new IllegalArgumentException("--package java requires a .v input or -o");
            }
            return new PackageRequest(compiler, output);
        }
    }
}
