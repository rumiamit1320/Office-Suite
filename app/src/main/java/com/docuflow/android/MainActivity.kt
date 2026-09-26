package com.docuflow.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocuFlowApp(activity: MainActivity, viewModel: DocuFlowViewModel, incomingUri: Uri?) {
    val context = LocalContext.current
    val uiState by viewModel.state.collectAsState()
    var lastHandledUri by rememberSaveable { mutableStateOf<String?>(null) }

    fun openDocument(uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
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
        Scaffold(topBar = { TopAppBar(title = { Text("DocuFlow") }) }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(24.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Top) {
                Icon(Icons.Default.FolderOpen, "Open document", Modifier.size(64.dp))
                Spacer(Modifier.height(16.dp))
                Text("Office Suite", style = MaterialTheme.typography.headlineMedium)
                Text("Word • Excel • PowerPoint • PDF")
                Spacer(Modifier.height(20.dp))
                Button(enabled = !uiState.isBusy, onClick = { picker.launch(arrayOf("*/*")) }) { Text(if (uiState.isBusy) "Working…" else "Open document") }
                if (uiState.documentOpen) {
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(enabled = !uiState.isBusy, onClick = viewModel::save) { Text("Save") }
                        OutlinedButton(enabled = !uiState.isBusy, onClick = viewModel::close) { Text("Close") }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(uiState.status, style = MaterialTheme.typography.bodyMedium)
                uiState.preview?.let { bitmap ->
                    Spacer(Modifier.height(20.dp))
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Document preview",
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.FillWidth
                    )
                }
            }
        }
    }
}
