#ifndef V_JAR_WINDOWS_BOOTSTRAP_COMPAT_H
#define V_JAR_WINDOWS_BOOTSTRAP_COMPAT_H

// rpcndr.h defines `small` as a C character type. The generated V compiler
// snapshot uses `small` as an ordinary identifier. Load the same SDK umbrella
// headers and in the same order as vc/v_win.c, then preserve the V identifier
// through the rest of the translation unit.
#include <winsock2.h>
#include <windows.h>

#ifdef small
#undef small
#endif
#define small vlang_small

#endif // V_JAR_WINDOWS_BOOTSTRAP_COMPAT_H
