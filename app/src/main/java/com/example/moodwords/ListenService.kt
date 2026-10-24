package com.example.moodwords

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat

/**
 * 대화 듣기: 켜 둔 동안 기기 음성 인식으로 말을 글자로 바꿔, 감정 표현이 나오면 알림을 띄움.
 * 녹음 파일을 만들지 않고, 글자로 바꾼 문장도 저장하지 않음 (찾은 감정 단어만 알림에 씀).
 */
class ListenService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var running = false
    private var endAt = 0L                      // 0 = 끌 때까지
    private val lastShown = mutableMapOf<String, Long>()
    private var lastAny = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopAll(); return START_NOT_STICKY }
        val minutes = intent?.getIntExtra(EXTRA_MINUTES, 30) ?: 30
        endAt = if (minutes <= 0) 0L else System.currentTimeMillis() + minutes * 60_000L
        channels()
        val n = statusNotification()
        if (Build.VERSION.SDK_INT >= 30) startForeground(ID_STATUS, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        else startForeground(ID_STATUS, n)
        if (!running) {
            running = true
            onDevice = Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(this)
            prefs(this).edit().putBoolean("on", true).putLong("endAt", endAt).putBoolean("onDevice", onDevice).apply()
            listen()
        }
        return START_NOT_STICKY
    }

    private var onDevice = false

    private fun listen() {
        if (!running) return
        if (endAt > 0 && System.currentTimeMillis() > endAt) { stopAll(); return }
        recognizer?.destroy()
        val r = if (onDevice && Build.VERSION.SDK_INT >= 31) SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
            else SpeechRecognizer.createSpeechRecognizer(this)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onResults(b: Bundle?) {
                b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { heard(it) }
                again(300)
            }
            override fun onError(error: Int) = again(if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) 300 else 3000)
            override fun onReadyForSpeech(p: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(b: Bundle?) {}
            override fun onEvent(t: Int, b: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        runCatching { r.startListening(intent) }.onFailure { again(5000) }
    }

    private fun again(delayMs: Long) {
        if (running) main.postDelayed({ listen() }, delayMs)
    }

    /** 들은 문장 → 감정 단어 찾기 → 알림. 문장 자체는 여기서 버려짐 */
    private fun heard(text: String) {
        val now = System.currentTimeMillis()
        val w = VoiceWords.match(this, text).firstOrNull { it.word !in TOO_COMMON } ?: return
        if (now - lastAny < GAP_ANY) return
        if (now - (lastShown[w.word] ?: 0L) < GAP_SAME) return
        lastAny = now; lastShown[w.word] = now
        popup(w)
    }

    private fun popup(w: EmotionWord) {
        val open = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_WORD, w.word)
        }
        val pi = PendingIntent.getActivity(this, w.word.hashCode(), open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(this, CH_HEARD)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("'${w.word}' 이(가) 들렸어요")
            .setContentText("지금 마음을 기록할까요?")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .addAction(0, "기록하기", pi)
            .build()
        getSystemService(NotificationManager::class.java).notify(ID_HEARD, n)
        runCatching {
            getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 120, 80, 120), -1))
        }
    }

    private fun statusNotification(): Notification {
        val stop = PendingIntent.getService(
            this, 1, Intent(this, ListenService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE
        )
        val open = PendingIntent.getActivity(this, 2, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val until = if (endAt > 0) java.text.SimpleDateFormat("a h:mm까지", java.util.Locale.KOREA).format(java.util.Date(endAt)) else "끌 때까지"
        return NotificationCompat.Builder(this, CH_STATUS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("대화 듣는 중")
            .setContentText("$until · 녹음하지 않아요")
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, "끄기", stop)
            .build()
    }

    private fun channels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_STATUS, "대화 듣는 중", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_HEARD, "들린 감정", NotificationManager.IMPORTANCE_HIGH))
    }

    private fun stopAll() {
        running = false
        main.removeCallbacksAndMessages(null)
        runCatching { recognizer?.destroy() }
        recognizer = null
        prefs(this).edit().putBoolean("on", false).apply()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        running = false
        main.removeCallbacksAndMessages(null)
        runCatching { recognizer?.destroy() }
        prefs(this).edit().putBoolean("on", false).apply()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_MINUTES = "minutes"
        const val EXTRA_WORD = "heard_word"
        private const val ACTION_STOP = "stop"
        private const val CH_STATUS = "listen_status"
        private const val CH_HEARD = "listen_heard"
        private const val ID_STATUS = 41
        private const val ID_HEARD = 42
        private const val GAP_ANY = 10 * 60_000L      // 알림 사이 최소 10분
        private const val GAP_SAME = 30 * 60_000L     // 같은 단어는 30분에 한 번
        /** 대화에 너무 흔히 섞여 감정 표현이 아닐 때가 많은 말 */
        private val TOO_COMMON = setOf("좋음", "괜찮음", "편함", "재미있음")

        fun prefs(c: Context) = c.getSharedPreferences("listen", Context.MODE_PRIVATE)
        fun isOn(c: Context) = prefs(c).getBoolean("on", false)
        fun endAt(c: Context) = prefs(c).getLong("endAt", 0L)
        fun usedOnDevice(c: Context) = prefs(c).getBoolean("onDevice", false)

        fun start(c: Context, minutes: Int) {
            c.startForegroundService(Intent(c, ListenService::class.java).putExtra(EXTRA_MINUTES, minutes))
        }
        fun stop(c: Context) {
            c.startService(Intent(c, ListenService::class.java).setAction(ACTION_STOP))
        }
    }
}
