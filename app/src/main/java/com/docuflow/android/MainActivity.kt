package com.docuflow.android

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.docuflow.android.office.DocuFlowViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: DocuFlowViewModel by viewModels()
    private val incomingUri = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        incomingUri.value = intent?.data
        setContent { DocuFlowApp(this, viewModel, incomingUri.value) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingUri.value = intent.data
    }
}

private class DocumentEditorView(
    context: Context,
    private val onTap: (Float, Float, Int) -> Unit,
    private val onPan: (Float, Float) -> Unit,
    private val onText: (String) -> Unit,
    private val onDelete: () -> Unit
) : View(context) {
    private var bitmap: Bitmap? = null
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var moved = false

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        setBackgroundColor(0xFFEFEFEF.toInt())
    }

    fun setBitmap(value: Bitmap?) {
        bitmap = value
        invalidate()
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        bitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }
    }

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        when (event.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> {
                requestFocus()
                downX = event.x
                downY = event.y
                lastX = downX
                lastY = downY
                moved = false
                return true
            }
            android.view.MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastX
                val dy = event.y - lastY
                if (kotlin.math.abs(event.x - downX) > 8 || kotlin.math.abs(event.y - downY) > 8) moved = true
                lastX = event.x
                lastY = event.y
                if (moved) onPan(-dx, -dy)
                return true
            }
            android.view.MotionEvent.ACTION_UP -> {
                if (!moved) {
                    val count = event.eventTime.let { t ->
                        if (t - downY.toLong() < 350) 1 else 1
                    }
                    onTap(event.x, event.y, count)
                    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
                }
                performClick()
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onCheckIsTextEditor(): Boolean = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI
        return object : BaseInputConnection(this, false) {
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                text?.toString()?.takeIf { it.isNotEmpty() }?.let(onText)
                return true
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                if (beforeLength > 0) onDelete()
                return true
            }

            override fun sendKeyEvent(event: KeyEvent): Boolean {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    when (event.keyCode) {
                        KeyEvent.KEYCODE_DEL -> onDelete()
                        KeyEvent.KEYCODE_ENTER -> onText("\n")
                    }
                }
                return true
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocuFlowApp(activity: MainActivity, viewModel: DocuFlowViewModel, incomingUri: Uri?) {
    val uiState by viewModel.state.collectAsState()
    var lastHandledUri by rememberSaveable { mutableStateOf<String?>(null) }

    fun openDocument(uri: Uri) {
        try {
            activity.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: SecurityException) { }
        viewModel.open(activity, uri)
    }

    LaunchedEffect(incomingUri) {
        val key = incomingUri?.toString()
        if (key != null && key != lastHandledUri) {
            lastHandledUri = key
            openDocument(incomingUri)
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            lastHandledUri = uri.toString()
            openDocument(uri)
        }
    }

    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("DocuFlow") },
                    actions = {
                        if (uiState.documentOpen) {
                            TextButton(onClick = viewModel::save, enabled = !uiState.isBusy) { Text("Save") }
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (!uiState.documentOpen) {
                    Spacer(Modifier.height(40.dp))
                    Icon(Icons.Default.FolderOpen, "Open document", Modifier.size(72.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("Office Suite", style = MaterialTheme.typography.headlineMedium)
                    Text("Word • Excel • PowerPoint • PDF")
                    Spacer(Modifier.height(24.dp))
                    Button(enabled = !uiState.isBusy, onClick = { picker.launch(arrayOf("*/*")) }) {
                        Text(if (uiState.isBusy) "Working…" else "Open document")
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(uiState.status)
                } else {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TextButton(onClick = { viewModel.executeCommand(".uno:Undo") }) { Text("Undo") }
                        TextButton(onClick = { viewModel.executeCommand(".uno:Redo") }) { Text("Redo") }
                        TextButton(onClick = { viewModel.executeCommand(".uno:Bold") }) { Text("B") }
                        TextButton(onClick = { viewModel.executeCommand(".uno:Italic") }) { Text("I") }
                        TextButton(onClick = { viewModel.executeCommand(".uno:Underline") }) { Text("U") }
                        TextButton(onClick = { viewModel.executeCommand(".uno:AlignLeft") }) { Text("Left") }
                        TextButton(onClick = { viewModel.executeCommand(".uno:AlignCenter") }) { Text("Center") }
                        TextButton(onClick = { viewModel.executeCommand(".uno:AlignRight") }) { Text("Right") }
                        TextButton(onClick = { viewModel.zoomOut() }) { Text("−") }
                        TextButton(onClick = { viewModel.zoomIn() }) { Text("+") }
                    }

                    Text(
                        uiState.status,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        style = MaterialTheme.typography.labelMedium
                    )

                    AndroidView(
                        modifier = Modifier.fillMaxWidth().weight(1f).padding(8.dp),
                        factory = { context ->
                            DocumentEditorView(
                                context = context,
                                onTap = viewModel::tapDocument,
                                onPan = viewModel::panBy,
                                onText = viewModel::insertText,
                                onDelete = viewModel::deleteBackward
                            )
                        },
                        update = { view ->
                            view.setBitmap(uiState.preview)
                            view.post {
                                if (view.width > 0 && view.height > 0)
                                    viewModel.setViewportSize(view.width, view.height)
                            }
                        }
                    )

                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        OutlinedButton(onClick = { viewModel.executeCommand(".uno:Copy") }) { Text("Copy") }
                        OutlinedButton(onClick = { viewModel.executeCommand(".uno:Cut") }) { Text("Cut") }
                        OutlinedButton(onClick = { viewModel.executeCommand(".uno:Paste") }) { Text("Paste") }
                        OutlinedButton(onClick = viewModel::close) { Text("Close") }
                    }
                }
            }
        }
    }
}
