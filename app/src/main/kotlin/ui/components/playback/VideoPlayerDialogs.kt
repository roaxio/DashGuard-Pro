package io.github.roaxio.dashguardpro.ui.components.playback

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.roaxio.dashguardpro.model.VideoClip
import io.github.roaxio.dashguardpro.ui.components.gallery.ShareChoiceDialog
import io.github.roaxio.dashguardpro.ui.theme.HudSurface
import io.github.roaxio.dashguardpro.ui.theme.NeonAmber
import java.io.File

/**
 * Handles presentation dialogs for VideoPlayerScreen:
 * 1. Share choice dialog (Single video vs Video + Subtitles vs Fused MP4).
 * 2. Unlock warning confirmation dialog to prevent accidental deletion of protected clips.
 */
@Composable
fun VideoPlayerDialogs(
    clip: VideoClip,
    showShareChoiceDialog: Boolean,
    showUnlockWarningDialog: Boolean,
    onDismissShare: () -> Unit,
    onDismissUnlockWarning: () -> Unit,
    onConfirmUnlock: () -> Unit,
    onShareSeparate: (File, File?) -> Unit,
    onShareFused: (File) -> Unit
) {
    if (showShareChoiceDialog) {
        ShareChoiceDialog(
            clip = clip,
            onDismiss = onDismissShare,
            onShareSeparate = onShareSeparate,
            onShareFused = onShareFused
        )
    }

    if (showUnlockWarningDialog) {
        AlertDialog(
            onDismissRequest = onDismissUnlockWarning,
            title = {
                Text(
                    text = "Unlock Protected Clip?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "This clip is currently locked to prevent automatic loop cycling and accidental deletion. Unlocking it will allow DashGuard to overwrite it when storage space runs low.\n\nAre you sure you want to unlock it?",
                    color = Color(0xFFCBD5E1),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = onConfirmUnlock,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonAmber, contentColor = Color.Black)
                ) {
                    Text("Unlock Clip", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissUnlockWarning) {
                    Text("Cancel", color = Color(0xFF94A3B8))
                }
            },
            containerColor = HudSurface
        )
    }
}
