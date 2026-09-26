#include <jni.h>
#include <android/log.h>
#include <string>
#include "LibreOfficeKit.h"

#define LOG_TAG "DocuFlowLOKit"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using Office = LibreOfficeKit*;
using Doc = LibreOfficeKitDocument*;

static Office gOffice = nullptr;
static Doc gDocument = nullptr;

static std::string jstringToString(JNIEnv* env, jstring value) {
    if (!value) return {};
    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (!chars) return {};
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM*, void*) {
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_docuflow_android_office_NativeLibreOffice_isRuntimeAvailable(JNIEnv*, jobject) {
    return gOffice ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_docuflow_android_office_NativeLibreOffice_initialize(JNIEnv* env, jobject, jobject handleBuffer) {
    if (gOffice && gOffice->pClass) return JNI_TRUE;
    if (!handleBuffer) return JNI_FALSE;
    void* address = env->GetDirectBufferAddress(handleBuffer);
    if (!address) {
        LOGE("LOKit handle is not a direct ByteBuffer");
        return JNI_FALSE;
    }
    auto* office = reinterpret_cast<LibreOfficeKit*>(address);
    if (!office || !office->pClass) {
        LOGE("Invalid LOKit handle");
        return JNI_FALSE;
    }
    if (!LIBREOFFICEKIT_HAS(office, documentLoad) || !office->pClass->documentLoad) {
        LOGE("LOKit office class table does not expose documentLoad");
        return JNI_FALSE;
    }
    gOffice = office;
    LOGI("LOKit handle attached; class size=%zu", gOffice->pClass->nSize);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_docuflow_android_office_NativeLibreOffice_open(JNIEnv* env, jobject, jstring uri) {
    if (!gOffice || !gOffice->pClass || !uri) return JNI_FALSE;

    if (gDocument) {
        if (gDocument->pClass && gDocument->pClass->destroy) {
            gDocument->pClass->destroy(gDocument);
        }
        gDocument = nullptr;
    }

    const std::string url = jstringToString(env, uri);
    if (url.empty()) return JNI_FALSE;
    LOGI("Calling LOKit documentLoad: %s", url.c_str());

    if (LIBREOFFICEKIT_HAS(gOffice, documentLoadWithOptions) &&
        gOffice->pClass->documentLoadWithOptions) {
        gDocument = gOffice->pClass->documentLoadWithOptions(gOffice, url.c_str(), nullptr);
    } else if (LIBREOFFICEKIT_HAS(gOffice, documentLoad) &&
               gOffice->pClass->documentLoad) {
        gDocument = gOffice->pClass->documentLoad(gOffice, url.c_str());
    } else {
        LOGE("No usable LibreOfficeKit document loader");
        return JNI_FALSE;
    }

    if (!gDocument || !gDocument->pClass) {
        char* error = nullptr;
        if (LIBREOFFICEKIT_HAS(gOffice, getError) && gOffice->pClass->getError) {
            error = gOffice->pClass->getError(gOffice);
        }
        LOGE("documentLoad returned null; LOKit error=%s", error ? error : "<none>");
        if (error && LIBREOFFICEKIT_HAS(gOffice, freeError) && gOffice->pClass->freeError) {
            gOffice->pClass->freeError(error);
        }
        gDocument = nullptr;
        return JNI_FALSE;
    }

    if (!LIBREOFFICEKIT_DOCUMENT_HAS(gDocument, destroy) || !gDocument->pClass->destroy ||
        !LIBREOFFICEKIT_DOCUMENT_HAS(gDocument, saveAs) || !gDocument->pClass->saveAs) {
        LOGE("Loaded document does not expose required stable ABI members");
        if (gDocument->pClass->destroy) gDocument->pClass->destroy(gDocument);
        gDocument = nullptr;
        return JNI_FALSE;
    }

    LOGI("Document loaded successfully; class size=%zu", gDocument->pClass->nSize);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_docuflow_android_office_NativeLibreOffice_saveAs(JNIEnv* env, jobject, jstring uri, jstring format) {
    if (!gDocument || !gDocument->pClass ||
        !LIBREOFFICEKIT_DOCUMENT_HAS(gDocument, saveAs) ||
        !gDocument->pClass->saveAs) return JNI_FALSE;

    const std::string url = jstringToString(env, uri);
    const std::string fmt = jstringToString(env, format);
    const int result = gDocument->pClass->saveAs(
        gDocument, url.c_str(), fmt.empty() ? nullptr : fmt.c_str(), nullptr);
    LOGI("LOKit saveAs result=%d", result);
    return result != 0 ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_docuflow_android_office_NativeLibreOffice_close(JNIEnv*, jobject) {
    if (gDocument && gDocument->pClass &&
        LIBREOFFICEKIT_DOCUMENT_HAS(gDocument, destroy) &&
        gDocument->pClass->destroy) {
        LOGI("Destroying LOKit document");
        gDocument->pClass->destroy(gDocument);
    }
    gDocument = nullptr;
}
