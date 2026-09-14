# Java launcher package

The release workflow builds `v.jar` with native V compiler libraries for Linux,
macOS, and Windows on both x86_64 and aarch64. It requires Java 21 and the
normal C runtime supplied by the host operating system.

Run the compiler through the JAR in the same way as the native compiler:

```text
java -jar v.jar run hello.v
```

The Java package includes the V support tree and extracts the matching TCC
runtime when it starts. The selected native compiler library is loaded through
JNI, so the JAR must be run on one of its six supported targets.

To compile a V program into a platform-specific launcher JAR, use the package
selector before the source file:

```text
java -jar v.jar --package java hello.v
```

This creates `hello.jar`. The result contains the program shared library, the
Java launcher, and the matching JNI bridge. Its native entry point is the
exported `jar_main(int, char**)` function. Use `-o` to select another output
JAR path:

```text
java -jar v.jar --package java -o build/hello.jar hello.v
```

`--package=jar` is accepted as a compatibility alias for `--package java`.
