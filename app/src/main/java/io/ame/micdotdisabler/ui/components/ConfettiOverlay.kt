package io.ame.micdotdisabler.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * A celebratory confetti animation drawn entirely via Compose [Canvas].
 *
 * When [trigger] becomes true, 70 semi-random confetti pieces rain down
 * from the top of the screen with sinusoidal horizontal drift and
 * gradual fade-out over ~5 seconds.
 *
 * The animation automatically resets when [trigger] goes back to false,
 * allowing it to be re-shown on subsequent successes.
 *
 * @param trigger Set to true to start the animation. Set to false to reset.
 * @param modifier Optional modifier for the overlay container.
 */
@Composable
fun ConfettiOverlay(
    trigger: Boolean,
    modifier: Modifier = Modifier
) {
    val animationProgress = remember { Animatable(0f) }
    val pieces = remember { generateConfettiPieces(count = 70) }
    var visible by remember { mutableStateOf(false) }

    // Start animation on rising edge of trigger; reset on falling edge
    LaunchedEffect(trigger) {
        if (trigger) {
            animationProgress.snapTo(0f)
            visible = true
            animationProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 5000, easing = LinearEasing)
            )
            visible = false
        } else {
            // Reset: ensure we stop rendering stale pieces
            visible = false
            animationProgress.snapTo(0f)
        }
    }

    if (!visible && animationProgress.value == 0f) return

    Canvas(modifier = modifier.fillMaxSize()) {
        val t = animationProgress.value
        val canvasH = size.height
        val canvasW = size.width

        pieces.forEach { piece ->
            val currentY = (piece.startY + piece.fallSpeed * t * canvasH) % canvasH
            val wobble = sin(t * PI * 2 * piece.wobbleFreq + piece.phase).toFloat() * piece.wobbleAmp * canvasW
            val currentX = piece.startX + wobble

            val alpha = (1f - t * t).coerceAtLeast(0f)

            val currentRotation = piece.startRotation + piece.rotationSpeed * t * 360f

            val pivot = Offset(currentX, currentY)
            rotate(degrees = currentRotation, pivot = pivot) {
                val color = piece.color.copy(alpha = alpha)
                if (piece.isCircle) {
                    drawCircle(
                        color = color,
                        radius = piece.size / 2f,
                        center = Offset(currentX, currentY)
                    )
                } else {
                    drawRect(
                        color = color,
                        topLeft = Offset(currentX - piece.size / 2f, currentY - piece.size / 4f),
                        size = Size(piece.size, piece.size * 0.5f)
                    )
                }
            }
        }
    }
}

// ──────────────────────────────────────────
// Confetti piece model & generation
// ──────────────────────────────────────────

private data class ConfettiPiece(
    val startX: Float,
    val startY: Float,
    val size: Float,
    val color: Color,
    val startRotation: Float,
    val fallSpeed: Float,
    val wobbleAmp: Float,
    val wobbleFreq: Float,
    val phase: Float,
    val rotationSpeed: Float,
    val isCircle: Boolean
)

private fun generateConfettiPieces(count: Int): List<ConfettiPiece> {
    val colors = listOf(
        Color(0xFF4285F4), // Google Blue
        Color(0xFFEA4335), // Google Red
        Color(0xFFFBBC04), // Google Yellow
        Color(0xFF34A853), // Google Green
        Color(0xFFFF6D00), // Orange
        Color(0xFFAB47BC), // Purple
        Color(0xFF00BCD4), // Cyan
        Color(0xFFFF4081), // Pink
        Color(0xFF7C4DFF), // Deep Purple A200
        Color(0xFF69F0AE), // Green A200
    )

    return List(count) { i ->
        val rng = Random(i * 31 + 17) // deterministic per index
        ConfettiPiece(
            startX = rng.nextFloat(),
            startY = rng.nextFloat() * 0.3f,
            size = rng.nextFloat() * 10f + 4f,
            color = colors[rng.nextInt(colors.size)],
            startRotation = rng.nextFloat() * 360f,
            fallSpeed = rng.nextFloat() * 0.35f + 0.40f,
            wobbleAmp = rng.nextFloat() * 0.04f + 0.01f,
            wobbleFreq = rng.nextFloat() * 2f + 1f,
            phase = rng.nextFloat() * PI.toFloat() * 2f,
            rotationSpeed = rng.nextFloat() * 0.5f + 0.1f,
            isCircle = rng.nextFloat() < 0.35f
        )
    }
}
