package com.example.moodwords

import android.content.Context

data class MoodEntry(
    val time: Long,
    val word: String,
    val bpm: Int?,          // 측정 실패 시 null
    val energy: Energy,
    val valence: Valence,
    val tremor: Double? = null,  // 워치: 가만히 있을 때 손 떨림 정도 (없으면 null)
    val cause: Cause? = null,    // 무엇 때문에 (v1.20 이전 기록은 null)
    val before: Int? = null,     // 이름 붙이기 전 감정 세기 1~5
    val after: Int? = null,      // 기록한 뒤 다시 잰 세기 1~5
    val word2: String? = null,   // 함께 섞인 두 번째 감정
    val note: String? = null,    // 한 줄 메모 (무슨 일이었는지)
) {
    /** 화면 표시용: "설렘 + 불안함" */
    val words get() = if (word2.isNullOrBlank()) word else "$word + $word2"
}

/** 기기 안에만 저장되는 간단한 기록 저장소 (밖으로 보내지 않음) */
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
                        p.getOrNull(6)?.let { c -> Cause.entries.firstOrNull { it.name == c } },
                        p.getOrNull(7)?.toIntOrNull(),
                        p.getOrNull(8)?.toIntOrNull(),
                        p.getOrNull(9)?.ifBlank { null },
                        p.getOrNull(10)?.ifBlank { null },
                    )
                }.getOrNull()
            }
            .sortedByDescending { it.time }

    private fun clean(s: String?) = s?.replace("|", "/")?.replace("\n", " ")?.trim().orEmpty()

    private fun lineOf(e: MoodEntry) =
        "${e.time}|${e.word}|${e.bpm ?: ""}|${e.energy.name}|${e.valence.name}|${e.tremor ?: ""}|${e.cause?.name ?: ""}" +
            "|${e.before ?: ""}|${e.after ?: ""}|${clean(e.word2)}|${clean(e.note)}"

    /** 가장 최근 기록에 '기록한 뒤 세기'와 메모를 채움 */
    fun updateLatest(after: Int?, note: String?) {
        val list = entries()
        val last = list.firstOrNull() ?: return
        val lines = prefs.getString("entries", "")!!.lines().filter { it.isNotBlank() }.toMutableList()
        val idx = lines.indexOfLast { it.startsWith("${last.time}|") }
        if (idx < 0) return
        lines[idx] = lineOf(last.copy(after = after ?: last.after, note = note?.ifBlank { null } ?: last.note))
        prefs.edit().putString("entries", lines.joinToString("\n")).apply()
    }

    /** 지금까지 써 본 서로 다른 감정 단어 수 */
    fun distinctWords(): Int = entries().flatMap { listOfNotNull(it.word, it.word2) }.toSet().size

    fun add(e: MoodEntry) {
        val line = lineOf(e)
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
