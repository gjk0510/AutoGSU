package com.gjk0510.autosu

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
                MainScreen()
            }
        }
    }
}

private val TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

private fun formatTime(millis: Long): String {
    if (millis <= 0L) return "未设置"
    return TIME_FORMATTER.format(
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    )
}

private fun alarmModeLabel(mode: AlarmMode): String = when (mode) {
    AlarmMode.OFF -> "关闭"
    AlarmMode.INEXACT -> "非精确"
    AlarmMode.EXACT -> "精确"
    AlarmMode.ALARM_CLOCK -> "闹钟"
}

@Composable
fun MainScreen() {
    val context = LocalContext.current.applicationContext
    var command by remember { mutableStateOf(Settings.getCommand(context)) }
    val intervalMs = Settings.getIntervalMs(context)
    var hour by remember { mutableStateOf((intervalMs / 3_600_000L).toInt().coerceIn(0, 1000000000)) }
    var minute by remember { mutableStateOf(((intervalMs % 3_600_000L) / 60_000L).toInt()) }
    var second by remember { mutableStateOf(((intervalMs % 60_000L) / 1000L).toInt()) }
    var nextTrigger by remember { mutableStateOf(AlarmScheduler.getNextTriggerWall(context)) }
    var lastLog by remember { mutableStateOf(ExecutionLog.readLast(context)) }
    var running by remember { mutableStateOf(false) }
    var alarmMode by remember { mutableStateOf(Settings.getAlarmMode(context)) }
    var showAbout by remember { mutableStateOf(false) }
    val activity = LocalContext.current
    val scope = rememberCoroutineScope()
    val logScrollState = rememberScrollState()
    // 日志内容变化后自动滚到底部（默认显示最新日志）。
    LaunchedEffect(lastLog) {
        logScrollState.scrollTo(logScrollState.maxValue)
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text("关于 AutoGSU") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("有问题邮件联系作者：")
                    Text(
                        text = "gjk0510@hotmail.com",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            activity.startActivity(
                                Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:gjk0510@hotmail.com"))
                            )
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "构建时间：${BuildConfig.BUILD_TIME}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAbout = false }) { Text("关闭") }
            }
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("AutoGSU 定时任务", style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = { showAbout = true }) {
                Text("!", fontWeight = FontWeight.Bold, fontSize = 22.sp)
            }
        }
        Text("下次执行：${formatTime(nextTrigger)}")

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            AlarmMode.values().forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = alarmMode == mode,
                    onClick = {
                        alarmMode = mode
                        Settings.setAlarmMode(context, mode)
                        if (mode == AlarmMode.OFF) {
                            AlarmScheduler.cancel(context)
                        } else {
                            AlarmScheduler.scheduleNext(context)
                        }
                        nextTrigger = AlarmScheduler.getNextTriggerWall(context)
                        lastLog = ExecutionLog.readLast(context)
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

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    Settings.setCommand(context, command)
                    val totalSeconds = hour * 3600L + minute * 60L + second
                    val ms = if (totalSeconds <= 0L) Settings.DEFAULT_INTERVAL_MS
                            else totalSeconds * 1000L
                    Settings.setIntervalMs(context, ms)
                    command = Settings.getCommand(context)
                    hour = (ms / 3_600_000L).toInt()
                    minute = ((ms % 3_600_000L) / 60_000L).toInt()
                    second = ((ms % 60_000L) / 1000L).toInt()
                    AlarmScheduler.scheduleNext(context)
                    nextTrigger = AlarmScheduler.getNextTriggerWall(context)
                    lastLog = ExecutionLog.readLast(context)
                }
            ) { Text("保存并应用") }

            Button(
                onClick = {
                    running = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            CommandExecutor.execute(Settings.getCommand(context))
                        }
                        withContext(Dispatchers.IO) {
                            ExecutionLog.append(context, result.summary())
                            if (result.output.isNotBlank()) {
                                ExecutionLog.append(context, "输出：${result.output.trim()}")
                            }
                        }
                        nextTrigger = AlarmScheduler.getNextTriggerWall(context)
                        lastLog = ExecutionLog.readLast(context)
                        running = false
                    }
                },
                enabled = !running
            ) { Text(if (running) "执行中..." else "立即执行一次") }
        }

        /* 后台保活引导按钮（暂时关闭，后续需要可恢复）
        Text("后台保活（推荐开启）", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { WhitelistGuide.requestIgnoreBatteryOptimizations(context) }) {
                Text("忽略电池优化")
            }
            Button(onClick = { WhitelistGuide.openAutostartSettings(context) }) {
                Text("打开自启动设置")
            }
        }
        */

        HorizontalDivider()

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

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, showSystemUi = true)
@Composable
fun MainScreenPreview() {
    AutoSUTheme {
        MainScreen()
    }
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