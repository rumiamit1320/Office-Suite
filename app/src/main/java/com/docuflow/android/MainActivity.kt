package com.docuflow.android

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.docuflow.android.office.DocuFlowViewModel
import kotlinx.coroutines.launch
import kotlin.math.abs

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
    private var dragOffsetX = 0f
    private var dragOffsetY = 0f
    private var moved = false
    private var lastTapTime = 0L
    private var lastTapX = 0f
    private var lastTapY = 0f

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
        setBackgroundColor(0xFFEFEFEF.toInt())
    }

    fun setBitmap(value: Bitmap?) {
        bitmap = value
        dragOffsetX = 0f
        dragOffsetY = 0f
        invalidate()
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        bitmap?.let { canvas.drawBitmap(it, dragOffsetX, dragOffsetY, null) }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                requestFocus()
                downX = event.x
                downY = event.y
                dragOffsetX = 0f
                dragOffsetY = 0f
                moved = false
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - downX
                val dy = event.y - downY
                if (abs(dx) > 8f || abs(dy) > 8f) moved = true
                if (moved) {
                    dragOffsetX = dx
                    dragOffsetY = dy
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (moved) {
                    // Commit the final viewport only once. During the gesture the existing
                    // bitmap is translated locally, avoiding a LibreOffice render per MOVE.
                    onPan(-dragOffsetX, -dragOffsetY)
                } else {
                    val now = event.eventTime
                    val doubleTap = now - lastTapTime <= 350L &&
                        abs(event.x - lastTapX) <= 48f &&
                        abs(event.y - lastTapY) <= 48f
                    val count = if (doubleTap) 2 else 1
                    lastTapTime = now
                    lastTapX = event.x
                    lastTapY = event.y
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

private data class EditorAction(val label: String, val command: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocuFlowApp(activity: MainActivity, viewModel: DocuFlowViewModel, incomingUri: Uri?) {
    val uiState by viewModel.state.collectAsState()
    var lastHandledUri by rememberSaveable { mutableStateOf<String?>(null) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

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

    val frequentTop = listOf(
        EditorAction("Undo", ".uno:Undo"),
        EditorAction("Redo", ".uno:Redo"),
        EditorAction("B", ".uno:Bold"),
        EditorAction("I", ".uno:Italic"),
        EditorAction("U", ".uno:Underline"),
        EditorAction("Filter", ".uno:DataFilterAutoFilter"),
        EditorAction("Sort", ".uno:DataSort"),
        EditorAction("Merge", ".uno:MergeCells"),
        EditorAction("Border", ".uno:BorderDialog")
    )

    val formattingActions = listOf(
        EditorAction("Font", ".uno:FontDialog"),
        EditorAction("Font size", ".uno:FontHeight"),
        EditorAction("Font colour", ".uno:FontColor"),
        EditorAction("Cell fill", ".uno:BackgroundColor"),
        EditorAction("Borders", ".uno:BorderDialog"),
        EditorAction("Merge cells", ".uno:MergeCells"),
        EditorAction("Wrap text", ".uno:WrapText"),
        EditorAction("Align left", ".uno:AlignLeft"),
        EditorAction("Align center", ".uno:AlignCenter"),
        EditorAction("Align right", ".uno:AlignRight")
    )

    val dataActions = listOf(
        EditorAction("Filter / AutoFilter", ".uno:DataFilterAutoFilter"),
        EditorAction("Sort", ".uno:DataSort"),
        EditorAction("Clear filter", ".uno:DataFilterRemoveFilter"),
        EditorAction("Insert row", ".uno:InsertRows"),
        EditorAction("Delete row", ".uno:DeleteRows"),
        EditorAction("Insert column", ".uno:InsertColumns"),
        EditorAction("Delete column", ".uno:DeleteColumns")
    )

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    "DocuFlow tools",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(20.dp)
                )
                HorizontalDivider()
                Text("Formatting", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 8.dp))
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    formattingActions.forEach { action ->
                        NavigationDrawerItem(
                            label = { Text(action.label) },
                            selected = false,
                            onClick = {
                                viewModel.executeCommand(action.command)
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                        )
                    }
                    Text("Data", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp, 18.dp, 16.dp, 8.dp))
                    dataActions.forEach { action ->
                        NavigationDrawerItem(
                            label = { Text(action.label) },
                            selected = false,
                            onClick = {
                                viewModel.executeCommand(action.command)
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                        )
                    }
                    Text("Document", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp, 18.dp, 16.dp, 8.dp))
                    NavigationDrawerItem(
                        label = { Text("Save") },
                        selected = false,
                        onClick = {
                            viewModel.save()
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                    )
                    NavigationDrawerItem(
                        label = { Text("Close document") },
                        selected = false,
                        onClick = {
                            viewModel.close()
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                    )
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    ) {
        MaterialTheme {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("DocuFlow") },
                        navigationIcon = {
                            if (uiState.documentOpen) {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(Icons.Default.Menu, contentDescription = "Tools")
                                }
                            }
                        },
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
                        // Compact, frequently used controls stay visible above the document.
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            frequentTop.forEach { action ->
                                TextButton(
                                    onClick = { viewModel.executeCommand(action.command) },
                                    contentPadding = PaddingValues(horizontal = 9.dp, vertical = 2.dp)
                                ) { Text(action.label) }
                            }
                            TextButton(onClick = viewModel::zoomOut, contentPadding = PaddingValues(horizontal = 9.dp, vertical = 2.dp)) { Text("−") }
                            TextButton(onClick = viewModel::zoomIn, contentPadding = PaddingValues(horizontal = 9.dp, vertical = 2.dp)) { Text("+") }
                        }

                        Text(
                            uiState.status,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelMedium
                        )

                        AndroidView(
                            modifier = Modifier.fillMaxWidth().weight(1f),
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
                                    if (view.width > 0 && view.height > 0) {
                                        viewModel.setViewportSize(view.width, view.height)
                                    }
                                }
                            }
                        )

                        // The same high-frequency actions are available below the sheet so
                        // users do not need to reach to the top while working.
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            listOf(
                                EditorAction("Copy", ".uno:Copy"),
                                EditorAction("Cut", ".uno:Cut"),
                                EditorAction("Paste", ".uno:Paste"),
                                EditorAction("Filter", ".uno:DataFilterAutoFilter"),
                                EditorAction("Sort", ".uno:DataSort"),
                                EditorAction("Merge", ".uno:MergeCells"),
                                EditorAction("Border", ".uno:BorderDialog")
                            ).forEach { action ->
                                OutlinedButton(
                                    onClick = { viewModel.executeCommand(action.command) },
                                    contentPadding = PaddingValues(horizontal = 11.dp, vertical = 2.dp)
                                ) { Text(action.label) }
                            }
                        }

                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TextButton(onClick = { viewModel.executeCommand(".uno:AlignLeft") }) { Text("Left") }
                            TextButton(onClick = { viewModel.executeCommand(".uno:AlignCenter") }) { Text("Center") }
                            TextButton(onClick = { viewModel.executeCommand(".uno:AlignRight") }) { Text("Right") }
                            TextButton(onClick = viewModel::close) { Text("Close") }
                        }
                    }
                }
            }
        }
    }
}
