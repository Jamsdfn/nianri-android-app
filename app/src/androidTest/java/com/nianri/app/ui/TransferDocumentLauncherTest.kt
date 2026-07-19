package com.nianri.app.ui

import android.app.Activity
import android.app.Instrumentation
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import com.nianri.app.MainActivity
import com.nianri.app.NianriApplication
import com.nianri.app.domain.model.CalendarSystem
import com.nianri.app.domain.model.ImportantDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue

class TransferDocumentLauncherTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun initIntents() {
        val application = compose.activity.application as NianriApplication
        runBlocking {
            withContext(Dispatchers.IO) {
                application.container.database.clearAllTables()
            }
        }
        clipboard().clearPrimaryClip()
        Intents.init()
    }

    @After
    fun releaseIntents() {
        Intents.release()
    }

    @Test
    fun importActionUsesOpenDocument() {
        intending(hasAction(Intent.ACTION_OPEN_DOCUMENT))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null))

        compose.waitUntil {
            compose.onAllNodesWithText("迁移").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("迁移").performClick()
        compose.onNodeWithTag("transfer-tab-import").performClick()
        compose.onNodeWithText("选择配置文件").performClick()

        intended(hasAction(Intent.ACTION_OPEN_DOCUMENT))
    }

    @Test
    fun exportActionUsesCreateDocument() {
        saveDay("测试纪念日")
        intending(hasAction(Intent.ACTION_CREATE_DOCUMENT))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null))

        compose.waitUntil {
            compose.onAllNodesWithText("迁移").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("迁移").performClick()
        compose.waitUntil {
            compose.onAllNodesWithText("将导出 1 个纪念日").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("保存配置到本机").performClick()

        intended(hasAction(Intent.ACTION_CREATE_DOCUMENT))
    }

    @Test
    fun copyActionWritesVersionedJsonToClipboard() {
        saveDay("测试纪念日")
        compose.onNodeWithText("迁移").performClick()
        compose.waitUntil {
            compose.onAllNodesWithText("复制配置到剪贴板").fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithText("复制配置到剪贴板").performClick()
        compose.waitUntil {
            clipboard().primaryClip
                ?.getItemAt(0)
                ?.text
                ?.toString()
                ?.contains("nianri-configuration") == true
        }

        assertTrue(
            clipboard().primaryClip!!
                .getItemAt(0)
                .text
                .toString()
                .contains("测试纪念日"),
        )
    }

    @Test
    fun pasteActionReadsClipboardIntoImportField() {
        clipboard().setPrimaryClip(ClipData.newPlainText("test", "{pasted-json}"))
        compose.onNodeWithText("迁移").performClick()
        compose.onNodeWithTag("transfer-tab-import").performClick()

        compose.onNodeWithText("从剪贴板粘贴").performClick()

        compose.onNodeWithTag("transfer-import-text").assertTextContains("{pasted-json}")
    }

    @Test
    fun emptyClipboardDoesNotReplaceExistingInput() {
        clipboard().clearPrimaryClip()
        compose.onNodeWithText("迁移").performClick()
        compose.onNodeWithTag("transfer-tab-import").performClick()
        compose.onNodeWithTag("transfer-import-text").performTextInput("keep-me")

        compose.onNodeWithText("从剪贴板粘贴").performClick()

        compose.onNodeWithTag("transfer-import-text").assertTextContains("keep-me")
        compose.onNodeWithText("剪贴板中没有可粘贴的配置").assertIsDisplayed()
    }

    private fun clipboard(): ClipboardManager =
        compose.activity.getSystemService(ClipboardManager::class.java)

    private fun saveDay(name: String) {
        val application = compose.activity.application as NianriApplication
        runBlocking {
            application.container.importantDays.save(
                ImportantDay(
                    name = name,
                    basis = CalendarSystem.SOLAR,
                    month = 8,
                    day = 6,
                    appDisplay = CalendarSystem.SOLAR,
                ),
            )
        }
    }
}
