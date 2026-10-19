package com.example.moodwords

import androidx.compose.ui.graphics.Color

/** 몸의 에너지(각성) 수준 — 심박으로 추정하고 사용자가 확인/수정 */
enum class Energy(val label: String) { HIGH("높음"), MID("보통"), LOW("낮음") }

/** 기분의 방향 — 사용자에게 물어봄 */
enum class Valence(val label: String) { GOOD("좋은 쪽"), NEUTRAL("그저 그럼"), BAD("안 좋은 쪽") }

data class EmotionWord(val word: String, val meaning: String)

/**
 * 에너지 × 기분 3×3 표(무드 미터 방식). 칸마다 가장 대표적인 단어가 맨 앞.
 */
object EmotionWords {
    private val table: Map<Pair<Energy, Valence>, List<EmotionWord>> = mapOf(
        (Energy.HIGH to Valence.GOOD) to listOf(
            EmotionWord("설렘", "곧 다가올 좋은 일을 기다리며 마음이 두근거리는 상태예요."),
            EmotionWord("신남", "에너지가 넘치고 무엇이든 해보고 싶은 즐거운 상태예요."),
            EmotionWord("뿌듯함", "해낸 일이 자랑스럽고 스스로가 대견하게 느껴지는 상태예요."),
        ),
        (Energy.HIGH to Valence.NEUTRAL) to listOf(
            EmotionWord("긴장됨", "중요한 일을 앞두고 몸과 마음이 바짝 준비된 상태예요."),
            EmotionWord("들뜸", "마음이 붕 떠서 차분히 있기 어려운 상태예요."),
            EmotionWord("조급함", "시간이나 일이 쫓기는 것 같아 서두르게 되는 상태예요."),
        ),
        (Energy.HIGH to Valence.BAD) to listOf(
            EmotionWord("불안함", "무슨 일이 생길지 몰라 마음이 놓이지 않는 상태예요."),
            EmotionWord("화남", "부당하거나 거슬리는 일로 속에서 열이 오르는 상태예요."),
            EmotionWord("초조함", "결과를 기다리며 애가 타고 안절부절못하는 상태예요."),
        ),
        (Energy.MID to Valence.GOOD) to listOf(
            EmotionWord("즐거움", "지금 하는 일이나 함께 있는 사람이 좋아 기분이 밝은 상태예요."),
            EmotionWord("만족스러움", "바라던 만큼 채워져서 더 바랄 게 없는 상태예요."),
            EmotionWord("감사함", "누군가의 마음이나 지금 가진 것이 고맙게 느껴지는 상태예요."),
        ),
        (Energy.MID to Valence.NEUTRAL) to listOf(
            EmotionWord("덤덤함", "특별히 흔들림 없이 담담하게 받아들이는 상태예요."),
            EmotionWord("궁금함", "무언가를 더 알고 싶어 관심이 가는 상태예요."),
            EmotionWord("무난함", "좋지도 나쁘지도 않게 평범하게 흘러가는 상태예요."),
        ),
        (Energy.MID to Valence.BAD) to listOf(
            EmotionWord("답답함", "생각대로 풀리지 않아 가슴이 꽉 막힌 듯한 상태예요."),
            EmotionWord("짜증남", "작은 일에도 신경이 곤두서고 거슬리는 상태예요."),
            EmotionWord("서운함", "기대했던 마음을 몰라줘서 섭섭한 상태예요."),
        ),
        (Energy.LOW to Valence.GOOD) to listOf(
            EmotionWord("편안함", "몸과 마음에 긴장이 풀려 느긋하게 쉬는 상태예요."),
            EmotionWord("평온함", "마음이 고요하고 흔들림 없이 안정된 상태예요."),
            EmotionWord("홀가분함", "부담이나 걱정을 내려놓아 가벼워진 상태예요."),
        ),
        (Energy.LOW to Valence.NEUTRAL) to listOf(
            EmotionWord("나른함", "몸이 늘어지고 졸음이 오는 느슨한 상태예요."),
            EmotionWord("멍함", "생각이 잘 이어지지 않고 머리가 비어 있는 듯한 상태예요."),
            EmotionWord("심심함", "할 일이나 자극이 없어 시간이 더디게 느껴지는 상태예요."),
        ),
        (Energy.LOW to Valence.BAD) to listOf(
            EmotionWord("지침", "몸과 마음의 힘이 다 빠져 아무것도 하기 싫은 상태예요."),
            EmotionWord("우울함", "마음이 가라앉고 즐거운 일이 잘 떠오르지 않는 상태예요."),
            EmotionWord("외로움", "곁에 마음을 나눌 누군가가 없다고 느끼는 상태예요."),
        ),
    )

    fun find(energy: Energy, valence: Valence): List<EmotionWord> = table.getValue(energy to valence)

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
