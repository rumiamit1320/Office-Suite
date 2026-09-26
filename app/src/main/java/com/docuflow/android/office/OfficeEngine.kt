package com.docuflow.android.office

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

class OfficeEngine(context: Context) {
    private val session = LibreOfficeSession(context.applicationContext)
    private var initialized = false

    suspend fun initialize(activity: Activity): Boolean =
        withContext(NativeOfficeDispatcher.dispatcher) {
            if (initialized) return@withContext true
            session.initialize(activity).also { initialized = it }
        }

    suspend fun open(uri: Uri) = withContext(NativeOfficeDispatcher.dispatcher) { session.open(uri) }

    suspend fun documentSize(): LongArray =
        withContext(NativeOfficeDispatcher.dispatcher) { session.documentSize() }

    suspend fun renderViewport(width: Int, height: Int, x: Long, y: Long, scale: Double): Bitmap =
        withContext(NativeOfficeDispatcher.dispatcher) { session.renderViewport(width, height, x, y, scale) }

    suspend fun postMouse(type: Int, x: Int, y: Int, count: Int = 1) =
        withContext(NativeOfficeDispatcher.dispatcher) { session.postMouse(type, x, y, count) }

    suspend fun postText(text: String) =
        withContext(NativeOfficeDispatcher.dispatcher) { session.postText(text) }

    suspend fun command(command: String) =
        withContext(NativeOfficeDispatcher.dispatcher) { session.command(command) }

    suspend fun save() = withContext(NativeOfficeDispatcher.dispatcher) { session.save() }

    suspend fun close() = withContext(NativeOfficeDispatcher.dispatcher) { session.close() }
}

data class DocuFlowUiState(
    val status: String = "Ready",
    val isBusy: Boolean = false,
    val documentOpen: Boolean = false,
    val preview: Bitmap? = null,
    val documentWidthTwips: Long = 0L,
    val documentHeightTwips: Long = 0L,
    val viewportXTwips: Long = 0L,
    val viewportYTwips: Long = 0L
)

class DocuFlowViewModel(application: Application) : AndroidViewModel(application) {
    private val engine = OfficeEngine(application)
    private val _state = MutableStateFlow(DocuFlowUiState())
    val state: StateFlow<DocuFlowUiState> = _state.asStateFlow()

    private var viewportWidthPx = 1080
    private var viewportHeightPx = 1200
    private var twipsPerPixel = 12.0

    fun zoomIn() { twipsPerPixel = max(5.0, twipsPerPixel * 0.85); refreshViewport() }\n\n    fun zoomOut() { twipsPerPixel = min(30.0, twipsPerPixel * 1.18); refreshViewport() }\n\n    fun setViewportSize(width: Int, height: Int) {
        if (width > 0) viewportWidthPx = width
        if (height > 0) viewportHeightPx = height
        if (_state.value.documentOpen) refreshViewport()
    }

    fun open(activity: Activity, uri: Uri) {
        if (_state.value.isBusy) return
        viewModelScope.launch {
            _state.value = _state.value.copy(status = "Initializing LibreOfficeKit…", isBusy = true)
            try {
                check(engine.initialize(activity)) { "LibreOfficeKit runtime is not available" }
                _state.value = _state.value.copy(status = "Opening document…")
                engine.open(uri)
                val size = engine.documentSize()
                _state.value = _state.value.copy(
                    status = "Rendering document…",
                    documentWidthTwips = size.getOrElse(0) { 0L },
                    documentHeightTwips = size.getOrElse(1) { 0L },
                    viewportXTwips = 0L,
                    viewportYTwips = 0L
                )
                val preview = engine.renderViewport(viewportWidthPx, viewportHeightPx, 0L, 0L, twipsPerPixel)
                _state.value = _state.value.copy(
                    status = "Document opened — edit mode",
                    isBusy = false,
                    documentOpen = true,
                    preview = preview
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    status = "Open failed: " + (e.message ?: e.javaClass.simpleName),
                    isBusy = false, documentOpen = false, preview = null
                )
            }
        }
    }

    private fun refreshViewport() {
        if (_state.value.isBusy || !_state.value.documentOpen) return
        viewModelScope.launch {
            try {
                val s = _state.value
                val maxX = max(0L, s.documentWidthTwips - (viewportWidthPx * twipsPerPixel).toLong())
                val maxY = max(0L, s.documentHeightTwips - (viewportHeightPx * twipsPerPixel).toLong())
                val x = min(max(0L, s.viewportXTwips), maxX)
                val y = min(max(0L, s.viewportYTwips), maxY)
                val bitmap = engine.renderViewport(viewportWidthPx, viewportHeightPx, x, y, twipsPerPixel)
                _state.value = _state.value.copy(viewportXTwips = x, viewportYTwips = y, preview = bitmap)
            } catch (e: Exception) {
                _state.value = _state.value.copy(status = "Render failed: " + (e.message ?: e.javaClass.simpleName))
            }
        }
    }

    fun panBy(dxPx: Float, dyPx: Float) {
        if (!_state.value.documentOpen) return
        val s = _state.value
        _state.value = s.copy(
            viewportXTwips = max(0L, s.viewportXTwips + (dxPx * twipsPerPixel).toLong()),
            viewportYTwips = max(0L, s.viewportYTwips + (dyPx * twipsPerPixel).toLong())
        )
        refreshViewport()
    }

    fun tapDocument(xPx: Float, yPx: Float, clickCount: Int = 1) {
        if (!_state.value.documentOpen) return
        viewModelScope.launch {
            val s = _state.value
            val x = s.viewportXTwips + (xPx * twipsPerPixel).toLong()
            val y = s.viewportYTwips + (yPx * twipsPerPixel).toLong()
            engine.postMouse(0, x.toInt(), y.toInt(), clickCount)
            engine.postMouse(1, x.toInt(), y.toInt(), clickCount)
            refreshViewport()
        }
    }

    fun insertText(text: String) {
        if (!_state.value.documentOpen || text.isEmpty()) return
        viewModelScope.launch {
            runCatching { engine.postText(text); refreshViewport() }
        }
    }

    fun deleteBackward() {
        if (!_state.value.documentOpen) return
        viewModelScope.launch {
            runCatching { engine.command(".uno:Delete"); refreshViewport() }
        }
    }

    fun executeCommand(command: String) {
        if (!_state.value.documentOpen) return
        viewModelScope.launch {
            runCatching { engine.command(command); refreshViewport() }
        }
    }

    fun save() {
        if (_state.value.isBusy || !_state.value.documentOpen) return
        viewModelScope.launch {
            _state.value = _state.value.copy(status = "Saving…", isBusy = true)
            try {
                engine.save()
                _state.value = _state.value.copy(status = "Document saved", isBusy = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(status = "Save failed: " + (e.message ?: e.javaClass.simpleName), isBusy = false)
            }
        }
    }

    fun close() {
        if (_state.value.isBusy || !_state.value.documentOpen) return
        viewModelScope.launch {
            _state.value = _state.value.copy(status = "Closing…", isBusy = true)
            try {
                engine.close()
                _state.value = DocuFlowUiState(status = "Document closed")
            } catch (e: Exception) {
                _state.value = _state.value.copy(status = "Close failed: " + (e.message ?: e.javaClass.simpleName), isBusy = false)
            }
        }
    }

    override fun onCleared() {
        CoroutineScope(NativeOfficeDispatcher.dispatcher).launch { runCatching { engine.close() } }
        super.onCleared()
    }
}
