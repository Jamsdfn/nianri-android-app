package com.nianri.app.widget

import android.content.Context
import android.content.ComponentName
import android.content.res.Configuration
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetHost
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
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
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.nianri.app.NianriApplication
import com.nianri.app.domain.model.CalendarSystem
import com.nianri.app.domain.model.ImportantDay
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
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
    @SdkSuppress(minSdkVersion = 29)
    fun privateReceiverDeletionLifecycleStillCleansItsInstance() = runBlocking {
        val container = (context as NianriApplication).container
        container.database.clearAllTables()
        val dayId = container.importantDays.save(
            ImportantDay(
                name = "接收器清理",
                basis = CalendarSystem.SOLAR,
                month = 8,
                day = 6,
                appDisplay = CalendarSystem.SOLAR,
            ),
        )
        val host = AppWidgetHost(context, 9_904)
        val manager = AppWidgetManager.getInstance(context)
        val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
        uiAutomation.adoptShellPermissionIdentity(android.Manifest.permission.BIND_APPWIDGET)
        val appWidgetId = host.allocateAppWidgetId()
        try {
            assertTrue(
                manager.bindAppWidgetIdIfAllowed(
                    appWidgetId,
                    ComponentName(context, NianriWideWidgetReceiver::class.java),
                ),
            )
            container.widgets.select(appWidgetId, dayId, CalendarSystem.SOLAR)

            host.deleteAppWidgetId(appWidgetId)

            withTimeout(3_000) {
                while (
                    container.widgets.resolve(appWidgetId) !is com.nianri.app.data.WidgetResolution.Unconfigured
                ) {
                    delay(25)
                }
            }
        } finally {
            runCatching { host.deleteAppWidgetId(appWidgetId) }
            host.stopListening()
            uiAutomation.dropShellPermissionIdentity()
        }
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

    @Test
    fun minimumWidgetsRemainReadableAtLargeSystemFontScales() = runBlocking {
        listOf(1.15f, 1.3f, 2.0f).forEach { fontScale ->
            val wide = render(DpSize(110.dp, 40.dp), wide = true, model = content, fontScale = fontScale)
            val square = render(DpSize(110.dp, 110.dp), wide = false, model = content, fontScale = fontScale)
            val wideTexts = wide.textViews().map { it.text.toString() }
            val squareTexts = square.textViews().map { it.text.toString() }

            assertTrue("wide name at $fontScale: $wideTexts", content.name in wideTexts)
            assertTrue("wide days at $fontScale: $wideTexts", "23天" in wideTexts)
            if (!(Build.VERSION.SDK_INT <= Build.VERSION_CODES.O && fontScale >= 2f)) {
                assertTrue("wide date at $fontScale: $wideTexts", wideTexts.any { "8/6" in it })
            }
            assertTrue("square days at $fontScale: $squareTexts", "23天" in squareTexts)
            assertTrue("square date at $fontScale: $squareTexts", squareTexts.any { "8月6日" in it })
            if (fontScale == 2.0f) {
                saveRender(wide, "wide-font-scale-2.0-110x40dp.png")
                saveRender(square, "square-font-scale-2.0-110x110dp.png")
            }
            assertChildrenInside(wide, verticalSafetyDp = 1, requireFontBox = true, label = "wide@$fontScale")
            assertChildrenInside(square, verticalSafetyDp = 1, requireFontBox = true, label = "square@$fontScale")
        }
    }

    @Test
    fun recoveryWidgetsRemainReadableAtMaximumFontScale() = runBlocking {
        val unconfigured = render(
            DpSize(110.dp, 40.dp),
            wide = true,
            model = WidgetModel.Unconfigured,
            fontScale = 2f,
        )
        val missing = render(
            DpSize(110.dp, 40.dp),
            wide = true,
            model = WidgetModel.MissingDay,
            fontScale = 2f,
        )
        val unavailable = render(
            DpSize(110.dp, 110.dp),
            wide = false,
            model = WidgetModel.DateUnavailable(99, "妈妈生日", "按农历", CalendarSystem.SOLAR),
            fontScale = 2f,
        )

        assertChildrenInside(unconfigured, verticalSafetyDp = 1, requireFontBox = true)
        assertChildrenInside(missing, verticalSafetyDp = 1, requireFontBox = true)
        assertChildrenInside(unavailable, verticalSafetyDp = 1, requireFontBox = true)
        assertTrue(missing.textViews().any { it.text.toString() == "这个日子已删除" })
        assertTrue(unavailable.textViews().any { it.text.toString() == "日期暂不可用" })
    }

    @Test
    fun primaryContentOpensDetailAndFullWidthDateRowOwnsToggle() = runBlocking {
        listOf(true to DpSize(110.dp, 40.dp), false to DpSize(110.dp, 110.dp)).forEach { (wide, size) ->
            val view = render(size, wide = wide, model = content)
            val fullBounds = Rect(0, 0, view.measuredWidth, view.measuredHeight)
            val primaryText = view.textViews().first { it.text.toString() == content.name }
            val dateText = view.textViews().first { "↻" in it.text.toString() }
            val primaryTarget = primaryText.closestClickableAncestor()
            val dateTarget = dateText.closestClickableAncestor()

            assertNotNull("primary content lacks detail target", primaryTarget)
            assertNotNull("date row lacks toggle target", dateTarget)
            assertNotEquals("detail and toggle targets must be siblings", primaryTarget, dateTarget)
            assertFalse(
                "content widgets must not retain an overlapping full-card action",
                view.allViews().any { it.hasOnClickListeners() && it.boundsIn(view) == fullBounds },
            )
            val horizontalInset = px(if (wide) 7 else 10)
            assertTrue(
                "date toggle must span the padded card width",
                dateTarget!!.boundsIn(view).width() >= view.measuredWidth - horizontalInset * 2,
            )
        }
    }

    private suspend fun render(
        size: DpSize,
        wide: Boolean,
        model: WidgetModel,
        exactPixels: Pair<Int, Int>? = null,
        fontScale: Float = 1.0f,
    ): View {
        val renderContext = context.createConfigurationContext(
            Configuration(context.resources.configuration).apply { this.fontScale = fontScale },
        )
        val remoteViews = GlanceRemoteViews().compose(renderContext, size) {
            NianriWidgetSurface(renderContext, 900, model, wide)
        }.remoteViews
        val root = FrameLayout(renderContext)
        val view = remoteViews.apply(renderContext, root)
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
        label: String = "",
    ) {
        val verticalSafetyPx = px(verticalSafetyDp)
        val hierarchy = root.allViews().joinToString(prefix = "hierarchy=[", postfix = "]") {
            "${it.javaClass.simpleName}{bounds=${it.boundsIn(root)},click=${it.hasOnClickListeners()}}"
        }
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
            if (requireFontBox && it.text.isNotEmpty()) {
                val metrics = it.paint.fontMetricsInt
                assertTrue(
                    "font top for ${it.text}: height=${it.height}, baseline=${it.baseline}, top=${metrics.top}",
                    it.baseline + metrics.top >= 0,
                )
                assertTrue(
                    "font bottom $label for ${it.text}: height=${it.height}, baseline=${it.baseline}, " +
                        "bottom=${metrics.bottom}; $hierarchy",
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

    private fun View.allViews(): List<View> = buildList {
        fun visit(view: View) {
            add(view)
            if (view is ViewGroup) (0 until view.childCount).forEach { visit(view.getChildAt(it)) }
        }
        visit(this@allViews)
    }

    private fun View.closestClickableAncestor(): View? {
        var candidate: View? = this
        while (candidate != null) {
            if (candidate.hasOnClickListeners()) return candidate
            candidate = candidate.parent as? View
        }
        return null
    }

    private fun View.boundsIn(root: View): Rect {
        var globalLeft = left
        var globalTop = top
        var ancestor = parent
        while (ancestor is View && ancestor !== root) {
            globalLeft += ancestor.left
            globalTop += ancestor.top
            ancestor = ancestor.parent
        }
        return Rect(globalLeft, globalTop, globalLeft + width, globalTop + height)
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
