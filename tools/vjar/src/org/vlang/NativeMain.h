#ifndef V_JAR_NATIVE_MAIN_H
#define V_JAR_NATIVE_MAIN_H

#include <jni.h>

JNIEXPORT jint JNICALL Java_org_vlang_NativeMain_runNative(JNIEnv *, jclass, jstring,
        jobjectArray);
JNIEXPORT void JNICALL Java_org_vlang_NativeMain_setEnvironmentNative(JNIEnv *, jclass,
        jstring, jstring);

#endif
