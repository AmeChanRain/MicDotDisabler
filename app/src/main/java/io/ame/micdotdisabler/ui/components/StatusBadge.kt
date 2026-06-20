package io.ame.micdotdisabler.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.ame.micdotdisabler.ui.AppState

/**
 * A small chip that displays the current Shizuku connection status.
 *
 * Renders with a colored dot indicator and a one-line label,
 * e.g. "Shizuku Ready (ADB)" or "Shizuku not running".
 */
@Composable
fun StatusBadge(state: AppState, modifier: Modifier = Modifier) {
    val (label, containerColor, contentColor, indicatorColor) = when (state) {
        AppState.ShizukuNotInstalled -> StatusStyle(
            "Shizuku not installed",
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            MaterialTheme.colorScheme.error
        )
        AppState.ShizukuNotRunning -> StatusStyle(
            "Shizuku not running",
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            MaterialTheme.colorScheme.outline
        )
        AppState.PermissionRequired -> StatusStyle(
            "Permission required",
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
            MaterialTheme.colorScheme.tertiary
        )
        AppState.Ready -> StatusStyle(
            "Shizuku Ready (ADB)",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            MaterialTheme.colorScheme.primary
        )
        AppState.Executing -> StatusStyle(
            "Executing…",
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
            MaterialTheme.colorScheme.secondary
        )
        is AppState.Success -> StatusStyle(
            "Disabled ✓",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            MaterialTheme.colorScheme.primary
        )
        is AppState.Error -> StatusStyle(
            "Error",
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            MaterialTheme.colorScheme.error
        )
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.height(36.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(indicatorColor)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = contentColor
            )
            Spacer(Modifier.width(12.dp))
        }
    }
}

/** Helper to group status style properties. */
private data class StatusStyle(
    val label: String,
    val containerColor: androidx.compose.ui.graphics.Color,
    val contentColor: androidx.compose.ui.graphics.Color,
    val indicatorColor: androidx.compose.ui.graphics.Color
)
