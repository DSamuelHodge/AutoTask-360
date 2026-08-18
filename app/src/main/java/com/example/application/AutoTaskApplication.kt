package com.example.application

import android.app.Application

class AutoTaskApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AutoTaskRuntime.start(this)
    }
}
