package io.github.roaxio.dashguardpro.ui.components.optimization

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.roaxio.dashguardpro.ui.components.common.DashboardDropdownRow
import io.github.roaxio.dashguardpro.ui.components.common.DashboardToggleRow
import io.github.roaxio.dashguardpro.ui.theme.CyberCyan
import io.github.roaxio.dashguardpro.ui.theme.HudSurface
import io.github.roaxio.dashguardpro.ui.theme.NeonAmber
import io.github.roaxio.dashguardpro.ui.theme.NeonGreen

/**
 * Numbered step instruction row used inside OEM and Kiosk guide dialogs.
 */
@Composable
fun GuideStepRow(number: String, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(CyberCyan),
            contentAlignment = Alignment.Center
        ) {
            Text(text = number, color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Black)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            color = Color(0xFFCBD5E1),
            fontSize = 12.sp,
            lineHeight = 16.sp,
            softWrap = true,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Diagnostic row displaying an environmental metric label, value, and status dot indicator.
 */
@Composable
fun DiagRow(
    label: String,
    value: String,
    isGood: Boolean,
    statusText: String,
    overrideStatusColor: Color? = null,
    onClick: (() -> Unit)? = null
) {
    val rowModifier = Modifier
        .fillMaxWidth()
        .defaultMinSize(minHeight = 48.dp)
        .clip(RoundedCornerShape(8.dp))
        .then(if (onClick != null) Modifier.clickable { onClick.invoke() } else Modifier)
        .animateContentSize()
        .padding(vertical = 8.dp, horizontal = 4.dp)

    Row(
        modifier = rowModifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        ) {
            Text(
                text = label,
                color = Color(0xFF94A3B8),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                softWrap = true
            )
            Text(
                text = value,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                lineHeight = 16.sp,
                softWrap = true
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Clean, high-contrast status indicator
        val statusColor = overrideStatusColor ?: (if (isGood) NeonGreen else NeonAmber)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = statusText,
                color = statusColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * Numbered optimization step container card with stateful action button and optional secondary guide button.
 */
@Composable
fun OptimizationStepCard(
    stepNumber: String? = null,
    icon: ImageVector,
    title: String,
    description: String,
    actionLabel: String,
    secondaryActionLabel: String? = null,
    isCompleted: Boolean,
    onAction: () -> Unit,
    onSecondaryAction: (() -> Unit)? = null
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = HudSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B)),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(if (isCompleted) NeonGreen else CyberCyan),
                    contentAlignment = Alignment.Center
                ) {
                    if (isCompleted) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(16.dp)
                        )
                    } else if (!stepNumber.isNullOrEmpty()) {
                        Text(
                            text = stepNumber,
                            color = Color.Black,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black
                        )
                    } else {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    softWrap = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = description,
                color = Color(0xFF94A3B8),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                softWrap = true
            )

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isCompleted) Color(0xFF1E293B) else CyberCyan
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
            ) {
                Text(
                    text = actionLabel,
                    color = if (isCompleted) NeonGreen else Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }

            if (secondaryActionLabel != null && onSecondaryAction != null) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onSecondaryAction,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberCyan),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                ) {
                    Text(
                        text = secondaryActionLabel,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

/**
 * Toggle row for vehicle automation and OEM settings with high-contrast cyber styling.
 */
@Composable
fun OptimizationToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    DashboardToggleRow(
        title = title,
        subtitle = subtitle,
        checked = checked,
        onCheckedChange = onCheckedChange,
        checkedTrackColor = NeonGreen
    )
}

/**
 * Dropdown selector row for vehicle and thermal cutoff configurations.
 */
@Composable
fun <T> OptimizationDropdownRow(
    title: String,
    subtitle: String,
    currentValue: String,
    options: List<T>,
    onSelect: (T) -> Unit,
    displayTransform: (T) -> String = { it.toString() }
) {
    DashboardDropdownRow(
        title = title,
        subtitle = subtitle,
        currentValue = currentValue,
        options = options,
        onSelect = onSelect,
        displayTransform = displayTransform
    )
}
