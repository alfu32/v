#ifndef _WIN32
#define _POSIX_C_SOURCE 200809L
#endif
#include <jni.h>
#include <stdlib.h>
#include <string.h>

#ifdef _WIN32
#include <windows.h>
typedef HMODULE vjar_handle;
#else
#include <dlfcn.h>
typedef void *vjar_handle;
#endif

typedef int (*vjar_main_fn)(int, char **);

JNIEXPORT jint JNICALL Java_org_vlang_NativeMain_runNative(JNIEnv *env, jclass cls,
                                                            jstring library,
                                                            jobjectArray java_args) {
    (void)cls;
    const char *library_path = (*env)->GetStringUTFChars(env, library, NULL);
    if (library_path == NULL) {
        return -1;
    }
    size_t library_length = strlen(library_path);
    char *library_copy = malloc(library_length + 1);
    if (library_copy == NULL) {
        (*env)->ReleaseStringUTFChars(env, library, library_path);
        return -3;
    }
    memcpy(library_copy, library_path, library_length + 1);
    vjar_handle handle;
#ifdef _WIN32
    handle = LoadLibraryA(library_path);
#else
    handle = dlopen(library_path, RTLD_NOW | RTLD_GLOBAL);
#endif
    (*env)->ReleaseStringUTFChars(env, library, library_path);
    if (handle == NULL) {
        free(library_copy);
        return -2;
    }

    jsize java_argc = (*env)->GetArrayLength(env, java_args);
    jsize argc = java_argc + 1;
    char **argv = calloc((size_t)argc + 1, sizeof(char *));
    if (argv == NULL) {
        free(library_copy);
        return -3;
    }
    argv[0] = library_copy;
    for (jsize i = 0; i < java_argc; ++i) {
        jstring value = (jstring)(*env)->GetObjectArrayElement(env, java_args, i);
        const char *text = (*env)->GetStringUTFChars(env, value, NULL);
        if (text == NULL) {
            free(argv[0]);
            free(argv);
            return -4;
        }
        size_t length = strlen(text);
        argv[i + 1] = malloc(length + 1);
        if (argv[i + 1] == NULL) {
            (*env)->ReleaseStringUTFChars(env, value, text);
            for (jsize j = 0; j <= i; ++j) free(argv[j]);
            free(argv);
            return -5;
        }
        memcpy(argv[i + 1], text, length + 1);
        (*env)->ReleaseStringUTFChars(env, value, text);
        (*env)->DeleteLocalRef(env, value);
    }

#ifdef _WIN32
    vjar_main_fn entry = (vjar_main_fn)GetProcAddress(handle, "jar_main");
#else
    vjar_main_fn entry = (vjar_main_fn)dlsym(handle, "jar_main");
#endif
    if (entry == NULL) {
        for (jsize i = 0; i < argc; ++i) free(argv[i]);
        free(argv);
        return -6;
    }
    int result = entry((int)argc, argv);
    for (jsize i = 0; i < argc; ++i) free(argv[i]);
    free(argv);
    return result;
}

JNIEXPORT void JNICALL Java_org_vlang_NativeMain_setEnv(JNIEnv *env, jclass cls,
                                                         jstring name, jstring value) {
    (void)cls;
    const char *name_text = (*env)->GetStringUTFChars(env, name, NULL);
    const char *value_text = (*env)->GetStringUTFChars(env, value, NULL);
    if (name_text == NULL || value_text == NULL) {
        if (name_text != NULL) (*env)->ReleaseStringUTFChars(env, name, name_text);
        if (value_text != NULL) (*env)->ReleaseStringUTFChars(env, value, value_text);
        return;
    }
#ifdef _WIN32
    _putenv_s(name_text, value_text);
#else
    setenv(name_text, value_text, 1);
#endif
    (*env)->ReleaseStringUTFChars(env, name, name_text);
    (*env)->ReleaseStringUTFChars(env, value, value_text);
}
