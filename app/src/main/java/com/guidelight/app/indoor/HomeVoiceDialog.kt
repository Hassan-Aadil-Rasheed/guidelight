package com.guidelight.app.indoor

import android.app.Activity
import android.text.InputType
import android.view.KeyEvent
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatEditText
import androidx.compose.runtime.*
import com.guidelight.app.ui.VoiceKeyboard
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.core.widget.doAfterTextChanged

/** A platform dialog keeps the dictation editor in a normal accessibility window.
 * Showing it does not pause the Activity or stop the home mapping session.
 */
@Composable
internal fun HomeVoiceDialog(activity: Activity, request: Int, onText: (String) -> Unit,
    onSubmit: () -> Unit, onKey: (KeyEvent) -> Boolean, onCancel: () -> Unit, hideKeyboard: Boolean = false) {
    val latestText = rememberUpdatedState(onText)
    val latestSubmit = rememberUpdatedState(onSubmit)
    val latestKey = rememberUpdatedState(onKey)
    val latestCancel = rememberUpdatedState(onCancel)
    val latestHideKeyboard = rememberUpdatedState(hideKeyboard)
    var activeEditor by remember { mutableStateOf<AppCompatEditText?>(null) }
    LaunchedEffect(hideKeyboard, activeEditor) {
        if (hideKeyboard) activeEditor?.let { VoiceKeyboard.hide(activity, it) }
    }
    DisposableEffect(request) {
        val editor = AppCompatEditText(activity).apply {
            id = com.guidelight.app.R.id.saved_home_command
            hint = "Your command"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_GO
            textSize = 20f
            doAfterTextChanged { latestText.value(it?.toString().orEmpty()) }
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_GO) { latestSubmit.value(); true } else false
            }
        }
        val dialog = AlertDialog.Builder(activity)
            .setTitle("Speak with Wispr Flow")
            .setMessage("Use the GuideLight button or the Wispr bubble. Speak after the vibration.")
            .setView(editor)
            .setPositiveButton("Send") { _, _ -> latestSubmit.value() }
            .setNegativeButton("Cancel") { _, _ -> latestCancel.value() }
            .create()
        dialog.setOnKeyListener { _, _, event -> latestKey.value(event) }
        dialog.setOnCancelListener { latestCancel.value() }
        dialog.setOnShowListener {
            editor.requestFocus()
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            editor.postDelayed({
                if (editor.isAttachedToWindow && !latestHideKeyboard.value) activity.getSystemService(InputMethodManager::class.java)
                    .showSoftInput(editor,InputMethodManager.SHOW_IMPLICIT)
            },150)
        }
        activeEditor = editor
        dialog.show()
        onDispose { editor.clearFocus(); VoiceKeyboard.hide(activity, editor); activeEditor = null; dialog.setOnCancelListener(null); dialog.dismiss() }
    }
}
