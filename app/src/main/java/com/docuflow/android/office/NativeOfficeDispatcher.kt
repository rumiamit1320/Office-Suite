package com.docuflow.android.office

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

object NativeOfficeDispatcher {
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "DocuFlow-LOKit")
    }
    val dispatcher: CoroutineDispatcher = executor.asCoroutineDispatcher()

    fun launch(block: suspend CoroutineScope.() -> Unit) {
        CoroutineScope(SupervisorJob() + dispatcher).launch(block = block)
    }

    fun shutdown() {
        executor.shutdown()
    }
}
