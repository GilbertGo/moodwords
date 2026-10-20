package com.example.moodwords.phone

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicBoolean
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
private enum class Screen { HOME, VOICE, ENERGY, VALENCE, CAUSE, INTENSITY, RESULT, AFTER, HISTORY, ABOUT, WEEK, GUIDE, RATE }

private class Check {
    var energy by mutableStateOf<Energy?>(null)
    var valence by mutableStateOf<Valence?>(null)
    var cause by mutableStateOf<Cause?>(null)
    var spoken by mutableStateOf<String?>(null)
    var voiceHits by mutableStateOf<List<EmotionWord>>(emptyList())
    var voiceLevel by mutableStateOf<VoiceLevel?>(null)
    var before by mutableStateOf<Int?>(null)          // 이름 붙이기 전 세기
    fun reset() { energy = null; valence = null; cause = null; spoken = null; voiceHits = emptyList(); voiceLevel = null; before = null }
}

@Composable
fun PhoneApp() {
    val context = LocalContext.current
    val store = remember { MoodStore(context) }
    val check = remember { Check() }
    // 처음 열면 앱 소개부터
    var screen by remember { mutableStateOf(if (Guide.seen(context)) Screen.HOME else Screen.GUIDE) }

    // 뒤로가기: 질문은 한 단계씩, 나머지는 홈으로
    BackHandler(enabled = screen != Screen.HOME) {
        screen = when (screen) {
            Screen.ENERGY -> Screen.VOICE
            Screen.VALENCE -> Screen.ENERGY
            Screen.CAUSE -> Screen.VALENCE
            Screen.INTENSITY -> Screen.CAUSE
            Screen.RESULT -> Screen.INTENSITY
            Screen.AFTER -> Screen.HISTORY
            else -> Screen.HOME
        }
    }

    MaterialTheme(colorScheme = scheme) {
        Surface(Modifier.fillMaxSize(), color = Paper) {
            Box(Modifier.fillMaxSize().systemBarsPadding()) {
                when (screen) {
                    Screen.HOME -> HomeScreen(
                        onStart = { check.reset(); screen = Screen.VOICE },
                        onHistory = { screen = Screen.HISTORY },
                        onAbout = { screen = Screen.ABOUT },
                        onWeek = { screen = Screen.WEEK },
                        onGuide = { screen = Screen.GUIDE },
                        onRate = { screen = Screen.RATE },
                    )
                    Screen.VOICE -> VoiceScreen(check, onNext = { screen = Screen.ENERGY })
                    Screen.ENERGY -> Question(
                        step = 1,
                        title = "지금 몸의 에너지는 어떤가요?",
                        hint = check.voiceLevel?.let { "목소리가 ${it.label} → '${check.energy?.label}'으로 골라 뒀어요. 느낌과 다르면 바꿔 주세요." }
                            ?: "워치 버전은 심박으로 재지만, 여기서는 느낌으로 골라주세요.",
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
                        hint = check.voiceHits.firstOrNull()?.let { "말한 '${it.word}' → '${it.valence.label}'으로 골라 뒀어요." },
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
                    ) { check.cause = it; screen = Screen.INTENSITY }
                    Screen.INTENSITY -> IntensityScreen(
                        title = "이 마음은 얼마나 강해요?",
                        hint = "단어를 고르기 전에 한 번, 기록한 뒤에 한 번 물어요. 이름을 붙이면 세기가 어떻게 달라지는지 내 기록으로 볼 수 있어요.",
                        before = null,
                        selected = check.before,
                        onPick = { check.before = it; screen = Screen.RESULT },
                        onSkip = { check.before = null; screen = Screen.RESULT },
                    )
                    Screen.RESULT -> ResultScreen(
                        check,
                        onSave = { word, word2 ->
                            store.add(
                                MoodEntry(System.currentTimeMillis(), word, null, check.energy!!, check.valence!!, null, check.cause,
                                    before = check.before, word2 = word2)
                            )
                            screen = Screen.AFTER
                        },
                        onRestart = { check.reset(); screen = Screen.VOICE },
                    )
                    Screen.AFTER -> AfterScreen(store, check.before, onDone = { screen = Screen.HISTORY })
                    Screen.HISTORY -> HistoryScreen(store, onBack = { screen = Screen.HOME })
                    Screen.ABOUT -> AboutScreen(onBack = { screen = Screen.HOME })
                    Screen.WEEK -> WeekScreen(store, onBack = { screen = Screen.HOME })
                    Screen.RATE -> RateScreen(onBack = { screen = Screen.HOME })
                    Screen.GUIDE -> GuideScreen(onDone = { Guide.markSeen(context); screen = Screen.HOME })
                }
            }
        }
    }
}

