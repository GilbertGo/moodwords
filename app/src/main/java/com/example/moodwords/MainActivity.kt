package com.example.moodwords

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.clickable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.*
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private const val MEASURE_MILLIS = 15_000L

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AutoUpdate.cancelBackground(this)
        // 워치용 APK가 폰에 잘못 깔린 경우: 폰용 앱으로 안내
        val isWatch = packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)
        setContent { if (isWatch) MoodWordsApp() else WrongDeviceScreen() }
    }
}

/** 폰에서 워치용 앱을 열었을 때: 폰용 앱을 바로 받아 설치하게 함 */
@Composable
fun WrongDeviceScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var phoneRelease by remember { mutableStateOf<Updater.Release?>(null) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var searching by remember { mutableStateOf(true) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(attempt) {
        searching = true
        phoneRelease = Updater.checkNewer(Updater.PHONE, 0)
        searching = false
    }

    MaterialTheme {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("⌚", fontSize = 48.sp)
                Spacer(Modifier.height(16.dp))
                Text("이 앱은 갤럭시워치용이에요", fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Text(
                    "폰에서는 폰용 감정단어 앱을 써주세요.\n아래 버튼으로 바로 설치한 뒤, 이 앱은 지우시면 돼요.",
                    fontSize = 15.sp, textAlign = TextAlign.Center, color = Color(0xFFBBBBBB)
                )
                Spacer(Modifier.height(28.dp))
                val rel = phoneRelease
                Chip(
                    onClick = {
                        if (rel == null) { if (!searching) attempt++; return@Chip }
                        if (progress != null) return@Chip
                        progress = 0f; error = null
                        scope.launch {
                            runCatching { Updater.install(context, rel) { progress = it } }
                                .onFailure { error = "받기 실패, 다시 눌러주세요"; progress = null }
                        }
                    },
                    label = {
                        Text(
                            when {
                                rel == null && searching -> "폰용 앱 찾는 중…"
                                rel == null -> "다시 찾기"
                                progress != null -> "받는 중… ${((progress ?: 0f) * 100).roundToInt()}%"
                                else -> "폰용 앱 설치하기"
                            },
                            fontSize = 16.sp, fontWeight = FontWeight.Bold
                        )
                    },
                    secondaryLabel = error?.let { { Text(it) } },
                    colors = ChipDefaults.chipColors(backgroundColor = Color(0xFF5B4BDB)),
                    modifier = Modifier.fillMaxWidth()
                )
                if (rel == null && !searching) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "못 찾았어요. 인터넷 연결을 확인하거나, 여기를 눌러 브라우저로 받으세요",
                        fontSize = 14.sp, textAlign = TextAlign.Center, color = Color(0xFF9FA8FF),
                        modifier = Modifier.clickable {
                            runCatching {
                                context.startActivity(
                                    android.content.Intent(android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse("https://github.com/GilbertGo/moodwords/releases"))
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

/** 한 번의 감정 체크 동안 화면 사이에서 공유하는 값 */
class CheckState {
    var bpm by mutableStateOf<Int?>(null)
    var tremor by mutableStateOf<Double?>(null)
    var tremorLevel by mutableStateOf<TremorLevel?>(null)
    var moved by mutableStateOf(false)
    var energy by mutableStateOf<Energy?>(null)
    var valence by mutableStateOf<Valence?>(null)
    var cause by mutableStateOf<Cause?>(null)
    var spoken by mutableStateOf<String?>(null)            // 내 말로 말한 문장
    var voiceHits by mutableStateOf<List<EmotionWord>>(emptyList())
    var voiceLevel by mutableStateOf<VoiceLevel?>(null)    // 제시 문장 읽기 결과
    fun reset() {
        bpm = null; tremor = null; tremorLevel = null; moved = false; energy = null; valence = null; cause = null
        spoken = null; voiceHits = emptyList(); voiceLevel = null
    }
}

@Composable
fun MoodWordsApp() {
    val context = LocalContext.current
    val store = remember { MoodStore(context) }
    val check = remember { CheckState() }
    val nav = rememberSwipeDismissableNavController()

    MaterialTheme {
        SwipeDismissableNavHost(navController = nav, startDestination = if (Guide.seen(context)) "home" else "guide") {
            composable("home") {
                HomeScreen(
                    onStart = { check.reset(); nav.navigate("measure") },
                    onHistory = { nav.navigate("history") },
                    onWeek = { nav.navigate("week") },
                    onGuide = { nav.navigate("guide") },
                )
            }
            composable("measure") {
                MeasureScreen(store) { m ->
                    check.bpm = m.bpm
                    check.tremor = m.tremor
                    check.moved = m.moved
                    check.tremorLevel = m.tremor?.let { tremorLevel(it, store.restingTremor) }
                    check.energy = estimateEnergy(m.bpm, check.tremorLevel, store)
                    nav.navigate("voice") { popUpTo("home") }
                }
            }
            composable("voice") {
                VoiceScreen(check) { nav.navigate("energy") }
            }
            composable("energy") {
                EnergyQuestion(check) { nav.navigate("valence") }
            }
            composable("valence") {
                ValenceQuestion(check) { nav.navigate("cause") }
            }
            composable("cause") {
                CauseQuestion(check) { nav.navigate("result") }
            }
            composable("result") {
                ResultScreen(check) { word ->
                    store.add(
                        MoodEntry(System.currentTimeMillis(), word, check.bpm, check.energy!!, check.valence!!, check.tremor, check.cause)
                    )
                    nav.navigate("history") { popUpTo("home") }
                }
            }
            composable("history") { HistoryScreen(store) }
            composable("week") { WeekScreen(store) }
            composable("guide") {
                GuideScreen {
                    Guide.markSeen(context)
                    nav.navigate("home") {
                        popUpTo("guide") { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
        }
    }
}

@Composable
fun HomeScreen(onStart: () -> Unit, onHistory: () -> Unit, onWeek: () -> Unit, onGuide: () -> Unit) {
    var update by remember { mutableStateOf<Updater.Release?>(null) }
    var dismissed by rememberSaveable { mutableStateOf(false) }

    // 앱을 열 때마다 GitHub에 새 버전이 있는지 확인 → 있으면 안내 화면
    LaunchedEffect(Unit) { update = Updater.checkNewer(Updater.WATCH, BuildConfig.VERSION_CODE) }

    val rel = update
    if (rel != null && !dismissed) {
        UpdateScreen(rel, onLater = { dismissed = true })
        return
    }

    Scaffold(timeText = { TimeText() }) {
        ScalingLazyColumn(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            item { Text("지금 어떤 마음인가요?", textAlign = TextAlign.Center, fontSize = 16.sp) }
            item {
                Chip(
                    onClick = onStart,
                    label = { Text("감정 알아보기") },
                    colors = ChipDefaults.primaryChipColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Chip(
                    onClick = onHistory,
                    label = { Text("감정 기록") },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Chip(
                    onClick = onWeek,
                    label = { Text("이번 주 돌아보기") },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                CompactChip(onClick = onGuide, label = { Text("사용법") }, colors = ChipDefaults.secondaryChipColors())
            }
            if (rel != null) item {
                CompactChip(
                    onClick = { dismissed = false },
                    label = { Text("새 버전 v1.${rel.version}") },
                    colors = ChipDefaults.chipColors(backgroundColor = Color(0xFF5B4BDB)),
                )
            }
            item { Text("v${BuildConfig.VERSION_NAME}", fontSize = 10.sp, color = Color.Gray) }
        }
    }
}

/** 새 버전 안내: 바뀐 점, 버전, 업데이트 / 나중에 */
@Composable
fun UpdateScreen(rel: Updater.Release, onLater: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var progress by remember { mutableStateOf<Float?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val accent = Color(0xFF8B7CF6)

    Scaffold(timeText = { TimeText() }) {
        ScalingLazyColumn(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            item { Text("🎁", fontSize = 26.sp) }
            item { Text("새 버전이 있어요", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            if (rel.notes.isNotBlank()) item {
                Text(rel.notes, fontSize = 11.sp, textAlign = TextAlign.Center, color = Color(0xFFCCCCCC))
            }
            item {
                Text(
                    "지금 v${BuildConfig.VERSION_NAME} → 새 v1.${rel.version}",
                    fontSize = 11.sp, color = Color.Gray
                )
            }
            item {
                Chip(
                    onClick = {
                        if (progress != null) return@Chip
                        progress = 0f; error = null
                        scope.launch {
                            runCatching { Updater.install(context, rel) { progress = it } }
                                .onFailure { error = "받기 실패, 다시 눌러주세요"; progress = null }
                        }
                    },
                    label = {
                        Text(
                            progress?.let { "받는 중… ${(it * 100).roundToInt()}%" } ?: "업데이트",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    secondaryLabel = error?.let { { Text(it) } },
                    colors = ChipDefaults.chipColors(backgroundColor = accent),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                CompactChip(
                    onClick = onLater,
                    label = { Text("나중에") },
                    colors = ChipDefaults.secondaryChipColors(),
                )
            }
        }
    }
}

/** 측정 결과: 가만히 있을 때의 평균 심박과 떨림 */
data class Measurement(val bpm: Int?, val tremor: Double?, val moved: Boolean)

/** 심박(평소 대비) + 떨림으로 에너지 추정. 심박이 없으면 추정하지 않음 */
fun estimateEnergy(bpm: Int?, tremor: TremorLevel?, store: MoodStore): Energy? {
    val base = bpm?.let { EmotionWords.estimateEnergy(it.toDouble(), store.restingBpm) } ?: return null
    // 떨림·꼼지락이 평소보다 많으면 에너지를 한 단계 올려 봄
    return if (tremor == TremorLevel.HIGH) when (base) {
        Energy.LOW -> Energy.MID
        else -> Energy.HIGH
    } else base
}

fun energyHint(check: CheckState): String {
    val voice = check.voiceLevel?.let { "\n목소리: ${it.label}" } ?: ""
    val bpm = check.bpm ?: return (if (check.voiceLevel != null) "목소리로 '${check.energy?.label}' 추정" + voice else "심박을 재지 못했어요. 직접 골라주세요")
    val parts = mutableListOf("심박 ${bpm}bpm")
    check.tremorLevel?.let { parts += "떨림 ${it.label}" }
    val tail = if (check.moved) "\n(움직임이 있어 정확도가 낮을 수 있어요)" else ""
    return parts.joinToString(" · ") + " → '${check.energy?.label}'으로 추정했어요" + voice + tail
}

/** Wear OS 6(API 36)부터 쓰이는 새 심박 권한 */
private const val HR_PERMISSION_NEW = "android.permission.health.READ_HEART_RATE"

/** 가만히 있는 시간이 이만큼 모이면 측정 끝. 계속 움직이면 최대 MAX_MILLIS에서 끝냄 */
private const val MAX_MILLIS = 30_000L

/**
 * 1단계: 심박 + 움직임 측정.
 * 가만히 있는 시간만 15초 모아서 계산하고, 움직이는 동안은 "잠시 가만히" 안내를 띄움.
 * 진행 링은 가만히 있는 시간 기준, 최대 30초 뒤엔 무조건 다음으로.
 */
@Composable
fun MeasureScreen(store: MoodStore, onDone: (Measurement) -> Unit) {
    val context = LocalContext.current
    fun hasPermission() = listOf(Manifest.permission.BODY_SENSORS, HR_PERMISSION_NEW).any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
    // 0 = 권한 묻는 중, 1 = 허용됨, -1 = 거부됨
    var permState by remember { mutableIntStateOf(if (hasPermission()) 1 else 0) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        permState = if (res.values.any { it } || hasPermission()) 1 else -1
    }
    var current by remember { mutableStateOf<Int?>(null) }
    var stillMs by remember { mutableLongStateOf(0L) }
    var totalMs by remember { mutableLongStateOf(0L) }
    var moving by remember { mutableStateOf(false) }
    var movedAny by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    val hrSamples = remember { mutableListOf<Double>() }
    val tremorSamples = remember { mutableListOf<Double>() }

    fun result(): Measurement {
        // 측정 초반은 불안정하니 뒤쪽 절반만 평균
        val bpm = hrSamples.drop(hrSamples.size / 2).takeIf { it.isNotEmpty() }?.average()?.roundToInt()
        val tremor = tremorSamples.takeIf { it.size >= 4 }?.sorted()?.let { it[it.size / 2] } // 중앙값
        return Measurement(bpm, tremor, movedAny)
    }
    fun finish(m: Measurement) {
        if (!finished) { finished = true; onDone(m) }
    }

    LaunchedEffect(Unit) {
        if (permState == 0) launcher.launch(arrayOf(Manifest.permission.BODY_SENSORS, HR_PERMISSION_NEW))
    }

    LaunchedEffect(permState) {
        if (permState == -1) {
            delay(1500); finish(Measurement(null, null, false)); return@LaunchedEffect
        }
        if (permState != 1) return@LaunchedEffect
        coroutineScope {
            // 움직임은 계속 감시: 가만히 있을 때만 떨림을 모음
            val motion = launch {
                runCatching {
                    motionFlow(context).collect { m ->
                        moving = m.isMoving
                        if (m.isMoving) movedAny = true else tremorSamples += m.tremor
                    }
                }
            }
            // 심박: 움직이는 동안 들어온 값은 버림
            val heart = launch {
                runCatching {
                    heartRateFlow(context).collect { bpm ->
                        current = bpm.roundToInt()
                        if (!moving) hrSamples += bpm
                    }
                }
            }
            var last = System.currentTimeMillis()
            while (stillMs < MEASURE_MILLIS && totalMs < MAX_MILLIS) {
                delay(200)
                val now = System.currentTimeMillis()
                val dt = now - last
                last = now
                totalMs += dt
                if (!moving) stillMs += dt
            }
            motion.cancel(); heart.cancel()
        }
        finish(result())
    }

    val secondsLeft = ((MEASURE_MILLIS - stillMs).coerceAtLeast(0) + 999) / 1000
    val status = when {
        permState == 0 -> "심박 권한을 허용해 주세요"
        permState == -1 -> "권한이 없어 직접 고를게요"
        moving -> "잠시 가만히 있어 주세요"
        current != null -> "편하게 숨 쉬세요"
        totalMs > 4000 -> "심박을 찾는 중이에요\n워치를 손목에 꼭 맞게 차주세요"
        else -> "센서 준비 중…"
    }

    Scaffold(timeText = { TimeText() }) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = (stillMs / MEASURE_MILLIS.toFloat()).coerceIn(0f, 1f),
                modifier = Modifier.fillMaxSize().padding(4.dp),
                indicatorColor = if (moving) Color(0xFFFFB74D) else Color(0xFFFF6B8A),
                strokeWidth = 5.dp
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("♥", color = Color(0xFFFF6B8A), fontSize = 20.sp)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(current?.let { "$it" } ?: "--", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    Text(" bpm", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 6.dp))
                }
                Text(
                    status, fontSize = 12.sp, textAlign = TextAlign.Center,
                    color = if (moving) Color(0xFFFFB74D) else Color.Unspecified
                )
                Text(
                    if (permState == 1) "${secondsLeft}초 · 평소 ${store.restingBpm.roundToInt()}bpm" else "",
                    fontSize = 10.sp, color = Color.Gray
                )
                Spacer(Modifier.height(6.dp))
                CompactChip(
                    onClick = { finish(result()) },
                    label = { Text("건너뛰기") },
                    colors = ChipDefaults.secondaryChipColors(),
                )
            }
        }
    }
}

/** 2단계: 심박으로 추정한 에너지를 보여주고 확인/수정 받기 */
@Composable
fun EnergyQuestion(check: CheckState, onNext: () -> Unit) {
    QuestionScreen(
        title = "몸의 에너지는 어떤가요?",
        hint = energyHint(check),
        options = Energy.entries.map { it to it.label },
        selected = check.energy,
    ) { check.energy = it; onNext() }
}

/** 3단계: 기분의 방향 묻기 (센서로는 알 수 없는 부분) */
@Composable
fun ValenceQuestion(check: CheckState, onNext: () -> Unit) {
    QuestionScreen(
        title = "기분은 어느 쪽에 가까워요?",
        hint = check.voiceHits.firstOrNull()?.let { "말한 '${it.word}' → '${it.valence.label}'으로 골라 뒀어요" },
        options = Valence.entries.map { it to it.label },
        selected = check.valence,
    ) { check.valence = it; onNext() }
}

/** 4단계: 무엇 때문인지 묻기 — 1,000개 중 후보를 좁힘 */
@Composable
fun CauseQuestion(check: CheckState, onNext: () -> Unit) {
    QuestionScreen(
        title = "무엇 때문인 것 같아요?",
        hint = null,
        options = Cause.entries.map { it to it.label },
        selected = check.cause,
    ) { check.cause = it; onNext() }
}

@Composable
fun <T> QuestionScreen(
    title: String,
    hint: String?,
    options: List<Pair<T, String>>,
    selected: T?,
    onPick: (T) -> Unit,
) {
    Scaffold(timeText = { TimeText() }) {
        ScalingLazyColumn(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            item { Text(title, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold) }
            if (hint != null) item {
                Text(hint, textAlign = TextAlign.Center, fontSize = 11.sp, color = Color.Gray)
            }
            items(options) { (value, label) ->
                Chip(
                    onClick = { onPick(value) },
                    label = { Text(if (value == selected) "✓ $label" else label) },
                    colors = if (value == selected) ChipDefaults.primaryChipColors()
                    else ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

private const val FIRST_SHOWN = 8

/** 한 장씩 넘겨 보는 결과 화면의 장 */
private enum class Page { WORD, POEM, MEANING, QUOTE, SAVE }

/**
 * 5단계: 결과. 한 화면에 하나씩 — 단어 → 감성 문장 → 뜻 → 글귀 → 기록.
 * 위로 밀거나 화면을 눌러 다음 장으로. 다른 단어는 마지막 장에서.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ResultScreen(check: CheckState, onSave: (String) -> Unit) {
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
    var picking by remember { mutableStateOf(false) }
    val color = Color(EmotionWords.argbOf(valence))
    val quotes = remember(chosen) { Quotes.forWord(context, chosen) }
    var quoteIdx by remember(chosen) { mutableIntStateOf(0) }
    val pages = remember(chosen, quotes) {
        Page.entries.filter {
            when (it) {
                Page.POEM -> chosen.poem.isNotBlank()
                Page.QUOTE -> quotes.isNotEmpty()
                else -> true
            }
        }
    }
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    fun next() { scope.launch { if (pager.currentPage < pages.lastIndex) pager.animateScrollToPage(pager.currentPage + 1) } }

    BackHandler(enabled = picking) { picking = false }
    // 크라운·베젤 돌리기로도 넘기기
    val focus = remember { FocusRequester() }
    var rotary by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(picking) { if (!picking) runCatching { focus.requestFocus() } }

    if (picking) {
        OtherWords(words.filter { it != chosen }, color) { w ->
            chosen = w; picking = false
            scope.launch { pager.scrollToPage(0) }
        }
        return
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .background(Brush.radialGradient(listOf(color.copy(alpha = 0.28f), Color.Transparent)))
    ) {
        VerticalPager(
            state = pager,
            modifier = Modifier.fillMaxSize()
                .onRotaryScrollEvent { e ->
                    rotary += e.verticalScrollPixels
                    if (kotlin.math.abs(rotary) > 60f && !pager.isScrollInProgress) {
                        val target = (pager.currentPage + if (rotary > 0) 1 else -1).coerceIn(0, pages.lastIndex)
                        rotary = 0f
                        scope.launch { pager.animateScrollToPage(target) }
                    }
                    true
                }
                .focusRequester(focus).focusable(),
        ) { i ->
            Box(
                Modifier.fillMaxSize()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        if (pages[i] == Page.QUOTE && quotes.size > 1) quoteIdx++ else next()
                    }
                    .padding(horizontal = 26.dp, vertical = 30.dp),
                contentAlignment = Alignment.Center,
            ) {
                when (pages[i]) {
                    Page.WORD -> WordPage(chosen, color, energy, valence)
                    Page.POEM -> PoemPage(chosen.poem, color)
                    Page.MEANING -> MeaningPage(chosen, color)
                    Page.QUOTE -> QuotePage(quotes[quoteIdx % quotes.size], color, quotes.size > 1)
                    Page.SAVE -> SavePage(chosen, color, onSave = { onSave(chosen.word) }, onOther = { picking = true })
                }
            }
        }
        // 오른쪽 가장자리 점: 지금 몇 번째 장인지
        Column(
            Modifier.align(Alignment.CenterEnd).padding(end = 6.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            pages.indices.forEach { i ->
                val on = i == pager.currentPage
                Box(
                    Modifier.size(if (on) 6.dp else 4.dp).clip(RoundedCornerShape(3.dp))
                        .background(if (on) color else Color.White.copy(alpha = 0.3f))
                )
            }
        }
    }
}

@Composable
private fun WordPage(w: EmotionWord, color: Color, energy: Energy, valence: Valence) {
    val size = when {
        w.word.length <= 3 -> 38.sp
        w.word.length <= 5 -> 32.sp
        w.word.length <= 7 -> 24.sp
        else -> 19.sp
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("지금 마음은", fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f), letterSpacing = 2.sp)
        Spacer(Modifier.height(10.dp))
        Text(w.word, fontSize = size, fontWeight = FontWeight.Bold, color = color, textAlign = TextAlign.Center, lineHeight = size * 1.2f)
        Spacer(Modifier.height(12.dp))
        Box(Modifier.width(24.dp).height(1.dp).background(color.copy(alpha = 0.6f)))
        Spacer(Modifier.height(12.dp))
        Text("에너지 ${energy.label} · ${valence.label}", fontSize = 11.sp, color = Color.White.copy(alpha = 0.45f))
        Spacer(Modifier.height(14.dp))
        Text("⌄", fontSize = 14.sp, color = Color.White.copy(alpha = 0.35f))
    }
}

@Composable
private fun PoemPage(poem: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("“", fontSize = 28.sp, color = color.copy(alpha = 0.7f), fontFamily = FontFamily.Serif, lineHeight = 22.sp)
        Text(
            poem, fontSize = if (poem.length <= 24) 18.sp else 16.sp, lineHeight = 27.sp,
            fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic,
            color = Color(0xFFF3EEE6), textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun MeaningPage(w: EmotionWord, color: Color) {
    val r = w.research
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(w.word, fontSize = 13.sp, color = color, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text(w.meaning, fontSize = 15.sp, lineHeight = 23.sp, color = Color(0xFFE6E1DA), textAlign = TextAlign.Center)
        Spacer(Modifier.height(14.dp))
        Text(
            when (w.basis) {
                Basis.RESEARCH -> "근거 ${Research.SHORT}\n쾌 %.1f · 각성 %.1f /7".format(r!!.pleasure, r.activation)
                Basis.RESEARCH_EXCEPTION -> "연구 단어 · 뜻 기준 배치"
                Basis.AI -> "AI 분류 · 연구 근거 없음"
            },
            fontSize = 10.sp, lineHeight = 14.sp, color = Color.White.copy(alpha = 0.4f), textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun QuotePage(q: Quote, color: Color, more: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            q.text, fontSize = if (q.text.length <= 30) 15.sp else 13.sp, lineHeight = 21.sp,
            fontFamily = FontFamily.Serif, color = Color(0xFFF3EEE6), textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text("— ${q.source}", fontSize = 10.sp, lineHeight = 13.sp, color = color.copy(alpha = 0.8f), textAlign = TextAlign.Center)
        if (more) {
            Spacer(Modifier.height(8.dp))
            Text("눌러서 다른 글귀", fontSize = 9.sp, color = Color.White.copy(alpha = 0.3f))
        }
    }
}

@Composable
private fun SavePage(w: EmotionWord, color: Color, onSave: () -> Unit, onOther: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(w.word, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text("이 마음을 남길까요?", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
        Spacer(Modifier.height(14.dp))
        Chip(
            onClick = onSave,
            label = { Text("기록하기", color = Color.Black, fontWeight = FontWeight.Bold) },
            colors = ChipDefaults.chipColors(backgroundColor = color),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        CompactChip(
            onClick = onOther,
            label = { Text("다른 단어 보기") },
            colors = ChipDefaults.secondaryChipColors(),
        )
    }
}

/** 같은 칸의 다른 단어 고르기 */
@Composable
private fun OtherWords(others: List<EmotionWord>, color: Color, onPick: (EmotionWord) -> Unit) {
    var showAll by remember { mutableStateOf(false) }
    val shown = if (showAll) others else others.take(FIRST_SHOWN)
    Scaffold(timeText = { TimeText() }) {
        ScalingLazyColumn(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            item { Text("더 가까운 말이 있나요?", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f)) }
            items(shown) { w ->
                Chip(
                    onClick = { onPick(w) },
                    label = { Text(w.word, color = color, fontWeight = FontWeight.Bold) },
                    secondaryLabel = { Text(w.meaning, maxLines = 1) },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (!showAll && others.size > FIRST_SHOWN) item {
                CompactChip(
                    onClick = { showAll = true },
                    label = { Text("더 보기 (+${others.size - FIRST_SHOWN})") },
                    colors = ChipDefaults.secondaryChipColors(),
                )
            }
        }
    }
}

@Composable
fun HistoryScreen(store: MoodStore) {
    val entries = remember { store.entries() }
    val fmt = remember { SimpleDateFormat("M/d HH:mm", Locale.KOREA) }

    Scaffold(timeText = { TimeText() }) {
        ScalingLazyColumn(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            item { Text("감정 기록", fontWeight = FontWeight.Bold) }
            if (entries.isEmpty()) item {
                Text("아직 기록이 없어요", fontSize = 12.sp, color = Color.Gray)
            }
            items(entries) { e ->
                Chip(
                    onClick = {},
                    label = { Text(e.word, color = Color(EmotionWords.argbOf(e.valence)), fontWeight = FontWeight.Bold) },
                    secondaryLabel = {
                        Text(fmt.format(Date(e.time)) + (e.bpm?.let { " · ${it}bpm" } ?: ""))
                    },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}


/** 앱 소개: 처음 열 때, 그리고 홈의 '사용법'에서 */
@Composable
fun GuideScreen(onDone: () -> Unit) {
    val sections = remember { Guide.sections(isWatch = true) }
    Scaffold(timeText = { TimeText() }) {
        ScalingLazyColumn(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            item { Text("감정단어 사용법", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            sections.forEach { sec ->
                item { Text(sec.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB9AEFF)) }
                item { Text(sec.body, fontSize = 12.sp, color = Color(0xFFDDDDDD)) }
            }
            item {
                Chip(
                    onClick = onDone,
                    label = { Text("시작하기", fontWeight = FontWeight.Bold) },
                    colors = ChipDefaults.primaryChipColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/** 이번 주 돌아보기 (워치용 요약) */
@Composable
fun WeekScreen(store: MoodStore) {
    val all = remember { store.entries() }
    var weeksAgo by remember { mutableIntStateOf(0) }
    val w = remember(weeksAgo) { Weekly.summarize(all, weeksAgo) }
    val fmt = remember { SimpleDateFormat("M/d", Locale.KOREA) }

    Scaffold(timeText = { TimeText() }) {
        ScalingLazyColumn(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            item { Text(if (weeksAgo == 0) "이번 주" else "${weeksAgo}주 전", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            item { Text("${fmt.format(Date(w.start))} – ${fmt.format(Date(w.end - 1))}", fontSize = 11.sp, color = Color.Gray) }
            item { Text(w.insight, fontSize = 12.sp, textAlign = TextAlign.Center) }
            if (w.count > 0) {
                item {
                    // 기분 비율 막대
                    Row(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF333333))) {
                        Valence.entries.forEach { v ->
                            val n = w.valence.getValue(v)
                            if (n > 0) Box(Modifier.weight(n.toFloat()).fillMaxHeight().background(Color(EmotionWords.argbOf(v))))
                        }
                    }
                }
                item {
                    Text(
                        Valence.entries.joinToString(" · ") { "${it.label} ${w.pct(w.valence.getValue(it))}%" },
                        fontSize = 10.sp, color = Color.Gray, textAlign = TextAlign.Center
                    )
                }
                item { Text("많이 고른 감정", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                w.topWords.take(3).forEach { (word, n) ->
                    item {
                        val v = w.entries.last { it.word == word }.valence
                        Text("$word  ${n}번", fontSize = 14.sp, color = Color(EmotionWords.argbOf(v)))
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CompactChip(onClick = { weeksAgo++ }, label = { Text("‹ 이전") }, colors = ChipDefaults.secondaryChipColors())
                    if (weeksAgo > 0) CompactChip(onClick = { weeksAgo-- }, label = { Text("다음 ›") }, colors = ChipDefaults.secondaryChipColors())
                }
            }
        }
    }
}


// ── 목소리 ───────────────────────────────────────────────
/**
 * 목소리로 듣기 (선택). 두 가지:
 *  · 제시 문장 읽기: 매번 같은 문장이라 평소 목소리와 비교 → 몸의 에너지 추정을 거듦
 *  · 내 말로 말하기: 말 속의 감정 단어를 찾아 후보 맨 앞에
 */
@Composable
fun VoiceScreen(check: CheckState, onNext: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val baseline = remember { VoiceBaseline(context) }
    val sensorEnergy = remember { check.energy }   // 여러 번 읽어도 한 번만 반영
    // idle → reading(문장 보여줌) → recording → idle
    var mode by remember { mutableStateOf("idle") }
    var level by remember { mutableFloatStateOf(0f) }
    var note by remember { mutableStateOf<String?>(null) }
    var heard by remember { mutableStateOf<String?>(null) }
    var tried by remember { mutableStateOf(false) }
    val stop = remember { java.util.concurrent.atomic.AtomicBoolean(false) }

    fun startRecording() {
        mode = "recording"; stop.set(false); note = null; heard = null
        scope.launch {
            val r = VoiceTone.record(stop) { level = it }
            mode = "idle"
            tried = true
            val f = r?.features
            if (f == null) {
                note = "목소리를 잘 못 들었어요. 조용한 곳에서 다시 해 보세요"
            } else {
                val lv = baseline.level(f)
                baseline.add(f)
                if (lv == null) {
                    note = if (baseline.ready) "평소 목소리를 다 배웠어요. 다음부터 비교해요"
                    else "평소 목소리를 배우는 중이에요 (${baseline.count}/${VoiceBaseline.LEARN})"
                } else {
                    check.voiceLevel = lv
                    check.energy = (sensorEnergy ?: Energy.MID).shiftBy(lv)
                }
            }
            // 들은 문장 받아쓰기 (지원하는 기기만)
            if (r != null) {
                heard = "받아쓰는 중…"
                heard = VoiceTone.transcribe(context, r)?.let { "들은 문장: “$it”" } ?: "이 기기는 읽은 문장 받아쓰기를 지원하지 않아요"
            }
        }
    }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) startRecording() else note = "마이크 권한이 없어 읽기를 할 수 없어요"
    }
    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val text = res.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (text != null) {
            check.spoken = text
            check.voiceHits = VoiceWords.match(context, text)
            check.voiceHits.firstOrNull()?.let { if (check.valence == null) check.valence = it.valence }
        }
    }

    Scaffold(timeText = { TimeText() }) {
        ScalingLazyColumn(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            when (mode) {
                "reading", "recording" -> {
                    item { Text("이 문장을 소리 내어 읽어 주세요", fontSize = 11.sp, color = Color.Gray, textAlign = TextAlign.Center) }
                    item {
                        Text(
                            VoiceTone.SENTENCE, fontSize = 16.sp, lineHeight = 23.sp, fontFamily = FontFamily.Serif,
                            color = Color(0xFFF3EEE6), textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }
                    if (mode == "recording") {
                        item {
                            Box(Modifier.fillMaxWidth(0.7f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color.DarkGray)) {
                                Box(Modifier.fillMaxHeight().fillMaxWidth(level.coerceAtLeast(0.03f)).background(Color(0xFFB9AEFF)))
                            }
                        }
                        item {
                            Chip(onClick = { stop.set(true) }, label = { Text("다 읽었어요") },
                                colors = ChipDefaults.secondaryChipColors(), modifier = Modifier.fillMaxWidth())
                        }
                    } else item {
                        Chip(
                            onClick = {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startRecording()
                                else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            },
                            label = { Text("🎙 읽기 시작", fontWeight = FontWeight.Bold) },
                            colors = ChipDefaults.primaryChipColors(), modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item { Text("평소 내 목소리와 비교하려고, 매번 같은 문장을 읽어요", fontSize = 10.sp, color = Color.Gray, textAlign = TextAlign.Center) }
                }
                else -> {
                    item { Text("목소리로도 들어 볼까요?", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) }
                    item {
                        Text(
                            "문장을 읽으면 평소 목소리와 비교해 몸의 에너지를 가늠하고, 내 말로 말하면 그 말 속 감정 단어를 찾아요",
                            fontSize = 11.sp, color = Color.Gray, textAlign = TextAlign.Center
                        )
                    }
                    check.voiceLevel?.let { lv -> item { Text("목소리: ${lv.label}", fontSize = 12.sp, color = Color(0xFFB9AEFF)) } }
                    note?.let { n -> item { Text(n, fontSize = 11.sp, color = Color(0xFFB9AEFF), textAlign = TextAlign.Center) } }
                    heard?.let { h -> item { Text(h, fontSize = 11.sp, fontStyle = FontStyle.Italic, textAlign = TextAlign.Center) } }
                    if (tried || check.spoken != null) item {
                        Chip(onClick = onNext, label = { Text("다음", fontWeight = FontWeight.Bold) },
                            colors = ChipDefaults.primaryChipColors(), modifier = Modifier.fillMaxWidth())
                    }
                    check.spoken?.let { sp ->
                        item { Text("“$sp”", fontSize = 12.sp, fontStyle = FontStyle.Italic, textAlign = TextAlign.Center) }
                        item {
                            Text(
                                if (check.voiceHits.isEmpty()) "찾은 감정 단어가 없어요. 질문으로 찾아볼게요"
                                else "찾은 단어: " + check.voiceHits.take(4).joinToString(", ") { it.word },
                                fontSize = 11.sp, color = Color(0xFFB9AEFF), textAlign = TextAlign.Center
                            )
                        }
                    }
                    item {
                        Chip(onClick = { mode = "reading" }, label = { Text("문장 읽기") },
                            secondaryLabel = { Text("목소리 톤 → 에너지") },
                            colors = ChipDefaults.secondaryChipColors(), modifier = Modifier.fillMaxWidth())
                    }
                    item {
                        Chip(
                            onClick = {
                                val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
                                    putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "지금 기분을 한마디로")
                                }
                                runCatching { speechLauncher.launch(intent) }
                                    .onFailure { note = "이 기기에서는 받아쓰기를 쓸 수 없어요" }
                            },
                            label = { Text("내 말로 말하기") },
                            secondaryLabel = { Text("말한 내용 → 단어") },
                            colors = ChipDefaults.secondaryChipColors(), modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (!tried && check.spoken == null) item {
                        Chip(onClick = onNext, label = { Text("건너뛰기") },
                            colors = ChipDefaults.secondaryChipColors(), modifier = Modifier.fillMaxWidth())
                    }
                    item { Text("녹음은 저장하지 않아요. 받아쓰기는 기기의 음성 인식을 써요", fontSize = 9.sp, color = Color.DarkGray, textAlign = TextAlign.Center) }
                }
            }
        }
    }
}
