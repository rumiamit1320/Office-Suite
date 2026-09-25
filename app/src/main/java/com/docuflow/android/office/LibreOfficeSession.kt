package com.docuflow.android.office

import android.app.Activity
import android.content.Context
import android.net.Uri
import java.io.File
import org.libreoffice.kit.LibreOfficeKit

class LibreOfficeSession(private val context: Context) : DocumentSession {

    private var openedFile: File? = null

    suspend fun initialize(): Boolean {
        val activity = context as? Activity
            ?: return false

        if (!LibreOfficeKit.init(activity)) {
            return false
        }

        val handle = LibreOfficeKit.getLibreOfficeKitHandle()
            ?: return false

        return NativeLibreOffice.initialize(handle)
    }

    override suspend fun open(uri: Uri) {
        val file = copyUriToWorkingFile(uri)
        check(NativeLibreOffice.open(file.toURI().toString())) {
            "LibreOfficeKit could not open " + file.name
        }
        openedFile = file
    }

    override suspend fun save() {
        val file = openedFile ?: error("No document is open")
        val format = when (file.extension.lowercase()) {
            "docx" -> "Office Open XML Text"
            "xlsx" -> "Calc MS Excel 2007 XML"
            "pptx" -> "Impress MS PowerPoint 2007 XML"
            "pdf" -> "pdf"
            else -> ""
        }
        check(NativeLibreOffice.saveAs(file.toURI().toString(), format)) {
            "LibreOfficeKit save failed"
        }
    }

    override suspend fun close() {
        NativeLibreOffice.close()
        openedFile = null
    }

    private fun copyUriToWorkingFile(uri: Uri): File {
        val name = uri.lastPathSegment
            ?.substringAfterLast('/')
            ?.takeIf { it.isNotBlank() }
            ?: "document"

        val extension = name.substringAfterLast('.', "")
            .takeIf { it.isNotBlank() }

        val safeName = "opened_" + System.currentTimeMillis() +
            (extension?.let { ".$it" } ?: "")

        val target = File(context.cacheDir, safeName)

        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Unable to read document URI: " + uri }
            target.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        return target
    }
}

object NativeLibreOffice {
    init { System.loadLibrary("docuflow-lokit") }

    external fun isRuntimeAvailable(): Boolean
    external fun initialize(handle: java.nio.ByteBuffer): Boolean
    external fun open(uri: String): Boolean
    external fun saveAs(uri: String, format: String): Boolean
    external fun close()
    external fun getParts(): Int
    external fun getDocumentSize(out: LongArray): Boolean
}
