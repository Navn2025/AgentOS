package com.agentos.app

// AgentOS for Android — a native Kotlin / Jetpack Compose port of the React frontend, in a single file.
//
// Sections:  1 Theme · 2 Models & JSON · 3 HTTP API · 4 App state (ViewModel) · 5 Voice agent mode
//            6 UI primitives · 7 Auth · 8 Shell & navigation · 9 Pages · 10 Activity

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowOutward
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

// ───────────────────────────── 1 · Theme ─────────────────────────────

private val Ink = Color(0xFF17251F)
private val Muted = Color(0xFF7A837B)
private val Bg = Color(0xFFF6F7F4)
private val Line = Color(0xFFDCE2D8)
private val Soft = Color(0xFFEDF1E8)
private val Dark = Color(0xFF284B38)
private val Green = Color(0xFF43775C)
private val Leaf = Color(0xFFE9F0DF)
private val LeafText = Color(0xFF64844D)
private val Mint = Color(0xFFBFDBAB)
private val Danger = Color(0xFFB34545)
private val Amber = Color(0xFFF5E7C8)
private val AmberText = Color(0xFF8A5A00)
private val R8 = RoundedCornerShape(8.dp)
private val R14 = RoundedCornerShape(14.dp)
private val IST: ZoneId = ZoneId.of("Asia/Kolkata")

@Composable
fun AgentOSTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(primary = Dark, onPrimary = Color.White, background = Bg, surface = Color.White, onSurface = Ink, secondary = Green),
        content = content,
    )
}

// ───────────────────────────── 2 · Models & JSON ─────────────────────────────

private fun JSONObject.s(k: String, d: String = ""): String = if (!has(k) || isNull(k)) d else optString(k, d)
private fun JSONObject.sn(k: String): String? = if (!has(k) || isNull(k)) null else optString(k)
private fun JSONArray.objs(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
private fun obj(vararg pairs: Pair<String, Any?>) = JSONObject().apply { pairs.forEach { (k, v) -> put(k, v ?: JSONObject.NULL) } }
private fun Any?.asObj(): JSONObject = this as? JSONObject ?: JSONObject()
private fun Any?.asArr(): JSONArray = this as? JSONArray ?: JSONArray()
private fun Any?.clean(): Any? = if (this == null || this == JSONObject.NULL) null else this

data class User(val id: String, val name: String, val email: String) {
    companion object { fun from(o: JSONObject) = User(o.s("id"), o.s("name"), o.s("email")) }
}
data class Project(val id: String, val name: String, val description: String, val status: String, val version: Int, val taskCount: Int, val completedCount: Int) {
    val progress get() = if (taskCount > 0) completedCount.toFloat() / taskCount else 0f
    companion object { fun from(o: JSONObject) = Project(o.s("id"), o.s("name"), o.s("description"), o.s("status"), o.optInt("version", 1), o.optInt("task_count"), o.optInt("completed_count")) }
}
data class Task(val id: String, val projectId: String, val projectName: String, val title: String, val description: String, val status: String, val priority: String, val dueAt: String?, val version: Int) {
    companion object { fun from(o: JSONObject) = Task(o.s("id"), o.s("project_id"), o.s("project_name"), o.s("title"), o.s("description"), o.s("status"), o.s("priority"), o.sn("due_at"), o.optInt("version", 1)) }
}
data class Reminder(val id: String, val title: String, val taskId: String?, val taskTitle: String?, val remindAt: String, val state: String) {
    companion object { fun from(o: JSONObject) = Reminder(o.s("id"), o.s("title"), o.sn("task_id"), o.sn("task_title"), o.s("remind_at"), o.s("state")) }
}
data class Activity(val id: String, val entityType: String, val action: String, val detail: JSONObject, val createdAt: String) {
    companion object { fun from(o: JSONObject) = Activity(o.s("id"), o.s("entity_type"), o.s("action"), o.optJSONObject("detail") ?: JSONObject(), o.s("created_at")) }
}
data class Source(val title: String, val url: String)
data class Note(val id: String, val title: String, val content: String, val projectName: String, val createdAt: String, val sources: List<Source>) {
    companion object {
        fun from(o: JSONObject) = Note(o.s("id"), o.s("title"), o.s("content"), o.s("project_name"), o.s("created_at"),
            o.optJSONArray("sources").asArr().objs().map { Source(it.s("title"), it.s("url")) })
    }
}
data class Analytics(val total: Int, val completed: Int, val pending: Int, val overdue: Int, val activeProjects: Int) {
    companion object { fun from(o: JSONObject) = Analytics(o.optInt("total_tasks"), o.optInt("completed_tasks"), o.optInt("pending_tasks"), o.optInt("overdue_tasks"), o.optInt("active_projects")) }
}
data class Provider(val provider: String, val model: String, val configured: Boolean)
data class Config(val planner: Provider, val research: Provider, val laptop: Boolean, val gmail: Boolean) {
    companion object {
        private fun p(o: JSONObject?) = Provider(o?.s("provider") ?: "", o?.s("model") ?: "", o?.optBoolean("configured") ?: false)
        fun from(o: JSONObject) = Config(p(o.optJSONObject("planner")), p(o.optJSONObject("research")), o.optJSONObject("laptop") != null, o.optJSONObject("laptop")?.optBoolean("gmail") ?: false)
    }
}
data class Option(val id: String, val label: String, val detail: String)
data class Question(val kind: String, val message: String, val action: String?, val affectedTasks: Int, val options: List<Option>)
data class StepResult(val action: String, val data: Any?, val step: Int)
data class Run(
    val id: String, val conversationId: String, val input: String, val status: String, val steps: List<JSONObject>, val results: List<StepResult>,
    val question: Question?, val error: String?, val reply: String?, val startedAt: String,
) {
    val active get() = status in listOf("queued", "planning", "executing")
    companion object {
        fun from(o: JSONObject): Run {
            val q = o.optJSONObject("question")?.let {
                Question(it.s("kind"), it.s("message"), it.sn("action"), it.optInt("affected_tasks"),
                    it.optJSONArray("options").asArr().objs().map { op -> Option(op.s("id"), op.s("label"), op.s("detail")) })
            }
            return Run(o.s("id"), o.s("conversation_id"), o.s("input"), o.s("status"), o.optJSONArray("steps").asArr().objs(),
                o.optJSONArray("results").asArr().objs().map { StepResult(it.s("action"), it.opt("data").clean(), it.optInt("step")) },
                q, o.sn("error"), o.sn("reply"), o.s("started_at"))
        }
    }
}
data class Conversation(val id: String, val title: String, val createdAt: String)
data class Message(val id: String, val role: String, val content: String)
data class Template(val id: String, val name: String, val concept: String, val sql: String)
data class SchemaInfo(val columns: List<JSONObject>, val constraints: List<JSONObject>)

private fun parseInstant(v: String?): Instant? = if (v.isNullOrBlank()) null else
    runCatching { OffsetDateTime.parse(v).toInstant() }.recoverCatching { LocalDateTime.parse(v).toInstant(ZoneOffset.UTC) }.getOrNull()
private val longFmt = DateTimeFormatter.ofPattern("d MMM, hh:mm a", Locale("en", "IN")).withZone(IST)
private val shortFmt = DateTimeFormatter.ofPattern("d MMM", Locale("en", "IN")).withZone(IST)
private fun fmtDate(v: String?, short: Boolean = false): String =
    parseInstant(v)?.let { (if (short) shortFmt else longFmt).format(it) } ?: "No deadline"
private fun isPast(v: String?) = parseInstant(v)?.isBefore(Instant.now()) == true
private fun label(v: String) = v.replace('_', ' ').replaceFirstChar { it.uppercase() }
private fun toLocal(iso: String?): LocalDateTime? = parseInstant(iso)?.atZone(IST)?.toLocalDateTime()
private fun toUtc(v: LocalDateTime?): Any? = v?.atZone(IST)?.toInstant()?.toString()
private fun cell(v: Any?): String = when (val x = v.clean()) { null -> "—"; else -> x.toString() }

// ───────────────────────────── 3 · HTTP API ─────────────────────────────

object Http {
    var baseUrl = "http://10.0.2.2:8000"
    var token = ""
    private val json = "application/json".toMediaType()
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS).build()

    /** Returns the parsed JSON body (JSONObject / JSONArray) or throws Exception(message) with the server's detail text. */
    suspend fun call(path: String, method: String = "GET", body: JSONObject? = null): Any? = withContext(Dispatchers.IO) {
        val builder = try { Request.Builder().url(baseUrl.trimEnd('/') + "/api" + path) } catch (e: IllegalArgumentException) { throw Exception("Enter a valid server address.") }
        if (token.isNotEmpty()) builder.header("Authorization", "Bearer $token")
        val payload = when {
            method == "GET" || method == "HEAD" -> null
            method == "DELETE" && body == null -> null
            else -> (body?.toString() ?: "").toRequestBody(json)
        }
        try {
            client.newCall(builder.method(method, payload).build()).execute().use { res ->
                val text = res.body?.string().orEmpty()
                val data = runCatching { JSONTokener(text).nextValue() }.getOrNull().takeIf { it is JSONObject || it is JSONArray }
                if (!res.isSuccessful) throw Exception(errorDetail(data) ?: "Request failed (${res.code})")
                data ?: throw Exception("The server returned an unreadable response.")
            }
        } catch (e: IOException) {
            throw Exception("Cannot reach the server at $baseUrl. Check the address and that the backend is running.")
        }
    }

    private fun errorDetail(data: Any?): String? {
        val d = (data as? JSONObject)?.opt("detail").clean() ?: return null
        return if (d is JSONArray) (0 until d.length()).joinToString(". ") { d.optJSONObject(it)?.s("msg") ?: d.optString(it) } else d.toString()
    }

    /** Whisper-style upload kept for parity with the web client's transcribe(); the app uses on-device recognition instead. */
    suspend fun transcribe(audio: ByteArray, mime: String = "audio/wav"): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(baseUrl.trimEnd('/') + "/api/transcribe")
            .apply { if (token.isNotEmpty()) header("Authorization", "Bearer $token") }
            .post(audio.toRequestBody(mime.toMediaType())).build()
        client.newCall(req).execute().use { res ->
            val data = runCatching { JSONObject(res.body?.string().orEmpty()) }.getOrDefault(JSONObject())
            if (!res.isSuccessful) throw Exception(data.s("detail", "Transcription failed (${res.code})"))
            data.s("text")
        }
    }
}

// ───────────────────────────── 4 · App state ─────────────────────────────

enum class Page(val title: String, val icon: ImageVector) {
    Overview("Overview", Icons.Filled.Dashboard), Workspace("AI Workspace", Icons.Filled.AutoAwesome), Projects("Projects", Icons.Filled.Folder),
    Tasks("Tasks", Icons.Filled.FormatListBulleted), Reminders("Reminders", Icons.Filled.Notifications), Research("Research", Icons.Filled.Search),
    Database("Database Explorer", Icons.Filled.Storage), Runs("Agent Runs", Icons.Filled.AccountTree),
}

class AppModel : ViewModel() {
    private var prefs: android.content.SharedPreferences? = null
    var serverUrl by mutableStateOf(Http.baseUrl)
    var savedEmail by mutableStateOf("")
    var user by mutableStateOf<User?>(null)
    var page by mutableStateOf(Page.Overview)
    var taskProject by mutableStateOf("")
    var search by mutableStateOf("")
    var toast by mutableStateOf<String?>(null)

    var projects by mutableStateOf(listOf<Project>())
    var tasks by mutableStateOf(listOf<Task>())
    var reminders by mutableStateOf(listOf<Reminder>())
    var analytics by mutableStateOf<Analytics?>(null)
    var activity by mutableStateOf(listOf<Activity>())
    var notes by mutableStateOf(listOf<Note>())
    var config by mutableStateOf<Config?>(null)
    var runs by mutableStateOf(listOf<Run>())
    var loading by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)

    fun init(p: android.content.SharedPreferences) {
        if (prefs != null) return
        prefs = p
        serverUrl = p.getString("server", serverUrl) ?: serverUrl
        savedEmail = p.getString("email", "") ?: ""
    }

    suspend fun authenticate(register: Boolean, name: String, email: String, password: String) {
        var url = serverUrl.trim().trimEnd('/')
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "http://$url"
        Http.baseUrl = url
        val body = obj("email" to email.trim(), "password" to password).also { if (register) it.put("name", name.trim()) }
        val res = Http.call("/auth/" + if (register) "register" else "login", "POST", body).asObj()
        Http.token = res.s("access_token")
        prefs?.edit()?.putString("server", url)?.putString("email", email.trim())?.apply()
        serverUrl = url; savedEmail = email.trim()
        clearData()
        user = User.from(res.getJSONObject("user"))
    }

    fun logout() { Http.token = ""; clearData(); user = null; page = Page.Overview }

    private fun clearData() {
        projects = emptyList(); tasks = emptyList(); reminders = emptyList(); analytics = null; activity = emptyList()
        notes = emptyList(); config = null; runs = emptyList(); loading = true; error = null; search = ""; taskProject = ""
    }

    suspend fun refreshAll() = coroutineScope {
        fun load(path: String) = async { runCatching { Http.call(path) } }
        val p = load("/projects"); val t = load("/tasks"); val r = load("/reminders"); val a = load("/analytics")
        val ac = load("/db-activity"); val n = load("/research-notes"); val c = load("/config"); val ru = load("/agent-runs")
        val pr = p.await(); val tr = t.await()
        pr.getOrNull()?.let { v -> projects = v.asArr().objs().map(Project::from) }
        tr.getOrNull()?.let { v -> tasks = v.asArr().objs().map(Task::from) }
        r.await().getOrNull()?.let { v -> reminders = v.asArr().objs().map(Reminder::from) }
        a.await().getOrNull()?.let { v -> analytics = Analytics.from(v.asObj()) }
        ac.await().getOrNull()?.let { v -> activity = v.asArr().objs().map(Activity::from) }
        n.await().getOrNull()?.let { v -> notes = v.asArr().objs().map(Note::from) }
        c.await().getOrNull()?.let { v -> config = Config.from(v.asObj()) }
        ru.await().getOrNull()?.let { v -> runs = v.asArr().objs().map(Run::from) }
        error = pr.exceptionOrNull()?.message ?: tr.exceptionOrNull()?.message
        loading = false
    }

    /** Mutating call that refreshes the workspace; returns an error message, or null on success. */
    suspend fun act(path: String, method: String = "POST", body: JSONObject? = null): String? = try {
        Http.call(path, method, body)
        refreshAll()
        toast = "Changes saved to your workspace."
        null
    } catch (e: Exception) { toast = e.message; e.message ?: "Request failed" }
}

