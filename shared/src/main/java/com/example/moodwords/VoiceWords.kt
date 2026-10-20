package com.example.moodwords

import android.content.Context

/**
 * 내 말로 말한 문장에서 감정 단어 찾기.
 * "좀 서운하고 외로워" → 서운함, 외로움. 활용형(서운해·외로운·슬퍼·설레요)도 찾도록
 * 단어마다 어간 형태를 몇 개 만들어 둠.
 */
object VoiceWords {
    @Volatile private var forms: List<Pair<EmotionWord, List<String>>>? = null

    private fun formsOf(context: Context) = forms ?: synchronized(this) {
        forms ?: EmotionWords.load(context).map { w ->
            w to (variants(w.word.replace(" ", "")) + ALIASES.filterValues { it == w.word }.keys)
        }.also { forms = it }
    }

    /** 말한 문장에 들어 있는 감정 단어 (긴 표현으로 맞은 것부터) */
    fun match(context: Context, text: String): List<EmotionWord> {
        val t = text.replace(" ", "")
        if (t.isBlank()) return emptyList()
        val hits = formsOf(context).mapNotNull { (w, fs) ->
            fs.filter { t.contains(it) }.maxByOrNull { it.length }?.let { w to it }
        }
        // 다른 맞은 단어 안에 통째로 들어 있는 짧은 것은 뺌 (예: '불안'이 '불안정'에 맞은 경우)
        val keep = hits.filter { (w, f) -> hits.none { (o, of) -> o != w && of.length > f.length && of.contains(f) } }
        return keep.sortedWith(compareByDescending<Pair<EmotionWord, String>> { it.second.length }.thenBy { it.first.rarity })
            .map { it.first }.take(8)
    }

    /** 규칙으로는 안 잡히는 입말 → 사전 단어 */
    private val ALIASES = mapOf(
        "지쳐" to "지침", "지친" to "지침", "졸려" to "졸림", "졸린" to "졸림",
        "화나" to "화남", "화났" to "화남", "화가나" to "화남", "짜증" to "짜증남",
        "빡쳐" to "빡침", "빡친" to "빡침", "열받" to "열받음", "긴장" to "긴장됨",
        "기분좋" to "기분 좋음", "기분이좋" to "기분 좋음",
    )

    // ── 한글 자모 계산 ──
    private const val BASE = 0xAC00
    private fun isHangul(c: Char) = c.code in BASE..0xD7A3
    private fun jong(c: Char) = (c.code - BASE) % 28
    private fun jung(c: Char) = ((c.code - BASE) / 28) % 21
    private fun withJong(c: Char, j: Int) = (c.code - jong(c) + j).toChar()
    private fun withJung(c: Char, v: Int) = (BASE + ((c.code - BASE) / 588) * 588 + v * 28 + jong(c)).toChar()
    private const val J_M = 16      // 받침 ㅁ
    private const val J_N = 4       // 받침 ㄴ
    private const val J_L = 8       // 받침 ㄹ
    private const val J_LM = 10     // 받침 ㄻ
    private const val J_B = 17      // 받침 ㅂ
    private const val V_EU = 18     // ㅡ
    private const val V_EO = 4      // ㅓ
    private const val V_A = 0       // ㅏ
    private const val V_O = 8       // ㅗ

    /** 단어 하나에서 문장 속에 나올 만한 형태들 (2글자 이상만) */
    fun variants(w: String): List<String> {
        val out = mutableSetOf(w)
        val last = w.lastOrNull() ?: return emptyList()
        val head = w.dropLast(1)
        when {
            last == '함' && head.isNotEmpty() -> {        // 서운함 → 서운, 서운해, 서운한
                out += head; out += head + "해"; out += head + "하"; out += head + "한"
            }
            isHangul(last) && jong(last) == J_LM -> {     // 받침 ㄻ: 힘듦 → 힘들(어/다)
                out += head + withJong(last, J_L)
            }
            last == '됨' && head.isNotEmpty() -> {        // 걱정됨 → 걱정돼, 걱정되
                out += head + "돼"; out += head + "되"
            }
            isHangul(last) && jong(last) == J_M -> {      // 받침 ㅁ 빼기: 설렘→설레, 외로움→외로우, 슬픔→슬프
                val base = head + withJong(last, 0)
                val b = base.last()
                when {
                    b == '우' && base.length >= 2 -> {     // ㅂ 불규칙: 외로워, 외로운, 외롭(다)
                        val h = base.dropLast(1)
                        out += h + "워"; out += h + "운"
                        val hl = h.last()
                        if (isHangul(hl) && jong(hl) == 0) out += h.dropLast(1) + withJong(hl, J_B)
                    }
                    jung(b) == V_EU -> {                    // 슬프 → 슬퍼, 슬픈 / 아프 → 아파
                        val prev = base.dropLast(1).lastOrNull()
                        val v = if (prev != null && isHangul(prev) && jung(prev) in listOf(V_A, V_O)) V_A else V_EO
                        out += base; out += base.dropLast(1) + withJung(b, v); out += base.dropLast(1) + withJong(b, J_N)
                    }
                    else -> out += base                     // 설레, 놀라, 지루하…
                }
            }
        }
        return out.filter { it.length >= 2 }
    }
}
