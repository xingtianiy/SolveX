package com.tianhuiu.solvex.floating

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tianhuiu.solvex.data.models.HistoryItem
import com.tianhuiu.solvex.ui.components.StatusBadge
import com.tianhuiu.solvex.ui.history.DetailSection
import com.tianhuiu.solvex.ui.history.FinalAnswerSection
import com.tianhuiu.solvex.ui.history.MetadataRow
import com.tianhuiu.solvex.utils.AutomationTools
import com.tianhuiu.solvex.utils.NotificationUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 侧边抽屉视图：显示解析详情。
 */
@Composable
fun DrawerView(
    item: HistoryItem?,
    showMetadata: Boolean,
    autoScroll: Boolean = true,
    onClose: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶部栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (showMetadata) "解析详情" else "实时解析",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp)
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "关闭")
                }
            }

            if (item == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                val scrollState = rememberScrollState()

                LaunchedEffect(item.query, item.result, autoScroll) {
                    if (autoScroll) {
                        scrollState.animateScrollTo(scrollState.maxValue)
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (showMetadata) {
                        val dateFormat = remember {
                            SimpleDateFormat(
                                "yyyy MM-dd HH:mm:ss",
                                Locale.getDefault()
                            )
                        }
                        val timeStr = dateFormat.format(Date(item.timestamp))

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    MetadataRow(
                                        icon = Icons.Default.Schedule,
                                        label = "时间",
                                        value = timeStr
                                    )
                                    StatusBadge(item.status)
                                }
                                MetadataRow(
                                    icon = Icons.Default.Face,
                                    label = "助手",
                                    value = item.assistantName ?: "默认"
                                )
                            }
                        }
                    }

                    DetailSection(
                        title = "解析问题",
                        content = NotificationUtils.renderStructuredQuestion(item.query),
                        badgeText = AutomationTools.extractQuestionType(item.query)
                    )

                    val (process, finalAnswer) = NotificationUtils.splitAnalysisResult(item.result)

                    if (process.isNotBlank()) {
                        DetailSection(title = "解析过程", content = process)
                    }

                    if (finalAnswer.isNotBlank()) {
                        FinalAnswerSection(title = "最终答案", content = finalAnswer)
                    }

                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}
