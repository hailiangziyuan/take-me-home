package com.example.takemehome

import android.app.Application
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

private val Application.dataStore by preferencesDataStore("local_settings")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = Room.databaseBuilder(application, AppDatabase::class.java, "practice.db").build()
        val settings = SettingsStore(application)
        setContent {
            TakeMeHomeApp(db.practiceRecordDao(), settings)
        }
    }
}

data class MeditationCue(
    val startSecond: Int,
    val stageName: String,
    val text: String
)

data class Stage(
    val startSecond: Int,
    val endSecond: Int,
    val name: String
)

@Entity(tableName = "practice_records")
data class PracticeRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateTime: Long,
    val triggerText: String,
    val innerChildText: String,
    val commitmentText: String,
    val durationSeconds: Int,
    val completed: Boolean
)

@Dao
interface PracticeRecordDao {
    @Query("SELECT * FROM practice_records ORDER BY dateTime DESC")
    fun observeAll(): Flow<List<PracticeRecordEntity>>

    @Insert
    suspend fun insert(record: PracticeRecordEntity)

    @Query("DELETE FROM practice_records")
    suspend fun clearAll()
}

@Database(entities = [PracticeRecordEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun practiceRecordDao(): PracticeRecordDao
}

enum class BackgroundSound(val label: String, val rawName: String?) {
    Tide("潮水声", "tide_soft"),
    Ocean("海浪声", "ocean_waves"),
    Rain("雨声", "rain_soft"),
    Stream("溪流声", "stream_soft"),
    Silence("静音", null)
}

data class UserSettings(
    val selectedBackgroundSound: BackgroundSound = BackgroundSound.Tide,
    val backgroundVolume: Float = 0.2f,
    val ttsSpeechRate: Float = 0.72f,
    val showFullText: Boolean = true,
    val safetyAccepted: Boolean = false
)

class SettingsStore(private val app: Application) {
    private val soundKey = stringPreferencesKey("selectedBackgroundSound")
    private val volumeKey = floatPreferencesKey("backgroundVolume")
    private val rateKey = floatPreferencesKey("ttsSpeechRate")
    private val showFullTextKey = booleanPreferencesKey("showFullText")
    private val safetyAcceptedKey = booleanPreferencesKey("safetyAccepted")

    val flow: Flow<UserSettings> = app.dataStore.data.map { prefs ->
        UserSettings(
            selectedBackgroundSound = runCatching {
                BackgroundSound.valueOf(prefs[soundKey] ?: BackgroundSound.Tide.name)
            }.getOrDefault(BackgroundSound.Tide),
            backgroundVolume = prefs[volumeKey] ?: 0.2f,
            ttsSpeechRate = prefs[rateKey] ?: 0.72f,
            showFullText = prefs[showFullTextKey] ?: true,
            safetyAccepted = prefs[safetyAcceptedKey] ?: false
        )
    }

    suspend fun updateSound(sound: BackgroundSound) = app.dataStore.edit { it[soundKey] = sound.name }
    suspend fun updateVolume(volume: Float) = app.dataStore.edit { it[volumeKey] = volume.coerceIn(0f, 1f) }
    suspend fun updateRate(rate: Float) = app.dataStore.edit { it[rateKey] = rate }
    suspend fun updateShowFullText(show: Boolean) = app.dataStore.edit { it[showFullTextKey] = show }
    suspend fun acceptSafety() = app.dataStore.edit { it[safetyAcceptedKey] = true }
}

private class LocalAudioPlayer(private val activity: ComponentActivity) {
    private var mediaPlayer: MediaPlayer? = null
    private var baseVolume: Float = 0.2f
    private var ducked = false

    fun play(sound: BackgroundSound, volume: Float) {
        stop()
        baseVolume = volume.coerceIn(0f, 1f)
        val rawName = sound.rawName ?: return
        val id = activity.resources.getIdentifier(rawName, "raw", activity.packageName)
        if (id == 0) return
        mediaPlayer = MediaPlayer.create(activity, id)?.apply {
            isLooping = true
            start()
        }
        applyVolume()
    }

    fun setVolume(volume: Float) {
        baseVolume = volume.coerceIn(0f, 1f)
        applyVolume()
    }

    fun duck() {
        ducked = true
        applyVolume()
    }

    fun unduck() {
        ducked = false
        applyVolume()
    }

    fun pause() {
        mediaPlayer?.takeIf { it.isPlaying }?.pause()
    }

    fun resume() {
        mediaPlayer?.start()
    }

    fun stop() {
        mediaPlayer?.release()
        mediaPlayer = null
        ducked = false
    }

    private fun applyVolume() {
        val volume = if (ducked) baseVolume * 0.35f else baseVolume
        mediaPlayer?.setVolume(volume, volume)
    }
}

private class CueAudioPlayer(private val activity: ComponentActivity) {
    private var mediaPlayer: MediaPlayer? = null

    fun play(cueNumber: Int, onStart: () -> Unit, onComplete: () -> Unit): Boolean {
        stop()
        val rawName = "cue_%03d".format(cueNumber)
        val id = activity.resources.getIdentifier(rawName, "raw", activity.packageName)
        if (id == 0) return false
        mediaPlayer = MediaPlayer.create(activity, id)?.apply {
            setOnCompletionListener {
                onComplete()
                stop()
            }
            onStart()
            start()
        }
        return mediaPlayer != null
    }

    fun stop() {
        mediaPlayer?.release()
        mediaPlayer = null
    }
}

private class ChineseTts(private val activity: ComponentActivity) {
    var ready by mutableStateOf(false)
        private set
    var chineseAvailable by mutableStateOf(true)
        private set
    var initializing by mutableStateOf(false)
        private set
    var failed by mutableStateOf(false)
        private set
    var diagnostic by mutableStateOf("语音未初始化")
        private set
    var onSpeechStart: () -> Unit = {}
    var onSpeechEnd: () -> Unit = {}
    private var tts: TextToSpeech? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun init(rate: Float, force: Boolean = false) {
        Log.i("TakeMeHomeTts", "init requested: force=$force ready=$ready initializing=$initializing engine=${tts != null}")
        diagnostic = "正在初始化语音引擎"
        if (force && !initializing) {
            shutdown()
        }
        if (tts != null || initializing) return
        initializing = true
        chineseAvailable = true
        failed = false
        diagnostic = "正在连接系统语音引擎"
        tts = TextToSpeech(activity) { status ->
            Log.i("TakeMeHomeTts", "init callback: status=$status")
            mainHandler.postDelayed({ configure(status, rate, 0) }, 80)
        }
    }

    private fun configure(status: Int, rate: Float, attempt: Int) {
        if (status != TextToSpeech.SUCCESS) {
            Log.w("TakeMeHomeTts", "TTS init failed: status=$status")
            ready = false
            initializing = false
            chineseAvailable = false
            failed = true
            diagnostic = "语音初始化失败 status=$status"
            return
        }

        val engine = tts
        if (engine == null) {
            if (attempt < 10) {
                mainHandler.postDelayed({ configure(status, rate, attempt + 1) }, 100)
            } else {
                Log.w("TakeMeHomeTts", "TTS init callback succeeded but engine stayed null")
                ready = false
                initializing = false
                chineseAvailable = false
                failed = true
                diagnostic = "语音引擎连接超时"
            }
            return
        }

        val locale = listOf(Locale.SIMPLIFIED_CHINESE, Locale.CHINESE, Locale.CHINA)
            .firstOrNull { engine.isLanguageAvailable(it) != TextToSpeech.LANG_NOT_SUPPORTED }
            ?: Locale.CHINESE
        val languageResult = engine.setLanguage(locale)
        engine.setSpeechRate(rate)
        engine.setPitch(0.95f)
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                mainHandler.post { onSpeechStart() }
            }

            override fun onDone(utteranceId: String?) {
                mainHandler.post { onSpeechEnd() }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                mainHandler.post { onSpeechEnd() }
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                mainHandler.post { onSpeechEnd() }
            }
        })
        ready = true
        initializing = false
        chineseAvailable = true
        failed = false
        diagnostic = "语音就绪：${engine.defaultEngine}"
        Log.i("TakeMeHomeTts", "TTS ready: engine=${engine.defaultEngine}, locale=$locale, languageResult=$languageResult")
    }

    fun speak(text: String, rate: Float): Boolean {
        val engine = tts
        if (!ready || engine == null) {
            diagnostic = "语音尚未就绪，无法朗读"
            Log.w("TakeMeHomeTts", "speak skipped: ready=$ready engine=${engine != null}")
            return false
        }
        engine.setSpeechRate(rate)
        val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "cue-${System.currentTimeMillis()}")
        diagnostic = "朗读请求返回：$result"
        Log.i("TakeMeHomeTts", "speak result=$result")
        return result != TextToSpeech.ERROR
    }

    fun stop() {
        tts?.stop()
        onSpeechEnd()
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        ready = false
        initializing = false
        chineseAvailable = true
        failed = false
        diagnostic = "语音已关闭"
    }
}