// ───────────────────────────── 5 · Voice agent mode ─────────────────────────────

enum class Phase(val text: String) {
    Off("Off"), Listening("Listening for “Hey Agent”"), Command("Listening… say your request"), Thinking("Working on it…"),
    Permission("Waiting for your permission"), Choice("Waiting for your choice"), Speaking("Speaking"),
}

// Speech engines often hear "hey agent" as "hey agents", "hi agent" or "a agent".
private val WAKE = Regex("""(?:\b(?:hey|hi|hay|hai|ok|okay|a)[\s,]+agent(?:s|os| os)?\b|^\s*agent(?:os| os)?\b)[\s,.!?:-]*""", RegexOption.IGNORE_CASE)
private val NO = Regex("""\b(no|nope|nah|don'?t|do not|deny|cancel|stop|never ?mind|not now|reject)\b""", RegexOption.IGNORE_CASE)
private val YES = Regex("""\b(yes|yeah|yep|yup|allow|allowed|go ahead|sure|ok|okay|do it|proceed|confirm|approve|haan)\b""", RegexOption.IGNORE_CASE)
// Ordinals win over numbers so "the second one" means 2, not 1.
private val ORDINALS = listOf("first", "second", "third", "fourth", "fifth", "sixth", "seventh", "eighth").mapIndexed { i, w -> Regex("""\b($w|${i + 1}(st|nd|rd|th))\b""", RegexOption.IGNORE_CASE) }
private val NUMBERS = listOf("one", "two", "three", "four", "five", "six", "seven", "eight").mapIndexed { i, w -> Regex("""\b($w|${i + 1})\b""", RegexOption.IGNORE_CASE) }
private val SLEEP = Regex("""^(stop listening|turn off|go to sleep|sleep|goodbye|bye)\b""", RegexOption.IGNORE_CASE)

/** Text after the wake word, "" for the wake word alone, or null when it was not said. */
fun parseWake(text: String): String? = WAKE.find(text)?.let { text.substring(it.range.last + 1).trim() }
fun parseDecision(text: String): Boolean? = if (NO.containsMatchIn(text)) false else if (YES.containsMatchIn(text)) true else null
fun parseChoice(text: String, count: Int): Int? {
    if (Regex("""\blast\b""", RegexOption.IGNORE_CASE).containsMatchIn(text)) return count - 1
    val ordinal = ORDINALS.indexOfFirst { it.containsMatchIn(text) }
    val index = if (ordinal >= 0) ordinal else NUMBERS.indexOfFirst { it.containsMatchIn(text) }
    return if (index in 0 until count) index else null
}
fun speakable(text: String) = text.replace(Regex("[*_#`>|]"), "").replace(Regex("""https?://\S+"""), "the link").replace(Regex("\\s+"), " ").trim()
private fun chunks(text: String): List<String> =
    Regex("""[^.!?]+[.!?]*\s*""").findAll(speakable(text)).map { it.value }.fold(mutableListOf<String>()) { all, part ->
        if (all.isNotEmpty() && (all.last() + part).length < 180) all[all.lastIndex] = all.last() + part else all.add(part)
        all
    }

/** Always-on "Hey Agent" voice mode: Android SpeechRecognizer for the ear, TextToSpeech for the voice. All calls run on the main thread. */
class AgentMode(private val app: Context, private val vm: AppModel, private val scope: CoroutineScope) {
    var phase by mutableStateOf(Phase.Off); private set
    var heard by mutableStateOf(""); private set
    var command by mutableStateOf(""); private set
    var run by mutableStateOf<Run?>(null); private set
    var reply by mutableStateOf(""); private set
    var error by mutableStateOf("")
    var hearing by mutableStateOf(false); private set
    var talking by mutableStateOf(false); private set
    val enabled get() = phase != Phase.Off

    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var recognizing = false
    private var directNext = false
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var speechToken = 0
    private var lastUtterance = ""
    private var doneCallback: (() -> Unit)? = null
    private var conversation: String? = null
    private var commandTimer: Runnable? = null

    init {
        tts = TextToSpeech(app) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) tts?.let { if (it.setLanguage(Locale("en", "IN")) < 0) it.setLanguage(Locale.US) }
        }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) { if (id == lastUtterance) main.post { doneCallback?.invoke() } }
            @Deprecated("Deprecated in Java") override fun onError(id: String?) { if (id == lastUtterance) main.post { doneCallback?.invoke() } }
        })
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(p: Bundle?) {}
        override fun onBeginningOfSpeech() { hearing = true }
        override fun onRmsChanged(rms: Float) {}
        override fun onBufferReceived(b: ByteArray?) {}
        override fun onEndOfSpeech() { hearing = false }
        override fun onPartialResults(p: Bundle?) {}
        override fun onEvent(t: Int, p: Bundle?) {}
        override fun onResults(results: Bundle?) {
            recognizing = false; hearing = false
            val direct = directNext; directNext = false
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty().trim()
            if (text.isNotEmpty() && phase != Phase.Off) { heard = text; handle(text, direct) }
            main.postDelayed({ listen() }, 250)
        }
        override fun onError(code: Int) {
            recognizing = false; hearing = false; directNext = false
            when (code) {
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> { error = "Microphone permission was denied. Allow the microphone for AgentOS in system settings, then turn agent mode on again."; disable(keepError = true) }
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> main.postDelayed({ listen() }, 200)
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> { recognizer?.cancel(); main.postDelayed({ listen() }, 700) }
                else -> main.postDelayed({ listen() }, 1200)
            }
        }
    }

    private fun listen() {
        if (phase == Phase.Off || phase == Phase.Speaking || phase == Phase.Thinking || recognizing) return
        if (recognizer == null) {
            if (!SpeechRecognizer.isRecognitionAvailable(app)) { error = "Speech recognition is not available on this device."; disable(keepError = true); return }
            recognizer = SpeechRecognizer.createSpeechRecognizer(app).also { it.setRecognitionListener(listener) }
        }
        recognizing = true
        recognizer?.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, app.packageName))
    }

    private fun pause() { recognizer?.cancel(); recognizing = false; hearing = false }

    private suspend fun speak(text: String, next: Phase) {
        pause(); phase = Phase.Speaking
        val token = ++speechToken
        val parts = chunks(text)
        suspendCancellableCoroutine { cont ->
            var finished = false
            fun done() {
                if (finished) return
                finished = true
                if (token == speechToken && phase == Phase.Speaking) { phase = next; listen() }
                if (cont.isActive) cont.resume(Unit)
            }
            doneCallback = ::done
            val engine = tts
            if (engine == null || !ttsReady || parts.isEmpty()) { done(); return@suspendCancellableCoroutine }
            engine.stop()
            // Some engines never fire onDone, hence the timer.
            main.postDelayed({ done() }, text.length * 90L + 4000)
            parts.forEachIndexed { i, part ->
                lastUtterance = "agent-$token-$i"
                engine.speak(part, TextToSpeech.QUEUE_ADD, null, lastUtterance)
            }
        }
    }

    private suspend fun settle(current: Run) {
        var latest = current
        while (latest.active) {
            if (phase == Phase.Off) return
            latest = try { Run.from(Http.call("/agent-runs/${latest.id}/wait?timeout=25").asObj()) } catch (e: Exception) { speak(e.message ?: "Something went wrong.", Phase.Listening); return }
            run = latest
        }
        if (phase == Phase.Off) return
        val q = latest.question
        if (latest.status == "awaiting_input" && q?.kind == "approval") {
            speak("I need your permission. ${q.message} Say yes or no.", Phase.Permission)
        } else if (latest.status == "awaiting_input" && q?.kind == "selection") {
            val options = q.options.mapIndexed { i, o -> "${i + 1}: ${o.label}" }.joinToString(". ")
            speak("${q.message} $options. Say the number.", Phase.Choice)
        } else {
            vm.refreshAll()
            val answer = if (latest.status == "failed") latest.error ?: "Sorry, that did not work." else latest.reply ?: "Done."
            reply = answer; speak(answer, Phase.Listening)
        }
    }

    fun submit(text: String) {
        commandTimer?.let { main.removeCallbacks(it) }
        scope.launch {
            if (SLEEP.containsMatchIn(text)) { speak("Okay, agent mode is off.", Phase.Listening); disable(); return@launch }
            pause(); phase = Phase.Thinking; command = text; reply = ""; error = ""; run = null
            try {
                val created = Run.from(Http.call("/commands", "POST", obj("input" to text, "conversation_id" to conversation)).asObj())
                conversation = created.conversationId; run = created
                settle(created)
            } catch (e: Exception) { speak(e.message ?: "Something went wrong.", Phase.Listening) }
        }
    }

    fun answer(approved: Boolean? = null, selectedId: String? = null) {
        val current = run ?: return
        scope.launch {
            pause(); phase = Phase.Thinking
            try {
                val body = JSONObject().also { b -> approved?.let { b.put("approved", it) }; selectedId?.let { b.put("selected_id", it) } }
                Http.call("/agent-runs/${current.id}/resume", "POST", body)
                settle(current.copy(status = "executing", question = null))
            } catch (e: Exception) { speak(e.message ?: "Something went wrong.", Phase.Listening) }
        }
    }

    private fun cancel() {
        val current = run ?: return
        scope.launch {
            try { Http.call("/agent-runs/${current.id}/cancel", "POST"); vm.refreshAll(); speak("Okay, cancelled.", Phase.Listening) }
            catch (e: Exception) { speak(e.message ?: "Something went wrong.", Phase.Listening) }
        }
    }

    private fun handle(text: String, direct: Boolean) {
        when (phase) {
            Phase.Listening -> {
                // Push-to-talk speech is addressed to the agent, so it needs no wake word.
                val said = (if (direct) parseWake(text) ?: text else parseWake(text)) ?: return
                if (said.isNotEmpty()) { submit(said); return }
                scope.launch {
                    speak("Yes?", Phase.Command)
                    val timer = Runnable { if (phase == Phase.Command) phase = Phase.Listening }
                    commandTimer = timer; main.postDelayed(timer, 9000)
                }
            }
            Phase.Command -> submit(parseWake(text)?.ifEmpty { null } ?: text)
            Phase.Permission -> {
                val decision = parseDecision(text)
                if (decision == null) scope.launch { speak("Please say yes or no.", Phase.Permission) } else answer(approved = decision)
            }
            Phase.Choice -> {
                val options = run?.question?.options.orEmpty()
                val index = parseChoice(text, options.size)
                if (index == null && NO.containsMatchIn(text)) cancel()
                else if (index == null) scope.launch { speak("Say a number from 1 to ${options.size}.", Phase.Choice) }
                else answer(selectedId = options[index].id)
            }
            else -> {}
        }
    }

    fun enable() {
        error = ""; phase = Phase.Listening
        // Speech output starts only after the user's tap; speak() hands control back to listening.
        scope.launch { speak("Agent mode is on. Say hey agent, then your request.", Phase.Listening) }
    }

    fun disable(keepError: Boolean = false) {
        commandTimer?.let { main.removeCallbacks(it) }
        tts?.stop(); doneCallback?.invoke(); doneCallback = null
        recognizer?.destroy(); recognizer = null; recognizing = false
        hearing = false; talking = false; heard = ""; phase = Phase.Off
        if (!keepError) error = ""
    }

    fun silence() { tts?.stop(); doneCallback?.invoke() }

    /** Push-to-talk: interrupts speech and records until released, no wake word needed. */
    fun talkStart() {
        if (phase == Phase.Off || phase == Phase.Thinking) return
        if (phase == Phase.Speaking) {
            speechToken++; tts?.stop()
            phase = if (run?.status == "awaiting_input") (if (run?.question?.kind == "selection") Phase.Choice else Phase.Permission) else Phase.Listening
        }
        talking = true; directNext = true
        pause(); listen()
    }

    fun talkEnd() { talking = false; recognizer?.stopListening() }

    fun destroy() { disable(); tts?.shutdown(); tts = null }
}

// ───────────────────────────── 6 · UI primitives ─────────────────────────────

