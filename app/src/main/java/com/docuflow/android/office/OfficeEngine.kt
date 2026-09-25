package com.docuflow.android.office

import android.app.Activity
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.withContext

class OfficeEngine(context: Context) {
    private val session = LibreOfficeSession(context.applicationContext)

    suspend fun initialize(activity: Activity): Boolean {
        // LibreOffice's Android bootstrap receives an Activity and should be
        // initialized from the Android main thread.
        val initialized = session.initialize(activity)
        if (!initialized) return false
        return withContext(NativeOfficeDispatcher.dispatcher) {
            true
        }
    }

    suspend fun open(uri: Uri) =
        withContext(NativeOfficeDispatcher.dispatcher) { session.open(uri) }

    suspend fun save() =
        withContext(NativeOfficeDispatcher.dispatcher) { session.save() }

    suspend fun close() =
        withContext(NativeOfficeDispatcher.dispatcher) { session.close() }
}
