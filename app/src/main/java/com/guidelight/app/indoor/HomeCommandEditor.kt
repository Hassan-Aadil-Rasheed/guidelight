package com.guidelight.app.indoor

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import kotlinx.coroutines.delay

/** Keep Wispr's insertion target mounted outside the scrolling instructions. */
@Composable
internal fun HomeCommandEditor(voice: HomeVoiceState, onText: (String) -> Unit, onSubmit: () -> Unit) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    LaunchedEffect(voice.request, voice.armed, voice.hideKeyboard) {
        if (voice.armed && !voice.hideKeyboard) {
            focus.requestFocus()
            delay(100)
            keyboard?.show()
        } else {
            if (!voice.armed && voice.hideKeyboard) focusManager.clearFocus(force = true)
            keyboard?.hide()
        }
    }
    OutlinedTextField(
        value = voice.text, onValueChange = onText,
        modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("saved-map-command"),
        label = { Text("Command") }, placeholder = { Text("Try “Describe the view”") },
        leadingIcon = { Icon(Icons.Outlined.ChatBubbleOutline, null) },
        trailingIcon = {
            IconButton(onClick = { keyboard?.hide(); onSubmit() }, enabled = voice.text.isNotBlank()) {
                Icon(Icons.AutoMirrored.Outlined.Send, "Send home command")
            }
        },
        shape = MaterialTheme.shapes.small,
        textStyle = MaterialTheme.typography.bodyLarge,
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); onSubmit() })
    )
}