@Composable
fun T(
    text: String, modifier: Modifier = Modifier, size: Int = 14, color: Color = Ink, weight: FontWeight? = null, maxLines: Int = Int.MAX_VALUE,
    deco: TextDecoration? = null, mono: Boolean = false, lineHeight: TextUnit = TextUnit.Unspecified, align: TextAlign? = null, spacing: TextUnit = TextUnit.Unspecified,
) = Text(text, modifier, color, size.sp, fontWeight = weight, fontFamily = if (mono) FontFamily.Monospace else null, letterSpacing = spacing,
    textDecoration = deco, textAlign = align, lineHeight = lineHeight, overflow = TextOverflow.Ellipsis, maxLines = maxLines)

@Composable
fun Eyebrow(text: String) = T(text.uppercase(), size = 10, color = Color(0xFF778476), weight = FontWeight.SemiBold, spacing = 1.7.sp)

@Composable
fun Panel(modifier: Modifier = Modifier, color: Color = Color.White, pad: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) =
    Column(modifier.fillMaxWidth().clip(R14).background(color).border(1.dp, Line, R14).padding(pad), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)

@Composable
fun PanelHeading(title: String, subtitle: String? = null, trailing: @Composable () -> Unit = {}) =
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { T(title, size = 17, weight = FontWeight.SemiBold); if (subtitle != null) T(subtitle, size = 12, color = Muted) }
        trailing()
    }

@Composable
fun PageHeading(eyebrow: String, title: String, description: String, actions: @Composable () -> Unit = {}) =
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Eyebrow(eyebrow)
        T(title, size = 28, weight = FontWeight.SemiBold)
        T(description, size = 13, color = Muted, lineHeight = 20.sp)
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { actions() }
    }

enum class BK { Primary, Secondary, Danger, Text }

@Composable
fun Btn(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, kind: BK = BK.Primary, icon: ImageVector? = null, enabled: Boolean = true) {
    val bg = when (kind) { BK.Primary -> Dark; BK.Secondary -> Color.White; BK.Danger -> Danger; BK.Text -> Color.Transparent }
    val fg = when (kind) { BK.Primary, BK.Danger -> Color.White; BK.Secondary -> Ink; BK.Text -> Color(0xFF47644B) }
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier.clip(shape).background(if (enabled) bg else bg.copy(alpha = .5f)).then(if (kind == BK.Secondary) Modifier.border(1.dp, Line, shape) else Modifier)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = if (kind == BK.Text) 6.dp else 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(16.dp), tint = fg); Spacer(Modifier.width(8.dp)) }
        T(text, size = 13, color = if (enabled) fg else fg.copy(alpha = .7f), weight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
fun IconBtn(icon: ImageVector, desc: String, onClick: () -> Unit, tint: Color = Muted, enabled: Boolean = true, size: Int = 18) =
    Box(Modifier.size(36.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, desc, Modifier.size(size.dp), tint = if (enabled) tint else tint.copy(alpha = .5f))
    }

@Composable
fun Badge(value: String) {
    val (bg, fg) = when (value) {
        "completed", "active", "low", "insert", "confirmed" -> Color(0xFFE3F0E3) to Color(0xFF2F6B3F)
        "pending", "medium", "awaiting_input", "scheduled" -> Amber to AmberText
        "in_progress", "executing", "planning", "queued", "update" -> Color(0xFFE2ECF7) to Color(0xFF2F5C8F)
        "high", "failed", "overdue", "delete" -> Color(0xFFFBE4E1) to Color(0xFFA33B3B)
        else -> Soft to Color(0xFF5E6B61)
    }
    Text(label(value), Modifier.clip(RoundedCornerShape(99.dp)).background(bg).padding(horizontal = 9.dp, vertical = 3.dp), fg, 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
}

@Composable
fun Empty(title: String, description: String, action: @Composable () -> Unit = {}) =
    Column(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(54.dp).clip(CircleShape).background(Soft), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Inbox, null, tint = Green, modifier = Modifier.size(26.dp)) }
        T(title, size = 16, weight = FontWeight.SemiBold, align = TextAlign.Center)
        T(description, size = 13, color = Muted, align = TextAlign.Center)
        action()
    }

@Composable
fun Loading() = Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
    CircularProgressIndicator(Modifier.size(20.dp), color = Green, strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)); T("Loading your workspace…", color = Muted)
}

@Composable
fun ErrorBox(message: String?) {
    if (!message.isNullOrBlank()) T(message, Modifier.fillMaxWidth().clip(R8).background(Color(0xFFFBECEC)).padding(10.dp), 13, Color(0xFFA33B3B))
}

@Composable
fun InfoStrip(icon: ImageVector, text: String, trailing: @Composable () -> Unit = {}) =
    Row(Modifier.fillMaxWidth().clip(R8).background(Leaf).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, null, Modifier.size(17.dp), tint = LeafText); T(text, Modifier.weight(1f), 12, Color(0xFF3F5A33)); trailing()
    }

@Composable
fun Pill(text: String) = T(text, Modifier.clip(RoundedCornerShape(99.dp)).background(Soft).padding(horizontal = 9.dp, vertical = 3.dp), 10, Color(0xFF5E6B61), FontWeight.SemiBold, spacing = 1.sp)

@Composable
fun Tabs(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) =
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (id, name) ->
            val sel = id == selected
            T(name, Modifier.clip(RoundedCornerShape(99.dp)).background(if (sel) Dark else Color.White).border(1.dp, if (sel) Dark else Line, RoundedCornerShape(99.dp))
                .clickable { onSelect(id) }.padding(horizontal = 14.dp, vertical = 8.dp), 12, if (sel) Color.White else Ink, FontWeight.Medium, maxLines = 1)
        }
    }

@Composable
fun FieldLabel(text: String, hint: String? = null) = Row(Modifier.padding(bottom = 6.dp)) {
    T(text, size = 13, weight = FontWeight.Medium); if (hint != null) T("  $hint", size = 11, color = Muted)
}

@Composable
fun Field(
    label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, placeholder: String = "", singleLine: Boolean = true, minLines: Int = 1,
    password: Boolean = false, keyboard: KeyboardType = KeyboardType.Text, enabled: Boolean = true, hint: String? = null, maxLength: Int = Int.MAX_VALUE,
) = Column(modifier) {
    FieldLabel(label, hint)
    OutlinedTextField(
        value, { if (it.length <= maxLength) onChange(it) }, Modifier.fillMaxWidth(), enabled = enabled, singleLine = singleLine, minLines = minLines,
        placeholder = { T(placeholder, color = Color(0xFFA6AEA7)) }, shape = R8,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Green, unfocusedBorderColor = Line, focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White, disabledContainerColor = Soft, disabledTextColor = Muted, focusedTextColor = Ink, unfocusedTextColor = Ink),
    )
}

@Composable
fun Select(label: String?, value: String, options: List<Pair<String, String>>, onChange: (String) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    var open by remember { mutableStateOf(false) }
    Column(modifier) {
        if (label != null) FieldLabel(label)
        Box {
            Row(Modifier.fillMaxWidth().clip(R8).background(if (enabled) Color.White else Soft).border(1.dp, Line, R8).clickable(enabled = enabled) { open = true }
                .padding(start = 13.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                T(options.firstOrNull { it.first == value }?.second ?: "", Modifier.weight(1f), 14, maxLines = 1)
                Icon(Icons.Filled.ArrowDropDown, null, tint = Muted)
            }
            DropdownMenu(open, { open = false }) {
                options.forEach { (id, name) -> DropdownMenuItem(text = { T(name) }, onClick = { onChange(id); open = false }) }
            }
        }
    }
}

/** Date + time picker in IST, built on the platform dialogs. */
@Composable
fun DateTimeField(label: String, value: LocalDateTime?, onChange: (LocalDateTime?) -> Unit, clearable: Boolean = false) {
    val ctx = LocalContext.current
    Column {
        FieldLabel(label)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).clip(R8).background(Color.White).border(1.dp, Line, R8).clickable {
                val base = value ?: LocalDateTime.now(IST)
                DatePickerDialog(ctx, { _, y, m, d ->
                    TimePickerDialog(ctx, { _, h, min -> onChange(LocalDateTime.of(y, m + 1, d, h, min)) }, base.hour, base.minute, false).show()
                }, base.year, base.monthValue - 1, base.dayOfMonth).show()
            }.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Schedule, null, Modifier.size(16.dp), tint = Muted); Spacer(Modifier.width(8.dp))
                T(value?.format(DateTimeFormatter.ofPattern("d MMM yyyy, hh:mm a", Locale.ENGLISH)) ?: "Choose date and time", color = if (value == null) Muted else Ink)
            }
            if (clearable && value != null) IconBtn(Icons.Filled.Close, "Clear date", { onChange(null) })
        }
    }
}

/** Centered modal card with a title, description and scrollable body. */
@Composable
fun AppDialog(title: String, description: String?, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) =
    Dialog(onClose, DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.padding(horizontal = 14.dp).fillMaxWidth().heightIn(max = 640.dp), shape = R14, color = Color.White) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T(title, Modifier.weight(1f), 20, weight = FontWeight.SemiBold)
                    IconBtn(Icons.Filled.Close, "Close dialog", onClose)
                }
                if (description != null) T(description, size = 13, color = Muted, lineHeight = 20.sp)
                content()
            }
        }
    }

@Composable
fun ResultTable(rows: List<JSONObject>) {
    if (rows.isEmpty()) { T("No matching records.", color = Muted, size = 13); return }
    val columns = rows.first().keys().asSequence().filter { it !in listOf("password_hash", "user_id") }.toList()
    Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).clip(R8).border(1.dp, Line, R8)) {
        Row(Modifier.background(Soft)) { columns.forEach { T(label(it), Modifier.width(150.dp).padding(10.dp), 11, Color(0xFF5E6B61), FontWeight.SemiBold, maxLines = 1) } }
        rows.forEach { row ->
            Row(Modifier.border(0.5.dp, Soft)) { columns.forEach { T(cell(row.opt(it)), Modifier.width(150.dp).padding(10.dp), 12, maxLines = 4) } }
        }
    }
}

@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier) =
    LinearProgressIndicator(progress = { fraction.coerceIn(0f, 1f) }, modifier.fillMaxWidth().height(6.dp).clip(CircleShape), color = Green, trackColor = Soft, strokeCap = StrokeCap.Round)

@Composable
fun IconTile(icon: ImageVector, tint: Color = LeafText, bg: Color = Leaf, size: Int = 38) =
    Box(Modifier.size(size.dp).clip(RoundedCornerShape(9.dp)).background(bg), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size((size / 2).dp), tint = tint) }

@Composable
fun PageColumn(content: @Composable ColumnScope.() -> Unit) =
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        content()
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            T("AgentOS · A little clarity, every day.", size = 11, color = Muted); T("Asia/Kolkata", size = 11, color = Muted)
        }
    }

@Composable
fun TaskRow(task: Task, vm: AppModel, onEdit: ((Task) -> Unit)? = null) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val done = task.status == "completed"
    val overdue = !done && isPast(task.dueAt)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconBtn(if (done) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked, (if (done) "Reopen " else "Complete ") + task.title, {
            scope.launch { busy = true; vm.act("/tasks/${task.id}", "PATCH", obj("version" to task.version, "status" to if (done) "pending" else "completed")); busy = false }
        }, tint = if (done) Green else Color(0xFFB5BDB4), enabled = !busy, size = 22)
        Column(Modifier.weight(1f).clickable(enabled = onEdit != null) { onEdit?.invoke(task) }.padding(vertical = 6.dp)) {
            T(task.title, size = 14, weight = FontWeight.Medium, deco = if (done) TextDecoration.LineThrough else null, color = if (done) Muted else Ink, maxLines = 2)
            T(task.projectName, size = 11, color = Muted, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Badge(task.priority)
            T(fmtDate(task.dueAt, true), size = 11, color = if (overdue) Danger else Muted)
        }
    }
}

@Composable
fun DeleteDialog(kind: String, id: String, title: String, extra: String, vm: AppModel, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AppDialog("Delete $kind?", "“$title” will be permanently deleted.$extra This cannot be undone.", onClose) {
        ErrorBox(error)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Btn("Keep $kind", onClose, Modifier.weight(1f), BK.Secondary)
            Btn(if (busy) "Deleting…" else "Delete", {
                scope.launch { busy = true; error = vm.act("/${kind}s/$id?confirmed=true", "DELETE"); busy = false; if (error == null) onClose() }
            }, Modifier.weight(1f), BK.Danger, Icons.Filled.Delete, !busy)
        }
    }
}

