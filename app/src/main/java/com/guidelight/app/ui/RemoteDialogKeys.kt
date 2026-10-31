package com.guidelight.app.ui

import android.view.KeyEvent
import android.view.Window
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

/** Keep the same opt-in Selfie handler available inside app-owned Material dialogs. */
@Composable
internal fun RemoteDialogKeys(onKey: (KeyEvent) -> Boolean) {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window
    val latestKey = rememberUpdatedState(onKey)
    DisposableEffect(window) {
        val original = window?.callback
        val callback = original?.let { previous -> object : Window.Callback by previous {
            override fun dispatchKeyEvent(event: KeyEvent) = latestKey.value(event) || previous.dispatchKeyEvent(event)
        } }
        if (callback != null) window.callback = callback
        onDispose { if (callback != null && window?.callback === callback) window.callback = original }
    }
}