// ── 홈 ───────────────────────────────────────────────────
@Composable
private fun HomeScreen(onStart: () -> Unit, onHistory: () -> Unit, onAbout: () -> Unit, onWeek: () -> Unit, onGuide: () -> Unit, onRate: () -> Unit) {
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
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onWeek,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
        ) { Text("이번 주 돌아보기", fontSize = 16.sp, color = Ink) }

        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onRate,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
        ) { Text("단어 점수 매기기 · 앱 개선 참여", fontSize = 16.sp, color = Ink) }

        Spacer(Modifier.height(28.dp))
        Text(
            "앱 소개 · 사용법",
            fontSize = 14.sp, color = Accent,
            modifier = Modifier.clickable(onClick = onGuide).padding(vertical = 6.dp)
        )
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
private fun ResultScreen(check: Check, onSave: (String, String?) -> Unit, onRestart: () -> Unit) {
    val context = LocalContext.current
    val energy = check.energy ?: return
    val valence = check.valence ?: return
    val cause = check.cause ?: Cause.ANY
    val found = remember(energy, valence, cause) { EmotionWords.find(context, energy, valence, cause) }
    if (found.isEmpty()) return
    // 내가 말한 단어를 맨 앞에
    val words = remember(found, check.voiceHits) { check.voiceHits + found.filter { it !in check.voiceHits } }
    var chosen by remember(words) {
        mutableStateOf(
            check.voiceHits.firstOrNull { it.energy == energy && it.valence == valence }
                ?: EmotionWords.pickFirst(context, found, energy, valence, cause)
        )
    }
    var showAll by remember(words) { mutableStateOf(false) }
    var second by remember(words) { mutableStateOf<EmotionWord?>(null) }   // 함께 섞인 감정
    var mixing by remember(words) { mutableStateOf(false) }                 // 칩을 누르면 두 번째로
    var query by remember(words) { mutableStateOf("") }
    val all = remember { EmotionWords.load(context) }
    val others = if (mixing && query.isNotBlank()) all.filter { it.word.contains(query.trim()) && it != chosen }.take(30)
        else words.filter { it != chosen && it != second }
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
            second?.let { w2 ->
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("+ ${w2.word}", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = deep(w2.valence))
                    Text(
                        "  ✕", fontSize = 18.sp, color = Muted,
                        modifier = Modifier.clickable { second = null }.padding(4.dp)
                    )
                }
                Text(w2.meaning, fontSize = 13.sp, lineHeight = 19.sp, color = Ink.copy(alpha = 0.6f))
            }
            Spacer(Modifier.height(12.dp))
            Text(chosen.meaning, fontSize = 15.sp, lineHeight = 22.sp, color = Ink.copy(alpha = 0.7f))
            Spacer(Modifier.height(16.dp))
            Text(
                "에너지 ${energy.label} · 기분 ${valence.label} · ${cause.label}",
                fontSize = 13.sp, color = Muted
            )
            Spacer(Modifier.height(10.dp))
            BasisLabel(chosen)
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
            onClick = { onSave(chosen.word, second?.word) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
        ) { Text("이 단어로 기록하기", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }

        Spacer(Modifier.height(28.dp))
        // 바꾸기 / 섞기 전환
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Line.copy(alpha = 0.5f)).padding(4.dp)) {
            listOf(false to "다른 단어로 바꾸기", true to "감정 하나 더 섞기").forEach { (m, label) ->
                Text(
                    label, fontSize = 14.sp, textAlign = TextAlign.Center,
                    color = if (mixing == m) Color.White else Ink,
                    fontWeight = if (mixing == m) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(9.dp))
                        .background(if (mixing == m) Accent else Color.Transparent)
                        .clickable { mixing = m; query = "" }.padding(vertical = 10.dp)
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        if (mixing) {
            Text("설레면서 불안한 것처럼, 마음은 자주 섞여 있어요. 함께 느끼는 감정을 하나 골라 보세요.", fontSize = 13.sp, color = Muted, lineHeight = 19.sp)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                placeholder = { Text("다른 칸 단어 찾기 (예: 불안)") },
                singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
            )
        } else {
            Text("• 표시는 연구 근거가 있는 단어예요", fontSize = 12.sp, color = Muted)
        }
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            shown.forEach { w ->
                Text(
                    if (w.basis == Basis.AI) w.word else "• ${w.word}", fontSize = 15.sp, color = Ink,
                    modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Color.White)
                        .border(1.dp, Line, RoundedCornerShape(20.dp))
                        .clickable { if (mixing) { second = w; mixing = false; query = "" } else chosen = w }
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
        val distinct = remember { store.distinctWords() }
        if (distinct > 0) Text("지금까지 쓴 감정 단어 ${distinct}개", fontSize = 15.sp, color = Accent)
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
                        Text(e.words, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = deep(e.valence))
                        e.note?.let { Text(it, fontSize = 14.sp, color = Ink.copy(alpha = 0.75f), lineHeight = 20.sp) }
                        Text(fmt.format(Date(e.time)), fontSize = 13.sp, color = Muted)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        if (e.before != null || e.after != null)
                            Text("세기 ${e.before ?: "-"} → ${e.after ?: "-"}", fontSize = 12.sp, color = Accent)
                        Text("에너지 ${e.energy.label}", fontSize = 12.sp, color = Muted)
                    }
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
            "한국어 감정단어 434개의 쾌–불쾌와 활성화를 대학생 123명씩이 7점 척도로 평정한 연구가 있어요. 앱 단어 ${"%,d".format(total)}개 중 ${"%,d".format(withResearch)}개가 이 연구의 단어이고, 그중 ${"%,d".format(placed)}개는 점수 그대로 칸을 정했어요. 나머지 ${withResearch - placed}개는 친숙성이 낮아 평정이 불안정해서 사전 뜻에 맞춰 두었어요.\n\n${Research.CITATION}\n\n연구에 없는 단어 ${"%,d".format(total - withResearch)}개(몸 상태, 일상 표현 등)는 근거가 될 점수가 없어서 AI가 뜻을 보고 같은 기준으로 분류했어요. 연구로 검증된 것이 아니어서, 결과 화면에 'AI 분류'로 표시해요."
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
            "이 앱은 내 마음을 들여다보도록 돕는 도구예요. 진단이나 평가를 하지 않고, 앱이 실제로 얼마나 도움이 되는지는 아직 검증하지 않았어요."
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


// ── 앱 소개 ─────────────────────────────────────────────
@Composable
private fun GuideScreen(onDone: () -> Unit) {
    val sections = remember { Guide.sections(isWatch = false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Spacer(Modifier.height(16.dp))
        Text("감정단어 사용법", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(24.dp))
        sections.forEach { sec ->
            Text(sec.title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            Spacer(Modifier.height(6.dp))
            Text(sec.body, fontSize = 15.sp, lineHeight = 24.sp, color = Ink.copy(alpha = 0.85f))
            Spacer(Modifier.height(22.dp))
        }
        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
        ) { Text("시작하기", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
        Spacer(Modifier.height(24.dp))
    }
}

// ── 이번 주 돌아보기 ─────────────────────────────────────
private fun valenceColor(v: Valence) = tint(v)
private val energyColors = mapOf(
    Energy.HIGH to Color(0xFFE07A5F), Energy.MID to Color(0xFFF2CC8F), Energy.LOW to Color(0xFF81B29A)
)

@Composable
private fun WeekScreen(store: MoodStore, onBack: () -> Unit) {
    val all = remember { store.entries() }
    var weeksAgo by remember { mutableIntStateOf(0) }
    val w = remember(weeksAgo) { Weekly.summarize(all, weeksAgo) }
    val dayFmt = remember { SimpleDateFormat("M월 d일", Locale.KOREA) }
    val dow = remember { SimpleDateFormat("E", Locale.KOREA) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(20.dp))
        TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) { Text("← 처음으로", color = Muted) }
        Text(if (weeksAgo == 0) "이번 주 돌아보기" else "${weeksAgo}주 전 돌아보기", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Ink)
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { weeksAgo++ }) { Text("‹ 이전", color = Accent) }
            Text(
                "${dayFmt.format(Date(w.start))} – ${dayFmt.format(Date(w.end - 1))}",
                fontSize = 14.sp, color = Muted, modifier = Modifier.weight(1f), textAlign = TextAlign.Center
            )
            TextButton(onClick = { if (weeksAgo > 0) weeksAgo-- }, enabled = weeksAgo > 0) { Text("다음 ›") }
        }

        // 한 줄 정리
        InfoCard(Color(0xFFEDEBFC)) {
            Text(w.insight, fontSize = 16.sp, lineHeight = 25.sp, color = Ink)
        }
        if (w.count == 0) return@Column

        // 이름 붙이기 전·후 세기, 쓴 단어 수
        SectionTitle("이름 붙이기 전과 후")
        InfoCard(Color.White) {
            val it3 = w.intensity
            if (it3 == null) {
                Text("감정의 세기를 기록하면 여기에 보여요.", fontSize = 14.sp, color = Muted)
            } else {
                Text("평균 세기 ${"%.1f".format(it3.first)} → ${"%.1f".format(it3.second)}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
                Text("단어를 고르기 전 → 기록한 뒤 (${it3.third}번, 5점 만점)", fontSize = 13.sp, color = Muted)
            }
            Spacer(Modifier.height(10.dp))
            Text("이 주에 쓴 감정 단어 ${w.wordsUsed}개", fontSize = 15.sp, color = Accent)
        }

        // 요일별 기록: 점 하나 = 기록 하나, 색 = 기분
        SectionTitle("날마다")
        InfoCard(Color.White) {
            Row(Modifier.fillMaxWidth()) {
                w.days.forEach { (d0, list) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(dow.format(Date(d0)), fontSize = 13.sp, color = Muted)
                        Spacer(Modifier.height(8.dp))
                        list.take(6).forEach { e ->
                            Box(Modifier.padding(2.dp).size(14.dp).clip(RoundedCornerShape(7.dp)).background(valenceColor(e.valence)))
                        }
                        if (list.size > 6) Text("+${list.size - 6}", fontSize = 11.sp, color = Muted)
                        if (list.isEmpty()) Text("·", fontSize = 13.sp, color = Line)
                    }
                }
            }
        }

        // 기분 · 에너지 비율
        SectionTitle("기분")
        InfoCard(Color.White) {
            StackBar(Valence.entries.map { Triple(it.label, w.valence.getValue(it), valenceColor(it)) }, w.count)
        }
        SectionTitle("몸의 에너지")
        InfoCard(Color.White) {
            StackBar(Energy.entries.map { Triple(it.label, w.energy.getValue(it), energyColors.getValue(it)) }, w.count)
        }

        // 많이 고른 감정
        SectionTitle("많이 고른 감정")
        InfoCard(Color.White) {
            w.topWords.forEach { (word, n) ->
                val v = w.entries.last { it.word == word }.valence
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(word, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = deep(v), modifier = Modifier.width(120.dp))
                    Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(Line)) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(n / w.topWords.first().second.toFloat()).clip(RoundedCornerShape(5.dp)).background(valenceColor(v)))
                    }
                    Text("  ${n}번", fontSize = 14.sp, color = Muted)
                }
            }
        }

        // 시간대
        SectionTitle("시간대별")
        InfoCard(Color.White) {
            TimeSlot.entries.forEach { s ->
                val list = w.slots.getValue(s)
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(s.label, fontSize = 15.sp, color = Ink, modifier = Modifier.width(48.dp))
                    Row(Modifier.weight(1f).height(14.dp).clip(RoundedCornerShape(7.dp)).background(Line)) {
                        Valence.entries.forEach { v ->
                            val c = list.count { it.valence == v }
                            if (c > 0) Box(Modifier.weight(c.toFloat()).fillMaxHeight().background(valenceColor(v)))
                        }
                        if (list.isEmpty()) Spacer(Modifier.weight(1f))
                    }
                    Text("  ${list.size}번", fontSize = 14.sp, color = Muted, modifier = Modifier.width(52.dp))
                }
            }
            Text("아침 5–11시 · 낮 11–17시 · 저녁 17–22시 · 밤 22–5시", fontSize = 12.sp, color = Muted)
        }

        // 원인
        SectionTitle("무엇 때문에")
        InfoCard(Color.White) {
            if (w.causes.isEmpty()) {
                Text("이번 버전부터 원인이 함께 기록돼요. 다음 주부터 여기에 보여요.", fontSize = 14.sp, color = Muted)
            } else {
                val total = w.causes.values.sum()
                w.causes.entries.sortedByDescending { it.value }.forEach { (c, n) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(c.label.removeSuffix(" 때문에"), fontSize = 15.sp, color = Ink, modifier = Modifier.width(120.dp))
                        Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(Line)) {
                            Box(Modifier.fillMaxHeight().fillMaxWidth(n / total.toFloat()).clip(RoundedCornerShape(5.dp)).background(Accent))
                        }
                        Text("  ${(n * 100.0 / total).roundToInt()}%", fontSize = 14.sp, color = Muted, modifier = Modifier.width(52.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Spacer(Modifier.height(22.dp))
    Text(text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Ink)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun InfoCard(bg: Color, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(bg)
            .border(1.dp, Line, RoundedCornerShape(18.dp)).padding(18.dp),
        content = content
    )
}

/** 비율 막대 + 범례 */
@Composable
private fun StackBar(parts: List<Triple<String, Int, Color>>, total: Int) {
    Row(Modifier.fillMaxWidth().height(18.dp).clip(RoundedCornerShape(9.dp)).background(Line)) {
        parts.forEach { (_, n, c) -> if (n > 0) Box(Modifier.weight(n.toFloat()).fillMaxHeight().background(c)) }
    }
    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        parts.forEach { (label, n, c) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(c))
                Text(" $label ${if (total == 0) 0 else (n * 100.0 / total).roundToInt()}%", fontSize = 13.sp, color = Ink)
            }
        }
    }
}