@Composable
fun GmailConnect(vm: AppModel, onConnected: () -> Unit = {}) {
    var open by remember { mutableStateOf(false) }
    Btn("Connect Gmail", { open = true }, kind = BK.Secondary, icon = Icons.Filled.Mail)
    if (open) {
        val scope = rememberCoroutineScope()
        val uri = LocalUriHandler.current
        var address by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        AppDialog("Connect Gmail", "AgentOS reads your inbox read-only (messages stay unread) and only after you allow each request.", { open = false }) {
            T("1. Turn on 2-Step Verification for your Google account.", size = 12, color = Color(0xFF4B584F))
            T("2. Open myaccount.google.com/apppasswords, create a password named “AgentOS”, and copy the 16 letters.", size = 12, color = Color(0xFF4B584F))
            Btn("Open app passwords page", { uri.openUri("https://myaccount.google.com/apppasswords") }, kind = BK.Text, icon = Icons.Filled.OpenInNew)
            Field("Gmail address", address, { address = it }, placeholder = "you@gmail.com", keyboard = KeyboardType.Email)
            Field("App Password", password, { password = it }, placeholder = "abcd efgh ijkl mnop", password = true)
            ErrorBox(error)
            Btn(if (busy) "Checking with Google…" else "Connect", {
                scope.launch {
                    busy = true; error = null
                    try { Http.call("/laptop/gmail", "POST", obj("address" to address.trim(), "app_password" to password)); vm.refreshAll(); open = false; onConnected() }
                    catch (e: Exception) { error = e.message }
                    busy = false
                }
            }, Modifier.fillMaxWidth(), enabled = !busy && address.isNotBlank() && password.length >= 16)
        }
    }
}

// ───────────────────────────── 7 · Auth ─────────────────────────────

@Composable
fun AuthScreen(vm: AppModel) {
    val scope = rememberCoroutineScope()
    var register by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf(vm.savedEmail) }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(Bg).safeDrawingPadding().imePadding().verticalScroll(rememberScrollState())) {
        Column(Modifier.fillMaxWidth().background(Dark).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(35.dp).clip(R8).background(Mint), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Hub, null, tint = Color(0xFF293D2A)) }
                Spacer(Modifier.width(10.dp)); T("Agent", size = 22, color = Color.White, weight = FontWeight.SemiBold); T("OS", size = 22, color = Color(0xFF99B69B), weight = FontWeight.SemiBold)
            }
            T("LESS FRICTION. MORE FOCUS.", size = 10, color = Color(0xFFA8B4A5), spacing = 1.7.sp, weight = FontWeight.SemiBold)
            T("Your ideas.\nYour projects.\nOne workspace.", size = 32, color = Color.White, weight = FontWeight.SemiBold, lineHeight = 38.sp)
            T("Turn a thought into a plan. Keep your tasks, research, and deadlines connected—with a little help from AI.", size = 13, color = Color(0xFFC9D6C8), lineHeight = 20.sp)
            listOf("Connected projects & tasks", "A real PostgreSQL foundation", "OpenRouter + Groq intelligence").forEach {
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.CheckCircle, null, Modifier.size(16.dp), tint = Mint); Spacer(Modifier.width(8.dp)); T(it, size = 13, color = Color(0xFFE2EBE0)) }
            }
        }
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Eyebrow("YOUR SPACE TO GET THINGS DONE")
            T(if (register) "Start something good." else "Welcome back.", size = 26, weight = FontWeight.SemiBold)
            T(if (register) "Create an account and make this workspace yours." else "Sign in to pick up where you left off.", size = 13, color = Muted)
            Field("Server address", vm.serverUrl, { vm.serverUrl = it }, placeholder = "http://10.0.2.2:8000", keyboard = KeyboardType.Uri, hint = "AgentOS backend")
            if (register) Field("Your name", name, { name = it }, placeholder = "Navne", maxLength = 100)
            Field("Email address", email, { email = it }, placeholder = "you@example.com", keyboard = KeyboardType.Email)
            Field("Password", password, { password = it }, placeholder = "At least 8 characters", password = true)
            ErrorBox(error)
            Btn(if (busy) "Connecting…" else if (register) "Create account" else "Sign in", {
                scope.launch {
                    busy = true; error = ""
                    try { vm.authenticate(register, name, email, password) } catch (e: Exception) { error = e.message ?: "Something went wrong." }
                    busy = false
                }
            }, Modifier.fillMaxWidth(), icon = Icons.Filled.ArrowForward, enabled = !busy && email.isNotBlank() && password.length >= 8 && (!register || name.isNotBlank()))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                T(if (register) "Already have an account? " else "New to AgentOS? ", size = 13, color = Muted)
                T(if (register) "Sign in" else "Create an account", Modifier.clickable { register = !register; error = "" }, 13, Dark, FontWeight.SemiBold)
            }
            Row(Modifier.fillMaxWidth().clip(R8).background(Soft).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF73A779))); Spacer(Modifier.width(8.dp))
                T("Local demo  demo@agentos.local / AgentOS2026!", size = 12, color = Color(0xFF4B584F))
            }
            T("Tip: the Android emulator reaches your computer at 10.0.2.2. On a phone, use your computer’s Wi-Fi IP address.", size = 11, color = Muted)
        }
    }
}

// ───────────────────────────── 8 · Shell & navigation ─────────────────────────────

@Composable
fun Shell(vm: AppModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val agent = remember { AgentMode(ctx.applicationContext, vm, scope) }
    val user = vm.user ?: return
    var searchOpen by remember { mutableStateOf(false) }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) agent.enable() else agent.error = "Microphone permission was denied. Allow the microphone for AgentOS in system settings, then turn agent mode on again."
    }
    DisposableEffect(Unit) { onDispose { agent.destroy() } }
    LaunchedEffect(Unit) { vm.refreshAll() }
    LaunchedEffect(vm.toast) { if (vm.toast != null) { delay(6000); vm.toast = null } }
    LaunchedEffect(vm.page) { vm.search = ""; searchOpen = false }
    BackHandler(drawer.isOpen || vm.page != Page.Overview) { if (drawer.isOpen) scope.launch { drawer.close() } else vm.page = Page.Overview }

    fun go(p: Page) { vm.page = p; scope.launch { drawer.close() } }
    fun toggleAgent() {
        if (agent.enabled) agent.disable()
        else if (ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) agent.enable()
        else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    ModalNavigationDrawer(drawerContent = {
        ModalDrawerSheet(drawerContainerColor = Color(0xFF1D2B22)) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
                    Box(Modifier.size(35.dp).clip(R8).background(Mint), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Hub, null, tint = Color(0xFF293D2A)) }
                    Spacer(Modifier.width(10.dp)); T("Agent", size = 22, color = Color.White, weight = FontWeight.SemiBold); T("OS", size = 22, color = Color(0xFF99B69B), weight = FontWeight.SemiBold)
                    T("BETA", Modifier.padding(start = 8.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFF29382C)).padding(horizontal = 5.dp, vertical = 2.dp), 8, Color(0xFFA8B4A5), spacing = 1.sp)
                }
                Row(Modifier.fillMaxWidth().clip(R8).background(Color(0xFF26372C)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    T(user.name.take(1).uppercase(), Modifier.size(30.dp).clip(CircleShape).background(Mint).padding(top = 5.dp), 14, Color(0xFF293D2A), FontWeight.Bold, align = TextAlign.Center)
                    Spacer(Modifier.width(10.dp))
                    Column { T("${user.name}’s workspace", size = 13, color = Color.White, maxLines = 1); T("Personal workspace", size = 11, color = Color(0xFF8FA08F)) }
                }
                Spacer(Modifier.height(10.dp))
                T("WORKSPACE", size = 10, color = Color(0xFF7F917F), spacing = 1.5.sp)
                Page.entries.take(6).forEach { DrawerItem(it, vm, ::go) }
                Spacer(Modifier.height(8.dp))
                T("UNDER THE HOOD", size = 10, color = Color(0xFF7F917F), spacing = 1.5.sp)
                Page.entries.drop(6).forEach { DrawerItem(it, vm, ::go) }
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(if (vm.error != null) Color(0xFFD48E70) else Color(0xFF73A779))); Spacer(Modifier.width(8.dp))
                    Column {
                        T(if (vm.loading) "Connecting to PostgreSQL" else if (vm.error != null) "Connection unavailable" else "PostgreSQL connected", size = 12, color = Color(0xFFD5DFD3))
                        T("Direct SQL · No ORM", size = 10, color = Color(0xFF8FA08F))
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 10.dp).clip(R8).clickable { vm.logout() }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    T(user.name.take(2).uppercase(), Modifier.size(32.dp).clip(CircleShape).background(Color(0xFF3B5642)).padding(top = 7.dp), 12, Color.White, FontWeight.Bold, align = TextAlign.Center)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) { T(user.name, size = 13, color = Color.White, maxLines = 1); T(user.email, size = 10, color = Color(0xFF8FA08F), maxLines = 1) }
                    Icon(Icons.AutoMirrored.Filled.Logout, "Sign out", Modifier.size(18.dp), tint = Color(0xFFA8B4A5))
                }
            }
        }
    }, drawerState = drawer) {
        Box(Modifier.fillMaxSize().background(Bg).safeDrawingPadding()) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().background(Color.White).border(0.5.dp, Line).padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBtn(Icons.Filled.Menu, "Open navigation", { scope.launch { drawer.open() } }, tint = Ink)
                    if (searchOpen) {
                        OutlinedTextField(vm.search, { vm.search = it }, Modifier.weight(1f), singleLine = true, shape = R8, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
                            placeholder = { T("Search projects and tasks…", color = Muted, size = 13) },
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Green, unfocusedBorderColor = Line))
                        IconBtn(Icons.Filled.Close, "Close search", { vm.search = ""; searchOpen = false })
                    } else {
                        Column(Modifier.weight(1f).padding(horizontal = 6.dp)) { T("Workspace › ", size = 10, color = Muted); T(vm.page.title, size = 16, weight = FontWeight.SemiBold, maxLines = 1) }
                        IconBtn(Icons.Filled.Search, "Search workspace", { searchOpen = true }, tint = Ink)
                        Row(Modifier.clip(RoundedCornerShape(99.dp)).background(if (agent.enabled) Dark else Color.White).border(1.dp, if (agent.enabled) Dark else Line, RoundedCornerShape(99.dp))
                            .clickable { toggleAgent() }.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (agent.enabled) Icons.Filled.Mic else Icons.Filled.MicOff, "Agent mode", Modifier.size(16.dp), tint = if (agent.enabled) Color.White else Ink)
                            Spacer(Modifier.width(5.dp))
                            Box(Modifier.size(7.dp).clip(CircleShape).background(if (agent.enabled) Color(0xFF7BE0A0) else Color(0xFFC4CBC3)))
                        }
                        Box {
                            IconBtn(Icons.Filled.Notifications, "View reminders", { vm.page = Page.Reminders }, tint = Ink)
                            if (vm.reminders.any { it.state == "scheduled" && isPast(it.remindAt) }) Box(Modifier.align(Alignment.TopEnd).padding(8.dp).size(8.dp).clip(CircleShape).background(Danger))
                        }
                    }
                }
                if (vm.error != null) Box(Modifier.padding(12.dp)) { ErrorBox(vm.error) }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (vm.page) {
                        Page.Overview -> DashboardPage(vm)
                        Page.Workspace -> AiWorkspacePage(vm)
                        Page.Projects -> ProjectsPage(vm)
                        Page.Tasks -> TasksPage(vm)
                        Page.Reminders -> RemindersPage(vm)
                        Page.Research -> ResearchPage(vm)
                        Page.Database -> DatabasePage(vm)
                        Page.Runs -> RunsPage(vm)
                    }
                }
            }
            Column(Modifier.align(Alignment.BottomCenter).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AgentDock(agent, vm)
                vm.toast?.let { msg ->
                    Row(Modifier.fillMaxWidth().clip(R8).background(Color(0xFF1D2B22)).padding(start = 14.dp, top = 4.dp, bottom = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CheckCircle, null, Modifier.size(18.dp), tint = Color(0xFF7BE0A0)); Spacer(Modifier.width(10.dp))
                        T(msg, Modifier.weight(1f), 13, Color.White)
                        IconBtn(Icons.Filled.Close, "Dismiss notification", { vm.toast = null }, tint = Color(0xFFA8B4A5), size = 16)
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerItem(page: Page, vm: AppModel, go: (Page) -> Unit) {
    val sel = vm.page == page
    Row(Modifier.fillMaxWidth().clip(R8).background(if (sel) Color(0xFF2E4636) else Color.Transparent).clickable { go(page) }.padding(horizontal = 10.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(page.icon, null, Modifier.size(18.dp), tint = if (sel) Mint else Color(0xFFA8B4A5)); Spacer(Modifier.width(12.dp))
        T(page.title, Modifier.weight(1f), 14, if (sel) Color.White else Color(0xFFD5DFD3))
        if (page == Page.Tasks && (vm.analytics?.pending ?: 0) > 0) T("${vm.analytics?.pending}", size = 11, color = Color(0xFFA8B4A5))
        if (page == Page.Workspace) T("AI", Modifier.clip(RoundedCornerShape(4.dp)).background(Mint).padding(horizontal = 5.dp, vertical = 1.dp), 9, Color(0xFF293D2A), FontWeight.Bold)
    }
}

@Composable
fun AgentDock(agent: AgentMode, vm: AppModel) {
    if (!agent.enabled && agent.error.isEmpty()) return
    val q = agent.run?.takeIf { it.status == "awaiting_input" }?.question
    val waiting = agent.phase == Phase.Permission || agent.phase == Phase.Choice || (agent.phase == Phase.Speaking && q != null)
    Surface(Modifier.fillMaxWidth().heightIn(max = 420.dp), shape = R14, color = Color(0xFFFBFCF9), shadowElevation = 12.dp, border = androidx.compose.foundation.BorderStroke(1.dp, Line)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val hot = agent.phase == Phase.Command || agent.phase == Phase.Speaking
                Box(Modifier.size(36.dp).clip(CircleShape).background(if (hot) Dark else if (waiting) Amber else Color(0xFFE7EFE5)), contentAlignment = Alignment.Center) {
                    when (agent.phase) {
                        Phase.Thinking -> CircularProgressIndicator(Modifier.size(17.dp), color = Dark, strokeWidth = 2.dp)
                        Phase.Speaking -> Icon(Icons.Filled.VolumeUp, null, Modifier.size(17.dp), tint = Color.White)
                        else -> Icon(Icons.Filled.SmartToy, null, Modifier.size(17.dp), tint = if (hot) Color.White else if (waiting) AmberText else Dark)
                    }
                }
                Column(Modifier.weight(1f).padding(horizontal = 11.dp)) { T("AgentOS", size = 13, weight = FontWeight.SemiBold); T(agent.phase.text, size = 12, color = Muted) }
                if (agent.phase == Phase.Speaking) IconBtn(Icons.Filled.Stop, "Stop speaking", { agent.silence() }, size = 16)
                IconBtn(Icons.Filled.Close, "Turn agent mode off", { agent.disable() }, size = 16)
            }
            if (agent.error.isNotEmpty()) T(agent.error, Modifier.fillMaxWidth().clip(R8).background(Color(0xFFFBECEC)).padding(9.dp), 12, Color(0xFFA33B3B))
            if (agent.enabled && agent.phase == Phase.Listening && agent.command.isEmpty())
                T("Try: “Hey Agent, check my latest 3 emails and summarize them.”", size = 12, color = Muted, lineHeight = 18.sp)
            if (agent.command.isNotEmpty()) Column(Modifier.fillMaxWidth().clip(R8).background(Soft).padding(10.dp)) { T("YOU", size = 10, color = Muted, weight = FontWeight.SemiBold, spacing = 1.2.sp); T(agent.command, size = 13) }
            if (q != null && waiting) Row(Modifier.fillMaxWidth().clip(R8).background(Color(0xFFFFF8EA)).border(1.dp, Color(0xFFF0DFB8), R8).padding(11.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.VerifiedUser, null, Modifier.size(17.dp), tint = AmberText)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    T(q.message, size = 13, color = Color(0xFF3A2D06), weight = FontWeight.SemiBold)
                    if (q.kind == "approval") Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Btn("Allow", { agent.answer(approved = true) }, icon = Icons.Filled.Check); Btn("Deny", { agent.answer(approved = false) }, kind = BK.Secondary)
                    } else q.options.forEachIndexed { i, o ->
                        Row(Modifier.fillMaxWidth().clip(R8).background(Color.White).border(1.dp, Color(0xFFEADFC4), R8).clickable { agent.answer(selectedId = o.id) }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            T("${i + 1}", Modifier.size(20.dp).clip(CircleShape).background(Amber).padding(top = 2.dp), 11, Color(0xFF3A2D06), FontWeight.Bold, align = TextAlign.Center)
                            Spacer(Modifier.width(9.dp)); T(o.label, size = 12)
                        }
                    }
                    T("Or just say ${if (q.kind == "approval") "“yes” or “no”" else "the number"}.", size = 11, color = Color(0xFF8A7440))
                }
            }
            if (agent.reply.isNotEmpty() && q == null) T(agent.reply, size = 13, lineHeight = 21.sp)
            if (Regex("Gmail is not connected").containsMatchIn(agent.reply) && q == null) GmailConnect(vm)
            if (agent.enabled && agent.heard.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Mic, null, Modifier.size(12.dp), tint = Color(0xFF8A948B)); Spacer(Modifier.width(6.dp)); T(agent.heard, size = 12, color = Color(0xFF8A948B))
            }
            if (agent.enabled) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val active = agent.talking
                Row(Modifier.clip(RoundedCornerShape(99.dp)).background(if (active) Dark else Soft).border(1.dp, if (active) Dark else Line, RoundedCornerShape(99.dp))
                    .pointerInput(agent.phase == Phase.Thinking) {
                        detectTapGestures(onPress = { if (agent.phase != Phase.Thinking) { agent.talkStart(); tryAwaitRelease(); agent.talkEnd() } })
                    }.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Mic, null, Modifier.size(15.dp), tint = if (active) Color.White else Dark); Spacer(Modifier.width(7.dp))
                    T(if (active) "Listening… release to send" else "Hold to talk", size = 12, color = if (active) Color.White else Dark, weight = FontWeight.SemiBold)
                }
                Column {
                    T(if (agent.talking) "" else if (agent.hearing) "Hearing speech…" else "or say “Hey Agent”", size = 11, color = Muted)
                    T("On-device speech recognition", size = 10, color = Color(0xFF9AA39B))
                }
            }
        }
    }
}

