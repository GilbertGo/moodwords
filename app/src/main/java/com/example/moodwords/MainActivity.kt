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
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private const val MEASURE_MILLIS = 15_000L

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MoodWordsApp() }
    }
}

/** 한 번의 감정 체크 동안 화면 사이에서 공유하는 값 */
class CheckState {
    var bpm by mutableStateOf<Int?>(null)
    var energy by mutableStateOf<Energy?>(null)
    var valence by mutableStateOf<Valence?>(null)
    fun reset() { bpm = null; energy = null; valence = null }
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
                ValenceQuestion(check) { nav.navigate("result") }
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
        }
    }
}

/** 1단계: 15초 동안 심박 측정. 권한이 없거나 센서가 없으면 건너뜀 */
@Composable
fun MeasureScreen(store: MoodStore, onDone: (Int?) -> Unit) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.BODY_SENSORS) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var asked by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it; asked = true
    }
    var current by remember { mutableStateOf<Int?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.BODY_SENSORS) }

    LaunchedEffect(granted, asked) {
        if (!granted) {
            if (asked) onDone(null)
            return@LaunchedEffect
        }
        val samples = mutableListOf<Double>()
        val start = System.currentTimeMillis()
        withTimeoutOrNull(MEASURE_MILLIS) {
            heartRateFlow(context).collect { bpm ->
                samples += bpm
                current = bpm.roundToInt()
                progress = ((System.currentTimeMillis() - start) / MEASURE_MILLIS.toFloat()).coerceAtMost(1f)
            }
        }
        // 측정 초반은 불안정하니 뒤쪽 절반만 평균
        val avg = samples.drop(samples.size / 2).takeIf { it.isNotEmpty() }?.average()
        onDone(avg?.roundToInt())
    }

    Scaffold(timeText = { TimeText() }) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxSize().padding(4.dp),
                indicatorColor = Color(0xFFFF6B8A),
                strokeWidth = 5.dp
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("♥", color = Color(0xFFFF6B8A), fontSize = 22.sp)
                Text(current?.let { "$it" } ?: "--", fontSize = 36.sp, fontWeight = FontWeight.Bold)
                Text("bpm", fontSize = 12.sp, color = Color.Gray)
                Spacer(Modifier.height(6.dp))
                Text("편하게 숨 쉬세요", fontSize = 12.sp)
                Text("평소 ${store.restingBpm.roundToInt()}bpm", fontSize = 10.sp, color = Color.Gray)
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

/** 4단계: 감정 단어 + 뜻. 다른 단어를 골라 바꿀 수 있고, 기록하기로 저장 */
@Composable
fun ResultScreen(check: CheckState, onSave: (String) -> Unit) {
    val energy = check.energy ?: return
    val valence = check.valence ?: return
    val words = remember(energy, valence) { EmotionWords.find(energy, valence) }
    var chosen by remember(words) { mutableStateOf(words.first()) }
    val color = EmotionWords.colorOf(valence)

    Scaffold(timeText = { TimeText() }) {
        ScalingLazyColumn(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            item { Text("지금 마음은", fontSize = 12.sp, color = Color.Gray) }
            item { Text(chosen.word, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = color) }
            item { Text(chosen.meaning, textAlign = TextAlign.Center, fontSize = 13.sp) }
            item {
                Button(
                    onClick = { onSave(chosen.word) },
                    colors = ButtonDefaults.buttonColors(backgroundColor = color),
                ) { Text("기록", color = Color.Black) }
            }
            item { Text("다른 단어가 더 맞나요?", fontSize = 11.sp, color = Color.Gray) }
            items(words.filter { it != chosen }) { w ->
                CompactChip(onClick = { chosen = w }, label = { Text(w.word) })
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
                    label = { Text(e.word, color = EmotionWords.colorOf(e.valence), fontWeight = FontWeight.Bold) },
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
