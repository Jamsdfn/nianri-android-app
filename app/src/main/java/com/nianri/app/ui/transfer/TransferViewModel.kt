package com.nianri.app.ui.transfer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nianri.app.AppContainer
import com.nianri.app.data.transfer.TransferFormatException
import com.nianri.app.data.transfer.TransferImportResult
import com.nianri.app.domain.model.ImportantDay
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class TransferTab { EXPORT, IMPORT }

enum class TransferMessageKind { SUCCESS, WARNING, ERROR }

data class TransferMessage(
    val kind: TransferMessageKind,
    val text: String,
)

data class TransferUiState(
    val selectedTab: TransferTab = TransferTab.EXPORT,
    val dayCount: Int = 0,
    val isProcessing: Boolean = false,
    val importText: String = "",
    val message: TransferMessage? = null,
)

fun defaultExportFileName(date: LocalDate): String =
    "念日配置-${date.format(DateTimeFormatter.BASIC_ISO_DATE)}.nianri.json"

class TransferViewModel(
    days: Flow<List<ImportantDay>>,
    private val exportConfiguration: suspend () -> String,
    private val importConfiguration: suspend (String) -> TransferImportResult,
    private val currentDate: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {
    private val mutableState = MutableStateFlow(TransferUiState())
    val uiState: StateFlow<TransferUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            days.collect { currentDays ->
                mutableState.update { it.copy(dayCount = currentDays.size) }
            }
        }
    }

    fun selectTab(tab: TransferTab) {
        mutableState.update { it.copy(selectedTab = tab) }
    }

    fun exportTo(write: suspend (String) -> Unit) {
        val exportedCount = mutableState.value.dayCount
        startOperation(
            failureCopy = { error -> errorCopy(error, isImport = false) },
        ) {
            val text = exportConfiguration()
            write(text)
            TransferMessage(
                kind = TransferMessageKind.SUCCESS,
                text = "已导出 $exportedCount 个纪念日",
            )
        }
    }

    fun copyToClipboard(copy: suspend (String) -> Unit) {
        startOperation(
            failureCopy = { "复制失败，请重试" },
        ) {
            copy(exportConfiguration())
            TransferMessage(
                kind = TransferMessageKind.SUCCESS,
                text = "配置已复制到剪贴板",
            )
        }
    }

    fun importFrom(read: suspend () -> String) {
        startOperation(
            failureCopy = { error -> errorCopy(error, isImport = true) },
        ) {
            val result = importConfiguration(read())
            result.toMessage()
        }
    }

    fun setImportText(text: String) {
        mutableState.update { it.copy(importText = text) }
    }

    fun pasteFromClipboard(read: () -> String?) {
        if (mutableState.value.isProcessing) return
        try {
            val text = read()
            if (text.isNullOrBlank()) {
                mutableState.update {
                    it.copy(
                        message = TransferMessage(
                            kind = TransferMessageKind.ERROR,
                            text = "剪贴板中没有可粘贴的配置",
                        ),
                    )
                }
            } else {
                mutableState.update { it.copy(importText = text, message = null) }
            }
        } catch (_: Exception) {
            mutableState.update {
                it.copy(
                    message = TransferMessage(
                        kind = TransferMessageKind.ERROR,
                        text = "读取剪贴板失败，请重试",
                    ),
                )
            }
        }
    }

    fun importPastedText() {
        val text = mutableState.value.importText
        if (text.isBlank()) return
        startOperation(
            failureCopy = { error -> errorCopy(error, isImport = true) },
            onSuccess = { state -> state.copy(importText = "") },
        ) {
            importConfiguration(text).toMessage()
        }
    }

    fun clearMessage() {
        mutableState.update { it.copy(message = null) }
    }

    fun defaultExportFileName(): String = defaultExportFileName(currentDate())

    private fun startOperation(
        failureCopy: (Exception) -> String,
        onSuccess: (TransferUiState) -> TransferUiState = { it },
        operation: suspend () -> TransferMessage,
    ) {
        if (mutableState.value.isProcessing) return
        mutableState.update { it.copy(isProcessing = true, message = null) }
        viewModelScope.launch {
            try {
                val message = withContext(Dispatchers.IO) { operation() }
                mutableState.update { state ->
                    onSuccess(state).copy(isProcessing = false, message = message)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableState.update {
                    it.copy(
                        isProcessing = false,
                        message = TransferMessage(
                            kind = TransferMessageKind.ERROR,
                            text = failureCopy(error),
                        ),
                    )
                }
            }
        }
    }

    private fun TransferImportResult.toMessage(): TransferMessage {
        if (refreshFailed) {
            return TransferMessage(
                kind = TransferMessageKind.WARNING,
                text = "已导入 $importedCount 个纪念日，但提醒刷新失败，请重新打开应用或检查提醒权限",
            )
        }
        val renamedCopy = if (renamedCount == 0) {
            ""
        } else {
            "，其中 $renamedCount 个因重名已改名"
        }
        return TransferMessage(
            kind = TransferMessageKind.SUCCESS,
            text = "已导入 $importedCount 个纪念日$renamedCopy",
        )
    }

    private fun errorCopy(error: Exception, isImport: Boolean): String = when (error) {
        is TransferFormatException.NotNianriConfiguration -> "选择的文件不是念日配置"
        is TransferFormatException.UnsupportedVersion -> "配置版本暂不支持"
        is TransferFormatException.Corrupt -> "配置文件已损坏或结构不完整"
        is TransferFormatException.InvalidDay -> "配置中包含无效的纪念日"
        is IOException -> "文件无法读取或写入"
        else -> if (isImport) "导入失败，请重试" else "导出失败，请重试"
    }

    class Factory(
        private val container: AppContainer,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(TransferViewModel::class.java))
            return TransferViewModel(
                days = container.importantDays.observeAll(),
                exportConfiguration = container.configurationTransferService::exportConfiguration,
                importConfiguration = container.configurationTransferService::importConfiguration,
            ) as T
        }
    }
}
