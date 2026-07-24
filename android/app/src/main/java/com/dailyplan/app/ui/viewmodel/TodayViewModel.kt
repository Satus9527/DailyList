// TodayViewModel.kt
// 今日待办 ViewModel：聚合 F1（文字记录）/ F5（完成/编辑/删除/拖拽）/ F6（持久化）与 X/Y 进度。
// UI 经 ViewModel 调 Repository，领域层不依赖具体存储（架构 §7.2）。

package com.dailyplan.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailyplan.app.data.repository.TaskRepository
import com.dailyplan.app.data.reminder.ReminderScheduler
import com.dailyplan.app.domain.model.Task
import com.dailyplan.app.domain.model.todayDateString
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Date
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TodayViewModel(
    private val repository: TaskRepository,
    private val reminderScheduler: ReminderScheduler
) : ViewModel() {

    // 当日任务列表（从库加载，规格 AC-17 F6）
    private val _tasks = MutableStateFlow<List<Task>>(emptyList())
    val tasks: StateFlow<List<Task>> = _tasks.asStateFlow()

    // 输入框文本
    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    /** 进度 X / Y（规格 R-U3 / AC-15）：X=已完成数，Y=当日总数 */
    val doneCount: Int get() = _tasks.value.count { it.isDone }
    val totalCount: Int get() = _tasks.value.size

    init {
        reload()
    }

    // MARK: - F6 加载
    fun reload() {
        viewModelScope.launch {
            runCatching { repository.todayTasks() }
                .onSuccess { _tasks.value = it }
                .onFailure { _errorMessage.value = "加载待办失败：${it.message}" }
        }
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

    // MARK: - F5 拖拽重排（AC-16）
    fun reorder(from: Int, to: Int) {
        val reordered = _tasks.value.toMutableList().apply { add(to, removeAt(from)) }
        val ids = reordered.map { it.id }
        viewModelScope.launch {
            runCatching { repository.reorder(ids) }
                .onSuccess { reload() }
                .onFailure { _errorMessage.value = "排序失败：${it.message}" }
        }
    }

    fun clearError() { _errorMessage.value = null }

    // MARK: - F3 提醒设置（UI 调用，M2-D，Task #36）
    /**
     * 为单条待办设置/修改提醒：先更新领域模型（remindAt / leadMinutes / repeatCount），
     * 再 repository.update 持久化；随后据新值排程。
     * - 编辑 remindAt 时由 reminderScheduler.schedule 内部先 cancel 再登记（幂等，改期不重复，AC-28 / R-E11）。
     * - remindAt=null（关闭提醒）→ reminderScheduler.cancel 移除该任务全部 pending。
     */
    fun saveReminder(taskId: UUID, remindAt: Date?, leadMinutes: Int, repeatCount: Int) {
        viewModelScope.launch {
            runCatching {
                val current = _tasks.value.firstOrNull { it.id == taskId } ?: return@runCatching
                val updated = current.copy(
                    remindAt = remindAt,
                    leadMinutes = leadMinutes,
                    repeatCount = repeatCount,
                    updatedAt = Date()
                )
                repository.update(updated)
                if (remindAt != null) reminderScheduler.schedule(updated)
                else reminderScheduler.cancel(taskId)
            }.onSuccess { reload() }
                .onFailure { _errorMessage.value = "保存提醒失败：${it.message}" }
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
}
