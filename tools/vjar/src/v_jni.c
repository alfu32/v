#include "org/vlang/NativeMain.h"

#include <stdlib.h>
#include <string.h>

#ifdef _WIN32
#include <windows.h>
#define v_strdup _strdup
typedef int (__cdecl *v_jar_main_fn)(int, char **);
#else
#include <dlfcn.h>
#define v_strdup strdup
typedef int (*v_jar_main_fn)(int, char **);
#endif

static void v_throw(JNIEnv *env, const char *message) {
    jclass error = (*env)->FindClass(env, "java/lang/UnsatisfiedLinkError");
    if (error != NULL) {
        (*env)->ThrowNew(env, error, message);
    }
}

JNIEXPORT void JNICALL Java_org_vlang_NativeMain_setEnvironmentNative(JNIEnv *env,
        jclass ignored, jstring name, jstring value) {
    const char *name_utf = (*env)->GetStringUTFChars(env, name, NULL);
    const char *value_utf = (*env)->GetStringUTFChars(env, value, NULL);
    if (name_utf == NULL || value_utf == NULL) {
        if (name_utf != NULL) {
            (*env)->ReleaseStringUTFChars(env, name, name_utf);
        }
        if (value_utf != NULL) {
            (*env)->ReleaseStringUTFChars(env, value, value_utf);
        }
        return;
    }
#ifdef _WIN32
    _putenv_s(name_utf, value_utf);
#else
    setenv(name_utf, value_utf, 1);
#endif
    (*env)->ReleaseStringUTFChars(env, name, name_utf);
    (*env)->ReleaseStringUTFChars(env, value, value_utf);
    (void)ignored;
}

JNIEXPORT jint JNICALL Java_org_vlang_NativeMain_runNative(JNIEnv *env, jclass ignored,
        jstring library, jobjectArray java_args) {
    const char *library_utf = (*env)->GetStringUTFChars(env, library, NULL);
    if (library_utf == NULL) {
        return -1;
    }
    jsize argument_count = (*env)->GetArrayLength(env, java_args);
    char **argv = (char **)calloc((size_t)argument_count + 2, sizeof(char *));
    if (argv == NULL) {
        (*env)->ReleaseStringUTFChars(env, library, library_utf);
        v_throw(env, "cannot allocate native argv");
        return -1;
    }
    argv[0] = v_strdup(library_utf);
    (*env)->ReleaseStringUTFChars(env, library, library_utf);
    if (argv[0] == NULL) {
        free(argv);
        v_throw(env, "cannot allocate native argv[0]");
        return -1;
    }

    for (jsize i = 0; i < argument_count; i++) {
        jstring argument = (jstring)(*env)->GetObjectArrayElement(env, java_args, i);
        const char *argument_utf = (*env)->GetStringUTFChars(env, argument, NULL);
        if (argument_utf == NULL) {
            free(argv[0]);
            free(argv);
            return -1;
        }
        argv[i + 1] = v_strdup(argument_utf);
        (*env)->ReleaseStringUTFChars(env, argument, argument_utf);
        (*env)->DeleteLocalRef(env, argument);
        if (argv[i + 1] == NULL) {
            for (jsize j = 0; j <= i; j++) {
                free(argv[j]);
            }
            free(argv);
            v_throw(env, "cannot allocate native argument");
            return -1;
        }
    }

#ifdef _WIN32
    HMODULE handle = LoadLibraryA(argv[0]);
    if (handle == NULL) {
        v_throw(env, "cannot load packaged V library");
        goto cleanup;
    }
    v_jar_main_fn entry = (v_jar_main_fn)(void *)GetProcAddress(handle, "jar_main");
#else
    void *handle = dlopen(argv[0], RTLD_NOW | RTLD_LOCAL);
    if (handle == NULL) {
        v_throw(env, dlerror());
        goto cleanup;
    }
    v_jar_main_fn entry = (v_jar_main_fn)dlsym(handle, "jar_main");
#endif
    if (entry == NULL) {
        v_throw(env, "packaged library does not export jar_main");
        goto cleanup;
    }
    {
        int result = entry((int)argument_count + 1, argv);
#ifdef _WIN32
        FreeLibrary(handle);
#else
        dlclose(handle);
#endif
        for (jsize i = 0; i <= argument_count; i++) {
            free(argv[i]);
        }
        free(argv);
        (void)ignored;
        return result;
    }

cleanup:
    for (jsize i = 0; i <= argument_count; i++) {
        free(argv[i]);
    }
    free(argv);
    (void)ignored;
    return -1;
}
