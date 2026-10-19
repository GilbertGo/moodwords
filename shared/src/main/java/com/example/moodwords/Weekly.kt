package com.example.moodwords

import java.util.Calendar
import kotlin.math.roundToInt

/** 하루를 네 구간으로 */
enum class TimeSlot(val label: String) {
    MORNING("아침"),   // 5–11시
    DAY("낮"),         // 11–17시
    EVENING("저녁"),   // 17–22시
    NIGHT("밤");       // 22–5시

    companion object {
        fun of(time: Long): TimeSlot {
            val h = Calendar.getInstance().apply { timeInMillis = time }.get(Calendar.HOUR_OF_DAY)
            return when (h) {
                in 5..10 -> MORNING
                in 11..16 -> DAY
                in 17..21 -> EVENING
                else -> NIGHT
            }
        }
    }
}

/** 7일 묶음 요약 */
data class WeekSummary(
    val start: Long,                          // 첫날 0시
    val end: Long,                            // 마지막 날 다음 0시
    val entries: List<MoodEntry>,
    val days: List<Pair<Long, List<MoodEntry>>>,   // 날짜(0시) → 그날 기록, 오래된 날부터
    val valence: Map<Valence, Int>,
    val energy: Map<Energy, Int>,
    val topWords: List<Pair<String, Int>>,
    val slots: Map<TimeSlot, List<MoodEntry>>,
    val causes: Map<Cause, Int>,
    val insight: String,
    val needsCare: Boolean,                   // 안 좋은 쪽이 대부분인 주 → 도움 안내
) {
    val count get() = entries.size
    fun pct(n: Int) = if (count == 0) 0 else (n * 100.0 / count).roundToInt()
}

object Weekly {
    private const val DAY = 24 * 60 * 60 * 1000L

    private fun startOfDay(t: Long): Long = Calendar.getInstance().apply {
        timeInMillis = t
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** weeksAgo = 0이면 오늘까지 최근 7일, 1이면 그 전 7일 */
    fun summarize(all: List<MoodEntry>, weeksAgo: Int, now: Long = System.currentTimeMillis()): WeekSummary {
        val todayStart = startOfDay(now)
        val end = Calendar.getInstance().apply { timeInMillis = todayStart; add(Calendar.DAY_OF_YEAR, 1 - 7 * weeksAgo) }.timeInMillis
        val start = Calendar.getInstance().apply { timeInMillis = end; add(Calendar.DAY_OF_YEAR, -7) }.timeInMillis
        val list = all.filter { it.time in start until end }.sortedBy { it.time }

        val days = (0 until 7).map { i ->
            val d0 = Calendar.getInstance().apply { timeInMillis = start; add(Calendar.DAY_OF_YEAR, i) }.timeInMillis
            val d1 = Calendar.getInstance().apply { timeInMillis = d0; add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis
            d0 to list.filter { it.time in d0 until d1 }
        }
        val valence = Valence.entries.associateWith { v -> list.count { it.valence == v } }
        val energy = Energy.entries.associateWith { e -> list.count { it.energy == e } }
        val topWords = list.groupingBy { it.word }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(5).map { it.key to it.value }
        val slots = TimeSlot.entries.associateWith { s -> list.filter { TimeSlot.of(it.time) == s } }
        val causes = Cause.entries.associateWith { c -> list.count { it.cause == c } }.filterValues { it > 0 }

        val badShare = if (list.isEmpty()) 0.0 else valence.getValue(Valence.BAD).toDouble() / list.size
        val needsCare = list.size >= 5 && badShare >= 0.7
        return WeekSummary(start, end, list, days, valence, energy, topWords, slots, causes,
            insight(list, valence, topWords, slots, causes, weeksAgo), needsCare)
    }

    /** 숫자에서 바로 나오는 사실만 문장으로 (해석·조언은 하지 않음) */
    private fun insight(
        list: List<MoodEntry>,
        valence: Map<Valence, Int>,
        top: List<Pair<String, Int>>,
        slots: Map<TimeSlot, List<MoodEntry>>,
        causes: Map<Cause, Int>,
        weeksAgo: Int,
    ): String {
        val which = if (weeksAgo == 0) "이번 주" else "이 주"
        if (list.isEmpty()) return "${which}에는 기록이 없어요. 하루 한두 번, 마음이 움직일 때 기록해 보세요."
        val n = list.size
        val parts = mutableListOf<String>()
        parts += "${which}에 ${n}번 기록했어요."
        top.firstOrNull()?.let { (w, c) ->
            parts += if (c >= 2) "가장 자주 고른 감정은 '$w'(${c}번)이에요." else "가장 최근에 고른 감정은 '${list.last().word}'."
        }
        val (topV, topVn) = valence.maxBy { it.value }
        val label = when (topV) { Valence.GOOD -> "좋은 쪽"; Valence.NEUTRAL -> "그저 그런 쪽"; Valence.BAD -> "안 좋은 쪽" }
        parts += "기분은 ${label}이${(topVn * 100.0 / n).roundToInt()}%로 가장 많았어요."
        causes.maxByOrNull { it.value }?.takeIf { it.value >= 2 }?.let { (c, _) ->
            val what = when (c) {
                Cause.PEOPLE -> "사람"; Cause.WORK -> "일·공부"; Cause.SELF -> "나 자신"; Cause.BODY -> "몸 상태"; Cause.ANY -> "특별한 이유 없는 감정"
            }
            parts += if (c == Cause.ANY) "이유를 꼽기 어려운 순간이 많았어요." else "주로 $what 때문이었어요."
        }
        val badBySlot = slots.mapValues { (_, l) -> l.count { it.valence == Valence.BAD } }
        badBySlot.maxByOrNull { it.value }?.takeIf { it.value >= 2 }?.let { (s, _) ->
            parts += "안 좋은 쪽 기분은 ${s.label}에 가장 많았어요."
        }
        return parts.joinToString(" ")
    }
}