/** 결과 화면: 이 단어의 칸이 어디서 왔는지 */
@Composable
private fun BasisLabel(w: EmotionWord) {
    val r = w.research
    val text = when (w.basis) {
        Basis.RESEARCH -> "연구 근거 · ${Research.SHORT} '${r!!.sourceWord}'\n쾌 %.2f · 각성 %.2f (7점 척도)".format(r.pleasure, r.activation)
        Basis.RESEARCH_EXCEPTION -> "연구 단어 · ${Research.SHORT} '${r!!.sourceWord}'\n친숙성이 낮아 점수 대신 뜻을 기준으로 두었어요"
        Basis.AI -> "AI 분류 · 연구에 없는 단어라 AI가 뜻을 보고 칸을 정했어요"
    }
    Text(text, fontSize = 12.sp, color = Muted, lineHeight = 17.sp)
}


// ── 단어 점수 매기기 (앱 개선 참여) ─────────────────────────
@Composable
private fun RateScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val ratings = remember { Ratings(context) }
    var done by remember { mutableIntStateOf(ratings.count()) }
    var batch by remember { mutableStateOf<List<EmotionWord>?>(null) }   // null = 안내 화면
    var idx by remember { mutableIntStateOf(0) }

    fun share() {
        val text = ratings.exportText(BuildConfig.VERSION_CODE)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "감정단어 점수")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(send, "점수 보내기"))
        ratings.sentCount = ratings.count()
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) { Text("← 처음으로", color = Muted) }
        val b = batch
        when {
            // 안내
            b == null -> {
                Text("단어 점수 매기기", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Ink)
                Spacer(Modifier.height(16.dp))
                Text(
                    "이 앱 단어 중 3분의 2는 연구에 없어서 AI가 칸을 정했어요. 여러 사람이 매긴 점수가 모이면, " +
                        "그 단어들을 실제 사람들의 느낌에 맞는 칸으로 옮길 수 있어요.",
                    fontSize = 16.sp, lineHeight = 25.sp, color = Ink
                )
                Spacer(Modifier.height(16.dp))
                InfoCard(Color.White) {
                    Text(
                        "• 한 번에 10개, 2~3분이면 끝나요\n" +
                            "• 단어마다 기분·에너지·익숙함을 1~7로 눌러요\n" +
                            "• 정답은 없어요. 처음 떠오르는 느낌대로\n" +
                            "• 모르는 단어는 '모르는 단어예요'를 누르면 돼요\n" +
                            "• 점수는 이 폰에만 저장되고, '보내기'를 누를 때만 공유 창(카톡·메일 등)으로 나가요. 이름이나 기록은 들어가지 않아요",
                        fontSize = 15.sp, lineHeight = 24.sp, color = Ink
                    )
                }
                Spacer(Modifier.height(16.dp))
                MbtiPicker(ratings)
                Spacer(Modifier.height(20.dp))
                val left = remember(done) { ratings.remaining(context) }
                if (done > 0) Text("지금까지 ${done}개 매겼어요 · 남은 단어 ${left}개", fontSize = 14.sp, color = Muted)
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { batch = ratings.nextBatch(context); idx = 0 },
                    enabled = left > 0,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                ) { Text(if (left > 0) "시작하기" else "모든 단어를 매겼어요", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
                if (done > ratings.sentCount) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { share() },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) { Text("점수 보내기 (${done}개)", fontSize = 16.sp, color = Ink) }
                }
            }
            // 10개 끝
            idx >= b.size -> {
                Spacer(Modifier.height(24.dp))
                Text("고마워요!", fontSize = 32.sp, fontWeight = FontWeight.Black, color = Ink)
                Spacer(Modifier.height(10.dp))
                Text(
                    "지금까지 ${done}개를 매겼어요.\n보내 주시면 다음 버전에서 단어 위치를 고치는 데 써요.",
                    fontSize = 16.sp, lineHeight = 25.sp, color = Ink
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { share() },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                ) { Text("점수 보내기", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { batch = ratings.nextBatch(context); idx = 0 },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                ) { Text("10개 더 하기", fontSize = 16.sp, color = Ink) }
                TextButton(onClick = onBack) { Text("나중에", color = Muted) }
            }
            // 단어 하나
            else -> {
                val w = b[idx]
                var p by remember(w) { mutableIntStateOf(0) }
                var a by remember(w) { mutableIntStateOf(0) }
                var f by remember(w) { mutableIntStateOf(0) }
                var showMeaning by remember(w) { mutableStateOf(false) }
                fun save(r: Rating) { ratings.add(r); done = ratings.count(); idx++ }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(b.size) { i ->
                        Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(if (i <= idx) Accent else Line))
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text("${idx + 1} / ${b.size}", fontSize = 14.sp, color = Muted)
                Spacer(Modifier.height(8.dp))
                Text(w.word, fontSize = 40.sp, fontWeight = FontWeight.Black, color = Ink)
                if (showMeaning) {
                    Text(w.meaning, fontSize = 14.sp, lineHeight = 21.sp, color = Muted)
                } else {
                    Text(
                        "뜻 보기", fontSize = 14.sp, color = Accent,
                        modifier = Modifier.clickable { showMeaning = true }.padding(vertical = 4.dp)
                    )
                }
                Spacer(Modifier.height(20.dp))
                Scale("이 단어는 어떤 기분인가요?", "아주 불쾌", "아주 유쾌", p) { p = it }
                Scale("몸의 에너지는 어느 정도인가요?", "아주 차분", "아주 들뜸", a) { a = it }
                Scale("얼마나 익숙한 말인가요?", "처음 들음", "자주 씀", f) { f = it }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { save(Rating(w.word, p, a, f, System.currentTimeMillis())) },
                    enabled = p > 0 && a > 0 && f > 0,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                ) { Text(if (idx + 1 < b.size) "다음" else "마치기", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
                TextButton(
                    onClick = { save(Rating(w.word, 0, 0, 1, System.currentTimeMillis())) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("모르는 단어예요", color = Muted) }
            }
        }
    }
}

