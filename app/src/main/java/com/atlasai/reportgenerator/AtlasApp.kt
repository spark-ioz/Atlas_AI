package com.atlasai.reportgenerator

import android.app.Application

class AtlasApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemeHelper.applySaved(this)
    }
}
