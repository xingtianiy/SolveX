package com.tianhuiu.solvex.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tianhuiu.solvex.data.models.AnalysisStatus

/**
 * 通用状态角标：用于展示解析任务的当前进度状态。
 */
@Composable
fun StatusBadge(
    status: AnalysisStatus,
    modifier: Modifier = Modifier,
) {
    val containerColor = when (status) {
        AnalysisStatus.SUCCESS -> Color(0xFFE8F5E9)
        AnalysisStatus.FAILURE -> Color(0xFFFFEBEE)
        AnalysisStatus.CANCELLED -> Color(0xFFF5F5F5)
        AnalysisStatus.PROCESSING -> Color(0xFFE3F2FD)
    }
    val contentColor = when (status) {
        AnalysisStatus.SUCCESS -> Color(0xFF2E7D32)
        AnalysisStatus.FAILURE -> Color(0xFFC62828)
        AnalysisStatus.CANCELLED -> Color(0xFF616161)
        AnalysisStatus.PROCESSING -> Color(0xFF1565C0)
    }

    Surface(
        color = containerColor,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Text(
            text = status.displayName,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

/**
 * SolveX 统一弹窗基础包装器。
 */
@Composable
fun SolveXDialog(
    onDismissRequest: () -> Unit,
    title: String,
    icon: ImageVector? = null,
    confirmButton: @Composable (() -> Unit)? = null,
    dismissButton: @Composable (() -> Unit)? = null,
    trailingTitleAction: @Composable (() -> Unit)? = null,
    centerTitle: Boolean = true,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(28.dp),
        icon = icon?.let {
            {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        title = {
            if (centerTitle) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    if (trailingTitleAction != null) {
                        Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                            trailingTitleAction()
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    trailingTitleAction?.invoke()
                }
            }
        },
        text = content,
        confirmButton = { confirmButton?.invoke() },
        dismissButton = { dismissButton?.invoke() },
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    )
}

/**
 * SolveX 统一确认弹窗。
 */
@Composable
fun SolveXConfirmDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    title: String,
    message: String,
    confirmText: String = "确认",
    dismissText: String = "取消",
    isDestructive: Boolean = false,
    icon: ImageVector? = null,
) {
    SolveXDialog(
        onDismissRequest = onDismissRequest,
        title = title,
        icon = icon,
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = if (isDestructive) ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                else ButtonDefaults.textButtonColors()
            ) {
                Text(confirmText, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(dismissText)
            }
        }
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
