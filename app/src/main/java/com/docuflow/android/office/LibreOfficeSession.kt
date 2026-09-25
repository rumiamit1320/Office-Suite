package com.docuflow.android.office
import android.net.Uri
class LibreOfficeSession: DocumentSession {
 override suspend fun open(uri: Uri)=NativeLibreOffice.open(uri.toString())
 override suspend fun save()=NativeLibreOffice.save()
 override suspend fun close()=NativeLibreOffice.close()
}
object NativeLibreOffice {
 init { System.loadLibrary("docuflow-lokit") }
 external fun attach(handle: java.nio.ByteBuffer)
 external fun open(uri:String)
 external fun save()
 external fun close()
 external fun getParts():Int
}
