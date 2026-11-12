package io.github.haeyeonyou.familytrip.data

import android.app.Application
import android.os.Build
import android.util.Log
import io.github.haeyeonyou.familytrip.BuildConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.File
import java.time.OffsetDateTime
import java.util.UUID

// 앱 크래시·오류 원격 수집 (#261). 기기 파일에 모았다가 서버 POST /logs/app으로 보낸다(서버가 NAS 로그 폴더에 저장).
// 개인정보는 보내지 않는다: 위치 좌표처럼 보이는 숫자는 지우고, 기기 모델·앱 버전·태그·메시지·스택만 담는다.

@Serializable
data class AppLogEvent(
    val ts: String,
    val level: String,
    val tag: String,
    val message: String,
    val stack: String?,
    val count: Int,
)

@Serializable
data class AppLogBatch(
    val install_id: String,
    val app_version: String,
    val device: String,
    val os: String,
    val events: List<AppLogEvent>,
)

/**
 * 보낼 로그를 파일 한 줄씩 쌓는 큐. 같은 오류가 [repeatWindowMs] 안에 되풀이되면 한 줄로 묶고 [count]로 센다
 * (주행 중 오프라인일 때 네트워크 오류가 폭주하지 않게). 기기 시계([now])를 주입해 단위 테스트한다.
 */
class LogQueue(
    private val file: File,
    private val now: () -> Long = System::currentTimeMillis,
    private val timestamp: () -> String = { OffsetDateTime.now().toString() },
    private val maxLines: Int = 300,
    private val repeatWindowMs: Long = 10 * 60_000L,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()
    /** 같은 오류 키 → (처음 남긴 시각, 그 뒤로 묶어 둔 수, 대표 이벤트) */
    private val repeats = HashMap<String, Triple<Long, Int, AppLogEvent>>()

    fun record(level: String, tag: String, message: String, stack: String?) {
        val event = AppLogEvent(timestamp(), level, tag.take(60), scrub(message).take(1900), stack?.let { scrub(it).take(15000) }, 1)
        val key = "$level|${event.tag}|${event.message.take(200)}"
        synchronized(lock) {
            val t = now()
            val seen = repeats[key]
            if (level != "crash" && seen != null && t - seen.first < repeatWindowMs) {
                repeats[key] = Triple(seen.first, seen.second + 1, seen.third)
                return
            }
            seen?.takeIf { it.second > 0 }?.let { append(it.third.copy(count = it.second)) }
            repeats[key] = Triple(t, 0, event)
            append(event)
        }
    }

    /** 묶어 두었던 반복 수를 큐에 내보낸다 (보내기 직전). */
    fun flushRepeats() = synchronized(lock) {
        for ((key, value) in repeats.entries.toList()) {
            if (value.second > 0) {
                append(value.third.copy(ts = timestamp(), count = value.second))
                repeats[key] = Triple(value.first, 0, value.third)
            }
        }
    }

    fun peek(limit: Int): List<AppLogEvent> = synchronized(lock) {
        readLines().take(limit).mapNotNull { runCatching { json.decodeFromString(AppLogEvent.serializer(), it) }.getOrNull() }
    }

    /** 앞에서부터 [n]줄을 지운다 (보내기에 성공했거나 서버가 받지 않는 묶음). */
    fun drop(n: Int) = synchronized(lock) {
        val rest = readLines().drop(n)
        writeLines(rest)
    }

    fun size(): Int = synchronized(lock) { readLines().size }

    private fun append(event: AppLogEvent) {
        val lines = readLines() + json.encodeToString(AppLogEvent.serializer(), event)
        writeLines(lines.takeLast(maxLines))
    }

    private fun readLines(): List<String> = if (file.exists()) file.readLines().filter { it.isNotBlank() } else emptyList()

    private fun writeLines(lines: List<String>) {
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        tmp.writeText(if (lines.isEmpty()) "" else lines.joinToString("\n", postfix = "\n"))
        tmp.renameTo(file)
    }

    companion object {
        // 37.5665, 126.9780 같은 위경도(소수 4자리 이상)를 지운다
        private val COORD = Regex("-?\\d{1,3}\\.\\d{4,}")
        fun scrub(text: String): String = COORD.replace(text, "<좌표>")
    }
}

/** 앱 어디서나 쓰는 진입점. [init]은 Application.onCreate에서 가장 먼저 부른다. */
object AppLog {
    private const val TAG = "AppLog"
    private var queue: LogQueue? = null
    private var installId: String = "unknown-install"

    fun init(app: Application) {
        val dir = File(app.filesDir, "applog")
        queue = LogQueue(File(dir, "queue.jsonl"))
        installId = runCatching {
            val f = File(dir, "install_id")
            if (f.exists()) f.readText().trim() else UUID.randomUUID().toString().replace("-", "").also { dir.mkdirs(); f.writeText(it) }
        }.getOrDefault(installId)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            // 프로세스가 죽기 전에 파일에 남기고, 원래 처리기(시스템 크래시 처리)로 넘긴다. 다음 실행 때 보낸다.
            runCatching { queue?.record("crash", "thread:${thread.name}", e.toString(), e.stackTraceToString()) }
            previous?.uncaughtException(thread, e)
        }
    }

    /** Log.w와 같고, 원격 수집 큐에도 남긴다. */
    fun w(tag: String, message: String, tr: Throwable? = null): Int {
        record("warn", tag, message, tr)
        return if (tr == null) Log.w(tag, message) else Log.w(tag, message, tr)
    }

    fun e(tag: String, message: String, tr: Throwable? = null): Int {
        record("error", tag, message, tr)
        return if (tr == null) Log.e(tag, message) else Log.e(tag, message, tr)
    }

    private fun record(level: String, tag: String, message: String, tr: Throwable?) {
        val text = if (tr == null) message else "$message: $tr"
        runCatching { queue?.record(level, tag, text, tr?.stackTraceToString()) }
    }

    /** 쌓인 로그를 50줄씩 보낸다. 네트워크·서버 오류면 남겨 두고 다음에 다시 보낸다. */
    suspend fun upload(api: TripApi) {
        val q = queue ?: return
        q.flushRepeats()
        while (true) {
            val events = q.peek(50)
            if (events.isEmpty()) return
            val batch = AppLogBatch(
                install_id = installId,
                app_version = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                device = "${Build.MANUFACTURER} ${Build.MODEL}".take(100),
                os = "Android ${Build.VERSION.RELEASE}",
                events = events,
            )
            val sent = try {
                val res = api.sendLogs(batch)
                // 422(형식 오류)는 다시 보내도 안 받으므로 버린다. 429·5xx는 남겨 둔다.
                res.isSuccessful || res.code() == 422
            } catch (e: Exception) {
                if (e !is java.io.IOException && e !is HttpException) Log.w(TAG, "log upload failed", e)
                false
            }
            if (!sent) return
            q.drop(events.size)
        }
    }
}
