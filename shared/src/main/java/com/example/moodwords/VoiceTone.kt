package com.example.moodwords

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/** 목소리에서 잰 값: 높낮이(Hz), 크기(dB), 소리 낸 시간(초) */
data class VoiceFeatures(val pitchHz: Double, val loudDb: Double, val voicedSec: Double)

/** 내 평소 목소리와 비교한 결과 (에너지 추정 보조용, 기분은 알 수 없음) */
enum class VoiceLevel(val label: String) { HIGH("평소보다 높고 빨라요"), NORMAL("평소와 비슷해요"), LOW("평소보다 낮고 느려요") }

/**
 * 제시 문장 읽기 → 기기 안에서만 분석. 녹음은 저장하지 않고 숫자 3개만 남김.
 * 같은 문장을 읽으므로 평소 값과 비교할 수 있음.
 */
object VoiceTone {
    const val SENTENCE = "지금 이 순간, 내 마음이 어떤지 천천히 들어 봅니다."
    private const val RATE = 16_000

    /**
     * 녹음해서 분석. 말을 시작한 뒤 1.2초 조용하면, 또는 stop 이 켜지면, 또는 10초가 지나면 끝.
     * onLevel: 0~1 소리 크기 (화면 막대용)
     */
    @SuppressLint("MissingPermission")
    suspend fun record(stop: AtomicBoolean, onLevel: (Float) -> Unit): VoiceFeatures? = withContext(Dispatchers.IO) {
        val minBuf = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuf <= 0) return@withContext null
        val rec = runCatching {
            AudioRecord(MediaRecorder.AudioSource.MIC, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, max(minBuf, 4096) * 2)
        }.getOrNull() ?: return@withContext null
        if (rec.state != AudioRecord.STATE_INITIALIZED) { rec.release(); return@withContext null }

        val all = ShortArray(RATE * 10)
        var n = 0
        val chunk = ShortArray(800)   // 50ms
        var spoke = false
        var quietMs = 0
        try {
            rec.startRecording()
            while (coroutineContext.isActive && !stop.get() && n + chunk.size <= all.size) {
                val got = rec.read(chunk, 0, chunk.size)
                if (got <= 0) continue
                System.arraycopy(chunk, 0, all, n, got)
                n += got
                val r = rms(chunk, 0, got)
                onLevel((r / 4000.0).coerceIn(0.0, 1.0).toFloat())
                if (r > 700) { spoke = true; quietMs = 0 } else if (spoke) quietMs += 50
                if (spoke && quietMs >= 1200) break
            }
        } finally {
            runCatching { rec.stop() }
            rec.release()
        }
        analyze(all, n)
    }

    private fun rms(a: ShortArray, from: Int, len: Int): Double {
        var s = 0.0
        for (i in from until from + len) s += a[i].toDouble() * a[i]
        return sqrt(s / len.coerceAtLeast(1))
    }

    /** 64ms 창으로 소리 난 구간만 골라 높낮이(자기상관)·크기·시간 계산 */
    private fun analyze(a: ShortArray, n: Int): VoiceFeatures? {
        val win = 1024; val hop = 512
        if (n < win * 4) return null
        val frames = (0..(n - win) step hop).map { it to rms(a, it, win) }
        val sorted = frames.map { it.second }.sorted()
        val noise = sorted[sorted.size / 5]
        val thr = max(500.0, noise * 3)
        val voiced = frames.filter { it.second > thr }
        if (voiced.size < 8) return null
        val pitches = voiced.mapNotNull { (start, _) -> pitchOf(a, start, win) }.sorted()
        if (pitches.size < 5) return null
        val pitch = pitches[pitches.size / 2]
        val loud = voiced.map { 20 * log10(it.second) }.average()
        return VoiceFeatures(pitch, loud, voiced.size * hop / RATE.toDouble())
    }

    /** 75~400Hz 사이에서 정규화 자기상관이 가장 큰 주기 */
    private fun pitchOf(a: ShortArray, start: Int, win: Int): Double? {
        val minLag = RATE / 400; val maxLag = RATE / 75
        var best = 0.0; var bestLag = 0
        for (lag in minLag..maxLag) {
            var num = 0.0; var e1 = 0.0; var e2 = 0.0
            for (i in start until start + win - lag) {
                val x = a[i].toDouble(); val y = a[i + lag].toDouble()
                num += x * y; e1 += x * x; e2 += y * y
            }
            val r = num / sqrt(e1 * e2 + 1e-9)
            if (r > best) { best = r; bestLag = lag }
        }
        return if (best > 0.6 && bestLag > 0) RATE.toDouble() / bestLag else null
    }
}

/** 평소 목소리 기준 (읽을 때마다 조금씩 갱신). 처음 3번은 기준을 배우기만 함 */
class VoiceBaseline(context: Context) {
    private val p = context.getSharedPreferences("voice", Context.MODE_PRIVATE)
    val count get() = p.getInt("n", 0)
    val ready get() = count >= LEARN

    /** 지금 값을 평소와 비교 (기준을 배우는 중이면 null) */
    fun level(f: VoiceFeatures): VoiceLevel? {
        if (!ready) return null
        fun z(key: String, v: Double, minSd: Double): Double {
            val mean = p.getFloat("${key}_m", 0f).toDouble()
            val sd = max(sqrt(p.getFloat("${key}_v", 0f).toDouble()), minSd)
            return (v - mean) / sd
        }
        // 높고, 크고, 빨리 읽을수록(시간이 짧을수록) 들뜬 쪽
        val score = (z("pitch", f.pitchHz, 10.0) + z("loud", f.loudDb, 2.5) - z("dur", f.voicedSec, 0.35)) / 3
        return when {
            score >= 0.8 -> VoiceLevel.HIGH
            score <= -0.8 -> VoiceLevel.LOW
            else -> VoiceLevel.NORMAL
        }
    }

    /** 기준에 더하기 (지수 이동 평균, 최근 20번 정도를 반영) */
    fun add(f: VoiceFeatures) {
        val n = count + 1
        val alpha = max(1.0 / n, 0.05)
        val e = p.edit()
        for ((key, v) in listOf("pitch" to f.pitchHz, "loud" to f.loudDb, "dur" to f.voicedSec)) {
            val m0 = if (n == 1) v else p.getFloat("${key}_m", 0f).toDouble()
            val v0 = p.getFloat("${key}_v", 0f).toDouble()
            val m1 = m0 + alpha * (v - m0)
            val v1 = (1 - alpha) * (v0 + alpha * (v - m0) * (v - m0))
            e.putFloat("${key}_m", m1.toFloat()).putFloat("${key}_v", v1.toFloat())
        }
        e.putInt("n", n).apply()
    }

    companion object { const val LEARN = 3 }
}

/** 에너지 추정에 목소리 결과를 한 단계 반영 */
fun Energy.shiftBy(level: VoiceLevel?): Energy = when (level) {
    VoiceLevel.HIGH -> if (this == Energy.LOW) Energy.MID else Energy.HIGH
    VoiceLevel.LOW -> if (this == Energy.HIGH) Energy.MID else Energy.LOW
    else -> this
}
