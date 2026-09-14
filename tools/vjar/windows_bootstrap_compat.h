#ifndef V_JAR_WINDOWS_BOOTSTRAP_COMPAT_H
#define V_JAR_WINDOWS_BOOTSTRAP_COMPAT_H

// rpcndr.h defines `small` as a C character type. The generated V compiler
// snapshot uses `small` as an ordinary identifier, so load the SDK declaration
// first and then preserve the V identifier through the rest of the translation
// unit.
#include <rpcndr.h>

#ifdef small
#undef small
#endif
#define small vlang_small

#endif // V_JAR_WINDOWS_BOOTSTRAP_COMPAT_H
