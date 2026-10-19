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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
        AutoUpdate.schedule(this)
        setContent { MoodWordsApp() }
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
    fun reset() { bpm = null; tremor = null; tremorLevel = null; moved = false; energy = null; valence = null; cause = null }
}

@Composable
fun MoodWordsApp() {
    val context = LocalContext.current
    val store = remember { MoodStore(context) }
    val check = remember { CheckState() }
    val nav = rememberSwipeDismissableNavController()

    MaterialTheme {
        SwipeDismissableNavHost(navController = nav, startDestination = "home") {
            composable("home") {
                HomeScreen(
                    onStart = { check.reset(); nav.navigate("measure") },
                    onHistory = { nav.navigate("history") },
                )
            }
            composable("measure") {
                MeasureScreen(store) { m ->
                    check.bpm = m.bpm
                    check.tremor = m.tremor
                    check.moved = m.moved
                    check.tremorLevel = m.tremor?.let { tremorLevel(it, store.restingTremor) }
                    check.energy = estimateEnergy(m.bpm, check.tremorLevel, store)
                    nav.navigate("energy") { popUpTo("home") }
                }
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
                        MoodEntry(System.currentTimeMillis(), word, check.bpm, check.energy!!, check.valence!!, check.tremor)
                    )
                    nav.navigate("history") { popUpTo("home") }
                }
            }
            composable("history") { HistoryScreen(store) }
        }
    }
}

@Composable
fun HomeScreen(onStart: () -> Unit, onHistory: () -> Unit) {
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
    val bpm = check.bpm ?: return "심박을 재지 못했어요. 직접 골라주세요"
    val parts = mutableListOf("심박 ${bpm}bpm")
    check.tremorLevel?.let { parts += "떨림 ${it.label}" }
    val tail = if (check.moved) "\n(움직임이 있어 정확도가 낮을 수 있어요)" else ""
    return parts.joinToString(" · ") + " → '${check.energy?.label}'으로 추정했어요" + tail
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
        hint = null,
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

/** 5단계: 감정 단어 + 뜻. 원인에 맞는 후보 8개를 먼저, "더 보기"로 같은 칸 전체 */
@Composable
fun ResultScreen(check: CheckState, onSave: (String) -> Unit) {
    val context = LocalContext.current
    val energy = check.energy ?: return
    val valence = check.valence ?: return
    val cause = check.cause ?: Cause.ANY
    val words = remember(energy, valence, cause) { EmotionWords.find(context, energy, valence, cause) }
    if (words.isEmpty()) return
    var chosen by remember(words) { mutableStateOf(words.first()) }
    var showAll by remember(words) { mutableStateOf(false) }
    val color = Color(EmotionWords.argbOf(valence))
    val others = words.filter { it != chosen }
    val shown = if (showAll) others else others.take(FIRST_SHOWN)
    // 긴 표현도 한 화면에 들어가게 글자 크기 조절
    val wordSize = when {
        chosen.word.length <= 4 -> 32.sp
        chosen.word.length <= 7 -> 24.sp
        else -> 18.sp
    }

    Scaffold(timeText = { TimeText() }) {
        ScalingLazyColumn(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            item { Text("지금 마음은", fontSize = 12.sp, color = Color.Gray) }
            item {
                Text(
                    chosen.word, fontSize = wordSize, fontWeight = FontWeight.Bold,
                    color = color, textAlign = TextAlign.Center
                )
            }
            item { Text(chosen.meaning, textAlign = TextAlign.Center, fontSize = 13.sp) }
            chosen.research?.let { r ->
                item {
                    Text(
                        "근거 ${Research.SHORT}\n쾌 %.1f · 각성 %.1f /7".format(r.pleasure, r.activation),
                        textAlign = TextAlign.Center, fontSize = 10.sp, color = Color.Gray
                    )
                }
            }
            item {
                Button(
                    onClick = { onSave(chosen.word) },
                    colors = ButtonDefaults.buttonColors(backgroundColor = color),
                ) { Text("기록", color = Color.Black) }
            }
            item { Text("다른 단어가 더 맞나요?", fontSize = 11.sp, color = Color.Gray) }
            items(shown) { w ->
                CompactChip(onClick = { chosen = w }, label = { Text(w.word) })
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
