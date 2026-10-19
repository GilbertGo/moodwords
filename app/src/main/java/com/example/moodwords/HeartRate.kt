package com.example.moodwords

import android.content.Context
import androidx.health.services.client.HealthServices
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DeltaDataType
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Health Services로 실시간 심박(bpm)을 흘려보냄. 수집을 멈추면 센서도 해제됨. */
fun heartRateFlow(context: Context): Flow<Double> = callbackFlow {
    val client = HealthServices.getClient(context).measureClient
    val callback = object : MeasureCallback {
        override fun onAvailabilityChanged(dataType: DeltaDataType<*, *>, availability: Availability) {}

        override fun onDataReceived(data: DataPointContainer) {
            data.getData(DataType.HEART_RATE_BPM)
                .map { it.value }
                .filter { it > 30 }          // 접촉 불량 등으로 들어오는 0 값 제거
                .forEach { trySend(it) }
        }
    }
    client.registerMeasureCallback(DataType.HEART_RATE_BPM, callback)
    awaitClose { client.unregisterMeasureCallbackAsync(DataType.HEART_RATE_BPM, callback) }
}
