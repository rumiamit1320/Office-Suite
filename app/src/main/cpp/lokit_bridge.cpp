#include <jni.h>
#include <android/log.h>
#include <dlfcn.h>
#include <string>

#define LOG_TAG "DocuFlowLOKit"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

struct LibreOfficeKit;
struct LibreOfficeKitDocument;
using Office = LibreOfficeKit*;
using Doc = LibreOfficeKitDocument*;
using Hook2 = Office (*)(const char*, const char*);

struct OfficeClass {
    size_t nSize;
    void (*destroy)(Office);
    Doc (*documentLoad)(Office, const char*);
    char* (*getError)(Office);
};
struct OfficeStruct { OfficeClass* pClass; };

struct DocClass {
    size_t nSize;
    void (*destroy)(Doc);
    int (*saveAs)(Doc, const char*, const char*, const char*);
    int (*getDocumentType)(Doc);
    int (*getParts)(Doc);
    char* (*getPartPageRectangles)(Doc);
    int (*getPart)(Doc);
    void (*setPart)(Doc, int);
    void (*paintTile)(Doc, unsigned char*, int, int, int, int, int, int);
    int (*getTileMode)(Doc);
    void (*getDocumentSize)(Doc, long*, long*);
    void (*initializeForRendering)(Doc, const char*);
};
struct DocStruct { DocClass* pClass; };

static void* gLoHandle = nullptr;
static Office gOffice = nullptr;
static Doc gDocument = nullptr;

static bool ensureRuntimeLoaded() {
    if (gLoHandle) return true;
    gLoHandle = dlopen("liblo-native-code.so", RTLD_NOW | RTLD_GLOBAL);
    if (!gLoHandle) {
        LOGE("liblo-native-code.so not loaded: %s", dlerror());
        return false;
    }
    return true;
}

static std::string jstringToString(JNIEnv* env, jstring value) {
    if (!value) return {};
    const char* chars = env->GetStringUTFChars(value, nullptr);
    std::string result = chars ? chars : "";
    if (chars) env->ReleaseStringUTFChars(value, chars);
    return result;
}

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM*, void*) {
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_docuflow_android_office_NativeLibreOffice_isRuntimeAvailable(JNIEnv*, jobject) {
    return ensureRuntimeLoaded() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_docuflow_android_office_NativeLibreOffice_initialize(
        JNIEnv* env, jobject, jstring installPath, jstring userProfile) {
    if (gOffice) return JNI_TRUE;
    if (!ensureRuntimeLoaded()) return JNI_FALSE;

    void* symbol = dlsym(gLoHandle, "libreofficekit_hook_2");
    if (!symbol) {
        LOGE("libreofficekit_hook_2 not found");
        return JNI_FALSE;
    }

    const std::string install = jstringToString(env, installPath);
    const std::string profile = jstringToString(env, userProfile);
    Hook2 hook = reinterpret_cast<Hook2>(symbol);
    gOffice = hook(install.c_str(), profile.empty() ? nullptr : profile.c_str());

    if (!gOffice || !gOffice->pClass) {
        LOGE("LibreOfficeKit initialization failed");
        gOffice = nullptr;
        return JNI_FALSE;
    }

    LOGI("LibreOfficeKit initialized");
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_docuflow_android_office_NativeLibreOffice_open(
        JNIEnv* env, jobject, jstring uri) {
    if (!gOffice || !gOffice->pClass || !gOffice->pClass->documentLoad) return JNI_FALSE;
    if (gDocument && gDocument->pClass && gDocument->pClass->destroy) {
        gDocument->pClass->destroy(gDocument);
        gDocument = nullptr;
    }

    const std::string path = jstringToString(env, uri);
    gDocument = gOffice->pClass->documentLoad(gOffice, path.c_str());
    if (!gDocument || !gDocument->pClass) {
        LOGE("documentLoad failed");
        return JNI_FALSE;
    }

    if (gDocument->pClass->initializeForRendering)
        gDocument->pClass->initializeForRendering(gDocument, nullptr);

    LOGI("Document opened");
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_docuflow_android_office_NativeLibreOffice_saveAs(
        JNIEnv* env, jobject, jstring uri, jstring format) {
    if (!gDocument || !gDocument->pClass || !gDocument->pClass->saveAs) return JNI_FALSE;
    const std::string path = jstringToString(env, uri);
    const std::string fmt = jstringToString(env, format);
    return gDocument->pClass->saveAs(gDocument, path.c_str(),
                                     fmt.empty() ? nullptr : fmt.c_str(), nullptr) == 1
           ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_docuflow_android_office_NativeLibreOffice_close(JNIEnv*, jobject) {
    if (gDocument && gDocument->pClass && gDocument->pClass->destroy) {
        gDocument->pClass->destroy(gDocument);
        gDocument = nullptr;
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_com_docuflow_android_office_NativeLibreOffice_getParts(JNIEnv*, jobject) {
    if (!gDocument || !gDocument->pClass || !gDocument->pClass->getParts) return 0;
    return gDocument->pClass->getParts(gDocument);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_docuflow_android_office_NativeLibreOffice_getDocumentSize(
        JNIEnv* env, jobject, jlongArray out) {
    if (!gDocument || !gDocument->pClass || !gDocument->pClass->getDocumentSize || !out) return JNI_FALSE;
    long w = 0, h = 0;
    gDocument->pClass->getDocumentSize(gDocument, &w, &h);
    const jlong values[2] = {static_cast<jlong>(w), static_cast<jlong>(h)};
    env->SetLongArrayRegion(out, 0, 2, values);
    return JNI_TRUE;
}
