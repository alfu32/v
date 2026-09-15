package org.vlang.cli;

import org.vlang.NativeMain;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/** Command-line launcher for the universal V Java package. */
public final class Main {
    private Main() {
    }

    public static void main(String[] input) throws Exception {
        String target = NativeMain.target();
        String suffix = NativeMain.librarySuffix();
        String nativePrefix = "native/" + target + "/";
        if (isPackageRequest(input)) {
            packageProgram(input, target, suffix);
            return;
        }
        if (!NativeMain.hasResource(nativePrefix + "v" + suffix)) {
            runEmbeddedProgram(input, target, suffix);
            return;
        }
        Path root = extractVroot();
        boolean hasTcc = installTcc(root, target);
        Path compiler = NativeMain.extract(nativePrefix + "v" + suffix, "v-compiler-");
        Path bridge = NativeMain.extract(nativePrefix + "v_jni" + suffix, "v-jni-");
        System.load(bridge.toString());
        configureVroot(root, target);
        List<String> args = new ArrayList<>(List.of(input));
        if (!hasBundledLibgc(root) && isCompilationRequest(args) && !hasOption(args, "-gc")) {
            args.add(0, "none");
            args.add(0, "-gc");
        }
        if (isPackagedReentry() && !hasOption(args, "-gc")) {
            args.add(0, "none");
            args.add(0, "-gc");
        }
        if (hasTcc && !hasOption(args, "-cc")) {
            args.add(0, root.resolve("thirdparty/tcc/tcc.exe").toString());
            args.add(0, "-cc");
        }
        System.exit(NativeMain.runNative(compiler.toString(), args.toArray(String[]::new)));
    }

    private static boolean isPackageRequest(String[] input) {
        for (int i = 0; i < input.length; ++i) {
            if (input[i].equals("--package=java") || input[i].equals("--package=jar")) return true;
            if (input[i].equals("--package") && i + 1 < input.length
                    && (input[i + 1].equals("java") || input[i + 1].equals("jar"))) return true;
        }
        return false;
    }

    private static void packageProgram(String[] input, String target, String suffix) throws Exception {
        Path root = extractVroot();
        boolean hasTcc = installTcc(root, target);
        String[] compilerArgs = removePackageOptions(input);
        Path outputJar = outputJar(input);
        Path work = Files.createTempDirectory("v-package-");
        Path program = work.resolve("program" + suffix);
        List<String> args = new ArrayList<>();
        args.add("-shared");
        args.add("-d");
        args.add("jar");
        args.add("-gc");
        args.add("none");
        if (hasTcc && !hasOption(List.of(compilerArgs), "-cc")) {
            args.add("-cc");
            args.add(root.resolve("thirdparty/tcc/tcc.exe").toString());
        }
        args.add("-o");
        args.add(program.toString());
        args.addAll(List.of(compilerArgs));
        Path compiler = NativeMain.extract("native/" + target + "/v" + suffix, "v-compiler-");
        Path bridge = NativeMain.extract("native/" + target + "/v_jni" + suffix, "v-jni-");
        System.load(bridge.toString());
        configureVroot(root, target);
        int result = NativeMain.runNative(compiler.toString(), args.toArray(String[]::new));
        if (result != 0) System.exit(result);
        if (!Files.isRegularFile(program)) {
            throw new IOException("V did not produce " + program);
        }
        writeProgramJar(outputJar, target, suffix, program);
        System.out.println("created " + outputJar);
    }

    private static void runEmbeddedProgram(String[] args, String target, String suffix) throws Exception {
        Path bridge = NativeMain.extract("native/" + target + "/v_jni" + suffix, "v-jni-");
        Path program = NativeMain.extract("native/" + target + "/program" + suffix, "v-program-");
        System.load(bridge.toString());
        System.exit(NativeMain.runNative(program.toString(), args));
    }

