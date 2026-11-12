package io.github.haeyeonyou.familytrip.voice

import io.github.haeyeonyou.familytrip.data.AppLog
import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType as SttError
import ai.pleos.playground.stt.constant.Mode as SttMode
import ai.pleos.playground.stt.listener.OnInitializeListener as SttInit
import ai.pleos.playground.stt.listener.ResultListener
import ai.pleos.playground.tts.TextToSpeech as PleosTts
import ai.pleos.playground.tts.constant.ErrorType as TtsError
import ai.pleos.playground.tts.constant.Mode as TtsMode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener as TtsInit
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

/** 듣기 결과. Heard는 인식한 문장, Failed는 실패 사유. */
sealed interface ListenResult {
    data class Heard(val text: String) : ListenResult
    data class Failed(val reason: String) : ListenResult
}

/**
 * 음성 입출력. 차량에서는 Pleos SpeechToText/TextToSpeech(ON_DEVICE, 한국어)를 먼저 쓰고,
 * 초기화나 인식에 실패하면 Android 기본 SpeechRecognizer/TextToSpeech로 넘어간다.
 * 둘 다 안 되면 화면의 문장 버튼·입력창으로 같은 처리를 태운다 ([VoiceController]).
 * 모든 콜백은 메인 스레드로 돌려준다.
 */
