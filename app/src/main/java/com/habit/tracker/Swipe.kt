package com.habit.tracker

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Swipe LEFT to delete.
 *
 *  - the card follows the finger, with a little resistance after the delete point
 *  - the red background fades in, the trash icon grows and pops (+ a light haptic tick) once the delete point is reached
 *  - let go before the point (or a weak fling) -> springs back
 *  - let go after the point (or a fast fling) -> [onSwiped]
 *      * if [holdOpen] is true the card waits half-open (used while a "delete?" dialog is showing)
 *      * when [exit] becomes true the card slides out, the row collapses, then [onExited] is called
 *
 * [key] resets all the animation state when the row is reused for another item.
 */
@Composable
fun SwipeToDelete(
    key: Any,
    radius: Dp,
    onSwiped: () -> Unit,
    onExited: () -> Unit,
    exit: Boolean,
    holdOpen: Boolean = false,
    content: @Composable () -> Unit,
) {
    val p = LocalPal.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val off = remember(key) { Animatable(0f) }          // 0 .. -width
    val collapse = remember(key) { Animatable(1f) }     // 1 = full height, 0 = gone
    var w by remember { mutableFloatStateOf(1f) }
    var dragging by remember(key) { mutableStateOf(false) }

    val trigger = w * 0.4f
    val armed = -off.value > trigger

    // haptic tick when the delete point is crossed by the finger
    LaunchedEffect(armed, dragging) {
        if (armed && dragging) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    // half-open while the confirm dialog is up, back to rest when it is cancelled
    LaunchedEffect(holdOpen) {
        if (!exit && !dragging) {
            off.animateTo(
                if (holdOpen) -w * 0.28f else 0f,
                spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
            )
        }
    }

    // slide out -> collapse -> really delete
    LaunchedEffect(exit) {
        if (exit) {
            off.animateTo(-w, tween(220, easing = FastOutLinearInEasing))
            collapse.animateTo(0f, tween(240, easing = FastOutSlowInEasing))
            onExited()
        }
    }

    val dragState = rememberDraggableState { d ->
        if (exit) return@rememberDraggableState
        val resist = if (-off.value > trigger) 0.45f else 1f
        scope.launch { off.snapTo((off.value + d * resist).coerceIn(-w, 0f)) }
    }

    val bg by animateColorAsState(
        if (armed) Color(0xFFE53935) else Color(0x33FF0000),
        tween(140), label = "swipeBg",
    )
    val iconScale by animateFloatAsState(
        if (armed) 1.25f else 0.9f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "swipeIcon",
    )
    val progress = (-off.value / trigger).coerceIn(0f, 1f)

    // outer box = the part that collapses (card + the 8dp gap under it)
    Box(
        Modifier.fillMaxWidth()
            .onSizeChanged { w = it.width.toFloat().coerceAtLeast(1f) }
            .layout { m, c ->
                val pl = m.measure(c)
                val h = (pl.height * collapse.value).roundToInt()
                layout(pl.width, h) { pl.place(0, 0) }
            }
            .clipToBounds()
            .graphicsLayer { alpha = 0.3f + 0.7f * collapse.value },
    ) {
        Box(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            if (off.value < -1f) {
                Box(
                    Modifier.matchParentSize().clip(RoundedCornerShape(radius)).background(bg),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Ic(
                        "trash", size = 22.dp,
                        tint = if (armed) Color.White else p.cross,
                        modifier = Modifier.padding(end = 20.dp).graphicsLayer {
                            alpha = progress
                            scaleX = iconScale; scaleY = iconScale
                        },
                    )
                }
            }
            Box(
                Modifier
                    .offset { IntOffset(off.value.roundToInt(), 0) }
                    .draggable(
                        state = dragState,
                        orientation = Orientation.Horizontal,
                        onDragStarted = { dragging = true },
                        onDragStopped = { v ->
                            dragging = false
                            val far = -off.value > trigger
                            val fling = v < -1800f && -off.value > w * 0.12f
                            if (far || fling) {
                                onSwiped()
                            } else {
                                scope.launch {
                                    off.animateTo(0f, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium))
                                }
                            }
                        },
                    ),
            ) {
                // soft glass cards are almost see-through: a solid page-coloured layer keeps the red delete background from showing through
                if (p.dark) Box(Modifier.clip(RoundedCornerShape(radius)).background(p.bgc)) { content() } else content()
            }
        }
    }
}
