#include <jni.h>
#include <string.h>
#include <android/log.h>
#include <pthread.h>
#include <unistd.h>
#include <stdlib.h>

#define LOG_TAG "QEMU_WRAPPER"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {
    #include "display.h"
    #include "input.h"
}

static JavaVM* g_jvm = NULL;
static pthread_t g_qemu_thread;
static int g_qemu_running = 0;
static DisplayInfo g_display_info = {0, 0, 0};

struct StartArgs {
    int argc;
    char** argv;
};

extern "C" int qemu_main(int argc, char** argv);

static void* qemu_thread_func(void* arg) {
    StartArgs* args = (StartArgs*)arg;
    
    LOGD("QEMU thread starting with %d args", args->argc);
    
    int ret = qemu_main(args->argc, args->argv);
    
    LOGD("QEMU main returned: %d", ret);
    g_qemu_running = 0;
    
    for (int i = 0; i < args->argc; i++) {
        free(args->argv[i]);
    }
    free(args->argv);
    free(args);
    
    return NULL;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_andwin_vm_native_QEMUWrapper_nativeInit(
        JNIEnv* env, jobject thiz, jstring dataPath) {
    
    const char* path = env->GetStringUTFChars(dataPath, NULL);
    LOGD("Initializing QEMU with data path: %s", path);
    
    setenv("QEMU_DATA_PATH", path, 1);
    
    env->ReleaseStringUTFChars(dataPath, path);
    
    return display_init();
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_andwin_vm_native_QEMUWrapper_nativeStartVM(
        JNIEnv* env, jobject thiz, jobjectArray args) {
    
    if (g_qemu_running) {
        LOGE("VM is already running");
        return NULL;
    }
    
    int argc = env->GetArrayLength(args);
    StartArgs* start_args = (StartArgs*)malloc(sizeof(StartArgs));
    start_args->argc = argc + 1;
    start_args->argv = (char**)malloc(sizeof(char*) * (argc + 1));
    
    start_args->argv[0] = strdup("qemu-system-aarch64");
    
    for (int i = 0; i < argc; i++) {
        jstring arg = (jstring)env->GetObjectArrayElement(args, i);
        const char* argStr = env->GetStringUTFChars(arg, NULL);
        start_args->argv[i + 1] = strdup(argStr);
        env->ReleaseStringUTFChars(arg, argStr);
        env->DeleteLocalRef(arg);
    }
    
    g_qemu_running = 1;
    
    int ret = pthread_create(&g_qemu_thread, NULL, qemu_thread_func, start_args);
    if (ret != 0) {
        LOGE("Failed to create QEMU thread: %d", ret);
        g_qemu_running = 0;
        return NULL;
    }
    
    while (g_qemu_running && !display_is_ready()) {
        usleep(100000);
    }
    
    g_display_info = display_get_info();
    
    jclass cls = env->FindClass("com/andwin/vm/native/NativeDisplayInfo");
    jmethodID constructor = env->GetMethodID(cls, "<init>", "(IIJ)V");
    
    jobject result = env->NewObject(cls, constructor,
        g_display_info.width,
        g_display_info.height,
        (jlong)g_display_info.framebuffer
    );
    
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_com_andwin_vm_native_QEMUWrapper_nativeStopVM(
        JNIEnv* env, jobject thiz) {
    
    if (!g_qemu_running) {
        return;
    }
    
    LOGD("Stopping QEMU VM");
    
    display_shutdown();
    
    g_qemu_running = 0;
}

extern "C" JNIEXPORT void JNICALL
Java_com_andwin_vm_native_QEMUWrapper_nativeSendInputEvent(
        JNIEnv* env, jobject thiz, jint type, jint code, jint value) {
    
    if (!g_qemu_running) {
        return;
    }
    
    input_send_event(type, code, value);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_andwin_vm_native_QEMUWrapper_nativeGetFramebuffer(
        JNIEnv* env, jobject thiz) {
    
    return (jlong)display_get_framebuffer();
}

extern "C" JNIEXPORT void JNICALL
Java_com_andwin_vm_native_QEMUWrapper_nativeCleanup(
        JNIEnv* env, jobject thiz) {
    
    LOGD("Cleaning up QEMU");
    
    if (g_qemu_running) {
        display_shutdown();
        g_qemu_running = 0;
    }
    
    display_cleanup();
}

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    g_jvm = vm;
    LOGD("QEMU wrapper loaded");
    return JNI_VERSION_1_6;
}
