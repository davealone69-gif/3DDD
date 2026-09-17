package com.threedd.studio

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.threedd.studio.ui.ThreeDoubleDApp
import com.threedd.studio.ui.theme.ThreeDoubleDTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ThreeDoubleDTheme {
                ThreeDoubleDApp()
            }
        }
    }

    /** Deep links for model/gltf-binary intents land in the Library, which offers import. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}
