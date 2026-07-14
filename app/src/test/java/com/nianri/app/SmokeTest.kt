package com.nianri.app

import org.junit.Assert.assertEquals
import org.junit.Test

class SmokeTest {
    @Test fun appName_isNianri() {
        assertEquals("念日", BuildConfig.APP_NAME)
    }
}
