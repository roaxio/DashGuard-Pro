package io.github.roaxio.dashguardpro.ui.components.gallery

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.roaxio.dashguardpro.model.TripSession
import io.github.roaxio.dashguardpro.ui.theme.CyberCyan
import io.github.roaxio.dashguardpro.ui.theme.EmergencyRed
import io.github.roaxio.dashguardpro.ui.theme.HudSurface

/**
 * Modal dialog for customizing a Trip Session title.
 * Preserves the chronological sequence prefix (#tripNumber).
 */
@Composable
fun TripRenameDialog(
    trip: TripSession,
    onSaveTitle: (newTitle: String) -> Unit,
    onResetTitle: () -> Unit,
    onDismiss: () -> Unit
) {
    var tripTitleText by remember(trip.id) { mutableStateOf(trip.customTitle ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Trip", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    text = "Customize the title for this trip session. The chronological sequence prefix (#${trip.tripNumber}) is immutable to preserve chronological tracking.",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = tripTitleText,
                    onValueChange = { tripTitleText = it },
                    leadingIcon = {
                        Text(
                            text = "#${trip.tripNumber} ",
                            color = CyberCyan,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    },
                    placeholder = {
                        Text(
                            text = if (trip.isSentry) "Sentry Incident Session" else "Ride",
                            color = Color(0xFF64748B)
                        )
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberCyan,
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("trip_rename_text_field")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSaveTitle(tripTitleText.trim()) },
                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color.Black),
                modifier = Modifier.testTag("trip_rename_save_button")
            ) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row {
                if (!trip.customTitle.isNullOrBlank()) {
                    TextButton(
                        onClick = onResetTitle,
                        modifier = Modifier.testTag("trip_rename_reset_button")
                    ) {
                        Text("Reset", color = EmergencyRed)
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("trip_rename_cancel_button")
                ) {
                    Text("Cancel", color = Color(0xFF94A3B8))
                }
            }
        },
        containerColor = HudSurface
    )
}
