package com.example.moodwords

import android.content.Context

data class MoodEntry(
    val time: Long,
    val word: String,
    val bpm: Int?,          // 측정 실패 시 null
    val energy: Energy,
    val valence: Valence,
    val tremor: Double? = null,  // 워치: 가만히 있을 때 손 떨림 정도 (없으면 null)
)

/** 워치 안에만 저장되는 간단한 기록 저장소 */
class MoodStore(context: Context) {
    private val prefs = context.getSharedPreferences("mood", Context.MODE_PRIVATE)

    /** 평소 안정 심박. 처음엔 70, 차분할 때 측정값으로 조금씩 맞춰짐 */
    var restingBpm: Double
        get() = prefs.getFloat("resting", 70f).toDouble()
        private set(v) = prefs.edit().putFloat("resting", v.toFloat()).apply()

    /** 평소 떨림 정도. 차분하다고 확인한 측정값으로 조금씩 맞춰짐 */
    var restingTremor: Double
        get() = prefs.getFloat("restingTremor", 0.06f).toDouble()
        private set(v) = prefs.edit().putFloat("restingTremor", v.toFloat()).apply()

    fun entries(): List<MoodEntry> =
        prefs.getString("entries", "")!!
            .lines()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val p = line.split("|")
                runCatching {
                    MoodEntry(
                        p[0].toLong(), p[1], p[2].toIntOrNull(), Energy.valueOf(p[3]), Valence.valueOf(p[4]),
                        p.getOrNull(5)?.toDoubleOrNull(),
                    )
                }.getOrNull()
            }
            .sortedByDescending { it.time }

    fun add(e: MoodEntry) {
        val line = "${e.time}|${e.word}|${e.bpm ?: ""}|${e.energy.name}|${e.valence.name}|${e.tremor ?: ""}"
        val kept = prefs.getString("entries", "")!!.lines().filter { it.isNotBlank() }.takeLast(199)
        prefs.edit().putString("entries", (kept + line).joinToString("\n")).apply()

        // 사용자가 "에너지 낮음"이라고 확인한 측정값으로 안정 심박을 학습
        if (e.bpm != null && e.energy == Energy.LOW) {
            restingBpm = restingBpm * 0.8 + e.bpm * 0.2
        }
        if (e.tremor != null && e.energy == Energy.LOW) {
            restingTremor = restingTremor * 0.8 + e.tremor * 0.2
        }
    }
}
