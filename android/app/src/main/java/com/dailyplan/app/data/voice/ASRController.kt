// ASRController.kt
// 平台无关 ASR 抽象（规格 M3 §2.2）。
// v1 实现 NativeASRController（Android SpeechRecognizer）；v1.1 可加 Xfyun/Whisper 同协议替换（接口先行，架构 §1.2 #5）。
//
// 语义约束（双端一致）：
// - start 前必须先 requestPermission()==GRANTED，否则应直接降级，不得静默无反应。
// - onPartial 仅用于展示；onFinal 才作为落库句段边界（交给领域层 VoiceTaskSplitter 切分）。
// - stop() 停止前对尾句再回调一次 onFinal（空则不回调，R-X4）。

package com.dailyplan.app.data.voice

interface ASRController {
    /** 当前是否可用（语言包/能力层判断，不触发授权弹窗） */
    val isAvailable: Boolean

    /**
     * 请求授权，返回最终状态。
     * Android 无独立「语音识别」授权；本方法仅校验当前麦克风(RECORD_AUDIO)是否已授权，
     * 真正弹系统授权由 UI 层经 Activity 的运行时权限申请完成（规格 §4.4）。
     */
    suspend fun requestPermission(): PermissionState

    /**
     * 开始持续听写。
     * @param onPartial 流式中间结果（实时刷新输入框，不可落库）
     * @param onFinal   确定句段边界（句末标点 / 长停顿 / 识别结束），调用方据此切分落库
     */
    fun start(onPartial: (String) -> Unit, onFinal: (String) -> Unit)

    /** 停止听写并释放资源；停止前对尾句再回调一次 onFinal */
    fun stop()

    /** 当前已识别但未落库的缓冲文本（供「停止并保存为文字条目」使用，规格 §6.2） */
    fun getBufferedText(): String

    /** 降级回调：关语音按钮 + Toast 引导（规格 §6）。由调用方在 start 前设置 */
    var onDegrade: ((DegradeReason) -> Unit)?
}
