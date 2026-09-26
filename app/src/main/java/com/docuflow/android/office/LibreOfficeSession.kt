package com.docuflow.android.office

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.nio.ByteBuffer
import org.libreoffice.kit.LibreOfficeKit

class LibreOfficeSession(private val context: Context) : DocumentSession {
    private var openedFile: File? = null
    private var sourceUri: Uri? = null
    private var sourceExtension: String = ""

    suspend fun documentKind(): DocumentKind = when (sourceExtension) {
        "xls", "xlsx", "ods", "csv" -> DocumentKind.CALC
        "doc", "docx", "odt", "rtf", "txt" -> DocumentKind.WRITER
        "ppt", "pptx", "odp" -> DocumentKind.IMPRESS
        "pdf" -> DocumentKind.PDF
        else -> DocumentKind.OTHER
    }

    suspend fun initialize(activity: Activity): Boolean {
        if (!LibreOfficeKit.init(activity)) return false
        val handle = LibreOfficeKit.getLibreOfficeKitHandle()
        if (handle == null) {
            LibreOfficeKit.redirectStdio(false)
            return false
        }
        val initialized = NativeLibreOffice.initialize(handle)
        if (!initialized) LibreOfficeKit.redirectStdio(false)
        return initialized
    }

    override suspend fun open(uri: Uri) {
        closeCurrentDocument()
        val metadata = queryDocumentMetadata(uri)
        val file = copyUriToWorkingFile(uri, metadata.displayName, metadata.mimeType)
        try {
            check(NativeLibreOffice.open(file.toURI().toString())) { "LibreOfficeKit could not open " + file.name }
        } catch (t: Throwable) {
            file.delete()
            throw t
        }
        openedFile = file
        sourceUri = uri
        sourceExtension = file.extension.lowercase()
    }

    suspend fun documentSize(): LongArray = NativeLibreOffice.getDocumentSize() ?: longArrayOf(0L, 0L)

    suspend fun renderViewport(width: Int, height: Int, xTwips: Long, yTwips: Long, twipsPerPixel: Double): Bitmap {
        val bytes = NativeLibreOffice.renderViewport(width, height, xTwips, yTwips, twipsPerPixel)
            ?: error("LibreOfficeKit returned no rendered viewport")
        require(bytes.size == width * height * 4) { "Unexpected rendered buffer size: " + bytes.size }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(bytes))
        return bitmap
    }

    suspend fun postMouse(type: Int, xTwips: Int, yTwips: Int, count: Int = 1) =
        NativeLibreOffice.postMouse(type, xTwips, yTwips, count)

    suspend fun postText(text: String) {
        if (text.isNotEmpty()) NativeLibreOffice.postText(text)
    }

    suspend fun command(command: String) = NativeLibreOffice.command(command)

    override suspend fun save() {
        val file = openedFile ?: error("No document is open")
        val uri = sourceUri ?: error("No source URI is associated with the document")
        val format = filterForExtension(sourceExtension)
        check(NativeLibreOffice.saveAs(file.toURI().toString(), format)) { "LibreOfficeKit save failed" }

        val backup = File.createTempFile("docuflow-save-", ".bak", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Unable to read original document for backup: " + uri }
                backup.outputStream().use { output -> input.copyTo(output) }
            }
            try {
                context.contentResolver.openOutputStream(uri, "wt").use { output ->
                    requireNotNull(output) { "Unable to open destination URI for writing: " + uri }
                    file.inputStream().use { input -> input.copyTo(output) }
                }
            } catch (writeFailure: Throwable) {
                runCatching {
                    context.contentResolver.openOutputStream(uri, "wt").use { output ->
                        requireNotNull(output) { "Unable to restore destination URI: " + uri }
                        backup.inputStream().use { input -> input.copyTo(output) }
                    }
                }
                throw writeFailure
            }
        } finally {
            backup.delete()
        }
    }

    override suspend fun close() { closeCurrentDocument() }

    private fun closeCurrentDocument() {
        if (openedFile != null) NativeLibreOffice.close()
        openedFile?.delete()
        openedFile = null
        sourceUri?.let { uri ->
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
        }
        sourceUri = null
        sourceExtension = ""
    }

    private data class DocumentMetadata(val displayName: String?, val mimeType: String?)

    private fun queryDocumentMetadata(uri: Uri): DocumentMetadata {
        var displayName: String? = null
        val mimeType = context.contentResolver.getType(uri)
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0 && !cursor.isNull(nameIndex)) displayName = cursor.getString(nameIndex)
            }
        } catch (_: Exception) { }
        return DocumentMetadata(displayName, mimeType)
    }

    private fun copyUriToWorkingFile(uri: Uri, displayName: String?, mimeType: String?): File {
        val fallbackName = uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "document"
        val name = displayName?.takeIf { it.isNotBlank() } ?: fallbackName
        val extension = extensionFromName(name) ?: extensionFromMimeType(mimeType)
        val target = File(context.cacheDir, "opened_" + System.currentTimeMillis() + (extension?.let { "." + it } ?: ""))
        try {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Unable to read document URI: " + uri }
                target.outputStream().use { output -> input.copyTo(output) }
            }
        } catch (t: Throwable) {
            target.delete()
            throw t
        }
        return target
    }

    private fun extensionFromName(name: String): String? {
        val dot = name.lastIndexOf('.')
        if (dot <= 0 || dot == name.lastIndex) return null
        return name.substring(dot + 1).lowercase().takeIf { it.length <= 10 }
    }

    private fun extensionFromMimeType(mimeType: String?): String? = when (mimeType) {
        "application/pdf" -> "pdf"
        "application/msword" -> "doc"
        "application/vnd.ms-excel" -> "xls"
        "application/vnd.ms-powerpoint" -> "ppt"
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx"
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> "xlsx"
        "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> "pptx"
        "application/vnd.oasis.opendocument.text" -> "odt"
        "application/vnd.oasis.opendocument.spreadsheet" -> "ods"
        "application/vnd.oasis.opendocument.presentation" -> "odp"
        "text/csv" -> "csv"
        "text/plain" -> "txt"
        "application/rtf" -> "rtf"
        else -> null
    }

    private fun filterForExtension(extension: String): String? = when (extension) {
        "docx" -> "Office Open XML Text"
        "xlsx" -> "Calc MS Excel 2007 XML"
        "pptx" -> "Impress MS PowerPoint 2007 XML"
        "pdf" -> "draw_pdf_Export"
        "odt" -> "writer8"
        "ods" -> "calc8"
        "odp" -> "impress8"
        "doc" -> "MS Word 97"
        "xls" -> "MS Excel 97"
        "ppt" -> "MS PowerPoint 97"
        "rtf" -> "Rich Text Format"
        "txt" -> "Text"
        "csv" -> "Text - txt - csv (StarCalc)"
        else -> null
    }
}

object NativeLibreOffice {
    init { System.loadLibrary("docuflow-lokit") }
    external fun isRuntimeAvailable(): Boolean
    external fun initialize(handle: java.nio.ByteBuffer): Boolean
    external fun open(uri: String): Boolean
    external fun getDocumentSize(): LongArray?
    external fun renderViewport(width: Int, height: Int, xTwips: Long, yTwips: Long, twipsPerPixel: Double): ByteArray?
    external fun postMouse(type: Int, xTwips: Int, yTwips: Int, count: Int)
    external fun postText(text: String)
    external fun command(command: String)
    external fun saveAs(uri: String, format: String?): Boolean
    external fun close()
}
