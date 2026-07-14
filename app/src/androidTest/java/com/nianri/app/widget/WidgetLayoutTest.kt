package com.nianri.app.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.RemoteViews
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.AppWidgetId
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nianri.app.NianriApplication
import com.nianri.app.domain.model.CalendarSystem
import com.nianri.app.domain.model.ImportantDay
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalGlanceRemoteViewsApi::class)
class WidgetLayoutTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val content = WidgetModel.Content(
        id = 99,
        name = "一个非常非常长的重要日子名称",
        days = 23,
        basisLabel = "按农历",
        displayedDate = "新历 8月6日",
        display = CalendarSystem.SOLAR,
    )

    @Test
    fun wideRemoteViewsFitsMinimumBoundsInTwoRowsWithoutSegmentedControl() = runBlocking {
        val view = render(DpSize(110.dp, 40.dp), wide = true, model = content)
        val texts = view.textViews()
        val diagnostic = texts.diagnostic()
        saveRender(view, "wide-min-110x40dp.png")

        assertEquals(px(110), view.measuredWidth)
        assertEquals(px(40), view.measuredHeight)
        assertTrue(diagnostic, texts.any { it.text.toString() == "23天" })
        assertTrue(diagnostic, texts.any { it.text.toString() == "新历 8/6 ↻" })
        assertTrue(diagnostic, texts.filter { it.text.isNotBlank() }.all { it.maxLines == 1 })
        assertFalse(diagnostic, texts.any { it.text.toString() == "新历" || it.text.toString() == "农历" })
        assertFalse(diagnostic, texts.any { it.text.toString() == "念日" })
        assertChildrenInside(view, verticalSafetyDp = 2, requireFontBox = true)
    }

    @Test
    fun squareRemoteViewsFitsMinimumBoundsWithFullDateControl() = runBlocking {
        val view = render(DpSize(110.dp, 110.dp), wide = false, model = content)
        val texts = view.textViews()
        val diagnostic = texts.diagnostic()
        saveRender(view, "square-min-110x110dp.png")

        assertEquals(px(110), view.measuredWidth)
        assertEquals(px(110), view.measuredHeight)
        assertTrue(diagnostic, texts.any { it.text.toString() == "按农历倒计时" })
        assertTrue(diagnostic, texts.any { it.text.toString() == "23天" })
        assertTrue(diagnostic, texts.any { it.text.toString() == "本次日期" })
        assertTrue(diagnostic, texts.any { it.text.toString() == "新历 8月6日 ↻" })
        assertFalse(diagnostic, texts.any { it.text.toString() == "念日" })
        assertChildrenInside(view)
    }

    @Test
    fun missingAndUnavailableStatesExposeRecoveryCopy() = runBlocking {
        val missing = render(DpSize(110.dp, 40.dp), wide = true, model = WidgetModel.MissingDay)
        val unavailable = render(
            DpSize(110.dp, 110.dp),
            wide = false,
            model = WidgetModel.DateUnavailable(99, "妈妈生日", "按农历", CalendarSystem.SOLAR),
        )

        val missingTexts = missing.textViews()
        val unavailableTexts = unavailable.textViews()
        saveRender(missing, "wide-missing-110x40dp.png")
        saveRender(unavailable, "square-unavailable-110x110dp.png")
        assertTrue(missingTexts.diagnostic(), missingTexts.any { it.text.toString() == "这个日子已删除" })
        assertTrue(missingTexts.diagnostic(), missingTexts.any { it.text.toString() == "点按选择其他日子" })
        assertTrue(unavailableTexts.diagnostic(), unavailableTexts.any { it.text.toString() == "日期暂不可用" })
        assertTrue(unavailableTexts.diagnostic(), unavailableTexts.any { it.text.toString() == "点按编辑这个日子" })
    }

    @Test
    fun receiverDeletionRemovesOnlyDeletedWidgetPreferences() = runBlocking {
        val container = (context as NianriApplication).container
        container.database.clearAllTables()
        val dayId = container.importantDays.save(
            ImportantDay(
                name = "妈妈生日",
                basis = CalendarSystem.SOLAR,
                month = 8,
                day = 6,
                appDisplay = CalendarSystem.SOLAR,
            ),
        )
        container.widgets.select(901, dayId, CalendarSystem.SOLAR)
        container.widgets.select(902, dayId, CalendarSystem.LUNAR)
        container.widgets.select(903, dayId, CalendarSystem.SOLAR)

        NianriWideWidget().onDelete(context, AppWidgetId(901))
        NianriSquareWidget().onDelete(context, AppWidgetId(902))

        assertTrue(container.widgets.resolve(901) is com.nianri.app.data.WidgetResolution.Unconfigured)
        assertTrue(container.widgets.resolve(902) is com.nianri.app.data.WidgetResolution.Unconfigured)
        assertTrue(container.widgets.resolve(903) is com.nianri.app.data.WidgetResolution.Configured)
        container.database.clearAllTables()
    }

    @Test
    fun unicodeDiagnosticComparesPlatformRemoteViewsWithGlance() = runBlocking {
        val expected = "中文 ↻ 23天"
        val platform = RemoteViews(context.packageName, android.R.layout.simple_list_item_1).apply {
            setTextViewText(android.R.id.text1, expected)
        }.apply(context, FrameLayout(context)).textViews().single().text.toString()
        val glance = render(
            DpSize(110.dp, 40.dp),
            wide = true,
            model = content.copy(name = expected),
        ).textViews().first { it.text.toString().contains("23") || it.text.isNotEmpty() }.text.toString()

        assertEquals("platform codepoints=${platform.codepoints()}", expected.codepoints(), platform.codepoints())
        assertEquals(
            "Glance should preserve Unicode; expected=${expected.codepoints()} actual=${glance.codepoints()}",
            expected.codepoints(),
            glance.codepoints(),
        )
    }

    @Test
    fun referencePixelRendersMatchMeasuredXiaomiCardFrames() = runBlocking {
        val wide = render(DpSize(110.dp, 40.dp), wide = true, model = content, exactPixels = 356 to 136)
        val square = render(DpSize(110.dp, 110.dp), wide = false, model = content, exactPixels = 356 to 356)

        assertEquals(356, wide.measuredWidth)
        assertEquals(136, wide.measuredHeight)
        assertEquals(356, square.measuredWidth)
        assertEquals(356, square.measuredHeight)
        assertChildrenInside(wide)
        assertChildrenInside(square)
        saveRender(wide, "wide-reference-356x136px.png")
        saveRender(square, "square-reference-356x356px.png")
    }

    private suspend fun render(
        size: DpSize,
        wide: Boolean,
        model: WidgetModel,
        exactPixels: Pair<Int, Int>? = null,
    ): View {
        val remoteViews = GlanceRemoteViews().compose(context, size) {
            NianriWidgetSurface(context, 900, model, wide)
        }.remoteViews
        val root = FrameLayout(context)
        val view = remoteViews.apply(context, root)
        val width = exactPixels?.first ?: px(size.width.value.toInt())
        val height = exactPixels?.second ?: px(size.height.value.toInt())
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, width, height)
        return view
    }

    private fun assertChildrenInside(
        root: View,
        verticalSafetyDp: Int = 0,
        requireFontBox: Boolean = false,
    ) {
        val verticalSafetyPx = px(verticalSafetyDp)
        root.textViews().forEach {
            var left = it.left
            var top = it.top
            var ancestor = it.parent
            while (ancestor is View && ancestor !== root) {
                left += ancestor.left
                top += ancestor.top
                ancestor = ancestor.parent
            }
            assertTrue("left bound for ${it.text}: $left", left >= 0)
            assertTrue("top bound for ${it.text}: $top", top >= verticalSafetyPx)
            assertTrue("right bound for ${it.text}: ${left + it.width}", left + it.width <= root.measuredWidth)
            assertTrue(
                "bottom safety for ${it.text}: ${top + it.height}, root=${root.measuredHeight}, safety=$verticalSafetyPx",
                top + it.height <= root.measuredHeight - verticalSafetyPx,
            )
            if (requireFontBox) {
                val metrics = it.paint.fontMetricsInt
                assertTrue(
                    "font top for ${it.text}: height=${it.height}, baseline=${it.baseline}, top=${metrics.top}",
                    it.baseline + metrics.top >= 0,
                )
                assertTrue(
                    "font bottom for ${it.text}: height=${it.height}, baseline=${it.baseline}, bottom=${metrics.bottom}",
                    it.baseline + metrics.bottom <= it.height,
                )
            }
            assertNotNull(it.text)
        }
    }

    private fun View.textViews(): List<TextView> = buildList {
        fun visit(view: View) {
            if (view is TextView) add(view)
            if (view is ViewGroup) (0 until view.childCount).forEach { visit(view.getChildAt(it)) }
        }
        visit(this@textViews)
    }

    private fun List<TextView>.diagnostic(): String = joinToString(prefix = "RemoteViews text nodes: [", postfix = "]") {
        "'${it.text}' codepoints=${it.text.toString().codepoints()} visibility=${it.visibility} " +
            "bounds=${it.left},${it.top}-${it.right},${it.bottom} maxLines=${it.maxLines}"
    }


    private fun String.codepoints(): String = codePoints().toArray().joinToString(",") { "U+%04X".format(it) }

    private fun saveRender(view: View, fileName: String) {
        val bitmap = Bitmap.createBitmap(view.measuredWidth, view.measuredHeight, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val directory = File(context.getExternalFilesDir(null), "task-9-visuals").apply { mkdirs() }
        FileOutputStream(File(directory, fileName)).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun px(dp: Int): Int = (dp * context.resources.displayMetrics.density).toInt()
}
