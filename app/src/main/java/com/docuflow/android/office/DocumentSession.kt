package com.docuflow.android.office
import android.net.Uri
interface DocumentSession {
 suspend fun open(uri: Uri)
 suspend fun save()
 suspend fun close()
}
