package com.gjk0510.autosu

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gjk0510.autosu.ui.theme.AutoSUTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AlarmScheduler.ensureScheduled(this)
        enableEdgeToEdge()
        setContent {
            AutoSUTheme {
                AppRoot()
            }
        }
    }
}

@Composable
fun AppRoot() {
    val context = LocalContext.current.applicationContext
    var editingTaskId by rememberSaveable { mutableStateOf<String?>(null) }

    AnimatedContent(
        targetState = editingTaskId,
        transitionSpec = {
            if (targetState != null) {
                // 进入详情页：详情从右滑入（纯位移、全程不透明），主页保持静止，避免透明度合成造成掉帧
                slideInHorizontally(
                    animationSpec = tween(240, easing = FastOutSlowInEasing)
                ) { it } togetherWith fadeOut(targetAlpha = 1f)
            } else {
                // 返回主页：详情向右滑出（纯位移、全程不透明），主页保持静止
                fadeIn(initialAlpha = 1f) togetherWith slideOutHorizontally(
                    animationSpec = tween(240, easing = FastOutSlowInEasing)
                ) { it }
            }
        },
        label = "home-detail"
    ) { taskId ->
        if (taskId == null) {
            HomeScreen(
                onOpen = { editingTaskId = it },
                onAdd = {
                    val task = Task.new()
                    TaskStore.save(context, task)
                    editingTaskId = task.id
                }
            )
        } else {
            TaskDetailScreen(
                taskId = taskId,
                onBack = { editingTaskId = null }
            )
        }
    }
}

