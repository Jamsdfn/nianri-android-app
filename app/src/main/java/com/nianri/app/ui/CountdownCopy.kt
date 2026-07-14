package com.nianri.app.ui

fun countdownCopy(daysRemaining: Long): String = when (daysRemaining) {
    0L -> "就是今天"
    else -> "还有 $daysRemaining 天"
}
