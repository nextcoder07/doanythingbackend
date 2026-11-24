package com.hakdaar.app

import android.app.Application

class HakdaarApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: HakdaarApplication
            private set
    }
}