class VoiceIo(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())

    // 읽어 주기

    private var pleosTts: PleosTts? = null
    private var pleosTtsReady = false
    private var pleosTtsFailed = false
    private var androidTts: TextToSpeech? = null
    private var androidTtsReady = false
    private var speakDone: (() -> Unit)? = null

    /**
     * [text]를 읽는다. [volume]은 0~1이다. Pleos TTS는 크기 조절이 없어서 1보다 작게 읽어야 하면
     * (수면 모드) 크기를 정할 수 있는 Android TTS를 쓰고, 그게 없을 때만 Pleos TTS로 읽는다.
     * 끝나면 [onDone]을 부른다 (실패해도 부른다).
     */
    fun speak(text: String, volume: Float = 1f, onDone: () -> Unit = {}) {
        main.post {
            speakDone = onDone
            val quiet = volume < 1f
            when {
                quiet && androidTtsUsable() -> speakAndroid(text, volume)
                !pleosTtsFailed && ensurePleosTts() -> speakPleos(text)
                androidTtsUsable() -> speakAndroid(text, volume)
                else -> finishSpeak()
            }
        }
    }

    fun stopSpeaking() {
        main.post {
            runCatching { pleosTts?.stop() }
            runCatching { androidTts?.stop() }
            speakDone = null
        }
    }

    private fun finishSpeak() {
        val done = speakDone
        speakDone = null
        done?.invoke()
    }

    private fun ensurePleosTts(): Boolean {
        if (pleosTtsReady) return true
        if (pleosTts == null) {
            runCatching {
                val tts = PleosTts(context, TtsMode.ON_DEVICE)
                tts.addEventListener(object : EventListener {
                    override fun onStart() = Unit
                    override fun onReady() = Unit
                    override fun onDone() {
                        main.post { finishSpeak() }
                    }

                    override fun onError(type: TtsError) {
                        AppLog.w(TAG, "Pleos TTS error $type")
                        main.post { finishSpeak() }
                    }

                    override fun onStop() = Unit
                    override fun onUpdatedRms(rms: Double) = Unit
                })
                tts.initialize(object : TtsInit {
                    override fun onSuccess() {
                        runCatching { tts.setLocale(Locale.KOREA) }
                        pleosTtsReady = true
                        Log.i(TAG, "Pleos TTS ready")
                    }

                    override fun onFailure(type: TtsError) {
                        AppLog.w(TAG, "Pleos TTS init failed $type")
                        pleosTtsFailed = true
                    }
                })
                pleosTts = tts
            }.onFailure {
                AppLog.w(TAG, "Pleos TTS unavailable", it)
                pleosTtsFailed = true
            }
        }
        return pleosTtsReady
    }

    private fun speakPleos(text: String) {
        runCatching { pleosTts?.speak(text, true) }.onFailure {
            AppLog.w(TAG, "Pleos TTS speak failed", it)
            pleosTtsFailed = true
            if (androidTtsUsable()) speakAndroid(text, 1f) else finishSpeak()
        }
    }

    private fun androidTtsUsable(): Boolean {
        if (androidTts == null) {
            androidTts = TextToSpeech(context) { status ->
                androidTtsReady = status == TextToSpeech.SUCCESS &&
                    androidTts?.setLanguage(Locale.KOREA).let { it != TextToSpeech.LANG_MISSING_DATA && it != TextToSpeech.LANG_NOT_SUPPORTED }
                Log.i(TAG, "Android TTS ready=$androidTtsReady")
            }.also {
                it.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit
                    override fun onDone(utteranceId: String?) {
                        main.post { finishSpeak() }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        main.post { finishSpeak() }
                    }
                })
            }
        }
        return androidTtsReady
    }

    private fun speakAndroid(text: String, volume: Float) {
        val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume.coerceIn(0f, 1f)) }
        val result = androidTts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "care") ?: TextToSpeech.ERROR
        if (result == TextToSpeech.ERROR) finishSpeak()
    }

    // 듣기

    private var pleosStt: SpeechToText? = null
    private var pleosSttReady = false
    private var pleosSttFailed = false
    private var recognizer: SpeechRecognizer? = null
    private var pendingListen: ((ListenResult) -> Unit)? = null
    private var pendingPartial: ((String) -> Unit)? = null
    private var timeout: Runnable? = null

    /** 한 문장을 듣는다. [timeoutMs] 안에 못 알아들으면 [ListenResult.Failed]로 끝낸다. */
    fun listen(timeoutMs: Long = LISTEN_TIMEOUT_MS, onPartial: (String) -> Unit = {}, onResult: (ListenResult) -> Unit) {
        main.post {
            cancelListenNow()
            pendingListen = onResult
            pendingPartial = onPartial
            timeout = Runnable { finishListen(ListenResult.Failed("시간 안에 말씀을 듣지 못했어요")) }.also { main.postDelayed(it, timeoutMs) }
            if (!pleosSttFailed && startPleosStt()) Unit else startAndroidStt()
        }
    }

    fun cancelListen() {
        main.post { cancelListenNow() }
    }

    private fun cancelListenNow() {
        timeout?.let(main::removeCallbacks)
        timeout = null
        pendingListen = null
        runCatching { pleosStt?.stop() }
        runCatching { recognizer?.cancel() }
    }

    private fun finishListen(result: ListenResult) {
        timeout?.let(main::removeCallbacks)
        timeout = null
        val cb = pendingListen
        pendingListen = null
        runCatching { pleosStt?.stop() }
        cb?.invoke(result)
    }

    /** Pleos 인식을 시작한다. 쓸 수 없으면 false. 처음이면 초기화가 끝난 뒤 이어서 듣는다. */
    private fun startPleosStt(): Boolean {
        if (pleosStt == null) {
            val created = runCatching {
                val stt = SpeechToText(context, SttMode.ON_DEVICE)
                stt.addListener(object : ResultListener {
                    override fun onUpdated(text: String, isFinal: Boolean) {
                        if (text.isBlank()) return
                        if (isFinal) main.post { finishListen(ListenResult.Heard(text)) } else main.post { pendingPartial?.invoke(text) }
                    }

                    override fun onUpdatedEpdData(epdOn: Long, epdOff: Long) = Unit
                    override fun onStartedRecognition() = Unit
                    override fun onEndedRecognition() = Unit
                    override fun onError(type: SttError) {
                        AppLog.w(TAG, "Pleos STT error $type")
                        main.post {
                            if (pendingListen != null) {
                                pleosSttFailed = true
                                startAndroidStt()
                            }
                        }
                    }

                    override fun onReady() = Unit
                    override fun onUpdatedRms(rms: Double) = Unit
                })
                stt.initialize(object : SttInit {
                    override fun onSuccess() {
                        runCatching { stt.setLocale(Locale.KOREA) }
                        pleosSttReady = true
                        Log.i(TAG, "Pleos STT ready")
                        main.post { if (pendingListen != null) requestPleos() }
                    }

                    override fun onFailure(type: SttError) {
                        AppLog.w(TAG, "Pleos STT init failed $type")
                        pleosSttFailed = true
                        main.post { if (pendingListen != null) startAndroidStt() }
                    }
                })
                stt
            }.onFailure {
                AppLog.w(TAG, "Pleos STT unavailable", it)
                pleosSttFailed = true
            }.getOrNull() ?: return false
            pleosStt = created
            return true
        }
        if (pleosSttReady) requestPleos()
        return true
    }

    private fun requestPleos() {
        runCatching { pleosStt?.request() }.onFailure {
            AppLog.w(TAG, "Pleos STT request failed", it)
            pleosSttFailed = true
            startAndroidStt()
        }
    }

    private fun startAndroidStt() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            finishListen(ListenResult.Failed("이 기기에는 음성 인식이 없어요"))
            return
        }
        val r = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also {
            it.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    finishListen(if (text.isBlank()) ListenResult.Failed("잘 알아듣지 못했어요") else ListenResult.Heard(text))
                }

                override fun onError(error: Int) {
                    AppLog.w(TAG, "Android STT error $error")
                    finishListen(ListenResult.Failed("잘 알아듣지 못했어요"))
                }

                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onPartialResults(partialResults: Bundle?) {
                    val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    if (text.isNotBlank()) pendingPartial?.invoke(text)
                }
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
            recognizer = it
        }
        runCatching {
            r.startListening(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
                    .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true),
            )
        }.onFailure {
            AppLog.w(TAG, "Android STT start failed", it)
            finishListen(ListenResult.Failed("음성 인식을 시작하지 못했어요"))
        }
    }

    fun release() {
        runCatching { pleosTts?.release() }
        runCatching { pleosStt?.release() }
        runCatching { androidTts?.shutdown() }
        runCatching { recognizer?.destroy() }
    }

    private companion object {
        const val TAG = "VoiceIo"
        const val LISTEN_TIMEOUT_MS = 8_000L
    }
}
