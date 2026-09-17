package com.threedd.studio.ui.components

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStoreOwner
import androidx.compose.ui.platform.LocalContext

/**
 * Resolves a ViewModel against the hosting Activity instead of the current navigation
 * back-stack entry, so every screen shares one studio session (model, material, light,
 * morph and animation state) rather than each tab owning a private copy.
 */
@Composable
inline fun <reified VM : ViewModel> sessionViewModel(): VM {
    val owner = LocalContext.current as? ViewModelStoreOwner
        ?: error("No ViewModelStoreOwner found in the composition context")
    return hiltViewModel(owner)
}
