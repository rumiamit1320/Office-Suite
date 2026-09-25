package com.docuflow.android.office
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors
object NativeOfficeDispatcher {
 private val executor=Executors.newSingleThreadExecutor { r -> Thread(r,"DocuFlow-LOKit") }
 val dispatcher: CoroutineDispatcher=executor.asCoroutineDispatcher()
}
