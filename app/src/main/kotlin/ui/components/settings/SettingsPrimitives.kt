package io.github.roaxio.dashguardpro.ui.components.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.roaxio.dashguardpro.ui.components.common.DashboardCard
import io.github.roaxio.dashguardpro.ui.components.common.DashboardDropdownRow
import io.github.roaxio.dashguardpro.ui.components.common.DashboardSectionHeader
import io.github.roaxio.dashguardpro.ui.components.common.DashboardToggleRow

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    DashboardSectionHeader(title = title, modifier = modifier)
}

@Composable
fun SettingsCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    DashboardCard(modifier = modifier, content = content)
}

@Composable
fun <T> SettingsDropdownRow(
    title: String,
    subtitle: String,
    currentValue: String,
    options: List<T>,
    onSelect: (T) -> Unit,
    displayTransform: (T) -> String
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

@Composable
fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    DashboardToggleRow(
        title = title,
        subtitle = subtitle,
        checked = checked,
        onCheckedChange = onCheckedChange
    )
}
