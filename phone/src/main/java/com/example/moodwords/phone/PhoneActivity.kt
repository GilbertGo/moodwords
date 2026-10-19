package com.example.moodwords.phone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.moodwords.*
import com.example.moodwords.AutoUpdate
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class PhoneActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AutoUpdate.cancelBackground(this)
        setContent { PhoneApp() }
    }
}

// ── 색과 글꼴 ─────────────────────────────────────────────
private val Paper = Color(0xFFFAF7F2)
private val Ink = Color(0xFF1F1B16)
private val Muted = Color(0xFF7A7268)
private val Line = Color(0xFFE6DFD5)
private val Accent = Color(0xFF3D5A80)

private fun tint(v: Valence) = Color(EmotionWords.argbOf(v))
/** 밝은 배경 위 글자용으로 더 진하게 */
private fun deep(v: Valence) = when (v) {
    Valence.GOOD -> Color(0xFFA86F00)
    Valence.NEUTRAL -> Color(0xFF2F5FA8)
    Valence.BAD -> Color(0xFF6B3FB0)
}

private val scheme = lightColorScheme(
    primary = Accent, onPrimary = Color.White,
    background = Paper, surface = Paper, onBackground = Ink, onSurface = Ink,
)

// ── 화면 상태 ─────────────────────────────────────────────
private enum class Screen { HOME, ENERGY, VALENCE, CAUSE, RESULT, HISTORY, ABOUT }

private class Check {
    var energy by mutableStateOf<Energy?>(null)
    var valence by mutableStateOf<Valence?>(null)
    var cause by mutableStateOf<Cause?>(null)
    fun reset() { energy = null; valence = null; cause = null }
}

@Composable
fun PhoneApp() {
    val context = LocalContext.current
    val store = remember { MoodStore(context) }
    val check = remember { Check() }
    var screen by remember { mutableStateOf(Screen.HOME) }

    // 뒤로가기: 질문은 한 단계씩, 나머지는 홈으로
    BackHandler(enabled = screen != Screen.HOME) {
        screen = when (screen) {
            Screen.VALENCE -> Screen.ENERGY
            Screen.CAUSE -> Screen.VALENCE
            Screen.RESULT -> Screen.CAUSE
            else -> Screen.HOME
        }
    }

    MaterialTheme(colorScheme = scheme) {
        Surface(Modifier.fillMaxSize(), color = Paper) {
            Box(Modifier.fillMaxSize().systemBarsPadding()) {
                when (screen) {
                    Screen.HOME -> HomeScreen(
                        onStart = { check.reset(); screen = Screen.ENERGY },
                        onHistory = { screen = Screen.HISTORY },
                        onAbout = { screen = Screen.ABOUT },
                    )
                    Screen.ENERGY -> Question(
                        step = 1,
                        title = "지금 몸의 에너지는 어떤가요?",
                        hint = "워치 버전은 심박으로 재지만, 여기서는 느낌으로 골라주세요.",
                        options = listOf(
                            Triple(Energy.HIGH, "높음", "들뜨거나 긴장돼서 심장이 빨리 뛰어요"),
                            Triple(Energy.MID, "보통", "평소처럼 깨어 있어요"),
                            Triple(Energy.LOW, "낮음", "몸이 처지거나 느긋하게 가라앉아 있어요"),
                        ),
                        selected = check.energy,
                    ) { check.energy = it; screen = Screen.VALENCE }
                    Screen.VALENCE -> Question(
                        step = 2,
                        title = "기분은 어느 쪽에 가까워요?",
                        hint = null,
                        options = listOf(
                            Triple(Valence.GOOD, "좋은 쪽", "편하거나 즐겁거나 기대돼요"),
                            Triple(Valence.NEUTRAL, "그저 그럼", "좋지도 나쁘지도 않거나 섞여 있어요"),
                            Triple(Valence.BAD, "안 좋은 쪽", "불편하거나 힘들거나 속상해요"),
                        ),
                        selected = check.valence,
                    ) { check.valence = it; screen = Screen.CAUSE }
                    Screen.CAUSE -> Question(
                        step = 3,
                        title = "무엇 때문인 것 같아요?",
                        hint = null,
                        options = Cause.entries.map { Triple(it, it.label, null) },
                        selected = check.cause,
                    ) { check.cause = it; screen = Screen.RESULT }
                    Screen.RESULT -> ResultScreen(
                        check,
                        onSave = { word ->
                            store.add(MoodEntry(System.currentTimeMillis(), word, null, check.energy!!, check.valence!!))
                            screen = Screen.HISTORY
                        },
                        onRestart = { check.reset(); screen = Screen.ENERGY },
                    )
                    Screen.HISTORY -> HistoryScreen(store, onBack = { screen = Screen.HOME })
                    Screen.ABOUT -> AboutScreen(onBack = { screen = Screen.HOME })
                }
            }
        }
    }
}

