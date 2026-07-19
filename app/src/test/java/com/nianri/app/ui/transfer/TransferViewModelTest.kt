package com.nianri.app.ui.transfer

import android.os.Looper
import com.nianri.app.data.transfer.TransferFormatException
import com.nianri.app.data.transfer.TransferImportResult
import com.nianri.app.domain.model.CalendarSystem
import com.nianri.app.domain.model.ImportantDay
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TransferViewModelTest {
    @Test
    fun `defaults to export and exposes current day count`() {
        val days = MutableStateFlow(listOf(day("A"), day("B")))
        val viewModel = viewModel(days = days)

        waitForState { viewModel.uiState.value.dayCount == 2 }

        assertEquals(TransferTab.EXPORT, viewModel.uiState.value.selectedTab)
        assertEquals(2, viewModel.uiState.value.dayCount)
    }

    @Test
    fun `successful import reports imported and renamed counts`() {
        val viewModel = viewModel(importResult = TransferImportResult(5, 2, false))

        viewModel.importFrom { "valid json" }
        waitForFinished(viewModel)

        assertEquals(
            TransferMessage(
                kind = TransferMessageKind.SUCCESS,
                text = "已导入 5 个纪念日，其中 2 个因重名已改名",
            ),
            viewModel.uiState.value.message,
        )
    }

    @Test
    fun `refresh failure reports a warning while preserving success`() {
        val viewModel = viewModel(importResult = TransferImportResult(3, 0, true))

        viewModel.importFrom { "valid json" }
        waitForFinished(viewModel)

        assertEquals(TransferMessageKind.WARNING, viewModel.uiState.value.message?.kind)
        assertEquals(
            "已导入 3 个纪念日，但提醒刷新失败，请重新打开应用或检查提醒权限",
            viewModel.uiState.value.message?.text,
        )
    }

    @Test
    fun `unsupported version has dedicated copy and processing resets`() {
        val viewModel = viewModel(
            importFailure = TransferFormatException.UnsupportedVersion(2),
        )

        viewModel.importFrom { "version 2" }
        waitForFinished(viewModel)

        assertFalse(viewModel.uiState.value.isProcessing)
        assertEquals(TransferMessageKind.ERROR, viewModel.uiState.value.message?.kind)
        assertEquals("配置版本暂不支持", viewModel.uiState.value.message?.text)
    }

    @Test
    fun `export writes content and reports the captured day count`() {
        val written = mutableListOf<String>()
        val viewModel = viewModel(days = MutableStateFlow(listOf(day("A"), day("B"))))
        waitForState { viewModel.uiState.value.dayCount == 2 }

        viewModel.exportTo { written += it }
        waitForFinished(viewModel)

        assertEquals(listOf("exported-json"), written)
        assertEquals("已导出 2 个纪念日", viewModel.uiState.value.message?.text)
        assertEquals("念日配置-20260719.nianri.json", viewModel.defaultExportFileName())
    }

    @Test
    fun `a second operation is ignored while processing`() {
        var imports = 0
        val gate = java.util.concurrent.CountDownLatch(1)
        val viewModel = TransferViewModel(
            days = MutableStateFlow(emptyList()),
            exportConfiguration = { "exported-json" },
            importConfiguration = {
                imports += 1
                gate.await()
                TransferImportResult(1, 0, false)
            },
            currentDate = { LocalDate.of(2026, 7, 19) },
        )

        viewModel.importFrom { "first" }
        waitForState { viewModel.uiState.value.isProcessing }
        viewModel.importFrom { "second" }
        gate.countDown()
        waitForFinished(viewModel)

        assertEquals(1, imports)
    }

    private fun viewModel(
        days: MutableStateFlow<List<ImportantDay>> = MutableStateFlow(emptyList()),
        importResult: TransferImportResult = TransferImportResult(1, 0, false),
        importFailure: Exception? = null,
    ) = TransferViewModel(
        days = days,
        exportConfiguration = { "exported-json" },
        importConfiguration = {
            importFailure?.let { throw it }
            importResult
        },
        currentDate = { LocalDate.of(2026, 7, 19) },
    )

    private fun waitForFinished(viewModel: TransferViewModel) {
        waitForState {
            !viewModel.uiState.value.isProcessing && viewModel.uiState.value.message != null
        }
    }

    private fun waitForState(condition: () -> Boolean) {
        repeat(300) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(10)
        }
        throw AssertionError("Timed out waiting for transfer state")
    }

    private fun day(name: String) = ImportantDay(
        name = name,
        basis = CalendarSystem.SOLAR,
        month = 8,
        day = 6,
        appDisplay = CalendarSystem.SOLAR,
    )
}