// ───────────────────────────── 9 · Pages ─────────────────────────────

@Composable
fun DashboardPage(vm: AppModel) {
    val user = vm.user ?: return
    if (vm.loading) { PageColumn { Loading() }; return }
    val a = vm.analytics
    val pending = vm.tasks.filter { it.status != "completed" && it.title.contains(vm.search, true) }.take(5)
    val active = vm.projects.filter { it.status == "active" }.take(3)
    val progress = if (a != null && a.total > 0) a.completed.toFloat() / a.total else 0f
    val projectColors = listOf(Color(0xFFE9F0DF) to LeafText, Color(0xFFE8E6F5) to Color(0xFF6A5FA8), Color(0xFFFBE9E1) to Color(0xFFC0714B))
    PageColumn {
        PageHeading(LocalDate.now(IST).format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)), "Hello, ${user.name.split(" ").first()}.", "A clear view of your work. A little more room to think.") {
            Btn("New project", { vm.page = Page.Projects }, kind = BK.Secondary, icon = Icons.Filled.Add)
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Dark).padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.AutoAwesome, null, Modifier.size(14.dp), tint = Mint); Spacer(Modifier.width(6.dp)); T("YOUR INTELLIGENT WORKSPACE", size = 10, color = Mint, spacing = 1.5.sp, weight = FontWeight.SemiBold) }
            T("Big ideas.\nSmaller to-do lists.", size = 28, color = Color.White, weight = FontWeight.SemiBold, lineHeight = 33.sp)
            T("Tell AgentOS what’s on your mind. Turn your next thought into a plan that sticks.", size = 13, color = Color(0xFFC9D6C8), lineHeight = 20.sp)
            Row(Modifier.clip(RoundedCornerShape(99.dp)).background(Mint).clickable { vm.page = Page.Workspace }.padding(horizontal = 18.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                T("Let’s get it done", size = 13, color = Color(0xFF1D2B22), weight = FontWeight.SemiBold); Spacer(Modifier.width(8.dp)); Icon(Icons.Filled.ArrowOutward, null, Modifier.size(16.dp), tint = Color(0xFF1D2B22))
            }
            T("THINK IT. PLAN IT. DO IT.", size = 10, color = Color(0xFF8FB39A), spacing = 2.sp)
        }
        val stats = listOf(
            Triple("Active projects", a?.activeProjects ?: 0, "Ideas in motion") to (Icons.Filled.Folder to (Leaf to LeafText)),
            Triple("Pending tasks", a?.pending ?: 0, "One step at a time") to (Icons.Filled.FormatListBulleted to (Color(0xFFFFF0DF) to Color(0xFFC77A1E))),
            Triple("Completed tasks", a?.completed ?: 0, "Progress worth seeing") to (Icons.Filled.CheckCircle to (Color(0xFFE8E6F5) to Color(0xFF6A5FA8))),
            Triple("Overdue tasks", a?.overdue ?: 0, "Ready for your attention") to (Icons.Filled.Notifications to (Color(0xFFFFF0EB) to Color(0xFFC0714B))),
        )
        stats.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { (info, look) ->
                    Panel(Modifier.weight(1f), pad = 14.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) { T(info.first, Modifier.weight(1f), 12, Muted, maxLines = 1); IconTile(look.first, look.second.second, look.second.first, 30) }
                        T(info.second.toString().padStart(2, '0'), size = 30, weight = FontWeight.SemiBold)
                        T(info.third, size = 11, color = Muted)
                    }
                }
            }
        }
        Panel {
            PanelHeading("Your next moves", "A few things to keep your momentum.") { Btn("All tasks", { vm.taskProject = ""; vm.page = Page.Tasks }, kind = BK.Text) }
            if (pending.isEmpty()) Empty("A little breathing room", "Add a task to start building your next idea.") { Btn("Create a task", { vm.page = Page.Tasks }, kind = BK.Secondary) }
            else pending.forEach { TaskRow(it, vm) }
        }
        Panel {
            PanelHeading("Small steps add up", "Your workspace at a glance.")
            Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(140.dp)) {
                    val stroke = Stroke(18f, cap = StrokeCap.Round)
                    drawArc(Color(0xFFE9EDE7), 0f, 360f, false, style = stroke)
                    drawArc(Green, -90f, 360f * progress, false, style = stroke)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) { T("${Math.round(progress * 100)}%", size = 26, weight = FontWeight.SemiBold); T("tasks complete", size = 11, color = Muted) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(8.dp).clip(CircleShape).background(Green)); T("  Completed ${a?.completed ?: 0}", size = 12) }
                Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFD5DAD3))); T("  Remaining ${a?.pending ?: 0}", size = 12) }
            }
        }
        Panel {
            PanelHeading("Projects in motion", "A place for every good idea.") { Btn("View all", { vm.page = Page.Projects }, kind = BK.Text) }
            if (active.isEmpty()) Empty("Start with an idea", "Create your first project to bring it together.")
            active.forEachIndexed { i, p ->
                Row(Modifier.fillMaxWidth().clip(R8).clickable { vm.taskProject = p.id; vm.page = Page.Tasks }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Filled.Folder, projectColors[i % 3].second, projectColors[i % 3].first, 40)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        T(p.name, size = 14, weight = FontWeight.SemiBold, maxLines = 1); T("${p.completedCount} of ${p.taskCount} tasks complete", size = 11, color = Muted); ProgressBar(p.progress)
                    }
                    Icon(Icons.Filled.ArrowOutward, null, Modifier.size(17.dp), tint = Muted)
                }
            }
        }
        Panel {
            PanelHeading("Recently in your workspace", "Every change has a story.") { Pill("LIVE") }
            if (vm.activity.isEmpty()) Empty("Your story starts here", "Your saved changes will appear in this feed.")
            vm.activity.take(4).forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(if (item.action == "INSERT") Icons.Filled.Add else Icons.Filled.Check, size = 32)
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        T("${label(item.entityType)} ${if (item.action == "INSERT") "created" else if (item.action == "DELETE") "deleted" else "updated"}", size = 13, weight = FontWeight.Medium)
                        T(item.detail.s("name").ifEmpty { item.detail.s("title") }.ifEmpty { "Workspace change" }, size = 11, color = Muted, maxLines = 1)
                    }
                    T(fmtDate(item.createdAt, true), size = 11, color = Muted)
                }
            }
        }
        Panel(color = Leaf) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(Icons.Filled.SmartToy, bg = Color.White); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    T("A thoughtful assistant. A solid foundation.", size = 14, weight = FontWeight.SemiBold)
                    T("${if (vm.config?.planner?.provider == "openrouter") "OpenRouter" else "Groq"} planning · Groq research · PostgreSQL persistence", size = 11, color = Color(0xFF3F5A33))
                }
            }
            Btn("Explore your database", { vm.page = Page.Database }, kind = BK.Text, icon = Icons.Filled.ArrowOutward)
        }
    }
}

// ── Projects ──

@Composable
fun ProjectEditor(initial: Project?, vm: AppModel, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var description by remember { mutableStateOf(initial?.description ?: "") }
    var status by remember { mutableStateOf(initial?.status ?: "active") }
    var tasks by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AppDialog(if (initial != null) "Edit project" else "New project", "Bring related work together. Initial tasks are saved in one transaction.", onClose) {
        Field("Project name", name, { name = it }, placeholder = "A good idea starts here", maxLength = 200)
        Field("Description", description, { description = it }, placeholder = "What would you like to accomplish?", singleLine = false, minLines = 3)
        if (initial != null) Select("Status", status, listOf("active", "completed", "archived").map { it to it }, { status = it })
        else Field("Initial tasks", tasks, { tasks = it }, placeholder = "ER diagram\nSQL queries\nProject report", singleLine = false, minLines = 3, hint = "One task per line · optional")
        ErrorBox(error)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Btn("Cancel", onClose, Modifier.weight(1f), BK.Secondary)
            Btn(if (busy) "Saving…" else "Save project", {
                scope.launch {
                    busy = true
                    val body = obj("name" to name.trim(), "description" to description)
                    if (initial != null) { body.put("status", status); body.put("version", initial.version) }
                    else body.put("tasks", JSONArray(tasks.lines().map { it.trim() }.filter { it.isNotEmpty() }))
                    error = vm.act("/projects" + (initial?.let { "/${it.id}" } ?: ""), if (initial != null) "PATCH" else "POST", body)
                    busy = false; if (error == null) onClose()
                }
            }, Modifier.weight(1f), icon = Icons.Filled.Check, enabled = !busy && name.isNotBlank())
        }
    }
}

