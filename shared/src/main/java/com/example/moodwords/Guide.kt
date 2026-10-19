package com.example.moodwords

import android.content.Context

/** 앱 소개: 목적, 사용 방법, 좋은 점, 알아둘 점. 워치·폰이 함께 씀 */
object Guide {
    data class Section(val title: String, val body: String)

    fun sections(isWatch: Boolean): List<Section> = listOf(
        Section(
            "이 앱은",
            "지금 내 마음에 꼭 맞는 이름을 찾도록 돕는 앱이에요. " +
                "느낌에 이름을 붙이면 감정이 조금 다루기 쉬워지고, 기록이 쌓이면 나의 마음 흐름이 보여요."
        ),
        Section(
            "이렇게 써요",
            if (isWatch)
                "1. 마음이 움직일 때, 또는 하루 한두 번 '감정 알아보기'\n" +
                    "2. 15초 동안 가만히: 심박과 손 떨림으로 몸의 에너지를 재요\n" +
                    "3. 질문 3개: 에너지 확인 → 기분의 방향 → 무엇 때문인지\n" +
                    "4. 제안된 단어 중 가장 가까운 것을 골라 기록\n" +
                    "5. 일주일에 한 번 '이번 주 돌아보기'"
            else
                "1. 마음이 움직일 때, 또는 하루 한두 번 '지금 감정 알아보기'\n" +
                    "2. 질문 3개: 몸의 에너지 → 기분의 방향 → 무엇 때문인지\n" +
                    "3. 제안된 단어 중 가장 가까운 것을 골라 기록\n" +
                    "4. 일주일에 한 번 '이번 주 돌아보기'"
        ),
        Section(
            "잘 쓰는 요령",
            "첫 단어가 정답은 아니에요. 비슷한 단어들을 읽어 보고 '이거다' 싶은 말을 고르는 게 핵심이에요. " +
                "좋은 감정도 기록해 주세요. 힘든 순간만 남기면 내 하루가 실제보다 어둡게 보여요."
        ),
        Section(
            "기록하면 좋은 점",
            "• 이름을 붙이는 순간 마음이 조금 가라앉아요\n" +
                "• 비슷한 감정을 가려내는 눈이 섬세해져요\n" +
                "• 언제, 무엇 때문에 마음이 흔들리는지 패턴이 보여요" +
                if (isWatch) "\n• 쓸수록 평소 심박·떨림 기준이 나에게 맞춰져요" else ""
        ),
        Section(
            "알아두세요",
            "• 진단이나 치료 도구가 아니에요. 몸 신호는 감정의 세기만 알려줄 뿐, 어떤 감정인지는 내가 정해요\n" +
                "• 기록은 이 기기 안에만 저장되고 밖으로 보내지 않아요 (인터넷은 업데이트 확인에만 써요)"
        ),
    )

    private const val PREF = "guide"

    fun seen(context: Context): Boolean =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean("seen", false)

    fun markSeen(context: Context) =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putBoolean("seen", true).apply()
}
