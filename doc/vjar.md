# Java package

The V Java package is a Java 21 launcher containing V compiler libraries for
Linux, macOS, and Windows on x86_64 and aarch64. It selects the native payload
for the host JVM at startup.

Run the compiler with:

```text
java -jar v.jar version
java -jar v.jar hello.v
```

The launcher bundles TinyCC for compiling programs without a separately
installed C compiler. An explicit `-cc` option is still passed through to V.
Native builds also carry V's `libgc.a` when available. If an older or partial
JAR has no `libgc.a`, the launcher defaults to `-gc none`; pass an explicit
`-gc` option when a different garbage collector is required.

To create a platform-specific executable JAR from a V program, use:

```text
java -jar v.jar --package java hello.v
java -jar v.jar --package=java -o hello.jar hello.v
```

The generated JAR contains the program as a native library and can be started
with `java -jar hello.jar`. It is intended for the operating system and CPU
architecture on which it was built.

The native artifacts are built and published independently by three manual
GitHub workflows. The separate JAR workflow can be run manually, or is started
automatically when one of the OS workflows completes. It combines every
matching artifact that is available for the commit and publishes
`vjar-universal`.

To assemble a JAR after a workflow-only commit without rebuilding the other
platforms, run the JAR workflow manually and provide the existing Linux,
macOS, and Windows workflow run IDs. Optionally provide `source_ref` for the
commit or branch containing the V sources. Explicit run IDs override the
normal same-commit artifact lookup. The macOS x86_64 and aarch64 run ID
overrides can refer to different runs when only one architecture was rebuilt.
The macOS workflow also accepts a target selection, so choose
`macos-x86_64` when retrying only the Intel build.
