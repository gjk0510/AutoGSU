package com.gjk0510.autosu

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 卡片概要数据（下次执行时间 + 最近一条日志）：统一后台加载，避免组合期间做磁盘 IO。 */
private data class CardExtras(val nextWall: Long, val lastLog: String)

@Composable
fun HomeScreen(onOpen: (String) -> Unit, onAdd: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val activity = LocalContext.current
    var tasks by remember { mutableStateOf(TaskStore.getAll(context)) }
    var extras by remember { mutableStateOf<Map<String, CardExtras>>(emptyMap()) }
    var showAbout by remember { mutableStateOf(false) }

    // 卡片概要统一在后台线程加载，不在组合过程中读磁盘（掉帧根源）。
    LaunchedEffect(tasks) {
        extras = withContext(Dispatchers.IO) {
            tasks.associate { t ->
                t.id to CardExtras(
                    nextWall = AlarmScheduler.getNextTriggerWall(context, t.id),
                    lastLog = ExecutionLog.readLastTask(context, t.id, maxLines = 1)
                )
            }
        }
    }
    var confirmTaskId by remember { mutableStateOf<String?>(null) }
    var confirmStage by remember { mutableStateOf(0) }

    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false }, activity = activity)
    }

    Box(modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .pointerInput(Unit) {
            detectTapGestures(onTap = {
                // 点击空白处：清除删除确认状态
                confirmTaskId = null
                confirmStage = 0
            })
        }) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "AutoGSU 定时任务",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = { showAbout = true }) {
                    Text(
                        "!",
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            if (tasks.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("还没有任务", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "点右下角 + 添加第一个命令",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(tasks, key = { it.id }) { task ->
                        val extra = extras[task.id]
                        TaskCard(
                            task = task,
                            nextWall = extra?.nextWall,
                            lastLog = extra?.lastLog.orEmpty(),
                            confirmStage = if (confirmTaskId == task.id) confirmStage else 0,
                            onClick = {
                                confirmTaskId = null
                                confirmStage = 0
                                onOpen(task.id)
                            },
                            onDeleteClick = {
                                if (confirmTaskId == task.id) {
                                    confirmStage += 1
                                    if (confirmStage >= 3) {
                                        TaskStore.delete(context, task.id)
                                        AlarmScheduler.cancel(context, task.id)
                                        ExecutionLog.clearTask(context, task.id)
                                        tasks = TaskStore.getAll(context)
                                        confirmTaskId = null
                                        confirmStage = 0
                                    }
                                } else {
                                    confirmTaskId = task.id
                                    confirmStage = 1
                                }
                            }
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = onAdd,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .size(72.dp)
        ) {
            Text("+", fontSize = 32.sp, fontWeight = FontWeight.Normal)
        }
    }
}

@Composable
private fun TaskCard(
    task: Task,
    nextWall: Long?,
    lastLog: String,
    confirmStage: Int,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = task.name.ifBlank { "未命名任务" },
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onDeleteClick) {
                    Text(
                        text = when (confirmStage) {
                            1 -> "确认删除"
                            2 -> "再点一次删除"
                            else -> "×"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = if (confirmStage > 0) 14.sp else 24.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Text(
                text = task.command.ifBlank { "(空命令)" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = "间隔：${formatInterval(task.intervalMs)}",
                style = MaterialTheme.typography.bodySmall
            )

            if (nextWall != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "模式：${alarmModeLabel(task.alarmMode)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "下次：${formatTime(nextWall)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (lastLog.isNotBlank() && lastLog != "暂无执行日志") {
                Text(
                    text = "最近：$lastLog",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit, activity: android.content.Context) {
    AlertDialog(
        onDismissRequest = onDismiss,
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
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}