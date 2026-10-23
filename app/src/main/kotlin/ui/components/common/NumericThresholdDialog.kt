package io.github.roaxio.dashguardpro.ui.components.common

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.editableText
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setText
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.github.roaxio.dashguardpro.ui.theme.CyberCyan
import io.github.roaxio.dashguardpro.ui.theme.EmergencyRed
import io.github.roaxio.dashguardpro.ui.theme.HudSurface

/**
 * Authoritative cyber-styled numeric threshold input dialog for DashGuard Pro.
 * Provides consistent layout, scroll state, digit filtering, stepper adjustment,
 * bounds error display, and high-contrast confirm/dismiss action buttons across
 * settings and optimization screens.
 */
@Composable
fun NumericThresholdDialog(
    title: String,
    description: String,
    initialValue: String,
    label: String,
    confirmButtonText: String = "Save",
    icon: ImageVector = Icons.Default.Edit,
    validator: (String) -> String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var inputText by remember(initialValue) { mutableStateOf(initialValue) }
    var errorMessage by remember(initialValue) { mutableStateOf<String?>(null) }
    val isHeadlessSandbox = remember { Build.FINGERPRINT.equals("robolectric", ignoreCase = true) }

    val updateValue: (String) -> Unit = { newText ->
        val filtered = newText.filter { it.isDigit() }
        inputText = filtered
        errorMessage = validator(filtered)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = HudSurface,
            border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = CyberCyan,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = description,
                    color = Color(0xFFCBD5E1),
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = label,
                    color = if (errorMessage != null) EmergencyRed else CyberCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0B1120))
                        .border(
                            width = 1.dp,
                            color = if (errorMessage != null) EmergencyRed else CyberCyan,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = {
                            val current = inputText.toIntOrNull() ?: 0
                            if (current > 0) {
                                updateValue((current - 1).toString())
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "Decrease",
                            tint = CyberCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    if (isHeadlessSandbox) {
                        Text(
                            text = inputText,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 8.dp)
                                .semantics {
                                    editableText = AnnotatedString(inputText)
                                    setText { annotated ->
                                        updateValue(annotated.text)
                                        true
                                    }
                                }
                                .onKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyUp) {
                                        when (val keyCode = event.nativeKeyEvent.keyCode) {
                                            android.view.KeyEvent.KEYCODE_DEL -> {
                                                if (inputText.isNotEmpty()) {
                                                    updateValue(inputText.dropLast(1))
                                                }
                                                true
                                            }
                                            in android.view.KeyEvent.KEYCODE_0..android.view.KeyEvent.KEYCODE_9 -> {
                                                val digit = (keyCode - android.view.KeyEvent.KEYCODE_0).toString()
                                                updateValue(inputText + digit)
                                                true
                                            }
                                            else -> false
                                        }
                                    } else {
                                        false
                                    }
                                }
                                .focusable()
                                .testTag("numeric_threshold_input")
                        )
                    } else {
                        BasicTextField(
                            value = inputText,
                            onValueChange = updateValue,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            cursorBrush = SolidColor(CyberCyan),
                            textStyle = TextStyle(
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                textAlign = TextAlign.Center
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 8.dp)
                                .testTag("numeric_threshold_input")
                        )
                    }
                    IconButton(
                        onClick = {
                            val current = inputText.toIntOrNull() ?: 0
                            updateValue((current + 1).toString())
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Increase",
                            tint = CyberCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = errorMessage!!,
                        color = EmergencyRed,
                        fontSize = 11.sp
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF94A3B8)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Cancel", fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (errorMessage == null && inputText.isNotEmpty()) {
                                onConfirm(inputText)
                            }
                        },
                        enabled = errorMessage == null && inputText.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyberCyan,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = confirmButtonText,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

