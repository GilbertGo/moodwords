package com.example.moodwords

import android.content.Context
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
    val research: Research? = null,
    val poem: String = "",        // 감정을 그려 보이는 한 줄 (앱이 새로 쓴 문장)
    val family: String = "",      // 글귀를 고르는 감정 갈래 (JOY, LONGING …)
)

/** 연구 근거: 박인조·민경환(2005) 한국어 감정단어 434개 평정 (7점 척도, 쾌–불쾌·활성화 각 대학생 123명) */
data class Research(
    val sourceWord: String,   // 논문 표기 (예: 설레다)
    val pleasure: Double,     // 쾌-불쾌 1(매우 불쾌) ~ 7(매우 쾌)
    val activation: Double,   // 활성화 1(매우 비활성) ~ 7(매우 활성)
) {
    companion object {
        const val CITATION = "박인조·민경환 (2005). 한국어 감정단어의 목록과 정서 차원 탐색. 한국심리학회지: 사회 및 성격, 19(1), 109–129."
        const val SHORT = "박인조·민경환(2005)"

        /** 앱의 칸 기준: 쾌 ≥4.5 좋은 쪽, ≤3.5 안 좋은 쪽 / 활성화 ≥4.8 높음, ≤3.8 낮음 */
        fun cellOf(r: Research): Pair<Energy, Valence> {
            val v = when { r.pleasure >= 4.5 -> Valence.GOOD; r.pleasure <= 3.5 -> Valence.BAD; else -> Valence.NEUTRAL }
            val e = when { r.activation >= 4.8 -> Energy.HIGH; r.activation <= 3.8 -> Energy.LOW; else -> Energy.MID }
            return e to v
        }
    }
}

/**
 * 감정 단어 사전 (assets/emotions.json). 일부는 박인조·민경환(2005) 평정 근거.
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
                research = if (o.has("s")) Research(o.getString("s"), o.getDouble("p"), o.getDouble("a")) else null,
                poem = o.optString("pm", ""),
                family = o.optString("f", ""),
            )
        }
    }

    /**
     * 후보 단어를 흔한 말 순서로. 고른 원인에 맞는 단어를 먼저,
     * 그다음 같은 칸의 나머지를 붙여서 "더 보기"로 볼 수 있게 함.
     */
    fun find(context: Context, energy: Energy, valence: Valence, cause: Cause): List<EmotionWord> {
        val cell = load(context).filter { it.energy == energy && it.valence == valence }
        // 흔한 말 먼저, 같은 등급이면 연구 근거가 있는 단어 먼저
        val order = compareBy<EmotionWord>({ it.rarity }, { if (it.research != null) 0 else 1 })
        if (cause == Cause.ANY) return cell.sortedWith(order)
        val (match, rest) = cell.partition { cause in it.causes }
        return match.sortedWith(order) + rest.sortedWith(order)
    }

    /** 원인에 딱 맞는 단어가 몇 개인지 (결과 화면 안내용) */
    fun matchCount(words: List<EmotionWord>, cause: Cause): Int =
        if (cause == Cause.ANY) words.size else words.count { cause in it.causes }

    /** 기분 방향별 색 (ARGB). 각 앱에서 Color(argbOf(v))로 사용 */
    fun argbOf(valence: Valence): Long = when (valence) {
        Valence.GOOD -> 0xFFFFC94A
        Valence.NEUTRAL -> 0xFF8FB8FF
        Valence.BAD -> 0xFFB98AFF
    }

    fun count(context: Context): Int = load(context).size
    fun researchCount(context: Context): Int = load(context).count { it.research != null }

    /** 논문 점수 기준(Research.cellOf)과 같은 칸에 놓인 단어 수 */
    fun researchPlacedCount(context: Context): Int =
        load(context).count { w -> w.research?.let { Research.cellOf(it) == (w.energy to w.valence) } == true }

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
