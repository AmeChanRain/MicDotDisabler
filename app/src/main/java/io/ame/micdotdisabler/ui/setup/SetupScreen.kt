package io.ame.micdotdisabler.ui.setup

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.ame.micdotdisabler.R
import io.ame.micdotdisabler.ui.AppState
import io.ame.micdotdisabler.ui.components.StatusBadge

/**
 * Onboarding / guidance screen shown when Shizuku is not yet fully configured.
 *
 * Displays step-by-step instructions and contextual action buttons
 * depending on the current [state].
 *
 * @param state The current Shizuku-related [AppState].
 * @param onCheckAgain Called when the user taps "Check Again".
 * @param onOpenShizuku Called to open the Shizuku app.
 * @param onOpenPlayStore Called to open Shizuku's Play Store page.
 * @param onOpenWirelessDebugging Called to open wireless debugging settings.
 * @param onRequestPermission Called to trigger Shizuku permission dialog.
 */
@Composable
fun SetupScreen(
    state: AppState,
    onCheckAgain: () -> Unit,
    onOpenShizuku: () -> Unit,
    onOpenPlayStore: () -> Unit,
    onOpenWirelessDebugging: () -> Unit,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Header ──
        Spacer(Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold
            ),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.setup_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(32.dp))

        // ── Step cards ──
        StepCard(
            stepNumber = "1",
            title = stringResource(R.string.step1_title),
            description = stringResource(R.string.step1_desc),
            buttonText = stringResource(R.string.step1_button),
            onButtonClick = onOpenPlayStore
        )

        Spacer(Modifier.height(12.dp))

        StepCard(
            stepNumber = "2",
            title = stringResource(R.string.step2_title),
            description = stringResource(R.string.step2_desc),
            buttonText = stringResource(R.string.step2_button),
            onButtonClick = onOpenWirelessDebugging
        )

        Spacer(Modifier.height(12.dp))

        StepCard(
            stepNumber = "3",
            title = stringResource(R.string.step3_title),
            description = stringResource(R.string.step3_desc),
            buttonText = stringResource(R.string.step3_button),
            onButtonClick = onOpenShizuku
        )

        // ── Permission step (only visible when permission is needed) ──
        AnimatedVisibility(
            visible = state == AppState.PermissionRequired,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(12.dp))
                StepCard(
                    stepNumber = "4",
                    title = stringResource(R.string.step4_title),
                    description = stringResource(R.string.step4_desc),
                    buttonText = stringResource(R.string.step4_button),
                    onButtonClick = onRequestPermission
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // ── Status badge ──
        StatusBadge(state = state)
    }
}

// ──────────────────────────────────────────
// Step card component
// ──────────────────────────────────────────

@Composable
private fun StepCard(
    stepNumber: String,
    title: String,
    description: String,
    buttonText: String,
    onButtonClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = stepNumber,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold
                )
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onButtonClick) {
                Text(buttonText)
            }
        }
    }
}
