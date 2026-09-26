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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.BorderAll
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.WrapText
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
    private var frameOffsetX = 0f
    private var frameOffsetY = 0f
    private var dragOffsetX = 0f
    private var dragOffsetY = 0f
    private var downX = 0f
    private var downY = 0f
    private var moved = false
    private var lastTapTime = 0L
    private var lastTapX = 0f
    private var lastTapY = 0f

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
        setBackgroundColor(0xFFF7F7F8.toInt())
    }

    fun setFrame(value: Bitmap?, viewportXTwips: Long, viewportYTwips: Long, originXTwips: Long, originYTwips: Long, twipsPerPixel: Double) {
        val newOffsetX = -((viewportXTwips - originXTwips) / twipsPerPixel).toFloat()
        val newOffsetY = -((viewportYTwips - originYTwips) / twipsPerPixel).toFloat()
        val changed = bitmap !== value || frameOffsetX != newOffsetX || frameOffsetY != newOffsetY
        bitmap = value
        frameOffsetX = newOffsetX
        frameOffsetY = newOffsetY
        dragOffsetX = 0f
        dragOffsetY = 0f
        if (changed) invalidate()
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        bitmap?.let { canvas.drawBitmap(it, frameOffsetX + dragOffsetX, frameOffsetY + dragOffsetY, null) }
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
                parent.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - downX
                val dy = event.y - downY
                if (!moved && abs(dx) <= 8f && abs(dy) <= 8f) return true
                moved = true
                dragOffsetX = dx
                dragOffsetY = dy
                invalidate()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                dragOffsetX = 0f
                dragOffsetY = 0f
                invalidate()
                parent.requestDisallowInterceptTouchEvent(false)
                return true
            }
            MotionEvent.ACTION_UP -> {
                parent.requestDisallowInterceptTouchEvent(false)
                if (moved) {
                    val dx = dragOffsetX
                    val dy = dragOffsetY
                    dragOffsetX = 0f
                    dragOffsetY = 0f
                    onPan(-dx, -dy)
                    invalidate()
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

    override fun performClick(): Boolean { super.performClick(); return true }
    override fun onCheckIsTextEditor(): Boolean = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
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
private data class EditorAction(
    val label: String,
    val command: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector? = null
)

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
        EditorAction("Undo", ".uno:Undo", Icons.Default.Undo),
        EditorAction("Redo", ".uno:Redo", Icons.Default.Redo),
        EditorAction("Bold", ".uno:Bold", Icons.Default.FormatBold),
        EditorAction("Italic", ".uno:Italic", Icons.Default.FormatItalic),
        EditorAction("Underline", ".uno:Underline", Icons.Default.FormatUnderlined),
        EditorAction("Filter", ".uno:DataFilterAutoFilter", Icons.Default.FilterAlt),
        EditorAction("Sort", ".uno:DataSort", Icons.Default.Sort),
        EditorAction("Merge", ".uno:MergeCells", Icons.Default.MergeType),
        EditorAction("Border", ".uno:BorderDialog", Icons.Default.BorderAll)
    )

    val formattingActions = listOf(
        EditorAction("Font", ".uno:FontDialog", Icons.Default.TextFields),
        EditorAction("Font size", ".uno:FontHeight", Icons.Default.TextFields),
        EditorAction("Font colour", ".uno:FontColor", Icons.Default.FormatColorText),
        EditorAction("Cell fill", ".uno:BackgroundColor", Icons.Default.FormatColorFill),
        EditorAction("Borders", ".uno:BorderDialog", Icons.Default.BorderAll),
        EditorAction("Merge cells", ".uno:MergeCells", Icons.Default.MergeType),
        EditorAction("Wrap text", ".uno:WrapText", Icons.Default.WrapText),
        EditorAction("Align left", ".uno:AlignLeft", Icons.Default.FormatAlignLeft),
        EditorAction("Align center", ".uno:AlignCenter", Icons.Default.FormatAlignCenter),
        EditorAction("Align right", ".uno:AlignRight", Icons.Default.FormatAlignRight)
    )

    val dataActions = listOf(
        EditorAction("Filter / AutoFilter", ".uno:DataFilterAutoFilter", Icons.Default.FilterAlt),
        EditorAction("Sort", ".uno:DataSort", Icons.Default.Sort),
        EditorAction("Clear filter", ".uno:DataFilterRemoveFilter", Icons.Default.FilterAlt),
        EditorAction("Insert row", ".uno:InsertRows", Icons.Default.Add),
        EditorAction("Delete row", ".uno:DeleteRows", Icons.Default.Delete),
        EditorAction("Insert column", ".uno:InsertColumns", Icons.Default.Add),
        EditorAction("Delete column", ".uno:DeleteColumns", Icons.Default.Delete)
    )

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerTonalElevation = 4.dp,
                modifier = Modifier.width(320.dp)
            ) {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier.fillMaxWidth().padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Icon(
                                Icons.Default.FolderOpen,
                                contentDescription = null,
                                modifier = Modifier.padding(10.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("DocuFlow", style = MaterialTheme.typography.titleLarge)
                            Text("Office tools", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider()
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        item {
                            Text("Formatting", style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(16.dp, 10.dp, 16.dp, 6.dp))
                        }
                        items(formattingActions, key = { it.label }) { action ->
                            NavigationDrawerItem(
                                icon = { action.icon?.let { Icon(it, null) } },
                                label = { Text(action.label) },
                                selected = false,
                                onClick = {
                                    viewModel.executeCommand(action.command)
                                    scope.launch { drawerState.close() }
                                },
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        item {
                            Text("Data", style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(16.dp, 18.dp, 16.dp, 6.dp))
                        }
                        items(dataActions, key = { it.label }) { action ->
                            NavigationDrawerItem(
                                icon = { action.icon?.let { Icon(it, null) } },
                                label = { Text(action.label) },
                                selected = false,
                                onClick = {
                                    viewModel.executeCommand(action.command)
                                    scope.launch { drawerState.close() }
                                },
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        item {
                            Text("Document", style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(16.dp, 18.dp, 16.dp, 6.dp))
                        }
                        item {
                            NavigationDrawerItem(
                                icon = { Icon(Icons.Default.Save, null) },
                                label = { Text("Save") },
                                selected = false,
                                onClick = {
                                    viewModel.save()
                                    scope.launch { drawerState.close() }
                                },
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        item {
                            NavigationDrawerItem(
                                icon = { Icon(Icons.Default.Close, null) },
                                label = { Text("Close document") },
                                selected = false,
                                onClick = {
                                    viewModel.close()
                                    scope.launch { drawerState.close() }
                                },
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    ) {
        MaterialTheme {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text("DocuFlow", style = MaterialTheme.typography.titleLarge)
                                if (uiState.documentOpen) {
                                    Text("Editing document",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        },
                        navigationIcon = {
                            if (uiState.documentOpen) {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(Icons.Default.Menu, "Tools")
                                }
                            }
                        },
                        actions = {
                            if (uiState.documentOpen) {
                                IconButton(onClick = viewModel::save, enabled = !uiState.isBusy) {
                                    Icon(Icons.Default.Save, "Save")
                                }
                            }
                        }
                    )
                },
                bottomBar = {
                    if (uiState.documentOpen) {
                        Surface(
                            tonalElevation = 3.dp,
                            shadowElevation = 2.dp
                        ) {
                            LazyRow(
                                modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 3.dp)
                            ) {
                                items(
                                    listOf(
                                        EditorAction("Copy", ".uno:Copy", Icons.Default.ContentCopy),
                                        EditorAction("Cut", ".uno:Cut", Icons.Default.ContentCut),
                                        EditorAction("Paste", ".uno:Paste", Icons.Default.ContentPaste),
                                        EditorAction("Filter", ".uno:DataFilterAutoFilter", Icons.Default.FilterAlt),
                                        EditorAction("Sort", ".uno:DataSort", Icons.Default.Sort),
                                        EditorAction("Merge", ".uno:MergeCells", Icons.Default.MergeType),
                                        EditorAction("Border", ".uno:BorderDialog", Icons.Default.BorderAll)
                                    ),
                                    key = { it.label }
                                ) { action ->
                                    IconButton(onClick = { viewModel.executeCommand(action.command) }) {
                                        Icon(action.icon!!, action.label)
                                    }
                                }
                                item {
                                    IconButton(onClick = viewModel::close) {
                                        Icon(Icons.Default.Close, "Close")
                                    }
                                }
                            }
                        }
                    }
                }
            ) { padding ->
                Column(
                    Modifier.fillMaxSize().padding(padding),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (!uiState.documentOpen) {
                        Spacer(Modifier.height(56.dp))
                        Icon(Icons.Default.FolderOpen, null,
                            Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(18.dp))
                        Text("Office Suite", style = MaterialTheme.typography.headlineMedium)
                        Text("Word • Excel • PowerPoint • PDF",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(28.dp))
                        FilledTonalButton(
                            enabled = !uiState.isBusy,
                            onClick = { picker.launch(arrayOf("*/*")) }
                        ) {
                            Icon(Icons.Default.FolderOpen, null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (uiState.isBusy) "Working…" else "Open document")
                        }
                        Spacer(Modifier.height(18.dp))
                        Text(uiState.status)
                    } else {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            tonalElevation = 1.dp,
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Edit", style = MaterialTheme.typography.labelLarge)
                                    Spacer(Modifier.width(8.dp))
                                    Text(uiState.status,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1)
                                }
                                Row {
                                    IconButton(onClick = viewModel::zoomOut) {
                                        Icon(Icons.Default.ZoomOut, "Zoom out")
                                    }
                                    IconButton(onClick = viewModel::zoomIn) {
                                        Icon(Icons.Default.ZoomIn, "Zoom in")
                                    }
                                }
                            }
                        }

                        LazyRow(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            contentPadding = PaddingValues(horizontal = 2.dp)
                        ) {
                            items(frequentTop, key = { it.label }) { action ->
                                FilledTonalIconButton(
                                    onClick = { viewModel.executeCommand(action.command) },
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Icon(action.icon!!, action.label)
                                }
                            }
                        }

                        Box(
                            Modifier.fillMaxWidth().weight(1f).padding(horizontal = 3.dp, vertical = 2.dp)
                        ) {
                            AndroidView(
                                modifier = Modifier.fillMaxSize(),
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
                                    view.setFrame(
                                        uiState.preview,
                                        uiState.viewportXTwips,
                                        uiState.viewportYTwips,
                                        uiState.renderOriginXTwips,
                                        uiState.renderOriginYTwips,
                                        12.0
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}