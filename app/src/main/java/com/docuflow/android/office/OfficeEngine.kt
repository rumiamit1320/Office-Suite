package com.docuflow.android.office

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.withContext

class OfficeEngine(private val context: Context) {
    private val session = LibreOfficeSession(context)

    suspend fun initialize(): Boolean =
        withContext(NativeOfficeDispatcher.dispatcher) { session.initialize() }

    suspend fun open(uri: Uri) =
        withContext(NativeOfficeDispatcher.dispatcher) { session.open(uri) }

    suspend fun save() =
        withContext(NativeOfficeDispatcher.dispatcher) { session.save() }

    suspend fun close() =
        withContext(NativeOfficeDispatcher.dispatcher) { session.close() }
}
