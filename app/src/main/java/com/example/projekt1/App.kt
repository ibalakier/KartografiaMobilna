package com.example.projekt1

import android.app.Application
import org.maplibre.android.MapLibre

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
    }
}