@Composable
fun ProjectsPage(vm: AppModel) {
    var filter by remember { mutableStateOf("all") }
    var editor by remember { mutableStateOf<Project?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Project?>(null) }
    val scope = rememberCoroutineScope()
    val colors = listOf(Leaf to LeafText, Color(0xFFE8E6F5) to Color(0xFF6A5FA8), Color(0xFFFBE9E1) to Color(0xFFC0714B))
    val filtered = vm.projects.filter { (filter == "all" || it.status == filter) && (it.name + " " + it.description).contains(vm.search, true) }
    PageColumn {
        PageHeading("A PLACE FOR EVERY IDEA", "Projects", "Keep the big picture connected to the small steps.") { Btn("New project", { creating = true }, icon = Icons.Filled.Add) }
        Tabs(listOf("all", "active", "completed", "archived").map { it to label(it) }, filter) { filter = it }
        T("${filtered.size} project${if (filtered.size == 1) "" else "s"}", size = 12, color = Muted)
        if (vm.loading) Loading()
        else if (filtered.isEmpty()) Panel { Empty("Make room for an idea", "Create a project, add a few tasks, and take the first step.") }
        else filtered.forEachIndexed { i, p ->
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Filled.Folder, colors[i % 3].second, colors[i % 3].first, 42); Spacer(Modifier.weight(1f)); Badge(p.status)
                    IconBtn(Icons.Filled.Edit, "Edit ${p.name}", { editor = p }, size = 16)
                }
                T(p.name, Modifier.clickable { vm.taskProject = p.id; vm.page = Page.Tasks }, 18, weight = FontWeight.SemiBold, maxLines = 2)
                T(p.description.ifEmpty { "A fresh space for your next idea." }, size = 13, color = Muted, maxLines = 3, lineHeight = 19.sp)
                Row { T("${p.completedCount} of ${p.taskCount} complete", Modifier.weight(1f), 12, Muted); T("${Math.round(p.progress * 100)}%", size = 12, weight = FontWeight.SemiBold) }
                ProgressBar(p.progress)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Btn("View tasks", { vm.taskProject = p.id; vm.page = Page.Tasks }, kind = BK.Text, icon = Icons.Filled.ArrowOutward); Spacer(Modifier.weight(1f))
                    if (p.status != "archived") IconBtn(Icons.Filled.Archive, "Archive ${p.name}", { scope.launch { vm.act("/projects/${p.id}", "PATCH", obj("version" to p.version, "status" to "archived")) } }, size = 17)
                    IconBtn(Icons.Filled.Delete, "Delete ${p.name}", { deleting = p }, size = 17)
                }
            }
        }
    }
    if (creating) ProjectEditor(null, vm) { creating = false }
    editor?.let { ProjectEditor(it, vm) { editor = null } }
    deleting?.let { p ->
        val linked = vm.tasks.count { it.projectId == p.id }
        DeleteDialog("project", p.id, p.name, " This also deletes $linked task(s), linked reminders, and research notes.", vm) { deleting = null }
    }
}

// ── Tasks ──

@Composable
fun TaskEditor(initial: Task?, defaultProject: String, vm: AppModel, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var description by remember { mutableStateOf(initial?.description ?: "") }
    var project by remember { mutableStateOf(initial?.projectId ?: defaultProject.ifEmpty { vm.projects.firstOrNull()?.id ?: "" }) }
    var priority by remember { mutableStateOf(initial?.priority ?: "medium") }
    var status by remember { mutableStateOf(initial?.status ?: "pending") }
    var due by remember { mutableStateOf(toLocal(initial?.dueAt)) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AppDialog(if (initial != null) "Edit task" else "New task", "Dates and times use Asia/Kolkata (IST).", onClose) {
        Field("Task title", title, { title = it }, placeholder = "Your next small step", maxLength = 300)
        if (initial != null) Field("Project", vm.projects.firstOrNull { it.id == initial.projectId }?.name ?: initial.projectName, {}, enabled = false)
        else Select("Project", project, vm.projects.map { it.id to it.name }, { project = it })
        Field("Description", description, { description = it }, singleLine = false, minLines = 2)
        Select("Priority", priority, listOf("low", "medium", "high").map { it to it }, { priority = it })
        if (initial != null) Select("Status", status, listOf("pending", "in_progress", "completed").map { it to label(it) }, { status = it })
        DateTimeField("Due date · IST", due, { due = it }, clearable = true)
        ErrorBox(error)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Btn("Cancel", onClose, Modifier.weight(1f), BK.Secondary)
            Btn(if (busy) "Saving…" else "Save task", {
                scope.launch {
                    busy = true
                    val body = obj("title" to title.trim(), "description" to description, "priority" to priority, "due_at" to toUtc(due))
                    if (initial != null) { body.put("status", status); body.put("version", initial.version) } else body.put("project_id", project)
                    error = vm.act("/tasks" + (initial?.let { "/${it.id}" } ?: ""), if (initial != null) "PATCH" else "POST", body)
                    busy = false; if (error == null) onClose()
                }
            }, Modifier.weight(1f), icon = Icons.Filled.Check, enabled = !busy && title.isNotBlank() && (initial != null || project.isNotEmpty()))
        }
    }
}

@Composable
fun TasksPage(vm: AppModel) {
    var filter by remember { mutableStateOf("all") }
    var priority by remember { mutableStateOf("all") }
    var editor by remember { mutableStateOf<Task?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Task?>(null) }
    val pid = vm.taskProject
    val filtered = vm.tasks.filter {
        (filter == "all" || it.status == filter || (filter == "overdue" && it.status != "completed" && isPast(it.dueAt))) &&
            (pid.isEmpty() || it.projectId == pid) && (priority == "all" || it.priority == priority) && (it.title + " " + it.projectName).contains(vm.search, true)
    }
    PageColumn {
        PageHeading("ONE STEP AT A TIME", "Tasks", "The little things that move the big things forward.") { Btn("New task", { creating = true }, icon = Icons.Filled.Add, enabled = vm.projects.isNotEmpty()) }
        Tabs(listOf("all", "pending", "in_progress", "completed", "overdue").map { it to label(it) }, filter) { filter = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Select(null, pid, listOf("" to "All projects") + vm.projects.map { it.id to it.name }, { vm.taskProject = it }, Modifier.weight(1f))
            Select(null, priority, listOf("all" to "All priorities", "low" to "low", "medium" to "medium", "high" to "high"), { priority = it }, Modifier.weight(1f))
        }
        Panel {
            if (vm.loading) Loading()
            else if (filtered.isEmpty()) Empty(if (vm.projects.isNotEmpty()) "Nothing on this list yet" else "Start with a project",
                if (vm.projects.isNotEmpty()) "Add a task or try a different filter." else "Create a project first, then give it a few achievable tasks.")
            else filtered.forEach { t ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { TaskRow(t, vm) { editor = it } }
                    IconBtn(Icons.Filled.Delete, "Delete ${t.title}", { deleting = t }, size = 16)
                }
            }
        }
    }
    if (creating) TaskEditor(null, pid, vm) { creating = false }
    editor?.let { TaskEditor(it, pid, vm) { editor = null } }
    deleting?.let { DeleteDialog("task", it.id, it.title, "", vm) { deleting = null } }
}

// ── Reminders ──

@Composable
fun ReminderEditor(vm: AppModel, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var taskId by remember { mutableStateOf("") }
    var at by remember { mutableStateOf<LocalDateTime?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AppDialog("New reminder", "Dates and times use Asia/Kolkata (IST).", onClose) {
        Field("Reminder title", title, { title = it }, placeholder = "What should you remember?", maxLength = 300)
        Select("Linked task · optional", taskId, listOf("" to "Standalone reminder") + vm.tasks.map { it.id to "${it.title} — ${it.projectName}" }, { taskId = it })
        DateTimeField("Remind at · IST", at, { at = it })
        ErrorBox(error)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Btn("Cancel", onClose, Modifier.weight(1f), BK.Secondary)
            Btn(if (busy) "Saving…" else "Save reminder", {
                scope.launch {
                    busy = true
                    error = vm.act("/reminders", "POST", obj("title" to title.trim(), "task_id" to taskId.ifEmpty { null }, "remind_at" to toUtc(at)))
                    busy = false; if (error == null) onClose()
                }
            }, Modifier.weight(1f), icon = Icons.Filled.Check, enabled = !busy && title.isNotBlank() && at != null)
        }
    }
}

@Composable
fun RemindersPage(vm: AppModel) {
    var filter by remember { mutableStateOf("scheduled") }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Reminder?>(null) }
    val scope = rememberCoroutineScope()
    val filtered = vm.reminders.filter { it.state == filter && it.title.contains(vm.search, true) }
    PageColumn {
        PageHeading("A GENTLE NUDGE", "Reminders", "Keep important moments from slipping through the cracks.") { Btn("New reminder", { creating = true }, icon = Icons.Filled.Add) }
        Tabs(listOf("scheduled", "dismissed").map { it to label(it) }, filter) { filter = it }
        T("Reminders are shown while this app is open.", size = 12, color = Muted)
        Panel {
            if (filtered.isEmpty()) Empty("Nothing to remember just yet", "Schedule a reminder for a deadline, a task, or your next good idea.")
            filtered.forEach { r ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Filled.Notifications, Color(0xFFC77A1E), Color(0xFFFFF0DF), 36)
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        T(r.title, size = 14, weight = FontWeight.SemiBold, maxLines = 2); T(r.taskTitle ?: "Standalone reminder", size = 11, color = Muted, maxLines = 1)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Schedule, null, Modifier.size(12.dp), tint = Muted); Spacer(Modifier.width(4.dp)); T(fmtDate(r.remindAt), size = 11, color = Muted)
                            Spacer(Modifier.width(8.dp)); Badge(if (r.state == "scheduled" && isPast(r.remindAt)) "overdue" else r.state)
                        }
                    }
                    if (r.state == "scheduled") IconBtn(Icons.Filled.Check, "Dismiss ${r.title}", { scope.launch { vm.act("/reminders/${r.id}/dismiss", "PATCH") } }, size = 17)
                    IconBtn(Icons.Filled.Delete, "Delete ${r.title}", { deleting = r }, size = 16)
                }
            }
        }
    }
    if (creating) ReminderEditor(vm) { creating = false }
    deleting?.let { DeleteDialog("reminder", it.id, it.title, "", vm) { deleting = null } }
}

// ── Research ──

@Composable
fun ResearchPage(vm: AppModel) {
    var open by remember { mutableStateOf(false) }
    val uri = LocalUriHandler.current
    val notes = vm.notes.filter { (it.title + " " + it.content).contains(vm.search, true) }
    PageColumn {
        PageHeading("FOLLOW YOUR CURIOSITY", "Research", "Source-backed notes, right where your project needs them.") { Btn("Research a topic", { open = true }, icon = Icons.Filled.Search, enabled = vm.projects.isNotEmpty()) }
        InfoStrip(Icons.Filled.Search, "Wikipedia sources via MCP · Summarized with ${vm.config?.research?.provider?.ifEmpty { null } ?: "Groq"} · Saved to your project") { Badge(if (vm.config?.research?.configured == true) "active" else "pending") }
        notes.forEach { n ->
            Panel {
                Eyebrow(n.projectName); T(n.title, size = 18, weight = FontWeight.SemiBold); T(fmtDate(n.createdAt), size = 11, color = Muted)
                T(n.content, size = 13, lineHeight = 21.sp)
                if (n.sources.isNotEmpty()) {
                    T("Sources", size = 12, weight = FontWeight.SemiBold)
                    n.sources.forEachIndexed { i, s ->
                        Row(Modifier.clickable { runCatching { uri.openUri(s.url) } }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            T("[${i + 1}] ${s.title}", Modifier.weight(1f, fill = false), 12, Green, maxLines = 2); Spacer(Modifier.width(4.dp)); Icon(Icons.Filled.OpenInNew, null, Modifier.size(12.dp), tint = Green)
                        }
                    }
                }
            }
        }
        if (vm.notes.isEmpty()) Panel { Empty("A place for your discoveries", "Choose a project and research a topic to save a cited note. Research requires a configured model API key.") }
    }
    if (open) {
        val scope = rememberCoroutineScope()
        var topic by remember { mutableStateOf("") }
        var project by remember { mutableStateOf(vm.projects.firstOrNull()?.id ?: "") }
        var busy by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        AppDialog("Research a topic", "Find Wikipedia articles through the bundled MCP server and save a cited summary.", { open = false }) {
            Field("Topic", topic, { topic = it }, placeholder = "Database normalization", maxLength = 200)
            Select("Save to project", project, vm.projects.map { it.id to it.name }, { project = it })
            ErrorBox(error)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn("Cancel", { open = false }, Modifier.weight(1f), BK.Secondary)
                Btn(if (busy) "Reading and summarizing…" else "Research & save", {
                    scope.launch { busy = true; error = vm.act("/research-notes", "POST", obj("topic" to topic.trim(), "project_id" to project)); busy = false; if (error == null) open = false }
                }, Modifier.weight(1.4f), enabled = !busy && topic.trim().length >= 2 && project.isNotEmpty())
            }
        }
    }
}

// ── Database explorer ──

