package com.nianri.app.ui.transfer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nianri.app.ui.theme.Night800
import com.nianri.app.ui.theme.TextMuted
import com.nianri.app.ui.theme.TextPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferSheet(
    state: TransferUiState,
    onDismiss: () -> Unit,
    onSelectTab: (TransferTab) -> Unit,
    onRequestExport: () -> Unit,
    onCopyExport: () -> Unit,
    onRequestImport: () -> Unit,
    onImportTextChange: (String) -> Unit,
    onPasteFromClipboard: () -> Unit,
    onImportPastedText: () -> Unit,
    onMessageShown: () -> Unit,
) {
    fun dismissSheet() {
        if (state.message != null) onMessageShown()
        onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = ::dismissSheet,
        containerColor = Night800,
        contentColor = TextPrimary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
        ) {
            Text(
                text = "配置迁移",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "在另一台设备上继续使用你的纪念日配置",
                color = TextMuted,
                modifier = Modifier.padding(top = 4.dp, bottom = 18.dp),
            )
            PrimaryTabRow(
                selectedTabIndex = state.selectedTab.ordinal,
                containerColor = Color.Transparent,
            ) {
                TransferTab.entries.forEach { tab ->
                    Tab(
                        selected = state.selectedTab == tab,
                        onClick = { onSelectTab(tab) },
                        text = { Text(if (tab == TransferTab.EXPORT) "导出" else "导入") },
                        modifier = Modifier.testTag(
                            if (tab == TransferTab.EXPORT) {
                                "transfer-tab-export"
                            } else {
                                "transfer-tab-import"
                            },
                        ),
                    )
                }
            }

            state.message?.let { message ->
                TransferMessageCard(message = message, onDismiss = onMessageShown)
            }

            when (state.selectedTab) {
                TransferTab.EXPORT -> ExportContent(
                    state = state,
                    onRequestExport = onRequestExport,
                    onCopyExport = onCopyExport,
                )

                TransferTab.IMPORT -> ImportContent(
                    state = state,
                    onRequestImport = onRequestImport,
                    onImportTextChange = onImportTextChange,
                    onPasteFromClipboard = onPasteFromClipboard,
                    onImportPastedText = onImportPastedText,
                )
            }
        }
    }
}

@Composable
private fun ExportContent(
    state: TransferUiState,
    onRequestExport: () -> Unit,
    onCopyExport: () -> Unit,
) {
    Column(modifier = Modifier.padding(top = 22.dp)) {
        if (state.dayCount == 0) {
            Text("暂无可导出的纪念日", color = TextMuted)
        } else {
            Text("将导出 ${state.dayCount} 个纪念日", color = TextMuted)
            Text(
                "包含日期、历法、提醒时间和置顶配置，不包含小部件与系统权限。",
                color = TextMuted,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        ProcessingRow(state.isProcessing)
        Button(
            onClick = onRequestExport,
            enabled = state.dayCount > 0 && !state.isProcessing,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("保存配置到本机")
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = onCopyExport,
            enabled = state.dayCount > 0 && !state.isProcessing,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("复制配置到剪贴板")
        }
        Text(
            "剪贴板配置包含纪念日名称，请注意隐私。",
            color = TextMuted,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

@Composable
private fun ImportContent(
    state: TransferUiState,
    onRequestImport: () -> Unit,
    onImportTextChange: (String) -> Unit,
    onPasteFromClipboard: () -> Unit,
    onImportPastedText: () -> Unit,
) {
    Column(modifier = Modifier.padding(top = 22.dp)) {
        Text(
            "导入会与本机数据合并，不会删除已有纪念日。",
            color = TextMuted,
        )
        Text(
            "重名项会自动改名；本机已有置顶时不会被覆盖。",
            color = TextMuted,
            modifier = Modifier.padding(top = 6.dp),
        )
        ProcessingRow(state.isProcessing)
        Button(
            onClick = onRequestImport,
            enabled = !state.isProcessing,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("选择配置文件")
        }
        Text(
            "或粘贴配置",
            color = TextMuted,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
        )
        OutlinedTextField(
            value = state.importText,
            onValueChange = onImportTextChange,
            enabled = !state.isProcessing,
            placeholder = { Text("粘贴导出的念日配置") },
            minLines = 5,
            maxLines = 8,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("transfer-import-text"),
        )
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = onPasteFromClipboard,
            enabled = !state.isProcessing,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("从剪贴板粘贴")
        }
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = onImportPastedText,
            enabled = state.importText.isNotBlank() && !state.isProcessing,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("导入粘贴内容")
        }
    }
}

@Composable
private fun ProcessingRow(isProcessing: Boolean) {
    if (isProcessing) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Text("正在处理…", color = TextMuted, modifier = Modifier.padding(start = 10.dp))
        }
    } else {
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun TransferMessageCard(
    message: TransferMessage,
    onDismiss: () -> Unit,
) {
    val containerColor = when (message.kind) {
        TransferMessageKind.SUCCESS -> MaterialTheme.colorScheme.primaryContainer
        TransferMessageKind.WARNING -> MaterialTheme.colorScheme.secondaryContainer
        TransferMessageKind.ERROR -> MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = when (message.kind) {
        TransferMessageKind.SUCCESS -> MaterialTheme.colorScheme.onPrimaryContainer
        TransferMessageKind.WARNING -> MaterialTheme.colorScheme.onSecondaryContainer
        TransferMessageKind.ERROR -> MaterialTheme.colorScheme.onErrorContainer
    }
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(message.text, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) {
                Text("知道了")
            }
        }
    }
}
