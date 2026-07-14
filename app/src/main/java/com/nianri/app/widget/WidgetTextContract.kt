package com.nianri.app.widget

data class WidgetTextRow(val leading: String, val trailing: String)

data class WideWidgetTextContract(
    val rows: List<WidgetTextRow>,
    val nameSingleLineEllipsized: Boolean = true,
    val hasSegmentedToggle: Boolean = false,
)

data class SquareWidgetTextContract(
    val name: String,
    val basis: String,
    val days: String,
    val dateLabel: String,
    val dateControl: String,
)

object WidgetTextContract {
    val missingRows = listOf("这个日子已删除", "点按选择其他日子")
    val unavailableRows = listOf("日期暂不可用", "点按编辑这个日子")

    fun daysText(days: Long): String = if (days == 0L) "就是今天" else "${days}天"

    fun wide(model: WidgetModel.Content): WideWidgetTextContract = WideWidgetTextContract(
        rows = listOf(
            WidgetTextRow(model.name, daysText(model.days)),
            WidgetTextRow("${model.basisLabel} ·", "${compactDate(model.displayedDate)} ↻"),
        ),
    )

    fun square(model: WidgetModel.Content): SquareWidgetTextContract = SquareWidgetTextContract(
        name = model.name,
        basis = "${model.basisLabel}倒计时",
        days = daysText(model.days),
        dateLabel = "本次日期",
        dateControl = "${model.displayedDate} ↻",
    )

    private fun compactDate(value: String): String = value
        .replace(Regex("(\\d{1,2})月(\\d{1,2})日"), "$1/$2")
        .replace("月", "/")
}