private data class Entity(val name: String, val x: Int, val y: Int, val fields: List<String>)
private val entities = listOf(
    Entity("users", 30, 235, listOf("id · PK", "email · UNIQUE", "name", "password_hash")),
    Entity("projects", 290, 25, listOf("id · PK", "user_id · FK", "name, description", "status, version")),
    Entity("tasks", 565, 25, listOf("id · PK", "project_id, user_id · FK", "title, status, priority", "due_at, version")),
    Entity("conversations", 290, 255, listOf("id · PK", "user_id · FK", "title", "created_at")),
    Entity("messages", 565, 255, listOf("id · PK", "conversation_id, user_id · FK", "role, content", "created_at")),
    Entity("reminders", 815, 255, listOf("id · PK", "task_id?, user_id · FK", "title, remind_at", "state")),
    Entity("audit_logs", 30, 485, listOf("id · PK", "user_id · FK", "entity_type, entity_id", "action, detail")),
    Entity("agent_runs", 290, 485, listOf("id · PK", "conversation_id, user_id · FK", "status, steps, results", "started_at, finished_at")),
    Entity("research_notes", 565, 485, listOf("id · PK", "project_id, user_id · FK", "title, content", "sources")),
)
private val erPaths = listOf(
    listOf(235 to 290, 263 to 290, 263 to 105, 290 to 105), listOf(495 to 105, 565 to 105), listOf(235 to 310, 290 to 310), listOf(495 to 335, 565 to 335),
    listOf(235 to 340, 263 to 340, 263 to 565, 290 to 565), listOf(130 to 410, 130 to 485), listOf(770 to 105, 913 to 105, 913 to 255),
    listOf(393 to 430, 393 to 485), listOf(495 to 135, 535 to 135, 535 to 565, 565 to 565),
)

@Composable
private fun ERDiagram(selected: String, onSelect: (String) -> Unit) {
    Box(Modifier.fillMaxWidth().heightIn(max = 380.dp).clip(R8).background(Color(0xFFFAFCF6)).border(1.dp, Line, R8).horizontalScroll(rememberScrollState()).verticalScroll(rememberScrollState())) {
        Box(Modifier.size(1040.dp, 690.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                val d = density
                erPaths.forEach { pts ->
                    val path = Path().apply { pts.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(x * d, y * d) else lineTo(x * d, y * d) } }
                    drawPath(path, Color(0xFFADBEA0), style = Stroke(1.5f * d))
                    val (x2, y2) = pts.last(); val (x1, y1) = pts[pts.size - 2]
                    val dx = (x2 - x1).coerceIn(-1, 1); val dy = (y2 - y1).coerceIn(-1, 1)
                    val tip = Offset(x2 * d, y2 * d)
                    val a = Offset((x2 - dx * 7 - dy * 3.5f) * d, (y2 - dy * 7 + dx * 3.5f) * d); val b = Offset((x2 - dx * 7 + dy * 3.5f) * d, (y2 - dy * 7 - dx * 3.5f) * d)
                    drawLine(Color(0xFFA4B895), tip, a, 1.5f * d); drawLine(Color(0xFFA4B895), tip, b, 1.5f * d)
                }
            }
            entities.forEach { e ->
                val sel = e.name == selected
                Column(Modifier.offset(e.x.dp, e.y.dp).size(205.dp, 175.dp).clip(RoundedCornerShape(9.dp)).background(Color.White)
                    .border(if (sel) 2.dp else 1.dp, if (sel) Color(0xFF799969) else Color(0xFFDCE4D3), RoundedCornerShape(9.dp)).clickable { onSelect(e.name) }) {
                    T(e.name, Modifier.fillMaxWidth().background(if (sel) Color(0xFFE9F0DF) else Color(0xFFF3F6ED)).padding(horizontal = 16.dp, vertical = 11.dp), 13, Color(0xFF526946), FontWeight.SemiBold)
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        e.fields.forEachIndexed { i, f -> T(f, size = 10, color = if (i == 0) Color(0xFF688253) else Color(0xFF929F87)) }
                    }
                }
            }
        }
    }
}

@Composable
fun DatabasePage(vm: AppModel) {
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf("schema") }
    var table by remember { mutableStateOf("projects") }
    var selected by remember { mutableStateOf("pending_join") }
    var schema by remember { mutableStateOf<SchemaInfo?>(null) }
    var templates by remember { mutableStateOf<List<Template>?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<List<JSONObject>?>(null) }
    var queryBusy by remember { mutableStateOf(false) }
    var queryError by remember { mutableStateOf<String?>(null) }
    var rollback by remember { mutableStateOf<JSONObject?>(null) }
    var rollbackBusy by remember { mutableStateOf(false) }
    var rollbackError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        try {
            val s = Http.call("/db/schema").asObj()
            schema = SchemaInfo(s.optJSONArray("columns").asArr().objs(), s.optJSONArray("constraints").asArr().objs())
            templates = Http.call("/db/templates").asArr().objs().map { Template(it.s("id"), it.s("name"), it.s("concept"), it.s("sql")) }
        } catch (e: Exception) { loadError = e.message }
    }
    val template = templates?.firstOrNull { it.id == selected }
    PageColumn {
        PageHeading("A SOLID FOUNDATION", "Database Explorer", "See the relationships, queries, and transactions behind your workspace.") { Pill("POSTGRESQL · DIRECT SQL") }
        InfoStrip(Icons.Filled.VerifiedUser, "Read-only query templates. Every result is filtered to your account. No arbitrary SQL execution.")
        Tabs(listOf("schema" to "Schema & relationships", "queries" to "SQL playground", "activity" to "Operation history"), tab) { tab = it }
        when (tab) {
            "schema" -> {
                Panel {
                    PanelHeading("Everything has its place", "Normalized entities · Composite ownership keys · Referential integrity") { Pill("9 TABLES") }
                    ERDiagram(table) { table = it }
                    T("Select a table to inspect its live PostgreSQL columns and constraints. Arrows show parent → child relationships; every owned table also references users.", size = 11, color = Muted)
                }
                Panel {
                    PanelHeading("$table / columns")
                    if (schema == null && loadError == null) Loading()
                    else ResultTable(schema?.columns.orEmpty().filter { it.s("table_name") == table }.map { obj("column" to it.s("column_name"), "type" to it.s("data_type"), "nullable" to it.s("is_nullable")) })
                }
                Panel {
                    PanelHeading("Database guarantees")
                    schema?.constraints.orEmpty().filter { it.s("table_name") == table }.forEach { c ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.CheckCircle, null, Modifier.size(14.dp), tint = Green)
                            Column { T(c.s("name"), size = 12, weight = FontWeight.SemiBold); T(c.s("definition"), size = 11, color = Muted, mono = true) }
                        }
                    }
                }
                ErrorBox(loadError)
            }
            "queries" -> {
                Panel {
                    PanelHeading("Approved templates")
                    templates?.forEach { t ->
                        Row(Modifier.fillMaxWidth().clip(R8).background(if (selected == t.id) Leaf else Color.Transparent).clickable { selected = t.id; result = null; queryError = null }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Code, null, Modifier.size(16.dp), tint = Green); Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) { T(t.name, size = 13, weight = FontWeight.Medium); T(t.concept, size = 11, color = Muted) }
                            Icon(Icons.Filled.ChevronRight, null, Modifier.size(14.dp), tint = Muted)
                        }
                    } ?: Loading()
                }
                Panel {
                    PanelHeading(template?.name ?: "", template?.concept)
                    Btn(if (queryBusy) "Running…" else "Run query", {
                        scope.launch {
                            queryBusy = true; queryError = null
                            try { result = Http.call("/db/templates/$selected/execute", "POST").asArr().objs() } catch (e: Exception) { queryError = e.message }
                            queryBusy = false
                        }
                    }, icon = Icons.Filled.PlayArrow, enabled = !queryBusy && template != null)
                    T(template?.sql?.replace("%s", ":current_user_id") ?: "", Modifier.fillMaxWidth().clip(R8).background(Color(0xFF1D2B22)).horizontalScroll(rememberScrollState()).padding(12.dp), 12, Color(0xFFD5E6D0), mono = true)
                    T(":current_user_id is securely bound on the server using psycopg parameters.", size = 11, color = Muted)
                }
                Panel {
                    PanelHeading("Query results") { result?.let { Pill("${it.size} ROWS") } }
                    ErrorBox(queryError)
                    result?.let { ResultTable(it) } ?: Empty("See your SQL in action", "Run a template to view live database results.")
                }
                Panel {
                    Eyebrow("ACID / ATOMICITY"); T("Watch a transaction roll back", size = 17, weight = FontWeight.SemiBold)
                    T("Insert a project and two tasks. The second task intentionally violates a CHECK constraint, rolling back all related inserts.", size = 13, color = Muted, lineHeight = 19.sp)
                    Btn(if (rollbackBusy) "Testing…" else "Test rollback", {
                        scope.launch {
                            rollbackBusy = true; rollbackError = null
                            try { rollback = Http.call("/db/rollback-demo", "POST").asObj() } catch (e: Exception) { rollbackError = e.message }
                            rollbackBusy = false
                        }
                    }, kind = BK.Secondary, icon = Icons.Filled.Replay, enabled = !rollbackBusy)
                    rollback?.let { r ->
                        Row(Modifier.fillMaxWidth().clip(R8).background(Leaf).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Filled.CheckCircle, null, Modifier.size(18.dp), tint = Green)
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                T(if (r.optBoolean("rolled_back")) "Complete rollback verified" else "Unexpected result — review the database", size = 13, weight = FontWeight.SemiBold)
                                T(r.s("explanation"), size = 12, color = Color(0xFF3F5A33))
                                T("Project rows: ${r.opt("project_rows_remaining")} · Tasks before: ${r.opt("tasks_before")} · Tasks after: ${r.opt("tasks_after")}", size = 11, color = Muted)
                            }
                        }
                    }
                    ErrorBox(rollbackError)
                }
            }
            else -> Panel {
                PanelHeading("Authorized database operations", "Audit records commit atomically with the changes they describe.") { Pill("LAST 100") }
                if (vm.activity.isEmpty()) Empty("An honest record of your work", "Create or update a record to see the database audit trail.")
                vm.activity.forEach { item ->
                    Column(Modifier.fillMaxWidth().border(0.5.dp, Soft).padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Badge(item.action.lowercase()); Spacer(Modifier.width(8.dp)); T(label(item.entityType), Modifier.weight(1f), 13, weight = FontWeight.Medium); T(fmtDate(item.createdAt), size = 11, color = Muted) }
                        T(item.detail.toString(), size = 11, color = Muted, mono = true, maxLines = 3)
                    }
                }
            }
        }
    }
}

// ── AI workspace ──

@Composable
private fun ResultView(data: Any?) {
    when (data) {
        is JSONArray -> ResultTable(data.objs())
        is JSONObject -> when {
            data.has("content") && data.has("path") -> SavedResult(data.s("name"), data.s("path"), data.s("content").let { it.take(1200) + if (it.length > 1200) " …" else "" })
            data.has("title") || data.has("name") -> SavedResult(
                data.s("title").ifEmpty { data.s("name") },
                (data.optJSONArray("task_ids")?.let { "${it.length()} initial tasks saved · " } ?: "") + if (data.has("id")) "Record ID: ${data.s("id")}" else "Saved successfully",
                if (data.has("content")) data.s("content") else "",
            )
            else -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                data.keys().asSequence().filter { it !in listOf("user_id", "password_hash") }.forEach { k ->
                    Row { T(label(k), Modifier.width(110.dp), 12, Muted); T(cell(data.opt(k)), Modifier.weight(1f), 12, weight = FontWeight.Medium) }
                }
            }
        }
        else -> T(data?.toString() ?: "No result", size = 13)
    }
}

@Composable
private fun SavedResult(title: String, sub: String, body: String) = Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    Icon(Icons.Filled.VerifiedUser, null, Modifier.size(17.dp), tint = Green)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        T(title, size = 13, weight = FontWeight.SemiBold); T(sub, size = 11, color = Muted)
        if (body.isNotEmpty()) T(body, size = 12, lineHeight = 18.sp)
    }
}

