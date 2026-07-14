package com.nianri.app.domain.model

fun adjustmentCopy(adjustment: DateAdjustment?): String? = when (adjustment) {
    DateAdjustment.NON_LEAP_YEAR -> "今年不是闰年，本次提前 1 天"
    DateAdjustment.SHORT_LUNAR_MONTH -> "本月只有二十九天，本次提前 1 天"
    null -> null
}
