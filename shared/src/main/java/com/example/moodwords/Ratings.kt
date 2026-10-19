package com.example.moodwords

import android.content.Context
import java.util.UUID

/**
 * 단어 점수 매기기 (앱 개선 참여).
 * AI가 칸을 정한 단어에 사용자가 기분·에너지·익숙함을 1~7점으로 매김.
 * 이 기기에만 저장되고, 사용자가 '보내기'를 누를 때만 공유 창으로 내보냄.
 */
data class Rating(val word: String, val pleasure: Int, val activation: Int, val familiarity: Int, val time: Long) {
    /** 모르는 단어라 기분·에너지는 건너뜀 */
    val skipped get() = pleasure == 0
}

class Ratings(context: Context) {
    private val prefs = context.getSharedPreferences("ratings", Context.MODE_PRIVATE)

    fun all(): List<Rating> = prefs.all.mapNotNull { (k, v) ->
        if (!k.startsWith("w:")) return@mapNotNull null
        val p = (v as? String)?.split("|") ?: return@mapNotNull null
        runCatching { Rating(k.removePrefix("w:"), p[0].toInt(), p[1].toInt(), p[2].toInt(), p[3].toLong()) }.getOrNull()
    }.sortedBy { it.time }

    fun count(): Int = prefs.all.keys.count { it.startsWith("w:") }

    fun add(r: Rating) {
        prefs.edit().putString("w:${r.word}", "${r.pleasure}|${r.activation}|${r.familiarity}|${r.time}").apply()
    }

    /** 몇 개까지 보냈는지 (새로 매긴 것만 다시 보낼 수 있게) */
    var sentCount: Int
        get() = prefs.getInt("sent", 0)
        set(v) { prefs.edit().putInt("sent", v).apply() }

    /** 이름 없는 참여 번호: 같은 사람이 두 번 보내도 겹치지 않게 세기 위한 것 */
    private val participant: String
        get() = prefs.getString("pid", null) ?: UUID.randomUUID().toString().take(8).also {
            prefs.edit().putString("pid", it).apply()
        }

    /** 아직 안 매긴 AI 분류 단어 중 흔한 말부터 (같은 등급 안에서는 섞음) */
    fun nextBatch(context: Context, n: Int = 10): List<EmotionWord> {
        val done = prefs.all.keys.filter { it.startsWith("w:") }.map { it.removePrefix("w:") }.toSet()
        return EmotionWords.load(context)
            .filter { it.basis == Basis.AI && it.word !in done }
            .groupBy { it.rarity }.toSortedMap()
            .values.flatMap { it.shuffled() }
            .take(n)
    }

    fun remaining(context: Context): Int {
        val done = prefs.all.keys.filter { it.startsWith("w:") }.map { it.removePrefix("w:") }.toSet()
        return EmotionWords.load(context).count { it.basis == Basis.AI && it.word !in done }
    }

    /** 공유용 글. 첫 줄 안내, 그다음 표 (단어,기분,에너지,익숙함 / 0 = 모르는 단어) */
    fun exportText(version: Int): String = buildString {
        val list = all()
        appendLine("[감정단어 점수] 참여번호 $participant · v1.$version · ${list.size}개")
        appendLine("단어,기분,에너지,익숙함")
        list.forEach { appendLine("${it.word},${it.pleasure},${it.activation},${it.familiarity}") }
    }
}
