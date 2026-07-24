// TodayViewModel.kt
// 今日待办 ViewModel：聚合 F1（文字记录）/ F5（完成/编辑/删除/拖拽）/ F6（持久化）与 X/Y 进度。
// UI 经 ViewModel 调 Repository，领域层不依赖具体存储（架构 §7.2）。

package com.dailyplan.app.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailyplan.app.data.local.CategoryEntity
import com.dailyplan.app.data.local.TagEntity
import com.dailyplan.app.data.repository.CategoryRepository
import com.dailyplan.app.data.repository.TagRepository
import com.dailyplan.app.data.repository.TaskRepository
import com.dailyplan.app.data.reminder.NotificationStatusHelper
import com.dailyplan.app.data.reminder.ReminderScheduler
import com.dailyplan.app.data.voice.ASRController
import com.dailyplan.app.data.voice.DegradeReason
import com.dailyplan.app.data.voice.VoiceState
import com.dailyplan.app.domain.model.Priority
import com.dailyplan.app.domain.model.Task
import com.dailyplan.app.domain.model.TaskFilter
import com.dailyplan.app.domain.model.matches
import com.dailyplan.app.domain.model.TaskSource
import com.dailyplan.app.domain.model.todayDateString
import com.dailyplan.app.domain.voice.TaskMergeSplitUseCase
import com.dailyplan.app.domain.voice.VoiceTaskSplitter
import com.dailyplan.app.util.ASRSplitConfig
import com.dailyplan.app.util.SettingsPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Date
import java.util.UUID
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 首页常驻提示状态（规格 §2 / AC-20，D4）：仅在有权限风险时非 null。
 * 埋点调用点：notification_banner_shown（仅标注，不写上报）。
 */
data class NotificationBannerInfo(
    val reason: Reason,
    val deepLink: DeepLink
) {
    enum class Reason { NOTIFICATIONS_DISABLED, DND_ACTIVE }
    enum class DeepLink { APP_NOTIFICATION_SETTINGS, POLICY_ACCESS_SETTINGS }
}