// ── 홈 ───────────────────────────────────────────────────
@Composable
private fun HomeScreen(onStart: () -> Unit, onHistory: () -> Unit, onAbout: () -> Unit) {
    val context = LocalContext.current
    var update by remember { mutableStateOf<Updater.Release?>(null) }
    var showUpdate by remember { mutableStateOf(false) }
    // 앱을 열면 새 버전 확인 → 있으면 안내 창
    LaunchedEffect(Unit) {
        update = Updater.checkNewer(Updater.PHONE, BuildConfig.VERSION_CODE)
        showUpdate = update != null
    }
    update?.let { rel -> if (showUpdate) UpdateDialog(rel, onLater = { showUpdate = false }) }
    val total = remember { EmotionWords.count(context) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        update?.let { rel ->
            // '나중에'를 눌렀어도 다시 열 수 있게 작은 알림
            Text(
                "🎁 새 버전 v1.${rel.version} 있어요",
                fontSize = 14.sp, color = Color(0xFF5B4BDB), fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Color(0xFFEDEBFC))
                    .clickable { showUpdate = true }.padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
        Spacer(Modifier.height(40.dp))
        Text("감정단어", fontSize = 40.sp, fontWeight = FontWeight.Black, color = Ink)
        Spacer(Modifier.height(8.dp))
        Text(
            "지금 내 마음에 꼭 맞는 이름을 찾아요.\n이름을 붙이면 감정이 조금 다루기 쉬워져요.",
            fontSize = 17.sp, lineHeight = 26.sp, color = Muted
        )
        Spacer(Modifier.height(36.dp))

        // 9칸 미리보기 — 앱이 쓰는 지도
        MoodGrid()
        Spacer(Modifier.height(36.dp))

        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth().height(60.dp),
            shape = RoundedCornerShape(16.dp),
        ) { Text("지금 감정 알아보기", fontSize = 18.sp, fontWeight = FontWeight.SemiBold) }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onHistory,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
        ) { Text("감정 기록 보기", fontSize = 16.sp, color = Ink) }

        Spacer(Modifier.height(28.dp))
        Text(
            "감정 단어 ${"%,d".format(total)}개 · 연구 근거 보기",
            fontSize = 14.sp, color = Accent,
            modifier = Modifier.clickable(onClick = onAbout).padding(vertical = 6.dp)
        )
        Text("v${BuildConfig.VERSION_NAME}", fontSize = 12.sp, color = Muted)
    }
}

