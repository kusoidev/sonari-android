package com.kusoi.sonari.music

import android.app.Application

class SonariApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppPaths.context = this
        LibraryStore.load()
        LegacyFlutterMigration.run()
    }
}
