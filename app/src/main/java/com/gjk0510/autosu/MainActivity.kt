package com.gjk0510.autosu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
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
    var intervalMinutes by remember {
        mutableStateOf((Settings.getIntervalMs(context) / 60_000L).toString())
    }
    var nextTrigger by remember { mutableStateOf(AlarmScheduler.getNextTriggerWall(context)) }
    var lastLog by remember { mutableStateOf(ExecutionLog.readLast(context)) }
    var running by remember { mutableStateOf(false) }
    var alarmMode by remember { mutableStateOf(Settings.getAlarmMode(context)) }
    val scope = rememberCoroutineScope()
    val logScrollState = rememberScrollState()
    // 日志内容变化后自动滚到底部（默认显示最新日志）。
    LaunchedEffect(lastLog) {
        logScrollState.scrollTo(logScrollState.maxValue)
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
        Text("AutoSU 定时任务", style = MaterialTheme.typography.titleLarge)
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

        OutlinedTextField(
            value = intervalMinutes,
            onValueChange = { intervalMinutes = it },
            label = { Text("间隔时间（分钟）") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    Settings.setCommand(context, command)
                    val minutes = intervalMinutes.toLongOrNull()?.takeIf { it > 0 }
                        ?: (Settings.DEFAULT_INTERVAL_MS / 60_000L)
                    Settings.setIntervalMs(context, minutes * 60_000L)
                    command = Settings.getCommand(context)
                    intervalMinutes = (Settings.getIntervalMs(context) / 60_000L).toString()
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