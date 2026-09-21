package com.threedd.studio

import android.app.Application
import com.google.android.filament.Filament
import com.google.android.filament.filamat.MaterialBuilder
import com.threedd.studio.ai.LocalModelLauncher
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class ThreeDoubleDApplication : Application() {

    @Inject
    lateinit var localModelLauncher: LocalModelLauncher

    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Loading the native libraries once, up front, avoids a stall on the first render.
        Filament.init()
        // filamat compiles the studio PBR material on device.
        MaterialBuilder.init()

        // Ask Termux to start Ollama immediately. ONLINE is only set after /v1/models answers.
        startupScope.launch { localModelLauncher.start() }
    }
}
