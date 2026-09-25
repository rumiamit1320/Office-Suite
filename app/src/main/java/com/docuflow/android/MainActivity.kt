package com.docuflow.android
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{DocuFlowApp()}}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun DocuFlowApp(){
 MaterialTheme{
  Scaffold(topBar={TopAppBar(title={Text("DocuFlow")})}){p->
   Column(Modifier.fillMaxSize().padding(p).padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){
    Icon(Icons.Default.FolderOpen,null,Modifier.size(64.dp))
    Spacer(Modifier.height(16.dp))
    Text("Office Suite",style=MaterialTheme.typography.headlineMedium)
    Text("Word • Excel • PowerPoint • PDF")
    Spacer(Modifier.height(20.dp))
    Button(onClick={}){Text("Open document")}
    Spacer(Modifier.height(8.dp))
    Text("LibreOfficeKit native runtime is the next packaging step.",style=MaterialTheme.typography.bodySmall)
   }
  }
 }
}