private enum class Screen { Home, Session, Reflection, History, Settings }

@Composable
fun TakeMeHomeApp(dao: PracticeRecordDao, settingsStore: SettingsStore) {
    val activity = LocalContext.current as ComponentActivity
    val scope = rememberCoroutineScope()
    val settings by settingsStore.flow.collectAsState(UserSettings())
    val records by dao.observeAll().collectAsState(emptyList())
    val tts = remember { ChineseTts(activity) }
    val audio = remember { LocalAudioPlayer(activity) }
    val cueAudio = remember { CueAudioPlayer(activity) }
    var screen by remember { mutableStateOf(Screen.Home) }
    var durationForRecord by remember { mutableIntStateOf(TOTAL_SECONDS) }
    var completedForRecord by remember { mutableStateOf(true) }

    DisposableEffect(Unit) {
        tts.init(settings.ttsSpeechRate)
        onDispose {
            tts.shutdown()
            audio.stop()
            cueAudio.stop()
        }
    }

    MaterialTheme(colorScheme = appleColorScheme) {
        Surface(color = InkBlue, modifier = Modifier.fillMaxSize()) {
            if (!settings.safetyAccepted) {
                SafetyDialog { scope.launch { settingsStore.acceptSafety() } }
            }
            when (screen) {
                Screen.Home -> HomeScreen(
                    totalCount = records.count { it.completed },
                    streak = calculateStreak(records),
                    onStart = { screen = Screen.Session },
                    onHistory = { screen = Screen.History },
                    onSettings = { screen = Screen.Settings }
                )
                Screen.Session -> SessionScreen(
                    settings = settings,
                    tts = tts,
                    audio = audio,
                    cueAudio = cueAudio,
                    onVolume = { scope.launch { settingsStore.updateVolume(it) } },
                    onFinished = { duration, completed ->
                        durationForRecord = duration
                        completedForRecord = completed
                        screen = Screen.Reflection
                    },
                    onBack = {
                        audio.stop()
                        tts.stop()
                        cueAudio.stop()
                        screen = Screen.Home
                    }
                )
                Screen.Reflection -> ReflectionScreen(
                    completed = completedForRecord,
                    durationSeconds = durationForRecord,
                    onSave = { trigger, child, commitment ->
                        scope.launch {
                            dao.insert(
                                PracticeRecordEntity(
                                    dateTime = System.currentTimeMillis(),
                                    triggerText = trigger,
                                    innerChildText = child,
                                    commitmentText = commitment,
                                    durationSeconds = durationForRecord,
                                    completed = completedForRecord
                                )
                            )
                            Toast.makeText(activity, "今天，我又把自己带回家了一点。", Toast.LENGTH_LONG).show()
                            screen = Screen.Home
                        }
                    },
                    onSkip = { screen = Screen.Home }
                )
                Screen.History -> HistoryScreen(records, onBack = { screen = Screen.Home })
                Screen.Settings -> SettingsScreen(
                    settings = settings,
                    onSound = { scope.launch { settingsStore.updateSound(it) } },
                    onVolume = { scope.launch { settingsStore.updateVolume(it) } },
                    onRate = { scope.launch { settingsStore.updateRate(it) } },
                    onShowText = { scope.launch { settingsStore.updateShowFullText(it) } },
                    onClear = { scope.launch { dao.clearAll() } },
                    onBack = { screen = Screen.Home }
                )
            }
        }
    }
}

