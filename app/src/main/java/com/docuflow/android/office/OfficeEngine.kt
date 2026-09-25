package com.docuflow.android.office

import android.app.Activity
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import android.app.Application
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class OfficeEngine(context: Context) {
    private val session = LibreOfficeSession(context.applicationContext)
    private var initialized = false

    suspend fun initialize(activity: Activity): Boolean {
        if (initialized) return true
        return session.initialize(activity).also { initialized = it }
    }

    suspend fun open(uri: Uri) =
        withContext(NativeOfficeDispatcher.dispatcher) { session.open(uri) }

    suspend fun save() =
        withContext(NativeOfficeDispatcher.dispatcher) { session.save() }

    suspend fun close() =
        withContext(NativeOfficeDispatcher.dispatcher) { session.close() }
}

data class DocuFlowUiState(
    val status: String = "Ready",
    val isBusy: Boolean = false,
    val documentOpen: Boolean = false
)

class DocuFlowViewModel(application: Application) : AndroidViewModel(application) {
    private val engine = OfficeEngine(application)
    private val _state = MutableStateFlow(DocuFlowUiState())
    val state: StateFlow<DocuFlowUiState> = _state.asStateFlow()

    fun open(activity: Activity, uri: Uri) {
        if (_state.value.isBusy) return
        viewModelScope.launch {
            _state.value = _state.value.copy(status = "Initializing LibreOfficeKit…", isBusy = true)
            try {
                check(engine.initialize(activity)) { "LibreOfficeKit runtime is not available" }
                _state.value = _state.value.copy(status = "Opening document…")
                engine.open(uri)
                _state.value = _state.value.copy(status = "Document opened", isBusy = false, documentOpen = true)
            } catch (e: Exception) {
                _state.value = _state.value.copy(status = "Open failed: ${e.message ?: e.javaClass.simpleName}", isBusy = false, documentOpen = false)
            }
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
                _state.value = _state.value.copy(status = "Save failed: ${e.message ?: e.javaClass.simpleName}", isBusy = false)
            }
        }
    }

    fun close() {
        if (_state.value.isBusy || !_state.value.documentOpen) return
        viewModelScope.launch {
            _state.value = _state.value.copy(status = "Closing…", isBusy = true)
            try {
                engine.close()
                _state.value = _state.value.copy(status = "Document closed", isBusy = false, documentOpen = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(status = "Close failed: ${e.message ?: e.javaClass.simpleName}", isBusy = false)
            }
        }
    }

    override fun onCleared() {
        runBlocking { runCatching { engine.close() } }
        super.onCleared()
    }
}
