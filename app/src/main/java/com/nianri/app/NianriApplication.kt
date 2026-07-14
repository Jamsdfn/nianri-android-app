package com.nianri.app

import android.app.Application

class NianriApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
