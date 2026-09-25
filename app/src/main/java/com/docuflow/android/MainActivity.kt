package com.docuflow.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.docuflow.android.office.OfficeEngine
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val incomingUri = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        incomingUri.value = intent?.data
        setContent { DocuFlowApp(incomingUri = incomingUri.value) }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingUri.value = intent?.data
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocuFlowApp(incomingUri: Uri?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val officeEngine = remember { OfficeEngine(context) }

    var status by remember { mutableStateOf("Ready") }
    var isOpening by remember { mutableStateOf(false) }
    var lastHandledUri by remember { mutableStateOf<Uri?>(null) }

    suspend fun openDocument(uri: Uri) {
        isOpening = true
        status = "Initializing LibreOfficeKit…"

        try {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Some document providers grant a non-persistable one-shot permission.
            }

            check(officeEngine.initialize()) {
                "LibreOfficeKit runtime is not available"
            }

            status = "Opening document…"
            officeEngine.open(uri)
            status = "Document opened"
        } catch (e: Exception) {
            status = "Open failed: ${e.message ?: e.javaClass.simpleName}"
        } finally {
            isOpening = false
        }
    }

    LaunchedEffect(incomingUri) {
        if (incomingUri != null && incomingUri != lastHandledUri) {
            lastHandledUri = incomingUri
            openDocument(incomingUri)
        }
    }

    val documentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            status = "Open cancelled"
        } else {
            scope.launch { openDocument(uri) }
        }
    }

    MaterialTheme {
        Scaffold(
            topBar = { TopAppBar(title = { Text("DocuFlow") }) }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.FolderOpen,
                    contentDescription = "Open document",
                    modifier = Modifier.size(64.dp)
                )

                Spacer(Modifier.height(16.dp))

                Text(
                    "Office Suite",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text("Word • Excel • PowerPoint • PDF")

                Spacer(Modifier.height(20.dp))

                Button(
                    enabled = !isOpening,
                    onClick = { documentPicker.launch(arrayOf("*/*")) }
                ) {
                    Text(if (isOpening) "Opening…" else "Open document")
                }

                Spacer(Modifier.height(12.dp))

                Text(status, style = MaterialTheme.typography.bodyMedium)

                Spacer(Modifier.height(8.dp))

                Text(
                    "Open documents from Android storage or another app.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