/** 새 버전 안내 창: 바뀐 점, 지금 → 새 버전, 업데이트 / 나중에 */
@Composable
private fun UpdateDialog(rel: Updater.Release, onLater: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var progress by remember { mutableStateOf<Float?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val violet = Color(0xFF5B4BDB)

    Dialog(onDismissRequest = { if (progress == null) onLater() }) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Color(0xFFF4F3F8))
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("🎁", fontSize = 44.sp)
            Spacer(Modifier.height(14.dp))
            Text("새 버전이 있어요", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink)
            if (rel.notes.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(rel.notes, fontSize = 15.sp, lineHeight = 22.sp, color = Ink.copy(alpha = 0.75f), textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(10.dp))
            Text("지금 v${BuildConfig.VERSION_NAME} → 새 v1.${rel.version}", fontSize = 14.sp, color = Muted)
            Spacer(Modifier.height(22.dp))
            TextButton(
                onClick = {
                    if (progress != null) return@TextButton
                    progress = 0f; error = null
                    scope.launch {
                        runCatching { Updater.install(context, rel) { progress = it } }
                            .onFailure { error = "받기 실패, 다시 눌러주세요"; progress = null }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    progress?.let { "받는 중… ${(it * 100).roundToInt()}%" } ?: "업데이트",
                    fontSize = 18.sp, fontWeight = FontWeight.Bold, color = violet
                )
            }
            error?.let { Text(it, fontSize = 13.sp, color = Color(0xFFB3261E)) }
            TextButton(onClick = onLater, enabled = progress == null, modifier = Modifier.fillMaxWidth()) {
                Text("나중에", fontSize = 16.sp, color = Muted)
            }
        }
    }
}

/** 에너지 × 기분 3×3 지도 (홈 화면 장식 겸 설명) */
@Composable
private fun MoodGrid() {
    // 각 칸에서 연구 근거가 있고 친숙성이 높은 단어 (emotions.json의 실제 칸과 일치)
    val samples = mapOf(
        (Energy.HIGH to Valence.GOOD) to "설렘", (Energy.HIGH to Valence.NEUTRAL) to "놀람", (Energy.HIGH to Valence.BAD) to "불안함",
        (Energy.MID to Valence.GOOD) to "행복함", (Energy.MID to Valence.NEUTRAL) to "부러움", (Energy.MID to Valence.BAD) to "걱정됨",
        (Energy.LOW to Valence.GOOD) to "편안함", (Energy.LOW to Valence.NEUTRAL) to "그리움", (Energy.LOW to Valence.BAD) to "외로움",
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (e in Energy.entries) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (v in listOf(Valence.BAD, Valence.NEUTRAL, Valence.GOOD)) {
                    val alpha = when (e) { Energy.HIGH -> 0.55f; Energy.MID -> 0.35f; Energy.LOW -> 0.2f }
                    Box(
                        Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(10.dp))
                            .background(tint(v).copy(alpha = alpha)),
                        contentAlignment = Alignment.Center
                    ) { Text(samples.getValue(e to v), fontSize = 14.sp, color = Ink) }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text("← 불쾌", fontSize = 12.sp, color = Muted)
            Spacer(Modifier.weight(1f))
            Text("위로 갈수록 에너지 높음", fontSize = 12.sp, color = Muted)
            Spacer(Modifier.weight(1f))
            Text("쾌 →", fontSize = 12.sp, color = Muted)
        }
    }
}

// ── 질문 ─────────────────────────────────────────────────
@Composable
private fun <T> Question(
    step: Int,
    title: String,
    hint: String?,
    options: List<Triple<T, String, String?>>,
    selected: T?,
    onPick: (T) -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        // 진행 표시
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(3) { i ->
                Box(
                    Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp))
                        .background(if (i < step) Accent else Line)
                )
            }
        }
        Spacer(Modifier.height(32.dp))
        Text("질문 $step / 3", fontSize = 14.sp, color = Muted)
        Spacer(Modifier.height(6.dp))
        Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold, lineHeight = 34.sp, color = Ink)
        if (hint != null) {
            Spacer(Modifier.height(8.dp))
            Text(hint, fontSize = 14.sp, color = Muted, lineHeight = 20.sp)
        }
        Spacer(Modifier.height(28.dp))
        options.forEach { (value, label, desc) ->
            val on = value == selected
            Column(
                Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(16.dp))
                    .background(if (on) Accent.copy(alpha = 0.08f) else Color.White)
                    .border(1.5.dp, if (on) Accent else Line, RoundedCornerShape(16.dp))
                    .clickable { onPick(value) }
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                Text(label, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                if (desc != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(desc, fontSize = 14.sp, color = Muted)
                }
            }
        }
    }
}