@Composable
fun RunDetail(run: Run, onResume: ((Boolean?, String?) -> Unit)? = null, onCancel: (() -> Unit)? = null, busy: Boolean = false) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Filled.SmartToy, size = 38); Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) { T(run.input, size = 14, weight = FontWeight.SemiBold, maxLines = 3); T("${fmtDate(run.startedAt)} · ${run.id.take(8)}", size = 11, color = Muted) }
            Badge(run.status)
        }
        run.steps.forEachIndexed { i, step ->
            val finished = run.results.any { it.step == i + 1 }
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.size(22.dp).clip(CircleShape).background(if (finished) Green else Soft), contentAlignment = Alignment.Center) {
                    if (finished) Icon(Icons.Filled.Check, null, Modifier.size(13.dp), tint = Color.White) else T("${i + 1}", size = 11, color = Muted)
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    T(label(step.s("action")), size = 13, weight = FontWeight.SemiBold)
                    T(listOf("name", "title", "project_name", "task_title", "topic").firstNotNullOfOrNull { step.sn(it) } ?: "Read and validate your owned records", size = 12, color = Muted)
                }
            }
        }
        if (run.active) Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(16.dp), color = Green, strokeWidth = 2.dp); Spacer(Modifier.width(8.dp))
            T(if (run.status == "planning") "Planning your command…" else "Executing validated tools…", size = 13, color = Muted)
        }
        run.question?.let { q ->
            Row(Modifier.fillMaxWidth().clip(R8).background(Color(0xFFFFF8EA)).border(1.dp, Color(0xFFF0DFB8), R8).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.VerifiedUser, null, Modifier.size(21.dp), tint = AmberText)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    T(q.message, size = 13, color = Color(0xFF3A2D06), weight = FontWeight.SemiBold)
                    if (q.affectedTasks > 0) T("This also deletes ${q.affectedTasks} linked task(s) and related reminders/notes.", size = 12, color = Color(0xFF8A7440))
                    if (q.kind == "approval") Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val deletion = q.action?.startsWith("delete_") == true
                        Btn(if (deletion) "Approve deletion" else "Allow", { onResume?.invoke(true, null) }, kind = if (deletion) BK.Danger else BK.Primary, enabled = !busy && onResume != null)
                        Btn("Deny", { onResume?.invoke(false, null) }, kind = BK.Secondary, enabled = !busy && onResume != null)
                    } else q.options.forEach { o ->
                        Row(Modifier.fillMaxWidth().clip(R8).background(Color.White).border(1.dp, Color(0xFFEADFC4), R8).clickable(enabled = !busy && onResume != null) { onResume?.invoke(null, o.id) }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { T(o.label, size = 13, weight = FontWeight.SemiBold); T(o.detail, size = 11, color = Muted) }
                            Icon(Icons.Filled.ChevronRight, null, Modifier.size(16.dp), tint = Muted)
                        }
                    }
                    if (onCancel != null) Btn("Cancel command", onCancel, kind = BK.Text, icon = Icons.Filled.Close, enabled = !busy)
                }
            }
        }
        ErrorBox(run.error)
        run.results.forEach { r ->
            Column(Modifier.fillMaxWidth().clip(R8).background(Color(0xFFFAFCF7)).border(1.dp, Line, R8).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Check, null, Modifier.size(14.dp), tint = Green); Spacer(Modifier.width(6.dp))
                    T(label(r.action), Modifier.weight(1f), 13, weight = FontWeight.SemiBold); T("CONFIRMED", size = 9, color = Green, spacing = 1.sp, weight = FontWeight.Bold)
                }
                ResultView(r.data)
            }
        }
        if (run.status == "interrupted") T("Execution was interrupted. Review saved results before starting a new command.", size = 12, color = Muted)
    }
}

/** Single-shot dictation into the command box (the web client's useVoiceInput). */
private class VoiceInput(private val ctx: Context, private val onTranscript: (String) -> Unit) {
    var listening by mutableStateOf(false); private set
    var error by mutableStateOf("")
    private var recognizer: SpeechRecognizer? = null

    fun toggle() {
        if (listening) { recognizer?.stopListening(); return }
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) { error = "Speech recognition is not available on this device."; return }
        error = ""
        val r = recognizer ?: SpeechRecognizer.createSpeechRecognizer(ctx).also { recognizer = it }
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(p: Bundle?) {}
            override fun onEvent(t: Int, p: Bundle?) {}
            override fun onResults(res: Bundle?) { listening = false; res?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(onTranscript) }
            override fun onError(code: Int) {
                listening = false
                error = when (code) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "I didn’t catch that. Tap the microphone and try again."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission was denied."
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Voice input needs a network connection."
                    else -> "Voice input failed (code $code)."
                }
            }
        })
        listening = true
        r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN"))
    }
    fun destroy() { recognizer?.destroy(); recognizer = null }
}

@Composable
fun AiWorkspacePage(vm: AppModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var conversation by remember { mutableStateOf<String?>(null) }
    var runId by remember { mutableStateOf<String?>(null) }
    var run by remember { mutableStateOf<Run?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    var conversations by remember { mutableStateOf(listOf<Conversation>()) }
    var messages by remember { mutableStateOf(listOf<Message>()) }
    var sending by remember { mutableStateOf(false) }
    var actionBusy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var picker by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf(false) }
    val voice = remember { VoiceInput(ctx.applicationContext) { t -> text = (if (text.isNotBlank()) text.trim() + " " else "") + t } }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) voice.toggle() else voice.error = "Microphone permission was denied." }
    DisposableEffect(Unit) { onDispose { voice.destroy() } }

    suspend fun loadConversations() { runCatching { Http.call("/conversations").asArr().objs().map { Conversation(it.s("id"), it.s("title"), it.s("created_at")) } }.onSuccess { conversations = it } }
    suspend fun loadMessages() { conversation?.let { id -> runCatching { Http.call("/conversations/$id/messages").asArr().objs().map { Message(it.s("id"), it.s("role"), it.s("content")) } }.onSuccess { messages = it } } }

    LaunchedEffect(Unit) { loadConversations() }
    LaunchedEffect(vm.runs, runId) {
        if (runId == null) vm.runs.firstOrNull { it.active || it.status == "awaiting_input" }?.let { runId = it.id; conversation = it.conversationId }
    }
    LaunchedEffect(conversation) { if (conversation == null) messages = emptyList() else loadMessages() }
    LaunchedEffect(runId, tick) {
        val id = runId ?: run { return@LaunchedEffect }
        while (true) {
            val r = try { Run.from(Http.call("/agent-runs/$id").asObj()) } catch (e: Exception) { error = e.message; break }
            run = r
            if (!r.active) break
            delay(1000)
        }
    }
    LaunchedEffect(run?.status) {
        val r = run ?: return@LaunchedEffect
        loadMessages()
        if (!r.active) vm.refreshAll()
    }
    val busy = sending || run?.active == true || run?.status == "awaiting_input"

    fun send() {
        if (text.isBlank() || busy || voice.listening) return
        scope.launch {
            sending = true; error = null
            try {
                val created = Run.from(Http.call("/commands", "POST", obj("input" to text, "conversation_id" to conversation)).asObj())
                run = created; runId = created.id; conversation = created.conversationId; text = ""
                loadConversations(); loadMessages()
            } catch (e: Exception) { error = e.message }
            sending = false
        }
    }
    fun resume(approved: Boolean?, selected: String?) {
        scope.launch {
            actionBusy = true
            try { Http.call("/agent-runs/$runId/resume", "POST", JSONObject().also { b -> approved?.let { b.put("approved", it) }; selected?.let { b.put("selected_id", it) } }); tick++ }
            catch (e: Exception) { vm.toast = e.message }
            actionBusy = false
        }
    }
    fun cancel() {
        scope.launch {
            actionBusy = true
            try { Http.call("/agent-runs/$runId/cancel", "POST"); tick++; vm.refreshAll() } catch (e: Exception) { vm.toast = e.message }
            actionBusy = false
        }
    }

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) { Eyebrow("THINK OUT LOUD"); T("AI Workspace", size = 24, weight = FontWeight.SemiBold) }
            Box {
                Btn("History", { picker = true }, kind = BK.Secondary, icon = Icons.Filled.History)
                DropdownMenu(picker, { picker = false }) {
                    if (conversations.isEmpty()) DropdownMenuItem(text = { T("Your conversations will appear here.", color = Muted, size = 13) }, onClick = { picker = false })
                    conversations.forEach { c ->
                        DropdownMenuItem(text = { Column { T(c.title, size = 13, maxLines = 1, weight = if (conversation == c.id) FontWeight.Bold else null); T(fmtDate(c.createdAt, true), size = 10, color = Muted) } },
                            leadingIcon = { Icon(Icons.Filled.Chat, null, Modifier.size(15.dp)) },
                            onClick = { conversation = c.id; runId = vm.runs.firstOrNull { it.conversationId == c.id }?.id; run = null; picker = false })
                    }
                }
            }
            Btn("New", { conversation = null; runId = null; run = null; text = ""; error = null }, kind = BK.Secondary, icon = Icons.Filled.Add, enabled = !busy)
        }
        Panel(Modifier.weight(1f), pad = 0.dp) {
            Row(Modifier.fillMaxWidth().clickable { info = !info }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconTile(Icons.Filled.SmartToy, size = 38); Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) { T("AgentOS assistant", size = 14, weight = FontWeight.SemiBold); T("Plans first. Validates. Then acts.", size = 11, color = Muted) }
                Pill("TOOLS + MCP")
            }
            if (info) Column(Modifier.fillMaxWidth().background(Soft).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Eyebrow("MODEL ROUTING")
                T("OpenRouter → planning", size = 12, weight = FontWeight.SemiBold); T(vm.config?.planner?.model ?: "", size = 11, color = Muted)
                T("Groq → research", size = 12, weight = FontWeight.SemiBold); T(vm.config?.research?.model ?: "", size = 11, color = Muted)
                T(if (vm.config?.planner?.configured == true) "● Planner configured" else "● API key needed", size = 11, color = Green)
                vm.config?.let { if (it.laptop) { if (it.gmail) T("● Gmail connected", size = 11, color = Green) else GmailConnect(vm) } }
            }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (conversation == null && run == null) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconTile(Icons.Filled.AutoAwesome, size = 56)
                        T("What’s on your mind?", size = 20, weight = FontWeight.SemiBold); T("Start with an idea, a task, or a question about your work.", size = 13, color = Muted, align = TextAlign.Center)
                        listOf("Create a project called DBMS Demo with tasks ER diagram, SQL queries, and report.", "Show my pending tasks.", "How many tasks have I completed?", "Show my upcoming reminders.").forEach { s ->
                            Row(Modifier.fillMaxWidth().clip(R8).border(1.dp, Line, R8).clickable { text = s }.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                                T(s, Modifier.weight(1f), 13); Icon(Icons.Filled.ArrowForward, null, Modifier.size(14.dp), tint = Muted)
                            }
                        }
                    }
                } else {
                    messages.forEach { m ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.size(30.dp).clip(CircleShape).background(if (m.role == "user") Soft else Leaf), contentAlignment = Alignment.Center) {
                                if (m.role == "user") T("YOU", size = 8, weight = FontWeight.Bold, color = Muted) else Icon(Icons.Filled.SmartToy, null, Modifier.size(16.dp), tint = LeafText)
                            }
                            Column(Modifier.weight(1f)) { T(if (m.role == "user") "You" else "AgentOS", size = 12, weight = FontWeight.SemiBold); T(m.content, size = 13, lineHeight = 20.sp) }
                        }
                    }
                    run?.let { RunDetail(it, ::resume, ::cancel, actionBusy) }
                }
                ErrorBox(error)
            }
            T(if (voice.listening) "Listening… tap the microphone to stop." else "Voice uses Android speech recognition. Review the text before sending.", Modifier.padding(horizontal = 12.dp), 11, Muted)
            ErrorBox(voice.error.ifEmpty { null })
            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(text, { if (it.length <= 4000) text = it }, Modifier.weight(1f).heightIn(max = 130.dp), shape = R8, maxLines = 5,
                    placeholder = { T("Create a project, update a task, or ask about your work…", color = Color(0xFFA6AEA7), size = 13) },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Green, unfocusedBorderColor = Line))
                Box(Modifier.size(46.dp).clip(R8).background(if (voice.listening) Danger else Soft).clickable(enabled = !busy) {
                    if (ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) voice.toggle() else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }, contentAlignment = Alignment.Center) { Icon(if (voice.listening) Icons.Filled.MicOff else Icons.Filled.Mic, if (voice.listening) "Stop voice input" else "Start voice input", Modifier.size(19.dp), tint = if (voice.listening) Color.White else Dark) }
                val canSend = text.isNotBlank() && !busy && !voice.listening
                Box(Modifier.size(46.dp).clip(R8).background(if (canSend) Dark else Dark.copy(alpha = .4f)).clickable(enabled = canSend) { send() }, contentAlignment = Alignment.Center) {
                    if (sending) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp) else Icon(Icons.AutoMirrored.Filled.Send, "Send command", Modifier.size(19.dp), tint = Color.White)
                }
            }
            T("Changes use authorized tools. Laptop access and deletions require your approval.", Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp), 10, Muted)
        }
    }
}

// ── Agent runs ──

@Composable
fun RunsPage(vm: AppModel) {
    var filter by remember { mutableStateOf("all") }
    var selected by remember { mutableStateOf<Run?>(null) }
    val filtered = vm.runs.filter { (filter == "all" || it.status == filter) && it.input.contains(vm.search, true) }
    PageColumn {
        PageHeading("SEE HOW THE WORK HAPPENED", "Agent Runs", "Plans, tools, outcomes, and a record of every command.")
        Tabs(listOf("all", "completed", "awaiting_input", "failed", "cancelled").map { it to label(it) }, filter) { filter = it }
        T("${filtered.size} runs", size = 12, color = Muted)
        Panel {
            if (filtered.isEmpty()) Empty("Every command leaves a trail", "Run a command in the AI workspace to see its plan and results here.")
            filtered.forEach { r ->
                Row(Modifier.fillMaxWidth().clip(R8).clickable { selected = r }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Filled.SmartToy, size = 36); Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) { T(r.input, size = 14, weight = FontWeight.Medium, maxLines = 2); T("${fmtDate(r.startedAt)} · ${r.results.size}/${r.steps.size} steps confirmed", size = 11, color = Muted) }
                    Badge(r.status); Icon(Icons.Filled.ChevronRight, null, Modifier.size(18.dp), tint = Muted)
                }
            }
        }
    }
    selected?.let { AppDialog("Run details", "Review the recorded plan and confirmed tool results.", { selected = null }) { RunDetail(it) } }
}

// ───────────────────────────── 10 · Activity ─────────────────────────────

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AgentOSTheme {
                val vm: AppModel = viewModel()
                vm.init(getSharedPreferences("agentos", MODE_PRIVATE))
                Surface(Modifier.fillMaxSize(), color = Bg) { if (vm.user == null) AuthScreen(vm) else Shell(vm) }
            }
        }
    }
}
