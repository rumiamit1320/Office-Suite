#include <jni.h>
#include <android/log.h>
#define LOG_TAG "DocuFlowLOKit"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
static void noop(JNIEnv*,const char*){}
extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM*,void*) { return JNI_VERSION_1_6; }
extern "C" JNIEXPORT void JNICALL Java_com_docuflow_android_office_NativeLibreOffice_attach(JNIEnv* e,jobject,jobject){ LOGI("LOKit runtime not bundled yet"); }
extern "C" JNIEXPORT void JNICALL Java_com_docuflow_android_office_NativeLibreOffice_open(JNIEnv*,jobject,jstring){}
extern "C" JNIEXPORT void JNICALL Java_com_docuflow_android_office_NativeLibreOffice_close(JNIEnv*,jobject){}
extern "C" JNIEXPORT void JNICALL Java_com_docuflow_android_office_NativeLibreOffice_save(JNIEnv*,jobject){}
extern "C" JNIEXPORT jint JNICALL Java_com_docuflow_android_office_NativeLibreOffice_getParts(JNIEnv*,jobject){return 1;}