@Composable
private fun SafetyDialog(onAccept: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("心理安全提示") },
        text = {
            Text("这个 App 是用于个人自我觉察、冥想和心理练习的辅助工具，不能替代专业心理咨询、精神科诊疗或紧急求助。如果你出现强烈自伤、伤人冲动，或无法控制自己的行为，请立即联系身边可信任的人、专业人员或当地紧急服务。")
        },
        confirmButton = { Button(onClick = onAccept) { Text("我知道了") } }
    )
}

@Composable
private fun HomeScreen(
    totalCount: Int,
    streak: Int,
    onStart: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit
) {
    AppScaffold {
        Text("带我回家", color = Paper, fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
        Text("每日 25 分钟心理自助练习", color = Mist, fontSize = 16.sp)
        Spacer(Modifier.height(28.dp))
        Text("连续练习 $streak 天 · 累计 $totalCount 次", color = Mist)
        Spacer(Modifier.height(28.dp))
        PrimaryButton("开始今日练习", onStart)
        QuietButton("查看练习记录", onHistory)
        QuietButton("设置", onSettings)
    }
}

@Composable
private fun SessionScreen(
    settings: UserSettings,
    tts: ChineseTts,
    audio: LocalAudioPlayer,
    cueAudio: CueAudioPlayer,
    onVolume: (Float) -> Unit,
    onFinished: (Int, Boolean) -> Unit,
    onBack: () -> Unit
) {
    var elapsed by remember { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    var lastSpokenStart by remember { mutableIntStateOf(-1) }
    var confirmEnd by remember { mutableStateOf(false) }
    val cue = cueAt(elapsed)
    val stage = stageAt(elapsed)
    val stageProgress = ((elapsed - stage.startSecond).toFloat() / (stage.endSecond - stage.startSecond)).coerceIn(0f, 1f)
    tts.onSpeechStart = { audio.duck() }
    tts.onSpeechEnd = { audio.unduck() }

    LaunchedEffect(running, paused) {
        while (running && !paused && elapsed < TOTAL_SECONDS) {
            val currentCue = cueAt(elapsed)
            if (currentCue.startSecond != lastSpokenStart) {
                val cueNumber = meditationCues.indexOf(currentCue) + 1
                if (
                    cueAudio.play(
                        cueNumber = cueNumber,
                        onStart = { audio.duck() },
                        onComplete = { audio.unduck() }
                    ) || tts.speak(currentCue.text, settings.ttsSpeechRate)
                ) {
                    lastSpokenStart = currentCue.startSecond
                }
            }
            delay(1000)
            elapsed += 1
        }
        if (elapsed >= TOTAL_SECONDS) {
            audio.unduck()
            audio.stop()
            tts.stop()
            cueAudio.stop()
            onFinished(TOTAL_SECONDS, true)
        }
    }

    if (confirmEnd) {
        AlertDialog(
            onDismissRequest = { confirmEnd = false },
            title = { Text("今天要提前结束练习吗？") },
            confirmButton = {
                TextButton(onClick = {
                    audio.unduck()
                    audio.stop()
                    tts.stop()
                    cueAudio.stop()
                    onFinished(elapsed, false)
                }) { Text("结束并记录") }
            },
            dismissButton = {
                Column {
                    TextButton(onClick = { confirmEnd = false }) { Text("继续练习") }
                    TextButton(onClick = {
                        audio.unduck()
                        audio.stop()
                        tts.stop()
                        cueAudio.stop()
                        onBack()
                    }) { Text("结束不记录") }
                }
            }
        )
    }

    AppScaffold {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("带我回家", color = Paper, fontWeight = FontWeight.SemiBold)
            Text(formatTime(TOTAL_SECONDS - elapsed), color = Paper, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(18.dp))
        Text(stage.name, color = Paper, fontSize = 22.sp, fontWeight = FontWeight.Medium)
        Text("本阶段剩余 ${formatTime(stage.endSecond - elapsed)}", color = Mist)
        Spacer(Modifier.height(14.dp))
        LinearProgressIndicator(progress = { stageProgress }, modifier = Modifier.fillMaxWidth(), color = Sage)
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(progress = { elapsed / TOTAL_SECONDS.toFloat() }, modifier = Modifier.fillMaxWidth(), color = Paper)
        Spacer(Modifier.height(24.dp))
        Card(colors = CardDefaults.cardColors(containerColor = SoftPanel), shape = RoundedCornerShape(8.dp)) {
            Text(
                if (settings.showFullText) cue.text else cue.text.lineSequence().firstOrNull().orEmpty(),
                color = InkBlue,
                fontSize = 20.sp,
                lineHeight = 30.sp,
                modifier = Modifier.padding(18.dp)
            )
        }
        if (tts.failed) {
            Text("语音引擎暂时未准备好。", color = Warning)
        }
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = {
                if (!running) {
                    tts.init(settings.ttsSpeechRate, force = true)
                    running = true
                    paused = false
                    audio.play(settings.selectedBackgroundSound, settings.backgroundVolume)
                } else {
                    paused = !paused
                    if (paused) {
                        tts.stop()
                        cueAudio.stop()
                        audio.unduck()
                        audio.pause()
                    } else {
                        lastSpokenStart = -1
                        audio.resume()
                    }
                }
            }) { Text(if (!running) "开始" else if (paused) "继续" else "暂停") }
            OutlinedButton(onClick = {
                tts.stop()
                cueAudio.stop()
                audio.unduck()
                audio.stop()
                elapsed = 0
                lastSpokenStart = -1
                paused = false
                running = false
            }) { Text("重新开始") }
            OutlinedButton(onClick = { confirmEnd = true }) { Text("结束") }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("背景音量", color = Mist)
            Slider(
                value = settings.backgroundVolume,
                onValueChange = {
                    audio.setVolume(it)
                    onVolume(it)
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ReflectionScreen(
    completed: Boolean,
    durationSeconds: Int,
    onSave: (String, String, String) -> Unit,
    onSkip: () -> Unit
) {
    var trigger by remember { mutableStateOf("") }
    var child by remember { mutableStateOf("") }
    var commitment by remember { mutableStateOf("") }
    AppScaffold {
        Text("练习记录", color = Paper, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        Text("${if (completed) "已完成" else "提前结束"} · ${formatTime(durationSeconds)}", color = Mist)
        Spacer(Modifier.height(16.dp))
        ReflectionField("今天最明显的触发是什么？", trigger) { trigger = it }
        ReflectionField("我今天想对小时候的自己说什么？", child) { child = it }
        ReflectionField("今天我承诺做的一个小行动是什么？", commitment) { commitment = it }
        PrimaryButton("保存记录") { onSave(trigger, child, commitment) }
        QuietButton("不保存，回到首页", onSkip)
    }
}

@Composable
private fun HistoryScreen(records: List<PracticeRecordEntity>, onBack: () -> Unit) {
    AppScaffold {
        Text("历史记录", color = Paper, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        Text("累计 ${records.count { it.completed }} 次 · 连续 ${calculateStreak(records)} 天", color = Mist)
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
            items(records, key = { it.id }) { record: PracticeRecordEntity ->
                Card(colors = CardDefaults.cardColors(containerColor = SoftPanel), shape = RoundedCornerShape(8.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(dateText(record.dateTime), color = InkBlue, fontWeight = FontWeight.SemiBold)
                        Text("触发：${record.triggerText}", color = InkBlue)
                        Text("对小时候的自己：${record.innerChildText}", color = InkBlue)
                        Text("小行动：${record.commitmentText}", color = InkBlue)
                    }
                }
            }
        }
        QuietButton("返回首页", onBack)
    }
}

@Composable
private fun SettingsScreen(
    settings: UserSettings,
    onSound: (BackgroundSound) -> Unit,
    onVolume: (Float) -> Unit,
    onRate: (Float) -> Unit,
    onShowText: (Boolean) -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit
) {
    var confirmClear by remember { mutableStateOf(false) }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空本地记录？") },
            text = { Text("这个操作只会删除本机数据库中的练习记录，无法撤销。") },
            confirmButton = { Button(onClick = { confirmClear = false; onClear() }) { Text("确认清空") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } }
        )
    }
    AppScaffold {
        Text("设置", color = Paper, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        Text("背景音", color = Paper, fontWeight = FontWeight.Medium)
        BackgroundSound.values().forEach { sound: BackgroundSound ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = settings.selectedBackgroundSound == sound, onClick = { onSound(sound) })
                Text(sound.label, color = Mist)
            }
        }
        Text("背景音量 ${(settings.backgroundVolume * 100).toInt()}%", color = Paper)
        Slider(value = settings.backgroundVolume, onValueChange = onVolume)
        Text("TTS 语速", color = Paper)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onRate(0.65f) }) { Text("很慢") }
            OutlinedButton(onClick = { onRate(0.72f) }) { Text("冥想") }
            OutlinedButton(onClick = { onRate(0.85f) }) { Text("正常") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = settings.showFullText, onCheckedChange = onShowText)
            Text("显示完整引导文字", color = Mist)
        }
        Divider(color = Mist.copy(alpha = 0.4f))
        QuietButton("一键清空本地记录", { confirmClear = true })
        QuietButton("返回首页", onBack)
    }
}

