package com.threedd.studio

import android.app.Application
import com.google.android.filament.Filament
import com.google.android.filament.filamat.MaterialBuilder
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ThreeDoubleDApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Loading the native libraries once, up front, avoids a stall on the first render.
        Filament.init()
        // filamat compiles the studio PBR material on device.
        MaterialBuilder.init()
    }
}