    private static String[] removePackageOptions(String[] input) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < input.length; ++i) {
            if (input[i].equals("--package=java") || input[i].equals("--package=jar")) continue;
            if (input[i].equals("--package") && i + 1 < input.length
                    && (input[i + 1].equals("java") || input[i + 1].equals("jar"))) {
                ++i;
                continue;
            }
            if ((input[i].equals("-o") || input[i].equals("--output")) && i + 1 < input.length) {
                ++i;
                continue;
            }
            result.add(input[i]);
        }
        return result.toArray(String[]::new);
    }

    private static boolean hasOption(List<String> args, String option) {
        return args.stream().anyMatch(arg -> arg.equals(option) || arg.startsWith(option + "="));
    }

    private static void configureVroot(Path root, String target) throws IOException {
        Path executable = createCompilerLauncher(root, target);
        NativeMain.setEnv("V_PACKAGED_ROOT", root.toString());
        NativeMain.setEnv("VEXE", executable.toString());
    }

    private static Path createCompilerLauncher(Path root, String target) throws IOException {
        Path jar = ownJar();
        boolean windows = target.startsWith("windows-");
        Path java = Path.of(System.getProperty("java.home"), "bin",
                windows ? "java.exe" : "java");
        Path launcher = root.resolve(windows ? "v.cmd" : "v");
        String contents;
        if (windows) {
            contents = "@echo off\r\n"
                    + "set \"V_PACKAGED_REENTRY=1\"\r\n"
                    + "\"" + java + "\" -jar \"" + jar + "\" %*\r\n"
                    + "exit /b %errorlevel%\r\n";
        } else {
            contents = "#!/bin/sh\nexport V_PACKAGED_REENTRY=1\nexec "
                    + shellQuote(java.toString()) + " -jar "
                    + shellQuote(jar.toString()) + " \"$@\"\n";
        }
        Files.writeString(launcher, contents, StandardCharsets.UTF_8);
        if (!windows) launcher.toFile().setExecutable(true);
        return launcher;
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private static boolean isPackagedReentry() {
        return "1".equals(System.getenv("V_PACKAGED_REENTRY"));
    }

    private static boolean hasBundledLibgc(Path root) {
        return Files.isRegularFile(root.resolve("thirdparty/tcc/lib/libgc.a"));
    }

    private static boolean isCompilationRequest(List<String> args) {
        for (String arg : args) {
            if (arg.equals("run") || arg.equals("crun") || arg.equals("build")
                    || arg.equals("build-module")) return true;
            if (arg.endsWith(".v") || arg.endsWith(".vsh")) return true;
        }
        return false;
    }

    private static Path outputJar(String[] input) {
        for (int i = 0; i + 1 < input.length; ++i) {
            if (input[i].equals("-o") || input[i].equals("--output")) {
                Path output = Path.of(input[i + 1]);
                return output.toString().endsWith(".jar") ? output : Path.of(output + ".jar");
            }
        }
        for (String arg : input) {
            if (!arg.startsWith("-") && (arg.endsWith(".v") || arg.endsWith(".vsh"))) {
                String name = Path.of(arg).getFileName().toString();
                int dot = name.lastIndexOf('.');
                return Path.of(name.substring(0, dot) + ".jar");
            }
        }
        return Path.of("program.jar");
    }

    private static Path extractVroot() throws IOException {
        Path root = Files.createTempDirectory("v-vroot-");
        root.toFile().deleteOnExit();
        try (JarFile jar = new JarFile(ownJar().toFile())) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!entry.getName().startsWith("vroot/")) continue;
                Path destination = root.resolve(entry.getName().substring("vroot/".length())).normalize();
                if (!destination.startsWith(root)) throw new IOException("invalid vroot resource");
                if (entry.isDirectory()) Files.createDirectories(destination);
                else {
                    Files.createDirectories(destination.getParent());
                    try (InputStream in = jar.getInputStream(entry)) {
                        Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }
        return root;
    }

    private static boolean installTcc(Path root, String target) throws IOException {
        Path source = root.resolve("tcc").resolve(target).resolve("tinycc");
        Path destination = root.resolve("thirdparty/tcc/tinycc");
        if (!Files.isDirectory(source)) return false;
        copyTree(source, destination);
        Path bundledLibgc = root.resolve("tcc").resolve(target).resolve("libgc.a");
        if (Files.isRegularFile(bundledLibgc)) {
            Path libgc = root.resolve("thirdparty/tcc/lib/libgc.a");
            Files.createDirectories(libgc.getParent());
            Files.copy(bundledLibgc, libgc, StandardCopyOption.REPLACE_EXISTING);
        }
        Path wrapper = root.resolve("thirdparty/tcc/tcc.exe");
        if (target.startsWith("windows-")) {
            Files.copy(destination.resolve("bin/tcc.exe"), wrapper, StandardCopyOption.REPLACE_EXISTING);
        } else {
            Files.writeString(wrapper, "#!/bin/sh\nexec \"$(dirname \"$0\")/tinycc/bin/tcc\" \"$@\"\n",
                    StandardCharsets.UTF_8);
            wrapper.toFile().setExecutable(true);
        }
        return true;
    }

    private static void copyTree(Path source, Path destination) throws IOException {
        Files.walk(source).forEach(path -> {
            try {
                Path target = destination.resolve(source.relativize(path));
                if (Files.isDirectory(path)) Files.createDirectories(target);
                else Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException exception) {
                throw new RuntimeException(exception);
            }
        });
    }

    private static void writeProgramJar(Path output, String target, String suffix, Path program) throws IOException {
        if (output.getParent() != null) Files.createDirectories(output.getParent());
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
        manifest.getMainAttributes().putValue("Main-Class", "org.vlang.cli.Main");
        try (OutputStream out = Files.newOutputStream(output); JarOutputStream jar = new JarOutputStream(out, manifest)) {
            try (JarFile source = new JarFile(ownJar().toFile())) {
                source.stream().filter(entry -> entry.getName().startsWith("org/vlang/")
                        && entry.getName().endsWith(".class")).forEach(entry -> copyEntry(source, entry, jar));
            }
            copyResource(jar, "native/" + target + "/v_jni" + suffix);
            JarEntry programEntry = new JarEntry("native/" + target + "/program" + suffix);
            jar.putNextEntry(programEntry);
            Files.copy(program, jar);
            jar.closeEntry();
        }
    }

    private static Path ownJar() throws IOException {
        try {
            return Path.of(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (Exception exception) {
            throw new IOException("could not locate the V JAR", exception);
        }
    }

    private static void copyResource(JarOutputStream output, String resource) throws IOException {
        JarEntry entry = new JarEntry(resource);
        output.putNextEntry(entry);
        try (InputStream input = Main.class.getClassLoader().getResourceAsStream(resource)) {
            if (input == null) throw new IOException("missing resource: " + resource);
            input.transferTo(output);
        }
        output.closeEntry();
    }

    private static void copyEntry(JarFile source, JarEntry entry, JarOutputStream output) {
        try {
            output.putNextEntry(new JarEntry(entry.getName()));
            try (InputStream input = source.getInputStream(entry)) { input.transferTo(output); }
            output.closeEntry();
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }
}
