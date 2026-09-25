#include <jni.h>
#include <android/log.h>
#include <dlfcn.h>
#include <string>
#include <cstddef>

#define LOG_TAG "DocuFlowLOKit"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

struct LibreOfficeKit;
struct LibreOfficeKitDocument;
using Office = LibreOfficeKit*;
using Doc = LibreOfficeKitDocument*;
using Hook2 = Office (*)(const char*, const char*);

struct OfficeClass;
struct DocClass;
struct LibreOfficeKit { OfficeClass* pClass; };
struct LibreOfficeKitDocument { DocClass* pClass; };

/* Exact LibreOffice 26.x document class layout through the members used here.
 * getPartName() and setPartMode() MUST be present; otherwise later function
 * pointers are shifted and opening a document can crash the process. */
struct OfficeClass {
    size_t nSize;
    void (*destroy)(Office);
    Doc (*documentLoad)(Office, const char*);
    char* (*getError)(Office);
};

struct DocClass {
    size_t nSize;
    void (*destroy)(Doc);
    int (*saveAs)(Doc, const char*, const char*, const char*);
    int (*getDocumentType)(Doc);
    int (*getParts)(Doc);
    char* (*getPartPageRectangles)(Doc);
    char* (*getPartName)(Doc, int);
    void (*setPartMode)(Doc, int);
    int (*getPart)(Doc);
    void (*setPart)(Doc, int);
    void (*paintTile)(Doc, unsigned char*, int, int, int, int, int, int);
    int (*getTileMode)(Doc);
    void (*getDocumentSize)(Doc, long*, long*);
    void (*initializeForRendering)(Doc, const char*);
};

static void* gLoHandle = nullptr;
static Office gOffice = nullptr;
static Doc gDocument = nullptr;

static bool hasMember(size_t nSize, size_t offset, size_t memberSize) {
    return nSize >= offset + memberSize;
}

static bool ensureRuntimeLoaded() {
    if (gLoHandle) return true;
    dlerror();
    gLoHandle = dlopen("liblo-native-code.so", RTLD_NOW | RTLD_GLOBAL);
    if (!gLoHandle) {
        LOGE("liblo-native-code.so not loaded: %s", dlerror());
        return false;
    }
    LOGI("Loaded liblo-native-code.so");
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
    if (gOffice && gOffice->pClass) return JNI_TRUE;
    if (!ensureRuntimeLoaded()) return JNI_FALSE;

    dlerror();
    void* symbol = dlsym(gLoHandle, "libreofficekit_hook_2");
    const char* error = dlerror();
    if (!symbol || error) {
        LOGE("libreofficekit_hook_2 not found: %s", error ? error : "unknown");
        return JNI_FALSE;
    }

    const std::string install = jstringToString(env, installPath);
    const std::string profile = jstringToString(env, userProfile);
    LOGI("Initializing LOK: install=%s profile=%s", install.c_str(), profile.c_str());

    Hook2 hook = reinterpret_cast<Hook2>(symbol);
    gOffice = hook(install.c_str(), profile.empty() ? nullptr : profile.c_str());

    if (!gOffice || !gOffice->pClass) {
        LOGE("LibreOfficeKit initialization failed");
        gOffice = nullptr;
        return JNI_FALSE;
    }

    LOGI("LibreOfficeKit initialized, class size=%zu", gOffice->pClass->nSize);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_docuflow_android_office_NativeLibreOffice_open(
        JNIEnv* env, jobject, jstring uri) {
    if (!gOffice || !gOffice->pClass) return JNI_FALSE;

    const OfficeClass* officeClass = gOffice->pClass;
    if (!hasMember(officeClass->nSize, offsetof(OfficeClass, documentLoad),
                   sizeof(officeClass->documentLoad)) || !officeClass->documentLoad) {
        LOGE("LibreOfficeKit class table is too small for documentLoad");
        return JNI_FALSE;
    }

    if (gDocument && gDocument->pClass && gDocument->pClass->destroy) {
        gDocument->pClass->destroy(gDocument);
        gDocument = nullptr;
    }

    const std::string path = jstringToString(env, uri);
    LOGI("Loading document: %s", path.c_str());

    gDocument = officeClass->documentLoad(gOffice, path.c_str());
    if (!gDocument || !gDocument->pClass) {
        LOGE("documentLoad failed");
        return JNI_FALSE;
    }

    const size_t nSize = gDocument->pClass->nSize;
    LOGI("Document loaded, class size=%zu", nSize);

    if (hasMember(nSize, offsetof(DocClass, initializeForRendering),
                  sizeof(gDocument->pClass->initializeForRendering)) &&
        gDocument->pClass->initializeForRendering) {
        gDocument->pClass->initializeForRendering(gDocument, "{}");
    }

    LOGI("Document opened");
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_docuflow_android_office_NativeLibreOffice_saveAs(
        JNIEnv* env, jobject, jstring uri, jstring format) {
    if (!gDocument || !gDocument->pClass) return JNI_FALSE;
    const size_t nSize = gDocument->pClass->nSize;
    if (!hasMember(nSize, offsetof(DocClass, saveAs),
                   sizeof(gDocument->pClass->saveAs)) || !gDocument->pClass->saveAs)
        return JNI_FALSE;

    const std::string path = jstringToString(env, uri);
    const std::string fmt = jstringToString(env, format);
    return gDocument->pClass->saveAs(
        gDocument, path.c_str(), fmt.empty() ? nullptr : fmt.c_str(), nullptr) == 1
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
    if (!gDocument || !gDocument->pClass) return 0;
    const size_t nSize = gDocument->pClass->nSize;
    if (!hasMember(nSize, offsetof(DocClass, getParts),
                   sizeof(gDocument->pClass->getParts)) || !gDocument->pClass->getParts)
        return 0;
    return gDocument->pClass->getParts(gDocument);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_docuflow_android_office_NativeLibreOffice_getDocumentSize(
        JNIEnv* env, jobject, jlongArray out) {
    if (!gDocument || !gDocument->pClass || !out) return JNI_FALSE;
    const size_t nSize = gDocument->pClass->nSize;
    if (!hasMember(nSize, offsetof(DocClass, getDocumentSize),
                   sizeof(gDocument->pClass->getDocumentSize)) ||
        !gDocument->pClass->getDocumentSize)
        return JNI_FALSE;

    long w = 0, h = 0;
    gDocument->pClass->getDocumentSize(gDocument, &w, &h);
    const jlong values[2] = {static_cast<jlong>(w), static_cast<jlong>(h)};
    env->SetLongArrayRegion(out, 0, 2, values);
    return JNI_TRUE;
}
