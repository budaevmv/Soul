package com.travelplanner.app.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material3.OutlinedTextField as M3OutlinedTextField

/** Поле ввода без обводки: мягкая подложка, тонкая линия только при фокусе. */
@Composable
fun OutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: (@Composable () -> Unit)? = null,
    singleLine: Boolean = false,
    isError: Boolean = false,
    readOnly: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    supportingText: (@Composable () -> Unit)? = null,
) {
    val cs = MaterialTheme.colorScheme
    M3OutlinedTextField(
        value = value, onValueChange = onValueChange, modifier = modifier, label = label, singleLine = singleLine,
        isError = isError, readOnly = readOnly, keyboardOptions = keyboardOptions, supportingText = supportingText,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = Color.Transparent,
            focusedBorderColor = cs.onSurface.copy(alpha = 0.35f),
            errorBorderColor = cs.error.copy(alpha = 0.5f),
            unfocusedContainerColor = cs.surfaceContainer,
            focusedContainerColor = cs.surfaceContainer,
            errorContainerColor = cs.surfaceContainer,
            unfocusedLabelColor = cs.onSurfaceVariant,
            focusedLabelColor = cs.onSurface,
        ),
    )
}
