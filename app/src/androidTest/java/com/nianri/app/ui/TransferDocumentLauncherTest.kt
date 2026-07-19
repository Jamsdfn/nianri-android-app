package com.nianri.app.ui

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
        compose.onNodeWithText("选择配置并导入").performClick()

        intended(hasAction(Intent.ACTION_OPEN_DOCUMENT))
    }

    @Test
    fun exportActionUsesCreateDocument() {
        val application = compose.activity.application as NianriApplication
        runBlocking {
            application.container.importantDays.save(
                ImportantDay(
                    name = "测试纪念日",
                    basis = CalendarSystem.SOLAR,
                    month = 8,
                    day = 6,
                    appDisplay = CalendarSystem.SOLAR,
                ),
            )
        }
        intending(hasAction(Intent.ACTION_CREATE_DOCUMENT))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null))

        compose.waitUntil {
            compose.onAllNodesWithText("迁移").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("迁移").performClick()
        compose.waitUntil {
            compose.onAllNodesWithText("将导出 1 个纪念日").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("导出全部配置").performClick()

        intended(hasAction(Intent.ACTION_CREATE_DOCUMENT))
    }
}