// ── 결과 ─────────────────────────────────────────────────
private const val FIRST_SHOWN = 12

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultScreen(check: Check, onSave: (String) -> Unit, onRestart: () -> Unit) {
    val context = LocalContext.current
    val energy = check.energy ?: return
    val valence = check.valence ?: return
    val cause = check.cause ?: Cause.ANY
    val words = remember(energy, valence, cause) { EmotionWords.find(context, energy, valence, cause) }
    if (words.isEmpty()) return
    var chosen by remember(words) { mutableStateOf(words.first()) }
    var showAll by remember(words) { mutableStateOf(false) }
    val others = words.filter { it != chosen }
    val shown = if (showAll) others else others.take(FIRST_SHOWN)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Text("지금 마음은", fontSize = 16.sp, color = Muted)
        Spacer(Modifier.height(12.dp))

        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                .background(tint(valence).copy(alpha = 0.22f)).padding(24.dp)
        ) {
            Text(
                chosen.word,
                fontSize = if (chosen.word.length <= 6) 44.sp else 32.sp,
                fontWeight = FontWeight.Black, color = deep(valence), lineHeight = 50.sp
            )
            if (chosen.poem.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    chosen.poem, fontSize = 20.sp, lineHeight = 30.sp,
                    fontStyle = FontStyle.Italic, fontFamily = FontFamily.Serif, color = Ink
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(chosen.meaning, fontSize = 15.sp, lineHeight = 22.sp, color = Ink.copy(alpha = 0.7f))
            Spacer(Modifier.height(16.dp))
            Text(
                "에너지 ${energy.label} · 기분 ${valence.label} · ${cause.label}",
                fontSize = 13.sp, color = Muted
            )
            chosen.research?.let { r ->
                Spacer(Modifier.height(10.dp))
                Text(
                    "연구 근거 · ${Research.SHORT} '${r.sourceWord}'\n쾌 %.2f · 각성 %.2f (7점 척도)".format(r.pleasure, r.activation),
                    fontSize = 12.sp, color = Muted, lineHeight = 17.sp
                )
            }
        }

        // 이 감정에 어울리는 글귀 (눌러서 다음 글귀)
        val quotes = remember(chosen) { Quotes.forWord(context, chosen) }
        var quoteIdx by remember(chosen) { mutableIntStateOf(0) }
        quotes.getOrNull(quoteIdx % quotes.size.coerceAtLeast(1))?.let { q ->
            Spacer(Modifier.height(16.dp))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White)
                    .border(1.dp, Line, RoundedCornerShape(20.dp))
                    .clickable(enabled = quotes.size > 1) { quoteIdx++ }
                    .padding(20.dp)
            ) {
                Text("“", fontSize = 36.sp, color = deep(valence), fontFamily = FontFamily.Serif, lineHeight = 30.sp)
                Text(q.text, fontSize = 17.sp, lineHeight = 26.sp, fontFamily = FontFamily.Serif, color = Ink)
                if (q.original.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(q.original, fontSize = 12.sp, lineHeight = 17.sp, color = Muted)
                }
                Spacer(Modifier.height(10.dp))
                Text("— ${q.source}", fontSize = 13.sp, color = Muted)
                if (quotes.size > 1) {
                    Spacer(Modifier.height(6.dp))
                    Text("눌러서 다른 글귀 보기 (${quoteIdx % quotes.size + 1}/${quotes.size})", fontSize = 12.sp, color = Accent)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { onSave(chosen.word) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
        ) { Text("이 단어로 기록하기", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }

        Spacer(Modifier.height(28.dp))
        Text("다른 단어가 더 맞나요?", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            shown.forEach { w ->
                Text(
                    w.word, fontSize = 15.sp, color = Ink,
                    modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Color.White)
                        .border(1.dp, Line, RoundedCornerShape(20.dp))
                        .clickable { chosen = w }
                        .padding(horizontal = 14.dp, vertical = 9.dp)
                )
            }
            if (!showAll && others.size > FIRST_SHOWN) {
                Text(
                    "더 보기 +${others.size - FIRST_SHOWN}", fontSize = 15.sp, color = Accent,
                    modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Accent.copy(alpha = 0.08f))
                        .clickable { showAll = true }
                        .padding(horizontal = 14.dp, vertical = 9.dp)
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onRestart) { Text("처음부터 다시", color = Muted) }
    }
}

// ── 기록 ─────────────────────────────────────────────────
@Composable
private fun HistoryScreen(store: MoodStore, onBack: () -> Unit) {
    val entries = remember { store.entries() }
    val fmt = remember { SimpleDateFormat("M월 d일 (E) a h:mm", Locale.KOREA) }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(20.dp))
        TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) { Text("← 처음으로", color = Muted) }
        Text("감정 기록", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(16.dp))
        if (entries.isEmpty()) {
            Text("아직 기록이 없어요.\n감정을 알아보고 기록해 보세요.", fontSize = 16.sp, color = Muted, lineHeight = 24.sp)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(entries) { e ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White)
                        .border(1.dp, Line, RoundedCornerShape(14.dp)).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(12.dp).clip(RoundedCornerShape(6.dp)).background(tint(e.valence)))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(e.word, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = deep(e.valence))
                        Text(fmt.format(Date(e.time)), fontSize = 13.sp, color = Muted)
                    }
                    Text("에너지 ${e.energy.label}", fontSize = 12.sp, color = Muted)
                }
            }
        }
    }
}