/** 任务详情页：即原来的单命令编辑页面，改为针对单个任务。 */
@Composable
fun TaskDetailScreen(taskId: String, onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val activityContext = LocalContext.current
    BackHandler(onBack = onBack)
    val initial = remember(taskId) { TaskStore.get(context, taskId) }

    if (initial == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    var name by remember(taskId) { mutableStateOf(initial.name) }
    var command by remember(taskId) { mutableStateOf(initial.command) }
    var alarmMode by remember(taskId) { mutableStateOf(initial.alarmMode) }
    var hour by remember(taskId) { mutableStateOf((initial.intervalMs / 3_600_000L).toInt()) }
    var minute by remember(taskId) { mutableStateOf(((initial.intervalMs % 3_600_000L) / 60_000L).toInt()) }
    var second by remember(taskId) { mutableStateOf(((initial.intervalMs % 60_000L) / 1000L).toInt()) }
    var nextTrigger by remember { mutableStateOf(AlarmScheduler.getNextTriggerWall(context, taskId)) }
    var lastLog by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val logScrollState = rememberScrollState()

    // 后台读取最近日志，避免打开详情页时在主线程读文件阻塞转场动画。
    LaunchedEffect(taskId) {
        lastLog = withContext(Dispatchers.IO) { ExecutionLog.readLastTask(context, taskId) }
    }

    // 日志内容变化后自动滚到底部（默认显示最新日志）。
    LaunchedEffect(lastLog) {
        logScrollState.scrollTo(logScrollState.maxValue)
    }

    fun buildTask(ms: Long): Task = Task(
        id = initial.id,
        name = name.trim().ifBlank { "任务" },
        command = command.trim(),
        intervalMs = ms,
        alarmMode = alarmMode,
        createdAt = initial.createdAt
    )

    fun save() {
        val totalSeconds = hour * 3600L + minute * 60L + second
        val ms = if (totalSeconds <= 0L) Settings.DEFAULT_INTERVAL_MS
                else totalSeconds * 1000L
        val updated = buildTask(ms)
        TaskStore.save(context, updated)
        hour = (ms / 3_600_000L).toInt()
        minute = ((ms % 3_600_000L) / 60_000L).toInt()
        second = ((ms % 60_000L) / 1000L).toInt()
        AlarmScheduler.schedule(context, updated)
        nextTrigger = AlarmScheduler.getNextTriggerWall(context, taskId)
        lastLog = ExecutionLog.readLastTask(context, taskId)
    }

    fun delete() {
        TaskStore.delete(context, taskId)
        AlarmScheduler.cancel(context, taskId)
        ExecutionLog.clearTask(context, taskId)
        onBack()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Text("←", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                }
                Text(
                    "任务详情",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { delete() }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("名称") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                AlarmMode.values().forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = alarmMode == mode,
                        onClick = {
                            alarmMode = mode
                            val updated = buildTask(initial.intervalMs)
                            TaskStore.save(context, updated)
                            AlarmScheduler.schedule(context, updated)
                            nextTrigger = AlarmScheduler.getNextTriggerWall(context, taskId)
                            lastLog = ExecutionLog.readLastTask(context, taskId)
                        },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = AlarmMode.values().size
                        )
                    ) {
                        Text(alarmModeLabel(mode))
                    }
                }
            }

            OutlinedTextField(
                value = command,
                onValueChange = { command = it },
                label = { Text("Shell 命令（通过 su -c 执行）") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 1,
                maxLines = 4
            )

            Text("间隔时间", style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NumberField(
                    value = hour,
                    onValueChange = { hour = it },
                    label = "时",
                    modifier = Modifier.weight(1f),
                    max = 1000000000
                )
                NumberField(
                    value = minute,
                    onValueChange = { minute = it },
                    label = "分",
                    modifier = Modifier.weight(1f),
                    max = 59
                )
                NumberField(
                    value = second,
                    onValueChange = { second = it },
                    label = "秒",
                    modifier = Modifier.weight(1f),
                    max = 59
                )
            }

            Text("下次执行：${formatTime(nextTrigger)}")

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { save() }) { Text("保存并应用") }

                Button(
                    onClick = {
                        running = true
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                CommandExecutor.execute(command)
                            }
                            withContext(Dispatchers.IO) {
                                ExecutionLog.appendTask(context, taskId, result.summary())
                                if (result.output.isNotBlank()) {
                                    ExecutionLog.appendTask(context, taskId, "输出：${result.output.trim()}")
                                }
                            }
                            nextTrigger = AlarmScheduler.getNextTriggerWall(context, taskId)
                            lastLog = ExecutionLog.readLastTask(context, taskId)
                            running = false
                        }
                    },
                    enabled = !running
                ) { Text(if (running) "执行中..." else "立即执行一次") }
            }

            HorizontalDivider()

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = {
                        val text = ExecutionLog.readAllTask(context, taskId)
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "AutoSU 日志 - ${initial.name}")
                            putExtra(Intent.EXTRA_TEXT, text)
                        }
                        activityContext.startActivity(Intent.createChooser(send, "导出日志"))
                    }
                ) { Text("导出日志") }

                OutlinedButton(
                    onClick = {
                        ExecutionLog.clearTask(context, taskId)
                        lastLog = ExecutionLog.readLastTask(context, taskId)
                    }
                ) { Text("清除日志") }
            }

            Text(
                text = lastLog,
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(logScrollState),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private val TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

internal fun formatTime(millis: Long): String {
    if (millis <= 0L) return "未设置"
    return TIME_FORMATTER.format(
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    )
}

internal fun formatInterval(ms: Long): String {
    val total = if (ms <= 0L) Settings.DEFAULT_INTERVAL_MS else ms
    val h = total / 3_600_000L
    val m = (total % 3_600_000L) / 60_000L
    val s = (total % 60_000L) / 1000L
    return when {
        h > 0 -> "${h}小时 ${m}分 ${s}秒"
        m > 0 -> "${m}分 ${s}秒"
        else -> "${s}秒"
    }
}

internal fun alarmModeLabel(mode: AlarmMode): String = when (mode) {
    AlarmMode.OFF -> "关闭"
    AlarmMode.INEXACT -> "非精确"
    AlarmMode.EXACT -> "精确"
    AlarmMode.ALARM_CLOCK -> "闹钟"
}

/** 数字输入框：输入即生效；删空可显示为空，失焦或回车时空值自动填 0；只允许数字，越界自动钳制到 [min, max]。 */
@Composable
private fun NumberField(
    value: Int,
    onValueChange: (Int) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    min: Int = 0,
    max: Int = 999
) {
    var text by remember { mutableStateOf(value.toString()) }
    val focusManager = LocalFocusManager.current

    // 空值时归 0（min）。
    fun fillIfEmpty() {
        if (text.isEmpty()) {
            text = min.toString()
            if (min != value) onValueChange(min)
        }
    }

    // 外部 value 变化（如保存后回写）时同步显示。
    LaunchedEffect(value) {
        text = value.toString()
    }

    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            val digits = raw.filter { it.isDigit() }
            text = digits
            digits.toIntOrNull()?.let { num ->
                val clamped = num.coerceIn(min, max)
                if (clamped != value) onValueChange(clamped)
                if (digits != clamped.toString()) text = clamped.toString()
            }
        },
        label = { Text(label) },
        modifier = modifier.onFocusChanged { focus ->
            if (!focus.isFocused) fillIfEmpty()
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(
            onDone = {
                fillIfEmpty()
                focusManager.clearFocus()
            }
        ),
        singleLine = true
    )
}