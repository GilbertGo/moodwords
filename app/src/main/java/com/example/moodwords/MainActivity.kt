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
    var energy by mutableStateOf<Energy?>(null)
    var valence by mutableStateOf<Valence?>(null)
    var cause by mutableStateOf<Cause?>(null)
    fun reset() { bpm = null; energy = null; valence = null; cause = null }
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
                MeasureScreen(store) { bpm ->
                    check.bpm = bpm
                    check.energy = bpm?.let { EmotionWords.estimateEnergy(it.toDouble(), store.restingBpm) }
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
                        MoodEntry(System.currentTimeMillis(), word, check.bpm, check.energy!!, check.valence!!)
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var update by remember { mutableStateOf<Updater.Release?>(null) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    // 앱을 열 때마다 GitHub에 새 버전이 있는지 확인
    // 앱을 열면 새 버전 확인 → 있으면 바로 자동 설치 (버튼은 실패 시 다시 시도용)
    LaunchedEffect(Unit) {
        update = Updater.checkNewer(Updater.WATCH, BuildConfig.VERSION_CODE)
        update?.let { rel ->
            progress = 0f
            runCatching { Updater.install(context, rel) { progress = it } }
                .onFailure { error = "다운로드 실패, 다시 눌러주세요" }
            progress = null
        }
    }

    Scaffold(timeText = { TimeText() }) {
        ScalingLazyColumn(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            update?.let { rel ->
                item {
                    Chip(
                        onClick = {
                            if (progress != null) return@Chip
                            progress = 0f
                            error = null
                            scope.launch {
                                runCatching { Updater.install(context, rel) { progress = it } }
                                    .onFailure { error = "다운로드 실패, 다시 눌러주세요" }
                                progress = null
                            }
                        },
                        label = {
                            Text(
                                when {
                                    progress != null -> "받는 중 ${((progress ?: 0f) * 100).roundToInt()}%"
                                    else -> "새 버전 설치 (v1.${rel.version})"
                                }
                            )
                        },
                        secondaryLabel = error?.let { { Text(it) } },
                        colors = ChipDefaults.chipColors(backgroundColor = Color(0xFF2E7D5B)),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
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
            item { Text("v${BuildConfig.VERSION_NAME}", fontSize = 10.sp, color = Color.Gray) }
        }
    }
}

/** Wear OS 6(API 36)부터 쓰이는 새 심박 권한 */
private const val HR_PERMISSION_NEW = "android.permission.health.READ_HEART_RATE"

/**
 * 1단계: 15초 동안 심박 측정.
 * 진행 링과 남은 시간은 심박 값과 상관없이 흘러가고, 값이 안 들어오면 안내 문구를 보여줌.
 * 권한이 없거나 센서가 값을 못 주면 심박 없이 다음 질문으로 넘어감. 언제든 건너뛰기 가능.
 */
@Composable
fun MeasureScreen(store: MoodStore, onDone: (Int?) -> Unit) {
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
    var elapsed by remember { mutableLongStateOf(0L) }
    var finished by remember { mutableStateOf(false) }
    val samples = remember { mutableListOf<Double>() }

    fun finish(bpm: Int?) {
        if (!finished) { finished = true; onDone(bpm) }
    }

    LaunchedEffect(Unit) {
        if (permState == 0) launcher.launch(arrayOf(Manifest.permission.BODY_SENSORS, HR_PERMISSION_NEW))
    }

    LaunchedEffect(permState) {
        when (permState) {
            -1 -> { delay(1500); finish(null) }
            1 -> {
                val start = System.currentTimeMillis()
                coroutineScope {
                    // 심박은 따로 받고, 시간은 그와 상관없이 흘러감
                    val reader = launch {
                        runCatching {
                            heartRateFlow(context).collect { bpm ->
                                samples += bpm
                                current = bpm.roundToInt()
                            }
                        }
                    }
                    while (elapsed < MEASURE_MILLIS) {
                        delay(200)
                        elapsed = System.currentTimeMillis() - start
                    }
                    reader.cancel()
                }
                // 측정 초반은 불안정하니 뒤쪽 절반만 평균
                val avg = samples.drop(samples.size / 2).takeIf { it.isNotEmpty() }?.average()
                finish(avg?.roundToInt())
            }
        }
    }

    val secondsLeft = ((MEASURE_MILLIS - elapsed).coerceAtLeast(0) + 999) / 1000
    val status = when {
        permState == 0 -> "심박 권한을 허용해 주세요"
        permState == -1 -> "권한이 없어 직접 고를게요"
        current != null -> "편하게 숨 쉬세요"
        elapsed > 4000 -> "심박을 찾는 중이에요\n워치를 손목에 꼭 맞게 차주세요"
        else -> "센서 준비 중…"
    }

    Scaffold(timeText = { TimeText() }) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = (elapsed / MEASURE_MILLIS.toFloat()).coerceIn(0f, 1f),
                modifier = Modifier.fillMaxSize().padding(4.dp),
                indicatorColor = Color(0xFFFF6B8A),
                strokeWidth = 5.dp
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("♥", color = Color(0xFFFF6B8A), fontSize = 20.sp)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(current?.let { "$it" } ?: "--", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    Text(" bpm", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 6.dp))
                }
                Text(status, fontSize = 12.sp, textAlign = TextAlign.Center)
                Text(
                    if (permState == 1) "${secondsLeft}초 · 평소 ${store.restingBpm.roundToInt()}bpm" else "",
                    fontSize = 10.sp, color = Color.Gray
                )
                Spacer(Modifier.height(6.dp))
                CompactChip(
                    onClick = { finish(current) },
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
        hint = check.bpm?.let { "심박 ${it}bpm → '${check.energy?.label}'으로 추정했어요" }
            ?: "심박을 재지 못했어요. 직접 골라주세요",
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