/** 1~7 고르기 */
@Composable
private fun Scale(question: String, low: String, high: String, value: Int, onPick: (Int) -> Unit) {
    Text(question, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Ink)
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (n in 1..7) {
            val on = n == value
            Box(
                Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(10.dp))
                    .background(if (on) Accent else Color.White)
                    .border(1.dp, if (on) Accent else Line, RoundedCornerShape(10.dp))
                    .clickable { onPick(n) },
                contentAlignment = Alignment.Center,
            ) { Text("$n", fontSize = 16.sp, color = if (on) Color.White else Ink, fontWeight = FontWeight.SemiBold) }
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(low, fontSize = 12.sp, color = Muted)
        Spacer(Modifier.weight(1f))
        Text(high, fontSize = 12.sp, color = Muted)
    }
    Spacer(Modifier.height(20.dp))
}


// ── 목소리 ───────────────────────────────────────────────
/**
 * 목소리로 듣기 (선택).
 *  · 제시 문장 읽기: 매번 같은 문장 → 평소 목소리와 비교해 몸의 에너지를 가늠
 *  · 내 말로 말하기: 말 속 감정 단어를 찾아 후보 맨 앞에
 */
@Composable
private fun VoiceScreen(check: Check, onNext: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val baseline = remember { VoiceBaseline(context) }
    var recording by remember { mutableStateOf(false) }
    var level by remember { mutableFloatStateOf(0f) }
    var note by remember { mutableStateOf<String?>(null) }
    var heard by remember { mutableStateOf<String?>(null) }
    var tried by remember { mutableStateOf(false) }
    val stop = remember { AtomicBoolean(false) }

    fun startRecording() {
        recording = true; stop.set(false); note = null; heard = null
        scope.launch {
            val r = VoiceTone.record(stop) { level = it }
            recording = false
            tried = true
            val f = r?.features
            if (f == null) {
                note = "목소리를 잘 못 들었어요. 조용한 곳에서 다시 해 보세요."
            } else {
                val lv = baseline.level(f)
                baseline.add(f)
                if (lv == null) {
                    note = if (baseline.ready) "평소 목소리를 다 배웠어요. 다음부터 비교해요."
                    else "평소 목소리를 배우는 중이에요 (${baseline.count}/${VoiceBaseline.LEARN})."
                } else {
                    check.voiceLevel = lv
                    check.energy = Energy.MID.shiftBy(lv)
                }
            }
            // 들은 문장 받아쓰기 (지원하는 기기만)
            if (r != null) {
                heard = "받아쓰는 중…"
                heard = VoiceTone.transcribe(context, r)?.let { "들은 문장: “$it”" } ?: "이 기기는 읽은 문장 받아쓰기를 지원하지 않아요."
            }
        }
    }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) startRecording() else note = "마이크 권한이 없어 읽기를 할 수 없어요."
    }
    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val text = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (text != null) {
            check.spoken = text
            check.voiceHits = VoiceWords.match(context, text)
            check.voiceHits.firstOrNull()?.let { if (check.valence == null) check.valence = it.valence }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Spacer(Modifier.height(12.dp))
        Text("목소리로도 들어 볼까요?", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Ink, lineHeight = 34.sp)
        Spacer(Modifier.height(8.dp))
        Text("하지 않아도 괜찮아요. 질문만으로도 찾을 수 있어요.", fontSize = 14.sp, color = Muted)
        Spacer(Modifier.height(20.dp))

        // ① 제시 문장 읽기
        InfoCard(Color.White) {
            Text("① 문장 읽기 · 목소리 톤", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            Spacer(Modifier.height(6.dp))
            Text(
                "왜? 마음이 들뜨면 목소리가 높고 빨라지고, 가라앉으면 낮고 느려져요. 매번 같은 문장을 읽으면 평소 내 목소리와 비교해 " +
                    "몸의 에너지를 가늠할 수 있어요. 목소리로 기분이 좋은지 나쁜지까지는 알 수 없어서, 에너지에만 써요.",
                fontSize = 14.sp, lineHeight = 21.sp, color = Muted
            )
            Spacer(Modifier.height(14.dp))
            Text(
                VoiceTone.SENTENCE, fontSize = 20.sp, lineHeight = 30.sp, fontFamily = FontFamily.Serif, color = Ink,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Paper).padding(16.dp)
            )
            Spacer(Modifier.height(12.dp))
            if (recording) {
                Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Line)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(level.coerceAtLeast(0.03f)).background(Accent))
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = { stop.set(true) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Text("다 읽었어요", color = Ink)
                }
            } else {
                Button(
                    onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startRecording()
                        else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                ) { Text(if (check.voiceLevel == null) "🎙 소리 내어 읽기" else "다시 읽기") }
            }
            check.voiceLevel?.let {
                Spacer(Modifier.height(10.dp))
                Text("목소리가 ${it.label}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Accent)
            }
            note?.let { Spacer(Modifier.height(8.dp)); Text(it, fontSize = 13.sp, color = Accent, lineHeight = 19.sp) }
            heard?.let { Spacer(Modifier.height(6.dp)); Text(it, fontSize = 14.sp, color = Ink, fontStyle = FontStyle.Italic, lineHeight = 20.sp) }
            if (tried && !recording) {
                Spacer(Modifier.height(12.dp))
                Button(onClick = onNext, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text("다음 →") }
            }
        }
        Spacer(Modifier.height(14.dp))

        // ② 내 말로 말하기
        InfoCard(Color.White) {
            Text("② 내 말로 말하기 · 말한 내용", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            Spacer(Modifier.height(6.dp))
            Text(
                "왜? 지금 기분을 내 말로 표현해 보는 것 자체가 감정에 이름을 붙이는 첫걸음이에요. " +
                    "\"좀 서운하고 피곤해\"처럼 말하면, 그 말 속 감정 단어를 찾아 후보 맨 앞에 보여줘요.",
                fontSize = 14.sp, lineHeight = 21.sp, color = Muted
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
                        putExtra(RecognizerIntent.EXTRA_PROMPT, "지금 기분을 한마디로 말해 주세요")
                    }
                    runCatching { speechLauncher.launch(intent) }.onFailure { note = "이 폰에서는 받아쓰기를 쓸 수 없어요." }
                },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
            ) { Text(if (check.spoken == null) "🗣 말하기" else "다시 말하기") }
            check.spoken?.let { sp ->
                Spacer(Modifier.height(12.dp))
                Text("“$sp”", fontSize = 17.sp, fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, color = Ink)
                Spacer(Modifier.height(6.dp))
                Text(
                    if (check.voiceHits.isEmpty()) "찾은 감정 단어가 없어요. 질문으로 함께 찾아볼게요."
                    else "찾은 단어: " + check.voiceHits.take(5).joinToString(", ") { it.word },
                    fontSize = 14.sp, color = Accent
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = onNext, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text("다음 →") }
            }
        }
        Spacer(Modifier.height(20.dp))
        val done = tried || check.spoken != null
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp),
            colors = if (done) ButtonDefaults.buttonColors() else ButtonDefaults.buttonColors(containerColor = Line, contentColor = Ink),
        ) { Text(if (done) "다음" else "건너뛰고 질문으로", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
        Spacer(Modifier.height(12.dp))
        Text(
            "녹음은 저장하지 않아요. 문장 읽기는 이 폰 안에서만 분석하고, 높낮이·크기·빠르기 숫자만 남겨요. " +
                "'내 말로 말하기'는 폰의 음성 인식(구글·삼성)을 쓰므로 목소리가 그 회사 서버로 갈 수 있어요.",
            fontSize = 12.sp, lineHeight = 18.sp, color = Muted
        )
    }
}


