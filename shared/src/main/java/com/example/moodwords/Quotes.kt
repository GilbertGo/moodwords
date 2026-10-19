package com.example.moodwords

import android.content.Context
import org.json.JSONArray

/**
 * 감정 갈래별 글귀 (assets/quotes.json).
 * 저작권이 끝난 작품만: 1929년 이전 발표작, 고전·경전, 속담.
 * 외국어·한문은 앱이 새로 옮긴 번역(translated = true).
 */
data class Quote(
    val family: String,
    val text: String,
    val original: String,
    val author: String,
    val work: String,
    val year: Int?,
    val translated: Boolean,
) {
    /** 화면 표시용 출처: "김소월, 「진달래꽃」(1925)" */
    val source: String
        get() = buildString {
            append(author)
            if (work.isNotBlank() && work != author) append(", 「$work」")
            year?.let { append(" ($it)") }
            if (translated) append(" · 옮김")
        }
}

object Quotes {
    @Volatile private var all: List<Quote>? = null

    fun load(context: Context): List<Quote> = all ?: synchronized(this) {
        all ?: parse(context.assets.open("quotes.json").bufferedReader().use { it.readText() }).also { all = it }
    }

    private fun parse(text: String): List<Quote> {
        val arr = JSONArray(text)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Quote(
                family = o.getString("f"),
                text = o.getString("t"),
                original = o.optString("o", ""),
                author = o.getString("a"),
                work = o.optString("k", ""),
                year = if (o.isNull("y")) null else o.optInt("y"),
                translated = o.optBoolean("tr", false),
            )
        }
    }

    /** 이 감정 갈래의 글귀들 (단어마다 시작 위치를 달리해 같은 갈래라도 다른 글귀가 먼저 보이게) */
    fun forWord(context: Context, word: EmotionWord): List<Quote> {
        val list = load(context).filter { it.family == word.family }
        if (list.isEmpty()) return list
        val start = Math.floorMod(word.word.hashCode(), list.size)
        return list.drop(start) + list.take(start)
    }
}
