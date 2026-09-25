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
    private val officeEngine by lazy { OfficeEngine(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        incomingUri.value = intent?.data
        setContent {
            DocuFlowApp(
                activity = this,
                officeEngine = officeEngine,
                incomingUri = incomingUri.value
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingUri.value = intent.data
    }

    override fun onDestroy() {
        // Prevent the process-wide LOKit document from surviving an Activity
        // recreation and becoming detached from the new UI/session.
        if (isFinishing || isChangingConfigurations) {
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                runCatching { officeEngine.close() }
            }
        }
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocuFlowApp(
    activity: MainActivity,
    officeEngine: OfficeEngine,
    incomingUri: Uri?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var status by remember { mutableStateOf("Ready") }
    var isBusy by remember { mutableStateOf(false) }
    var documentOpen by remember { mutableStateOf(false) }
    var lastHandledUri by remember { mutableStateOf<Uri?>(null) }

    suspend fun initializeEngine() {
        check(officeEngine.initialize(activity)) {
            "LibreOfficeKit runtime is not available"
        }
    }

    suspend fun openDocument(uri: Uri) {
        isBusy = true
        status = "Initializing LibreOfficeKit…"

        try {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Some document providers grant a non-persistable one-shot permission.
            }

            initializeEngine()

            status = "Opening document…"
            officeEngine.open(uri)
            documentOpen = true
            status = "Document opened"
        } catch (e: Exception) {
            documentOpen = false
            status = "Open failed: ${e.message ?: e.javaClass.simpleName}"
        } finally {
            isBusy = false
        }
    }

    fun saveDocument() {
        scope.launch {
            isBusy = true
            status = "Saving…"
            try {
                officeEngine.save()
                status = "Document saved"
            } catch (e: Exception) {
                status = "Save failed: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                isBusy = false
            }
        }
    }

    fun closeDocument() {
        scope.launch {
            isBusy = true
            status = "Closing…"
            try {
                officeEngine.close()
                documentOpen = false
                status = "Document closed"
            } catch (e: Exception) {
                status = "Close failed: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                isBusy = false
            }
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
                    enabled = !isBusy,
                    onClick = { documentPicker.launch(arrayOf("*/*")) }
                ) {
                    Text(if (isBusy) "Working…" else "Open document")
                }

                if (documentOpen) {
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            enabled = !isBusy,
                            onClick = { saveDocument() }
                        ) {
                            Text("Save")
                        }
                        OutlinedButton(
                            enabled = !isBusy,
                            onClick = { closeDocument() }
                        ) {
                            Text("Close")
                        }
                    }
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