class TodayViewModel(
    private val repository: TaskRepository,
    private val categoryRepository: CategoryRepository,
    private val tagRepository: TagRepository,
    private val reminderScheduler: ReminderScheduler,
    private val asrController: ASRController,
    asrSplitConfig: ASRSplitConfig?,
    private val mergeSplitUseCase: TaskMergeSplitUseCase,
    private val settingsPrefs: SettingsPrefs
) : ViewModel() {

    // M3 语音层：领域拆分器（config 缺失则为 null，语音不可用）
    private val voiceSplitter: VoiceTaskSplitter? =
        asrSplitConfig?.let { VoiceTaskSplitter(it, repository) }

    // 当日任务列表（按展示日取数，含跨 0 点任务，规格 §3.3 / S5）
    private val _tasks = MutableStateFlow<List<Task>>(emptyList())
    val tasks: StateFlow<List<Task>> = _tasks.asStateFlow()

    // M4-S5 三个展示区块：错过的提醒 / 进行中 / 已完成（displayDay == 今日）
    private val _missedTasks = MutableStateFlow<List<Task>>(emptyList())
    val missedTasks: StateFlow<List<Task>> = _missedTasks.asStateFlow()
    private val _inProgressTasks = MutableStateFlow<List<Task>>(emptyList())
    val inProgressTasks: StateFlow<List<Task>> = _inProgressTasks.asStateFlow()
    private val _doneTasks = MutableStateFlow<List<Task>>(emptyList())
    val doneTasks: StateFlow<List<Task>> = _doneTasks.asStateFlow()

    // M4 平铺展示顺序（错过的提醒 → 进行中 → 已完成），供合并「上一条」与拖拽重排定位
    private val _flatOrder = MutableStateFlow<List<Task>>(emptyList())

    // M5 F4 筛选（规格 §3.1 / §3.4）：内存过滤作用于已加载的展示日集合
    private val _filter = MutableStateFlow(TaskFilter())
    val filter: StateFlow<TaskFilter> = _filter.asStateFlow()

    // M5 分类列表（编辑页选择器 + 首页筛选栏用）
    private val _categories = MutableStateFlow<List<CategoryEntity>>(emptyList())
    val categories: StateFlow<List<CategoryEntity>> = _categories.asStateFlow()

    // M5 全部标签（首页筛选栏标签下拉用）
    private val _allTags = MutableStateFlow<List<TagEntity>>(emptyList())
    val allTags: StateFlow<List<TagEntity>> = _allTags.asStateFlow()

    // M5 taskId → 标签 id 集合 映射（内存筛选用，规格 §3.4）
    private val _taskTagIds = MutableStateFlow<Map<UUID, Set<UUID>>>(emptyMap())

    // 输入框文本
    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // MARK: - D4 首页常驻提示
    private val _notificationBanner = MutableStateFlow<NotificationBannerInfo?>(null)
    val notificationBanner: StateFlow<NotificationBannerInfo?> = _notificationBanner.asStateFlow()

    // MARK: - M3 语音状态
    private val _voiceState = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    /** 语音流式中间文本（实时展示，不落库） */
    private val _partialText = MutableStateFlow("")
    val partialText: StateFlow<String> = _partialText.asStateFlow()

    // M4 R-4 语音输入开关（持久化于 SettingsPrefs）
    private val _voiceInputEnabled = MutableStateFlow(settingsPrefs.voiceInputEnabled)
    val voiceInputEnabled: StateFlow<Boolean> = _voiceInputEnabled.asStateFlow()

    /** 进度 X / Y（规格 R-U3 / AC-15）：X=已完成数，Y=当日总数（不受筛选影响，反映当日整体完成度） */
    val doneCount: Int get() = _tasks.value.count { it.isDone }
    val totalCount: Int get() = _tasks.value.size

    init {
        reload()
        loadCategories()
        loadAllTags()
        // 降级回调：关语音按钮 + 引导文字（规格 §6）；Toast 由 UI 观察 voiceState 展示
        asrController.onDegrade = { reason -> onVoiceDegraded(reason) }
    }

    // MARK: - F6 加载（按展示日取数；S5 重归类为展示层，不改 date）
    fun reload() {
        viewModelScope.launch {
            runCatching {
                val all = repository.tasksByDisplayDay(todayDateString())
                val map = repository.taskTagIds()
                _tasks.value = all
                _taskTagIds.value = map
            }.onSuccess { recomputeSections() }
                .onFailure { _errorMessage.value = "加载待办失败：${it.message}" }
        }
    }

    /**
     * M5 重算三区块（错过的提醒 / 进行中 / 已完成），对每个区块统一叠加当前筛选条件（§4.3）。
     * 区块内排序口径沿用 M4；筛选对各区块一致生效。内存过滤，零额外查询。
     */
    private fun recomputeSections() {
        val all = _tasks.value
        val f = _filter.value
        val now = Date()
        val visible = all.filter { f.matches(it, _taskTagIds.value[it.id] ?: emptySet()) }
        // D3 错过区：应响未响（remindAt < now 且未完成）
        _missedTasks.value = visible.filter { !it.isDone && it.remindAt != null && it.remindAt < now }
            .sortedBy { it.remindAt }
        _inProgressTasks.value = visible.filter { !it.isDone && !(it.remindAt != null && it.remindAt < now) }
        _doneTasks.value = visible.filter { it.isDone }
        _flatOrder.value = _missedTasks.value + _inProgressTasks.value + _doneTasks.value
    }

    /** M5 应用筛选（单维/组合）；仅重算展示，不触库（规格 §3.4 推荐路径） */
    fun applyFilter(filter: TaskFilter) {
        _filter.value = filter
        recomputeSections()
    }

    fun onInputChanged(text: String) { _inputText.value = text }

    // MARK: - F1 文字记录
    /** 将输入框文本加入当日列表。去空白；≤500 字，超出截断并提示（AC-29 / R-X5）。 */
    fun addFromInput() {
        val raw = _inputText.value.trim()
        if (raw.isEmpty()) return   // 空内容不生成（AC-1 / R-X4）

        val title = if (raw.length > 500) {
            _errorMessage.value = "内容超过 500 字，已截断。"
            raw.take(500)
        } else raw

        val maxOrder = (_tasks.value.maxOfOrNull { it.sortOrder } ?: -1) + 1
        val task = Task.makeNew(title = title, sortOrder = maxOrder)
        viewModelScope.launch {
            runCatching { repository.add(task) }
                .onSuccess {
                    _inputText.value = ""   // 清空输入框（AC-1）
                    // 若新建任务带提醒（未来 UI 设置 remindAt），登记排程（规格 §7.3 数据流）
                    if (task.remindAt != null) reminderScheduler.schedule(task)
                    reload()
                }
                .onFailure { _errorMessage.value = "添加失败：${it.message}" }
        }
    }

    // MARK: - M3 语音输入（F2）
    /** 开始持续听写。语音开关关闭 / config 缺失 / 能力不可用 → 置 Unavailable，不阻断文字流（规格 §6） */
    fun startVoice() {
        if (!_voiceInputEnabled.value || voiceSplitter == null || !asrController.isAvailable) {
            _voiceState.value = VoiceState.Unavailable
            return
        }
        _voiceState.value = VoiceState.Listening
        _partialText.value = ""
        asrController.start(
            onPartial = { _partialText.value = it },
            onFinal = { text -> commitVoiceSegment(text) }   // 句段边界 → 切分落库
        )
    }

    /** 语音 final 文本 → 领域层切分 → 逐段 add(source=VOICE)；随后刷新列表 */
    private fun commitVoiceSegment(text: String) {
        val splitter = voiceSplitter ?: return
        viewModelScope.launch {
            val startOrder = (_tasks.value.maxOfOrNull { it.sortOrder } ?: -1) + 1
            splitter.commitFinalSegment(text, startOrder = startOrder)
            reload()
        }
    }

    /** 停止听写（用户主动） */
    fun stopVoice() {
        asrController.stop()
        _voiceState.value = VoiceState.Idle
        _partialText.value = ""
    }

    /** 用户手动「落一条」：把当前缓冲/partial 立即切分落库（用户优先，R-E2） */
    fun commitManual() {
        val text = asrController.getBufferedText().ifBlank { _partialText.value }
        if (text.isNotBlank()) {
            _partialText.value = ""
            commitVoiceSegment(text)
        }
    }

    /**
     * 「停止并保存当前已识别文本」为文字条目（source=TEXT，规格 §6.2）。
     * 避免丢失已说内容；为空则不落（R-X4）。
     */
    fun saveBufferedAsText() {
        val text = asrController.getBufferedText().ifBlank { _partialText.value }
        if (text.isNotBlank()) {
            viewModelScope.launch {
                val startOrder = (_tasks.value.maxOfOrNull { it.sortOrder } ?: -1) + 1
                repository.add(Task.makeNew(title = text.take(500), source = TaskSource.TEXT, sortOrder = startOrder))
                reload()
            }
        }
        asrController.stop()
        _voiceState.value = VoiceState.Idle
        _partialText.value = ""
    }

    /** 权限被拒（UI 申请后回调）：降级引导文字输入（规格 §6） */
    fun onPermissionDenied() {
        _voiceState.value = VoiceState.Degraded(DegradeReason.PERMISSION_DENIED)
        _partialText.value = ""
    }

    /** 降级处理：关语音按钮 + UI 弹 Toast 引导（规格 §6） */
    private fun onVoiceDegraded(reason: DegradeReason) {
        _voiceState.value = VoiceState.Degraded(reason)
        _partialText.value = ""
    }

    // MARK: - F5 完成
    fun toggleDone(task: Task) {
        viewModelScope.launch {
            runCatching {
                if (task.isDone) {
                    // 取消完成（AC-26）：isDone=false, doneAt=null，回到进行中
                    val restored = task.copy(isDone = false, doneAt = null)
                    repository.update(restored)
                    // 恢复其提醒排程（因 isDone 已回退，触发时 Work 会正常发通知，规格 §6.4）
                    reminderScheduler.schedule(restored)
                } else {
                    repository.markDone(task.id, Date())
                    // 完成即取消后续未触发提醒（AC-9 / R-E7，主路径）
                    reminderScheduler.cancel(task.id)
                }
            }.onSuccess { reload() }
                .onFailure { _errorMessage.value = "操作失败：${it.message}" }
        }
    }

    // MARK: - F5 行内编辑 title
    fun editTitle(task: Task, newTitle: String) {
        val trimmed = newTitle.trim()
        if (trimmed.isEmpty()) return
        val title = if (trimmed.length > 500) trimmed.take(500) else trimmed
        viewModelScope.launch {
            runCatching { repository.update(task.copy(title = title)) }
                .onSuccess { reload() }
                .onFailure { _errorMessage.value = "编辑失败：${it.message}" }
        }
    }

    // MARK: - F5 删除
    fun delete(task: Task) {
        viewModelScope.launch {
            runCatching {
                repository.delete(task.id)
                // 删除任务 → 取消其全部未触发提醒
                reminderScheduler.cancel(task.id)
            }
                .onSuccess { reload() }
                .onFailure { _errorMessage.value = "删除失败：${it.message}" }
        }
    }

    // MARK: - F5 拖拽重排（AC-16），基于平铺展示顺序（与首页索引一致）
    fun reorder(from: Int, to: Int) {
        val list = _flatOrder.value
        if (from !in list.indices || to !in list.indices) return
        val reordered = list.toMutableList().apply { add(to, removeAt(from)) }
        val ids = reordered.map { it.id }
        viewModelScope.launch {
            runCatching { repository.reorder(ids) }
                .onSuccess { reload() }
                .onFailure { _errorMessage.value = "排序失败：${it.message}" }
        }
    }

    fun clearError() { _errorMessage.value = null }

    // MARK: - M5 F4 任务组织能力写方法（编辑页统一保存分类/优先级/标签 + 复用 M4 提醒）

    /** 加载分类列表（预设 + 自建），供编辑页选择器与首页筛选栏 */
    fun loadCategories() {
        viewModelScope.launch {
            runCatching { categoryRepository.all() }.onSuccess { _categories.value = it }
        }
    }

    /** 新建自建分类（isPreset=false），成功后刷新列表（规格 §4.1 / AC-13） */
    suspend fun addCategory(name: String): CategoryEntity? =
        runCatching { categoryRepository.add(name) }
            .onSuccess { loadCategories() }
            .getOrNull()

    /** 加载全部标签（首页筛选栏标签下拉用） */
    fun loadAllTags() {
        viewModelScope.launch {
            runCatching { tagRepository.all() }.onSuccess { _allTags.value = it }
        }
    }

    /** 标签联想补全（先归一前缀，规格 §3.3 / §5.1） */
    suspend fun suggestTags(prefix: String, limit: Int): List<TagEntity> =
        runCatching { tagRepository.suggestTags(prefix, limit) }.getOrDefault(emptyList())

    /** 读取某任务标签（编辑页回显 chips，规格 §2.4） */
    suspend fun tagsForTask(id: UUID): List<TagEntity> =
        runCatching { repository.tagsForTask(id) }.getOrDefault(emptyList())

    /** 输入标签经归一后写入/复用，返回 TagEntity（规格 §2.4 / §5.1，复用 TagRepository 去重） */
    suspend fun addTagFromInput(raw: String): TagEntity? =
        runCatching { tagRepository.addOrReuse(raw) }.getOrNull()

    /**
     * 保存单条待办的组织属性 + 提醒（编辑页统一入口，规格 §4.1）：
     * - 刷新读取最新任务避免丢失更新，整体 update（category/priority/remindAt/lead/repeat）；
     * - setTags 整体替换标签关联（tagIds 为归一后的 Tag.id，由 UI 经 TagRepository.addOrReuse 得到）；
     * - 据 remindAt 重新排程。
     */
    fun saveTaskAll(
        taskId: UUID,
        categoryId: UUID?,
        priority: Priority,
        tags: List<UUID>,
        remindAt: Date?,
        leadMinutes: Int,
        repeatCount: Int
    ) {
        viewModelScope.launch {
            runCatching {
                val current = repository.get(taskId) ?: return@runCatching
                val updated = current.copy(
                    categoryId = categoryId,
                    priority = priority,
                    remindAt = remindAt,
                    leadMinutes = leadMinutes,
                    repeatCount = repeatCount,
                    updatedAt = Date()
                )
                repository.update(updated)
                repository.setTags(taskId, tags.toSet())
                if (remindAt != null) reminderScheduler.schedule(updated)
                else reminderScheduler.cancel(taskId)
            }.onSuccess { reload() }
                .onFailure { _errorMessage.value = "保存失败：${it.message}" }
        }
    }

    // MARK: - F3 启动/前台补偿
    /** 进入前台时调用：重建未来 7 天未完成任务提醒（补偿 Doze/重启/清理丢失，规格 §4.4 / §5）。 */
    fun rescheduleReminders() {
        viewModelScope.launch {
            runCatching { reminderScheduler.rescheduleAllPending() }
            reload()   // 刷新可能因通知 Action 变化的完成态
        }
    }

    // MARK: - D4 首页常驻提示（规格 §2 / AC-20）
    /** 重新检测通知可达性风险并刷新常驻横幅状态（进入前台 / 从设置返回时调用）。 */
    fun refreshNotificationStatus(context: Context) {
        val status = NotificationStatusHelper.getStatus(context)
        _notificationBanner.value = when {
            !status.notificationsEnabled ->
                NotificationBannerInfo(NotificationBannerInfo.Reason.NOTIFICATIONS_DISABLED,
                    NotificationBannerInfo.DeepLink.APP_NOTIFICATION_SETTINGS)
            status.dndBlocking ->
                NotificationBannerInfo(NotificationBannerInfo.Reason.DND_ACTIVE,
                    NotificationBannerInfo.DeepLink.POLICY_ACCESS_SETTINGS)
            else -> null
        }
        // 埋点调用点：notification_banner_shown（仅标注，不写上报）
    }

    /** 点击常驻横幅 → 深链到对应系统设置页（规格 §2.3） */
    fun openNotificationSettings(context: Context) {
        val banner = _notificationBanner.value ?: return
        val intent = when (banner.deepLink) {
            NotificationBannerInfo.DeepLink.APP_NOTIFICATION_SETTINGS ->
                NotificationStatusHelper.appNotificationSettingsIntent(context)
            NotificationBannerInfo.DeepLink.POLICY_ACCESS_SETTINGS ->
                NotificationStatusHelper.policyAccessSettingsIntent()
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    // MARK: - R-4 语音输入开关（规格 §4.1，持久化）
    fun setVoiceInputEnabled(enabled: Boolean) {
        settingsPrefs.voiceInputEnabled = enabled
        _voiceInputEnabled.value = enabled
        if (!enabled) stopVoice()   // 关闭即停止当前录音
    }

    // MARK: - R-4 合并/拆分（规格 §4.2 / AC-5，复用 M3 TaskMergeSplitUseCase）
    /** 合并当前条到上一条（相邻、同展示日）。埋点：todo_merge */
    fun mergeWithPrevious(current: Task, previous: Task) {
        viewModelScope.launch {
            runCatching { mergeSplitUseCase.merge(listOf(previous, current)) }
                .onSuccess { reload() }
                .onFailure { _errorMessage.value = "合并失败：${it.message}" }
        }
    }

    /** 在当前条的指定字符位置拆分。埋点：todo_split */
    fun splitTask(task: Task, at: Int) {
        viewModelScope.launch {
            runCatching { mergeSplitUseCase.split(task, at) }
                .onSuccess { reload() }
                .onFailure { _errorMessage.value = "拆分失败：${it.message}" }
        }
    }
}