@Composable
private fun AppScaffold(content: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(AppBackground)
            .padding(horizontal = 22.dp, vertical = 32.dp)
    ) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            content = content
        )
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = AppleBlue, contentColor = Color.White),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
    ) { Text(text) }
}

@Composable
private fun QuietButton(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(text, color = AppleBlue) }
}

@Composable
private fun ReflectionField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        minLines = 3,
        modifier = Modifier.fillMaxWidth()
    )
}

private fun cueAt(elapsed: Int): MeditationCue = meditationCues.lastOrNull { elapsed >= it.startSecond } ?: meditationCues.first()
private fun stageAt(elapsed: Int): Stage = stages.lastOrNull { elapsed >= it.startSecond } ?: stages.first()

private fun formatTime(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    return "%02d:%02d".format(safe / 60, safe % 60)
}

private fun dateText(value: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(value)

private fun calculateStreak(records: List<PracticeRecordEntity>): Int {
    val zone = ZoneId.systemDefault()
    val days = records.filter { it.completed }
        .map { Instant.ofEpochMilli(it.dateTime).atZone(zone).toLocalDate() }
        .toSet()
    if (days.isEmpty()) return 0
    var day = LocalDate.now(zone)
    if (day !in days) day = day.minusDays(1)
    var streak = 0
    while (day in days) {
        streak += 1
        day = day.minusDays(1)
    }
    return streak
}

private const val TOTAL_SECONDS = 1500
private val InkBlue = Color(0xFF1D1D1F)
private val Paper = Color(0xFF1D1D1F)
private val Mist = Color(0xFF6E6E73)
private val Sage = Color(0xFF34C759)
private val SoftPanel = Color(0xFFFFFFFF)
private val Warning = Color(0xFFFF9F0A)
private val AppleBlue = Color(0xFF007AFF)
private val AppBackground = Color(0xFFF5F5F7)
private val appleColorScheme = lightColorScheme(
    primary = AppleBlue,
    secondary = AppleBlue,
    tertiary = Sage,
    background = AppBackground,
    surface = SoftPanel,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = InkBlue,
    onSurface = InkBlue
)

private val stages = listOf(
    Stage(0, 180, "身体落地"),
    Stage(180, 480, "正念观察"),
    Stage(480, 900, "内在小孩安抚"),
    Stage(900, 1320, "信念重构"),
    Stage(1320, 1500, "今日行动承诺")
)

private val meditationCues = listOf(
    MeditationCue(0, "身体落地", """
        现在，请慢慢坐好。
        让双脚轻轻踏在地面上。
        让身体找到一个稳定、舒服、不费力的位置。
        不需要坐得很完美。
        不需要表现得很好。
        这一刻，你只需要回来。
        回到身体。
        回到呼吸。
        回到这里。
    """.trimIndent()),
    MeditationCue(30, "身体落地", """
        轻轻感受脚底和地面的接触。
        感受身体被椅子承托。
        感受双手自然地放着。
        你不用逃。
        你不用证明。
        你不用解决所有问题。
        此刻，你只是安静地坐在这里。
    """.trimIndent()),
    MeditationCue(60, "身体落地", """
        慢慢吸气。
        慢慢呼气。
        吸气的时候，知道自己正在吸气。
        呼气的时候，知道自己正在呼气。
        如果有念头出现，不需要追走它。
        只是轻轻知道：我有念头了。
        然后，再回到呼吸。
    """.trimIndent()),
    MeditationCue(100, "身体落地", """
        请在心里轻轻对自己说：
        我现在是安全的。
        我回到身体。
        我不用逃，也不用证明。
        我可以暂时放下外面的世界。
        这二十五分钟，是我正当地给自己的时间。
    """.trimIndent()),
    MeditationCue(145, "身体落地", """
        如果身体某个地方紧绷，就轻轻看见它。
        不强迫它放松。
        只是允许它在那里。
        你已经撑了很久。
        现在，可以稍微放下一点点。
        一点点就够了。
    """.trimIndent()),
    MeditationCue(180, "正念观察", """
        现在，我们进入正念观察。
        请轻轻看一看，今天心里最明显的感受是什么。
        是委屈？
        是疲惫？
        是愤怒？
        是焦虑？
        是冲动？
        是渴望？
        是不甘心？
        还是一种说不清楚的空？
    """.trimIndent()),
    MeditationCue(225, "正念观察", """
        不管出现什么，都先不要批判。
        你不需要立刻改变它。
        你只是在心里轻轻说：
        我看见了。
        这是一个念头。
        这是一个情绪。
        这是旧伤被触发了。
        它不是命令。
        它不需要立刻变成行动。
    """.trimIndent()),
    MeditationCue(275, "正念观察", """
        如果你看见了“我不配”，请只是看见它。
        如果你看见了“我想被选择”，请只是看见它。
        如果你看见了“我想证明自己”，请只是看见它。
        如果你看见了“我想逃进手机、幻想、刺激或者熬夜”，也只是看见它。
        看见，不等于认同。
        看见，是自由的开始。
    """.trimIndent()),
    MeditationCue(335, "正念观察", """
        请问自己：
        我现在最想要的，其实是什么？
        是被爱？
        是被看见？
        是被选择？
        是被安慰？
        是自由？
        是休息？
        是尊重？
        还是只是想从痛苦里逃开一会儿？
    """.trimIndent()),
    MeditationCue(395, "正念观察", """
        你不需要马上回答得很准确。
        只需要诚实地靠近自己一点。
        这些念头和冲动，并不是凭空出现的。
        它们常常是在替一个受伤的自己说话。
        他说：
        我好饿。
        我好委屈。
        我好想被爱。
        我好想有人选择我。
    """.trimIndent()),
    MeditationCue(455, "正念观察", """
        现在，请再次回到呼吸。
        吸气。
        呼气。
        对自己说：
        我看见这些感受。
        但我不是这些感受本身。
        我可以有冲动。
        但我不需要被冲动带走。
        我可以有痛。
        但我不再抛弃自己。
    """.trimIndent()),
    MeditationCue(480, "内在小孩安抚", """
        现在，请把一只手轻轻放在胸口。
        如果愿意，也可以用另一只手轻轻放在腹部。
        这不是一个形式。
        这是一个信号。
        从现在开始，由成年后的你，来靠近那个小时候的自己。
    """.trimIndent()),
    MeditationCue(530, "内在小孩安抚", """
        请在心里想象小时候的自己。
        那个不敢表达需要、不敢哭、不敢麻烦别人的孩子。
        那个担心自己会添乱、会被责骂、会被抛下的孩子。
        那个以为只有优秀、听话、懂事、有用，才值得被爱的孩子。
    """.trimIndent()),
    MeditationCue(590, "内在小孩安抚", """
        你可以在心里慢慢对他说：
        我看见你了。
        你真的很委屈。
        你真的太早学会了害怕。
        你不是坏孩子。
        你不是麻烦。
        你不是不配。
        你只是太久没有被温柔地理解。
    """.trimIndent()),
    MeditationCue(650, "内在小孩安抚", """
        你很想被爱。
        你很想被选择。
        你很想被抱住。
        你很想有人主动喜欢你。
        你很想有人告诉你：
        你不用那么优秀，也值得被爱。
        这些愿望本身，并不可耻。
    """.trimIndent()),
    MeditationCue(715, "内在小孩安抚", """
        以前，你可能一直在向外面讨证明。
        向漂亮的人讨证明。
        向成绩讨证明。
        向工作讨证明。
        向别人的认可讨证明。
        但是今天，我们先不向外讨。
        今天，我这个成年人，先回来抱住你。
    """.trimIndent()),
    MeditationCue(780, "内在小孩安抚", """
        请在心里对他说：
        以后，不用你一个人冲出去讨爱了。
        不用你跪着证明自己。
        不用你低声下气地求别人选择你。
        她可以不喜欢我们。
        别人可以拒绝我们。
        但我不会因此抛弃你。
    """.trimIndent()),
    MeditationCue(840, "内在小孩安抚", """
        你可以哭。
        你可以委屈。
        你可以承认自己很饿。
        你可以承认自己很想被爱。
        但是我们不能再用伤害边界、伤害家庭、伤害自己前途的方式来救你。
        我现在是成年人。
        我会保护你。
        也会保护别人。
    """.trimIndent()),
    MeditationCue(900, "信念重构", """
        现在，我们进入信念重构。
        不是强迫自己相信空洞的正能量。
        而是温柔而清醒地改写那些伤害了你很多年的旧信念。
        如果身体一时还不相信，也没关系。
        我们只是每天种下一点新的证据。
    """.trimIndent()),
    MeditationCue(960, "信念重构", """
        旧信念说：
        我不配。
        新信念说：
        这是小时候缺爱、压抑和被拒绝留下的痛。
        它很真实。
        但它不是事实。
        我不需要完美，才有基本尊严。
    """.trimIndent()),
    MeditationCue(1020, "信念重构", """
        旧信念说：
        再也没有重要的人会真正选择我了。
        新信念说：
        我渴望被选择，这可以被理解。
        但我的价值，不由任何一个人的选择决定。
        一个人不选择我，只代表她没有选择这段关系。
        不代表我这个人没有价值。
    """.trimIndent()),
    MeditationCue(1085, "信念重构", """
        旧信念说：
        我永远比不上更优秀、更有资源的人。
        新信念说：
        现实条件会影响机会。
        但现实条件不决定我的基本尊严。
        我可以承认自己的自卑。
        但我不再用比较来处死自己。
    """.trimIndent()),
    MeditationCue(1150, "信念重构", """
        旧信念说：
        弱小就是没用。
        新信念说：
        弱小不是罪。
        有需求不是罪。
        不会赚钱、不会表现、不会讨好，也不是罪。
        弱小的生命需要保护。
        小时候的我，也曾经值得被保护。
    """.trimIndent()),
    MeditationCue(1215, "信念重构", """
        旧信念说：
        我必须偷偷索取。
        偷偷寻找刺激。
        偷偷幻想。
        偷偷熬夜。
        偷偷把属于自己的时间抢回来。
        新信念说：
        我可以正当地给自己爱、休息、自由和尊重。
        我不用再从黑暗里偷一点点活着的感觉。
    """.trimIndent()),
    MeditationCue(1280, "信念重构", """
        旧信念说：
        有欲望就很可耻。
        新信念说：
        欲望本身不可耻。
        但欲望不是命令。
        我可以有欲望。
        我可以看见欲望。
        我可以安放欲望。
        但我不让欲望越界。
    """.trimIndent()),
    MeditationCue(1320, "今日行动承诺", """
        现在，我们进入最后三分钟。
        请不要承诺很大的改变。
        今天只需要选择一个很小的行动。
        一个小到你真的可以做到的行动。
        改变不是靠豪言壮语。
        改变靠每天一点点新的选择。
    """.trimIndent()),
    MeditationCue(1365, "今日行动承诺", """
        你可以从下面这些行动里选择一个：
        今天，我不向外讨证明。
        今天，我不看侵犯他人边界的内容。
        今天，我不联系让我失控的人。
        今天，我给自己三十分钟正式的自由时间。
        今天，我睡前把手机放远。
        今天，家庭场景让我烦躁时，我先确保安全，再处理情绪。
    """.trimIndent()),
    MeditationCue(1425, "今日行动承诺", """
        请在心里选定一个。
        只选一个。
        然后轻轻告诉自己：
        今天，我不需要完美。
        今天，我只需要比昨天多一秒觉察。
        今天，我只需要在一个地方，把自己带回来。
    """.trimIndent()),
    MeditationCue(1470, "今日行动承诺", """
        现在，慢慢回到呼吸。
        感受身体。
        感受脚底。
        感受胸口。
        今天的练习即将结束。
        请对自己说：
        我看见你了。
        我不会再丢下你。
        今天，我又把自己带回家了一点。
    """.trimIndent())
)
