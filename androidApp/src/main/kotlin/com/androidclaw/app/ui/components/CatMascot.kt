package com.androidclaw.app.ui.components

import android.util.Log
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.androidclaw.app.R

/**
 * Animated pixel cat mascot using sprite sheets from Stray Cat Runner.
 * States: idle (sitting), walking (thinking), running (processing tool), jumping (task done)
 */
enum class CatState {
    IDLE,       // Sitting, waiting for input
    WALKING,    // Thinking / processing
    RUNNING,    // Executing tool
    JUMPING     // Task completed celebration
}

@Composable
fun CatMascot(
    state: CatState,
    modifier: Modifier = Modifier,
    size: Int = 48
) {
    val spriteSheet = when (state) {
        CatState.IDLE -> ImageBitmap.imageResource(R.drawable.cat_idle_sheet)
        CatState.WALKING -> ImageBitmap.imageResource(R.drawable.cat_walk_sheet)
        CatState.RUNNING -> ImageBitmap.imageResource(R.drawable.cat_run_sheet)
        CatState.JUMPING -> ImageBitmap.imageResource(R.drawable.cat_jump_sheet)
    }

    val frameCount = when (state) {
        CatState.JUMPING -> 6
        else -> 4
    }

    val frameDurationMs = when (state) {
        CatState.IDLE -> 300
        CatState.WALKING -> 200
        CatState.RUNNING -> 120
        CatState.JUMPING -> 150
    }

    val frameWidth = spriteSheet.width / frameCount
    val frameHeight = spriteSheet.height

    // Animate frame index
    val infiniteTransition = rememberInfiniteTransition(label = "cat_anim")
    val frameIndex by infiniteTransition.animateValue(
        initialValue = 0,
        targetValue = frameCount,
        typeConverter = Int.VectorConverter,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = frameDurationMs * frameCount,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "cat_frame"
    )

    val currentFrame = frameIndex.coerceIn(0, frameCount - 1)

    Canvas(modifier = modifier.size(size.dp)) {
        val dstSize = IntSize(this.size.width.toInt(), this.size.height.toInt())
        drawImage(
            image = spriteSheet,
            srcOffset = IntOffset(currentFrame * frameWidth, 0),
            srcSize = IntSize(frameWidth, frameHeight),
            dstOffset = IntOffset(0, 0),
            dstSize = dstSize
        )
    }
}
