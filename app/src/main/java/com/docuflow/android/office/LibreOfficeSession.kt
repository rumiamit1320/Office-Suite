package com.docuflow.android.office

import android.content.Context
import android.net.Uri
import java.io.File

class LibreOfficeSession(private val context: Context) : DocumentSession {

    companion object {
        private const val RUNTIME_VERSION = "26.2.5.2"
        private const val ASSET_ROOT = "libreoffice"
    }

    private var openedFile: File? = null

    suspend fun initialize(): Boolean {
        val runtimeRoot = installBundledRuntime()
        val profile = File(context.filesDir, "lo-profile")
        profile.mkdirs()

        return NativeLibreOffice.initialize(
            runtimeRoot.absolutePath,
            "file:" + profile.absolutePath
        )
    }

    override suspend fun open(uri: Uri) {
        val file = copyUriToWorkingFile(uri)
        check(NativeLibreOffice.open(file.toURI().toString())) {
            "LibreOfficeKit could not open " + file.absolutePath
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

    private fun installBundledRuntime(): File {
        val root = File(context.filesDir, ASSET_ROOT)
        val marker = File(root, ".runtime-" + RUNTIME_VERSION)

        if (marker.exists()) return root

        if (root.exists()) root.deleteRecursively()
        root.mkdirs()

        copyAssetTree(ASSET_ROOT, root)
        marker.writeText(RUNTIME_VERSION)
        return root
    }

    private fun copyAssetTree(assetPath: String, destination: File) {
        val manager = context.assets
        val children = manager.list(assetPath).orEmpty()

        if (children.isEmpty()) {
            destination.parentFile?.mkdirs()
            manager.open(assetPath).use { input ->
                destination.outputStream().use { output -> input.copyTo(output) }
            }
            return
        }

        destination.mkdirs()
        for (child in children) {
            copyAssetTree(
                "$assetPath/$child",
                File(destination, child)
            )
        }
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
    external fun initialize(installPath: String, userProfile: String): Boolean
    external fun open(uri: String): Boolean
    external fun saveAs(uri: String, format: String): Boolean
    external fun close()
    external fun getParts(): Int
    external fun getDocumentSize(out: LongArray): Boolean
}
