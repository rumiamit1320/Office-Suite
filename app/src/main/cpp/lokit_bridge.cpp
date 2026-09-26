#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <algorithm>
#define LOK_USE_UNSTABLE_API
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
    if (!address) return JNI_FALSE;
    auto* office = reinterpret_cast<LibreOfficeKit*>(address);
    if (!office || !office->pClass) return JNI_FALSE;
    if (!LIBREOFFICEKIT_HAS(office, documentLoad) || !office->pClass->documentLoad) return JNI_FALSE;
    gOffice = office;
    LOGI("LOKit handle attached; class size=%zu", gOffice->pClass->nSize);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_docuflow_android_office_NativeLibreOffice_open(JNIEnv* env, jobject, jstring uri) {
    if (!gOffice || !gOffice->pClass || !uri) return JNI_FALSE;

    if (gDocument) {
        if (gDocument->pClass && gDocument->pClass->destroy) gDocument->pClass->destroy(gDocument);
        gDocument = nullptr;
    }

    const std::string url = jstringToString(env, uri);
    if (url.empty()) return JNI_FALSE;
    LOGI("Loading %s", url.c_str());

    if (LIBREOFFICEKIT_HAS(gOffice, documentLoadWithOptions) && gOffice->pClass->documentLoadWithOptions)
        gDocument = gOffice->pClass->documentLoadWithOptions(gOffice, url.c_str(), nullptr);
    else if (gOffice->pClass->documentLoad)
        gDocument = gOffice->pClass->documentLoad(gOffice, url.c_str());

    if (!gDocument || !gDocument->pClass) {
        char* error = nullptr;
        if (LIBREOFFICEKIT_HAS(gOffice, getError) && gOffice->pClass->getError) error = gOffice->pClass->getError(gOffice);
        LOGE("documentLoad failed: %s", error ? error : "<none>");
        if (error && LIBREOFFICEKIT_HAS(gOffice, freeError) && gOffice->pClass->freeError) gOffice->pClass->freeError(error);
        gDocument = nullptr;
        return JNI_FALSE;
    }

    if (!LIBREOFFICEKIT_DOCUMENT_HAS(gDocument, destroy) || !gDocument->pClass->destroy ||
        !LIBREOFFICEKIT_DOCUMENT_HAS(gDocument, saveAs) || !gDocument->pClass->saveAs) {
        gDocument->pClass->destroy(gDocument);
        gDocument = nullptr;
        return JNI_FALSE;
    }

    if (!LIBREOFFICEKIT_DOCUMENT_HAS(gDocument, initializeForRendering) ||
        !gDocument->pClass->initializeForRendering ||
        !LIBREOFFICEKIT_DOCUMENT_HAS(gDocument, paintTile) ||
        !gDocument->pClass->paintTile ||
        !LIBREOFFICEKIT_DOCUMENT_HAS(gDocument, getDocumentSize) ||
        !gDocument->pClass->getDocumentSize) {
        LOGE("Document lacks tiled editing/rendering API");
        gDocument->pClass->destroy(gDocument);
        gDocument = nullptr;
        return JNI_FALSE;
    }

    gDocument->pClass->initializeForRendering(gDocument, nullptr);
    LOGI("Document loaded and initialized for editing/rendering");
    return JNI_TRUE;
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_com_docuflow_android_office_NativeLibreOffice_getDocumentSize(JNIEnv* env, jobject) {
    if (!gDocument || !gDocument->pClass ||
        !LIBREOFFICEKIT_DOCUMENT_HAS(gDocument, getDocumentSize) ||
        !gDocument->pClass->getDocumentSize) return nullptr;
    long w = 0, h = 0;
    gDocument->pClass->getDocumentSize(gDocument, &w, &h);
    jlongArray result = env->NewLongArray(2);
    if (!result) return nullptr;
    jlong values[2] = {static_cast<jlong>(w), static_cast<jlong>(h)};
    env->SetLongArrayRegion(result, 0, 2, values);
    return result;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_docuflow_android_office_NativeLibreOffice_renderViewport(
        JNIEnv* env, jobject, jint canvasWidth, jint canvasHeight,
        jlong tilePosX, jlong tilePosY, jdouble twipsPerPixel) {
    if (!gDocument || !gDocument->pClass ||
        !LIBREOFFICEKIT_DOCUMENT_HAS(gDocument, paintTile) ||
        !gDocument->pClass->paintTile ||
        !LIBREOFFICEKIT_DOCUMENT_HAS(gDocument, getDocumentSize) ||
        !gDocument->pClass->getDocumentSize) return nullptr;

    if (canvasWidth <= 0 || canvasHeight <= 0 || canvasWidth > 4096 || canvasHeight > 4096 ||
        twipsPerPixel <= 0.1 || twipsPerPixel > 100.0) return nullptr;

    long documentWidth = 0, documentHeight = 0;
    gDocument->pClass->getDocumentSize(gDocument, &documentWidth, &documentHeight);
    if (documentWidth <= 0 || documentHeight <= 0) return nullptr;

    const long tileWidth = std::max<long>(1, std::min<long>(
        documentWidth, static_cast<long>(canvasWidth * twipsPerPixel)));
    const long tileHeight = std::max<long>(1, std::min<long>(
        documentHeight, static_cast<long>(canvasHeight * twipsPerPixel)));
    const long x = std::max<long>(0, std::min<long>(tilePosX, std::max<long>(0, documentWidth - tileWidth)));
    const long y = std::max<long>(0, std::min<long>(tilePosY, std::max<long>(0, documentHeight - tileHeight)));

    if (LIBREOFFICEKIT_DOCUMENT_HAS(gDocument, setClientZoom) && gDocument->pClass->setClientZoom)
        gDocument->pClass->setClientZoom(gDocument, canvasWidth, canvasHeight,
                                         static_cast<int>(tileWidth), static_cast<int>(tileHeight));
    if (LIBREOFFICEKIT_DOCUMENT_HAS(gDocument, setClientVisibleArea) && gDocument->pClass->setClientVisibleArea)
        gDocument->pClass->setClientVisibleArea(gDocument, static_cast<int>(x), static_cast<int>(y),
                                                static_cast<int>(tileWidth), static_cast<int>(tileHeight));

    std::vector<unsigned char> pixels(static_cast<size_t>(canvasWidth) *
                                      static_cast<size_t>(canvasHeight) * 4, 0);
    gDocument->pClass->paintTile(gDocument, pixels.data(), canvasWidth, canvasHeight,
                                 static_cast<int>(x), static_cast<int>(y),
                                 static_cast<int>(tileWidth), static_cast<int>(tileHeight));

    int tileMode = 1; // LOK_TILEMODE_BGRA
    if (LIBREOFFICEKIT_DOCUMENT_HAS(gDocument, getTileMode) && gDocument->pClass->getTileMode)
        tileMode = gDocument->pClass->getTileMode(gDocument);
    if (tileMode == 0) {
        for (size_t i = 0; i + 3 < pixels.size(); i += 4)
            std::swap(pixels[i], pixels[i + 2]);
    }

    jbyteArray result = env->NewByteArray(static_cast<jsize>(pixels.size()));
    if (!result) return nullptr;
    env->SetByteArrayRegion(result, 0, static_cast<jsize>(pixels.size()),
                            reinterpret_cast<const jbyte*>(pixels.data()));
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_com_docuflow_android_office_NativeLibreOffice_postMouse(
        JNIEnv*, jobject, jint type, jint x, jint y, jint count) {
    if (!gDocument || !gDocument->pClass ||
        !LIBREOFFICEKIT_DOCUMENT_HAS(gDocument, postMouseEvent) ||
        !gDocument->pClass->postMouseEvent) return;
    gDocument->pClass->postMouseEvent(gDocument, type, x, y, count, 1, 0);
}

extern "C" JNIEXPORT void JNICALL
Java_com_docuflow_android_office_NativeLibreOffice_postText(
        JNIEnv* env, jobject, jstring text) {
    if (!gDocument || !gDocument->pClass || !text) return;
    if (!LIBREOFFICEKIT_DOCUMENT_HAS(gDocument, postWindowExtTextInputEvent) ||
        !gDocument->pClass->postWindowExtTextInputEvent) return;
    const std::string value = jstringToString(env, text);
    if (value.empty()) return;
    gDocument->pClass->postWindowExtTextInputEvent(gDocument, 0, 0, value.c_str());
    gDocument->pClass->postWindowExtTextInputEvent(gDocument, 0, 2, "");
}

extern "C" JNIEXPORT void JNICALL
Java_com_docuflow_android_office_NativeLibreOffice_command(
        JNIEnv* env, jobject, jstring command) {
    if (!gDocument || !gDocument->pClass || !command ||
        !LIBREOFFICEKIT_DOCUMENT_HAS(gDocument, postUnoCommand) ||
        !gDocument->pClass->postUnoCommand) return;
    const std::string value = jstringToString(env, command);
    if (!value.empty()) gDocument->pClass->postUnoCommand(gDocument, value.c_str(), nullptr, false);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_docuflow_android_office_NativeLibreOffice_saveAs(
        JNIEnv* env, jobject, jstring uri, jstring format) {
    if (!gDocument || !gDocument->pClass ||
        !LIBREOFFICEKIT_DOCUMENT_HAS(gDocument, saveAs) ||
        !gDocument->pClass->saveAs) return JNI_FALSE;
    const std::string url = jstringToString(env, uri);
    const std::string fmt = jstringToString(env, format);
    const int result = gDocument->pClass->saveAs(gDocument, url.c_str(),
        fmt.empty() ? nullptr : fmt.c_str(), nullptr);
    return result != 0 ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_docuflow_android_office_NativeLibreOffice_close(JNIEnv*, jobject) {
    if (gDocument && gDocument->pClass &&
        LIBREOFFICEKIT_DOCUMENT_HAS(gDocument, destroy) &&
        gDocument->pClass->destroy) {
        gDocument->pClass->destroy(gDocument);
    }
    gDocument = nullptr;
}