// ── 연구 근거 ─────────────────────────────────────────────
@Composable
private fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val total = remember { EmotionWords.count(context) }
    val withResearch = remember { EmotionWords.researchCount(context) }
    val placed = remember { EmotionWords.researchPlacedCount(context) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) { Text("← 처음으로", color = Muted) }
        Text("이 앱의 근거", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(20.dp))

        Section(
            "감정 지도: 쾌–불쾌 × 각성",
            "감정을 '얼마나 기분 좋은가'와 '몸이 얼마나 깨어 있는가' 두 축으로 보는 정서 원형 모델(Russell, 1980)을 3×3으로 단순화했어요."
        )
        Section(
            "한국어 감정 단어 연구",
            "한국어 감정단어 434개의 쾌–불쾌와 활성화를 대학생 123명씩이 7점 척도로 평정한 연구가 있어요. 앱 단어 ${"%,d".format(total)}개 중 ${"%,d".format(withResearch)}개가 이 연구의 단어이고, 그중 ${"%,d".format(placed)}개는 점수 그대로 칸을 정했어요. 나머지 ${withResearch - placed}개는 친숙성이 낮아 평정이 불안정해서 사전 뜻에 맞춰 두었어요.\n\n${Research.CITATION}\n\n연구에 없는 단어(몸 상태, 일상 표현 등)는 같은 기준으로 앱이 분류한 것이라 연구로 검증되지는 않았어요."
        )
        Section(
            "왜 이름을 붙이나요?",
            "감정을 말로 표현하면 정서 반응이 누그러진다는 연구(정서 명명, Lieberman 외 2007)와, 감정을 세밀하게 구분할수록 조절을 잘한다는 연구(정서 세분화, Barrett)가 있어요."
        )
        Section(
            "'무엇 때문에' 질문",
            "위 한국어 연구에서 쾌–불쾌 다음으로 나타난 차원은 '타인 초점 대 자기 초점'이었어요. 사람 때문인지, 나 자신 때문인지 묻는 이유예요."
        )
        Section(
            "감성 문장과 글귀",
            "단어마다 붙은 한 줄 문장은 앱이 새로 쓴 것이에요. 글귀는 저작권이 끝난 작품(1929년 이전 발표작, 고전·경전, 속담)에서만 골랐고, 외국어와 한문은 앱이 직접 우리말로 옮겼어요(‘옮김’ 표시)."
        )
        Section(
            "한계",
            "이 앱은 자기 이해를 돕는 도구이며 진단이나 치료를 위한 것이 아니에요. 마음이 오래 힘들다면 가까운 사람이나 전문가와 이야기해 보세요."
        )
    }
}

@Composable
private fun Section(title: String, body: String) {
    Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Ink)
    Spacer(Modifier.height(6.dp))
    Text(body, fontSize = 15.sp, lineHeight = 23.sp, color = Ink.copy(alpha = 0.85f))
    Spacer(Modifier.height(22.dp))
}
