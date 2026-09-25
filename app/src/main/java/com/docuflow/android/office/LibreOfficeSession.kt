package com.docuflow.android.office

import android.app.Activity
import android.content.Context
import android.net.Uri
import java.io.File
import org.libreoffice.kit.LibreOfficeKit

class LibreOfficeSession(private val context: Context) : DocumentSession {

    private var openedFile: File? = null
    private var sourceUri: Uri? = null
    private var sourceExtension: String = ""

    suspend fun initialize(activity: Activity): Boolean {
        if (!LibreOfficeKit.init(activity)) {
            return false
        }

        val handle = LibreOfficeKit.getLibreOfficeKitHandle()
            ?: return false

        return NativeLibreOffice.initialize(handle)
    }

    override suspend fun open(uri: Uri) {
        // The native LOKit engine is process-wide. Never leave the previous
        // document alive when switching documents.
        closeCurrentDocument()

        val file = copyUriToWorkingFile(uri)
        try {
            check(NativeLibreOffice.open(file.toURI().toString())) {
                "LibreOfficeKit could not open " + file.name
            }
        } catch (e: Throwable) {
            file.delete()
            throw e
        }

        openedFile = file
        sourceUri = uri
        sourceExtension = file.extension.lowercase()
    }

    override suspend fun save() {
        val file = openedFile ?: error("No document is open")
        val uri = sourceUri ?: error("No source URI is associated with the document")

        val format = when (sourceExtension) {
            "docx" -> "Office Open XML Text"
            "xlsx" -> "Calc MS Excel 2007 XML"
            "pptx" -> "Impress MS PowerPoint 2007 XML"
            "pdf" -> "draw_pdf_Export"
            else -> ""
        }

        check(NativeLibreOffice.saveAs(file.toURI().toString(), format)) {
            "LibreOfficeKit save failed"
        }

        // LOKit writes to the private working copy. Copy the result back to
        // the original Android document URI so Save actually persists it.
        context.contentResolver.openOutputStream(uri, "wt").use { output ->
            requireNotNull(output) { "Unable to open destination URI for writing: $uri" }
            file.inputStream().use { input -> input.copyTo(output) }
        }
    }

    override suspend fun close() {
        closeCurrentDocument()
    }

    private fun closeCurrentDocument() {
        if (openedFile != null) {
            NativeLibreOffice.close()
        }
        openedFile?.delete()
        openedFile = null
        sourceUri = null
        sourceExtension = ""
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
            requireNotNull(input) { "Unable to read document URI: $uri" }
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
