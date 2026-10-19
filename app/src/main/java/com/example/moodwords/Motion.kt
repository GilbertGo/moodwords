package com.example.moodwords

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.sqrt

/**
 * 0.5초 동안의 손목 움직임 요약.
 * movement: 큰 동작 크기 (중력 뺀 가속도의 RMS, m/s²) — 걷기·팔 흔들기 판별용
 * tremor: 빠르고 작은 흔들림 (약 3Hz 이상 성분의 RMS) — 손 떨림·꼼지락 정도
 */
data class MotionSample(val movement: Double, val tremor: Double) {
    val isMoving get() = movement > MOVING_THRESHOLD

    companion object {
        /** 이보다 크면 '움직이는 중'으로 보고 그동안의 심박은 계산에서 뺌 */
        const val MOVING_THRESHOLD = 0.8
    }
}

/** 떨림 정도를 평소(개인 기준값)와 비교한 등급 */
enum class TremorLevel(val label: String) { LOW("적음"), MID("보통"), HIGH("많음") }

fun tremorLevel(tremor: Double, baseline: Double): TremorLevel {
    val ratio = tremor / baseline.coerceAtLeast(0.005)
    return when {
        ratio >= 1.6 -> TremorLevel.HIGH
        ratio <= 0.7 -> TremorLevel.LOW
        else -> TremorLevel.MID
    }
}

/** 가속도 센서로 0.5초마다 MotionSample을 흘려보냄. 센서가 없으면 아무것도 안 보냄 */
fun motionFlow(context: Context): Flow<MotionSample> = callbackFlow {
    val sm = context.getSystemService(SensorManager::class.java)
    val sensor = sm?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        ?: sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    if (sm == null || sensor == null) {
        close()
        return@callbackFlow
    }
    val linear = sensor.type == Sensor.TYPE_LINEAR_ACCELERATION
    val gravity = FloatArray(3)
    var prevMag = 0.0
    var highPass = 0.0
    var first = true
    var windowStart = 0L
    var sumMag = 0.0
    var sumHp = 0.0
    var n = 0

    val listener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            var x = e.values[0]; var y = e.values[1]; var z = e.values[2]
            if (!linear) {
                // 일반 가속도 센서면 저역 통과로 중력을 추정해 빼기
                gravity[0] = 0.9f * gravity[0] + 0.1f * x
                gravity[1] = 0.9f * gravity[1] + 0.1f * y
                gravity[2] = 0.9f * gravity[2] + 0.1f * z
                x -= gravity[0]; y -= gravity[1]; z -= gravity[2]
            }
            val mag = sqrt((x * x + y * y + z * z).toDouble())
            // 1차 고역 통과 (50Hz 기준 약 3Hz 이상): 떨림 성분만 남김
            highPass = if (first) 0.0 else 0.73 * (highPass + mag - prevMag)
            prevMag = mag
            first = false

            sumMag += mag * mag
            sumHp += highPass * highPass
            n++
            if (windowStart == 0L) windowStart = e.timestamp
            if (e.timestamp - windowStart >= 500_000_000L && n > 0) {
                trySend(MotionSample(sqrt(sumMag / n), sqrt(sumHp / n)))
                sumMag = 0.0; sumHp = 0.0; n = 0
                windowStart = e.timestamp
            }
        }

        override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}
    }
    sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
    awaitClose { sm.unregisterListener(listener) }
}