/** 선택 항목: MBTI. 단어 추천에는 쓰지 않고, 보내는 점수에만 함께 적음 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MbtiPicker(ratings: Ratings) {
    var mbti by remember { mutableStateOf(ratings.mbti) }
    var open by remember { mutableStateOf(false) }
    InfoCard(Color.White) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("MBTI (선택)", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                Text(if (mbti.isBlank()) "안 골랐어요" else mbti, fontSize = 14.sp, color = if (mbti.isBlank()) Muted else Accent)
            }
            TextButton(onClick = { open = !open }) { Text(if (open) "닫기" else "고르기", color = Accent) }
        }
        if (open) {
            Spacer(Modifier.height(8.dp))
            val types = listOf(
                "ISTJ", "ISFJ", "INFJ", "INTJ", "ISTP", "ISFP", "INFP", "INTP",
                "ESTP", "ESFP", "ENFP", "ENTP", "ESTJ", "ESFJ", "ENFJ", "ENTJ",
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                (types + "모름·안 함").forEach { t ->
                    val value = if (t.startsWith("모름")) "" else t
                    val on = value == mbti
                    Text(
                        t, fontSize = 14.sp, color = if (on) Color.White else Ink,
                        modifier = Modifier.clip(RoundedCornerShape(16.dp))
                            .background(if (on) Accent else Paper)
                            .border(1.dp, if (on) Accent else Line, RoundedCornerShape(16.dp))
                            .clickable { mbti = value; ratings.mbti = value; open = false }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "왜 묻나요? 유형마다 같은 단어를 다르게 느끼는지 나중에 모인 점수로 살펴보려고요. " +
                "감정 단어를 고르는 데는 쓰지 않아요. MBTI는 성격을 재는 정확한 검사가 아니어서, 참고용으로만 봐요.",
            fontSize = 12.sp, lineHeight = 18.sp, color = Muted
        )
    }
}


// ── 감정 세기 ────────────────────────────────────────────
@Composable
private fun IntensityScreen(title: String, hint: String, before: Int?, selected: Int?, onPick: (Int) -> Unit, onSkip: () -> Unit) {
    val labels = listOf("아주 약함", "약함", "보통", "강함", "아주 강함")
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Spacer(Modifier.height(32.dp))
        Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold, lineHeight = 34.sp, color = Ink)
        Spacer(Modifier.height(8.dp))
        Text(hint, fontSize = 14.sp, color = Muted, lineHeight = 20.sp)
        before?.let {
            Spacer(Modifier.height(10.dp))
            Text("아까는 $it (${labels[it - 1]})", fontSize = 15.sp, color = Accent)
        }
        Spacer(Modifier.height(24.dp))
        for (n in 5 downTo 1) {
            val on = n == selected
            Row(
                Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(16.dp))
                    .background(if (on) Accent.copy(alpha = 0.08f) else Color.White)
                    .border(1.5.dp, if (on) Accent else Line, RoundedCornerShape(16.dp))
                    .clickable { onPick(n) }.padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("$n", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Accent, modifier = Modifier.width(32.dp))
                Text(labels[n - 1], fontSize = 18.sp, color = Ink, modifier = Modifier.weight(1f))
                // 세기만큼 점
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(5) { i -> Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(if (i < n) Accent else Line)) }
                }
            }
        }
        TextButton(onClick = onSkip) { Text("건너뛰기", color = Muted) }
    }
}

/** 기록한 뒤: 지금 세기 + 한 줄 메모 */
@Composable
private fun AfterScreen(store: MoodStore, before: Int?, onDone: () -> Unit) {
    var after by remember { mutableStateOf<Int?>(null) }
    var note by remember { mutableStateOf("") }
    val labels = listOf("아주 약함", "약함", "보통", "강함", "아주 강함")
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Spacer(Modifier.height(24.dp))
        Text("기록했어요", fontSize = 15.sp, color = Accent)
        Spacer(Modifier.height(6.dp))
        Text("이름을 붙이고 나니,\n지금은 얼마나 강해요?", fontSize = 26.sp, fontWeight = FontWeight.Bold, lineHeight = 34.sp, color = Ink)
        before?.let {
            Spacer(Modifier.height(8.dp))
            Text("아까는 $it (${labels[it - 1]})", fontSize = 15.sp, color = Muted)
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (n in 1..5) {
                val on = n == after
                Box(
                    Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(14.dp))
                        .background(if (on) Accent else Color.White)
                        .border(1.5.dp, if (on) Accent else Line, RoundedCornerShape(14.dp))
                        .clickable { after = n },
                    contentAlignment = Alignment.Center,
                ) { Text("$n", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = if (on) Color.White else Ink) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text("아주 약함", fontSize = 12.sp, color = Muted); Spacer(Modifier.weight(1f)); Text("아주 강함", fontSize = 12.sp, color = Muted)
        }
        if (before != null && after != null) {
            Spacer(Modifier.height(10.dp))
            Text("$before → $after", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Accent)
        }
        Spacer(Modifier.height(26.dp))
        Text("무슨 일이었나요? (선택)", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        Text("나중에 기록을 볼 때 그때가 떠오르게 한 줄만. 키보드의 마이크로 말해도 돼요.", fontSize = 13.sp, color = Muted, lineHeight = 19.sp)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = note, onValueChange = { if (it.length <= 120) note = it },
            placeholder = { Text("예: 친구가 약속을 잊었다") },
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), minLines = 2,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { store.updateLatest(after, note); onDone() },
            modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp),
        ) { Text("저장", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
        TextButton(onClick = onDone) { Text("건너뛰기", color = Muted) }
    }
}
