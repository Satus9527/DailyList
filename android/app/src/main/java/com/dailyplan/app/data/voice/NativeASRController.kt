// NativeASRController.kt
// Android 原生 ASR 实现（规格 M3 §4）：android.speech.SpeechRecognizer 持续听 +
// 流式 partial + 离线优先/联网回退(P0-1) + 长停顿边界(配置阈值) + 失败降级(§6) + AudioFocus(§4.4)。
//
// 关键非硬编码约定（P0-4）：切分标点集 / 长停顿阈值 / 顿号换行开关一律来自 ASRSplitConfig（assets JSON），
// 本类不出现 "。！？；" / 1200 等字面量常量。

package com.dailyplan.app.data.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import com.dailyplan.app.util.ASRSplitConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NativeASRController(
    private val context: Context,
    private val config: ASRSplitConfig?   // 来自 assets/asr_split_config.json（单例）；null 表示配置缺失
) : ASRController {

    private var recognizer: SpeechRecognizer? = null
    private var onPartial: ((String) -> Unit)? = null
    private var onFinal: ((String) -> Unit)? = null
    override var onDegrade: ((DegradeReason) -> Unit)? = null

    private var listening = false
    private val latestPartial = StringBuilder()   // 缓存当前句段，用于长停顿/停止收尾

    // 长停顿阈值：取自配置（禁止硬编码）；配置缺失时置极大值使其不触发（语音能力将被判不可用）
    private val pauseThresholdMs: Long = config?.splitPauseThresholdMs?.toLong() ?: Long.MAX_VALUE
    private var lastSpeechTime = 0L
    private var silenceStart: Long? = null
    private var awaitingResultsAfterPause = false   // 长停顿已收尾，后续 onResults 不再重复落库
    private var didFallbackToOnline = false         // 离线→联网回退已尝试一次（防无限重试）

    // 本轮识别（一次 beginListening → 结束）已提交的 final 文本（trim 归一化后）。
    // onEndOfSpeech 与 onResults 同属一轮，可能不按序各发一次相同 final；
    // 记录后仅在该轮内对相同文本去重，确保同一句只向 onFinal 消费方提交一次（D1 修复）。
    private var lastFinalTextInTurn: String? = null

    // AudioFocus 相关
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var audioFocusPaused = false            // 临时丢失暂停中（保留 listening，待恢复重听）

    override val isAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    override suspend fun requestPermission(): PermissionState = withContext(Dispatchers.Main) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) PermissionState.GRANTED else PermissionState.DENIED
    }

    override fun start(onPartial: (String) -> Unit, onFinal: (String) -> Unit) {
        this.onPartial = onPartial
        this.onFinal = onFinal
        if (!isAvailable) {
            onDegrade?.invoke(DegradeReason.ASR_ERROR)   // 能力不可用 → 降级
            return
        }
        ensureAudioFocus()   // 拿不到焦点不阻断识别（多数场景仍可识别）
        listening = true
        didFallbackToOnline = false
        latestPartial.clear()
        lastSpeechTime = System.currentTimeMillis()
        silenceStart = null
        awaitingResultsAfterPause = false
        // SpeechRecognizer 必须在主线程创建
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(buildListener())
        }
        beginListening()
    }

    /** 单次识别意图：FREE_FORM + zh-CN + 流式 partial + 离线优先（P0-1） */
    private fun beginListening() {
        if (!listening || recognizer == null) return
        lastFinalTextInTurn = null   // 新一轮识别：重置 final 去重窗口（允许后续轮次提交相同文本）
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)   // 流式中间结果
            // 离线优先：优先设备端离线包；无离线包时系统自动回退联网识别（P0-1）
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        recognizer?.startListening(intent)
    }

    private fun buildListener() = object : RecognitionListener {
        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return
            val text = matches.firstOrNull() ?: return
            latestPartial.clear().append(text)
            lastSpeechTime = System.currentTimeMillis()
            silenceStart = null
            onPartial?.invoke(text)                    // 实时显示（不落库）
        }

        override fun onResults(results: Bundle?) {
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull().orEmpty()
            // 若长停顿已收尾并触发过 onFinal，本次 onResults 视为冗余，不再重复落库（避免重复句段）
            if (!awaitingResultsAfterPause && text.isNotEmpty()) {
                latestPartial.clear()
                emitFinal(text)                        // 一个句段边界完成（内部去重）
            }
            awaitingResultsAfterPause = false
            if (listening) beginListening()            // 持续听：结束即重启（规格 §4.2）
        }

        override fun onEndOfSpeech() {
            // 端侧判定一条说完：若长停顿尚未处理，把当前缓冲作为 final 边界吐出
            val text = latestPartial.toString()
            if (!awaitingResultsAfterPause && text.isNotEmpty()) {
                latestPartial.clear()
                emitFinal(text)                        // 与 onResults 可能重复，交由 emitFinal 去重
            }
            // onResults 会随后触发并重启循环；此处不重复重启
        }

        override fun onError(error: Int) = handleError(error)

        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {
            lastSpeechTime = System.currentTimeMillis()
            silenceStart = null
        }

        override fun onRmsChanged(rmsdB: Float) {
            // 长停顿检测：rms 低于静音阈值累计 > pauseThresholdMs（取自配置） → 触发 final 边界
            val now = System.currentTimeMillis()
            val speaking = rmsdB > SILENCE_RMS_THRESHOLD   // 平台音频调参，非拆分配置，允许硬编码
            if (speaking) {
                lastSpeechTime = now
                silenceStart = null
            } else {
                if (silenceStart == null) silenceStart = now
                val paused = now - lastSpeechTime
                if (paused >= pauseThresholdMs && latestPartial.isNotBlank()) {
                    val text = latestPartial.toString()
                    latestPartial.clear()
                    awaitingResultsAfterPause = true        // onResults 不再重复落库
                    emitFinal(text)                         // 长停顿边界（P0-4，内部去重）
                    lastSpeechTime = now
                    silenceStart = null
                }
            }
        }

        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    /** 错误映射与降级（规格 §6.1 / §4.4） */
    private fun handleError(error: Int) {
        when (error) {
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                degrade(DegradeReason.PERMISSION_DENIED)
            SpeechRecognizer.ERROR_NETWORK -> {
                // 首次联网失败重试一次（离线→联网回退），仍失败则降级
                if (!didFallbackToOnline) {
                    didFallbackToOnline = true
                    if (listening) beginListening()
                } else degrade(DegradeReason.NETWORK)
            }
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                // 静音/无匹配：不强行降级，重启循环继续听（R-X4 不生成空条）
                if (listening) beginListening()
            }
            else -> {
                // 其他不可恢复错误 → 降级（关按钮 + 引导文字）
                if (listening) degrade(DegradeReason.ASR_ERROR)
            }
        }
    }

    /** 进入降级：停止音频 + 通知 UI（关按钮 + Toast） */
    private fun degrade(reason: DegradeReason) {
        stopInternal()
        onDegrade?.invoke(reason)
    }

    override fun stop() = stopInternal()

    /**
     * 提交一条 final 文本，并在「本轮识别」内对相同文本去重（D1 修复）。
     * 仅当文本 trim 后非空且与本轮已提交 final 不同，才回调 onFinal；
     * 这样 onEndOfSpeech 与 onResults 先后顺序不定地各发一次相同文本时，只会落库一次。
     */
    private fun emitFinal(text: String) {
        val norm = text.trim()
        if (norm.isEmpty()) return
        if (norm == lastFinalTextInTurn) return   // 同句已在本次识别轮次提交过 → 跳过
        lastFinalTextInTurn = norm
        onFinal?.invoke(text)
    }

    /** 内部停止：释放识别器；停止前把尾句作为一次 final 收尾（空则不回调，R-X4） */
    private fun stopInternal() {
        listening = false
        awaitingResultsAfterPause = false
        lastFinalTextInTurn = null   // 会话结束，重置去重窗口，保证尾句无条件提交一次（R-E2）
        val tail = latestPartial.toString()
        if (tail.isNotBlank()) {
            latestPartial.clear()
            emitFinal(tail)                          // 尾句单独成条（R-E2）
        }
        runCatching { recognizer?.stopListening() }
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
        abandonAudioFocus()
    }

    override fun getBufferedText(): String = latestPartial.toString()

    // —— AudioFocus（规格 §4.4）——
    private fun ensureAudioFocus(): Boolean {
        val listener = AudioManager.OnAudioFocusChangeListener { focusChange ->
            when (focusChange) {
                AudioManager.AUDIOFOCUS_LOSS -> {
                    // 永久丢失：降级
                    if (listening) degrade(DegradeReason.AUDIOFOCUS_LOST)
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    // 临时丢失：暂停（保留 listening，不销毁），待恢复
                    audioFocusPaused = true
                    runCatching { recognizer?.stopListening() }
                }
                AudioManager.AUDIOFOCUS_GAIN -> {
                    // 恢复：若仍应听写，重新 beginListening
                    if (audioFocusPaused && listening) {
                        audioFocusPaused = false
                        beginListening()
                    }
                }
            }
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setOnAudioFocusChangeListener(listener)
                .build()
            audioFocusRequest = req
            audioManager.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(listener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
                == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
        audioFocusRequest = null
    }

    companion object {
        // 平台音频 RMS 静音阈值（dB）。属音频调参，非拆分配置，允许硬编码（P0-4 不约束此项）。
        private const val SILENCE_RMS_THRESHOLD = 1.0f
    }
}
