package com.example.moodwords

import android.content.Context
import androidx.compose.ui.graphics.Color
import org.json.JSONArray

/** 몸의 에너지(각성) 수준 — 심박으로 추정하고 사용자가 확인/수정 */
enum class Energy(val label: String) { HIGH("높음"), MID("보통"), LOW("낮음") }

/** 기분의 방향 — 사용자에게 물어봄 */
enum class Valence(val label: String) { GOOD("좋은 쪽"), NEUTRAL("그저 그럼"), BAD("안 좋은 쪽") }

/** 무엇 때문에 — 후보를 좁히는 세 번째 질문 */
enum class Cause(val label: String) {
    PEOPLE("사람 때문에"),
    WORK("일·공부 때문에"),
    SELF("나 자신 때문에"),
    BODY("몸 상태 때문에"),
    ANY("그냥 / 잘 모르겠음"),
}

data class EmotionWord(
    val word: String,
    val meaning: String,
    val energy: Energy,
    val valence: Valence,
    val causes: Set<Cause>,
    val rarity: Int,          // 1 흔함 ~ 3 드묾
)

/**
 * 감정 단어 사전 (assets/emotions.json, 약 1,000개).
 * 에너지 × 기분 9칸에 나뉘어 있고, 각 단어에 "무엇 때문에" 태그가 붙어 있음.
 */
object EmotionWords {
    @Volatile private var all: List<EmotionWord>? = null

    fun load(context: Context): List<EmotionWord> = all ?: synchronized(this) {
        all ?: parse(context.assets.open("emotions.json").bufferedReader().use { it.readText() }).also { all = it }
    }

    private fun parse(text: String): List<EmotionWord> {
        val arr = JSONArray(text)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val c = o.getJSONArray("c")
            EmotionWord(
                word = o.getString("w"),
                meaning = o.getString("m"),
                energy = Energy.valueOf(o.getString("e")),
                valence = Valence.valueOf(o.getString("v")),
                causes = (0 until c.length()).mapNotNull { runCatching { Cause.valueOf(c.getString(it)) }.getOrNull() }.toSet(),
                rarity = o.optInt("r", 2),
            )
        }
    }

    /**
     * 후보 단어를 흔한 말 순서로. 고른 원인에 맞는 단어를 먼저,
     * 그다음 같은 칸의 나머지를 붙여서 "더 보기"로 볼 수 있게 함.
     */
    fun find(context: Context, energy: Energy, valence: Valence, cause: Cause): List<EmotionWord> {
        val cell = load(context).filter { it.energy == energy && it.valence == valence }
        if (cause == Cause.ANY) return cell.sortedBy { it.rarity }
        val (match, rest) = cell.partition { cause in it.causes }
        return match.sortedBy { it.rarity } + rest.sortedBy { it.rarity }
    }

    /** 원인에 딱 맞는 단어가 몇 개인지 (결과 화면 안내용) */
    fun matchCount(words: List<EmotionWord>, cause: Cause): Int =
        if (cause == Cause.ANY) words.size else words.count { cause in it.causes }

    fun colorOf(valence: Valence): Color = when (valence) {
        Valence.GOOD -> Color(0xFFFFC94A)
        Valence.NEUTRAL -> Color(0xFF8FB8FF)
        Valence.BAD -> Color(0xFFB98AFF)
    }

    /** 평소 안정 심박 대비 지금 심박으로 에너지 수준 추정 */
    fun estimateEnergy(bpm: Double, restingBpm: Double): Energy {
        val diff = bpm - restingBpm
        return when {
            diff >= 12 -> Energy.HIGH
            diff <= 2 -> Energy.LOW
            else -> Energy.MID
        }
    }
}
