package com.contextpulse.phase0.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.contextpulse.phase0.ui.theme.LocalContextPulseTheme

/**
 * Custom Scrollbar matching user reference design (image_3e1a2908.png):
 * - Dark rounded capsule track with top/bottom vertical insets
 * - Nested inner pill thumb with vibrant theme accent color
 * - Smooth fade and proportional travel
 */
fun Modifier.contextPulseScrollbar(
    scrollState: ScrollState,
    trackWidth: Dp = 7.dp,
    thumbPadding: Dp = 1.2.dp,
    verticalInset: Dp = 8.dp,
    minThumbHeight: Dp = 36.dp
): Modifier = composed {
    val theme = LocalContextPulseTheme.current
    val isScrollable = scrollState.maxValue > 0

    val alpha by animateFloatAsState(
        targetValue = if (isScrollable) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "ScrollbarAlpha"
    )

    drawWithContent {
        drawContent()

        if (isScrollable && alpha > 0f) {
            val viewHeight = size.height
            val trackInsetY = verticalInset.toPx()
            val effectiveHeight = (viewHeight - (2 * trackInsetY)).coerceAtLeast(32.dp.toPx())

            // Active thumb pill: twice as large as before for enhanced visibility
            val maxPillHeight = (effectiveHeight * 0.24f).coerceAtLeast(56.dp.toPx()).coerceAtMost(96.dp.toPx())
            val minPillHeight = 44.dp.toPx()
            val contentHeight = size.height + scrollState.maxValue
            val heightRatio = (viewHeight / contentHeight.toFloat()).coerceIn(0f, 1f)
            val thumbHeightPx = (minPillHeight + (maxPillHeight - minPillHeight) * heightRatio)
                .coerceIn(minPillHeight, maxPillHeight)

            val scrollableTravel = effectiveHeight - thumbHeightPx
            val scrollProgress = (scrollState.value.toFloat() / scrollState.maxValue).coerceIn(0f, 1f)
            val thumbOffsetY = trackInsetY + (scrollProgress * scrollableTravel)

            val trackWidthPx = trackWidth.toPx()
            val trackOffsetX = size.width - trackWidthPx - 4.dp.toPx()

            // 1. Dark capsule track (matches reference image)
            val trackColor = if (theme.isLight) Color(0xFFD1D5DB) else Color(0xFF2C2D32)
            drawRoundRect(
                color = trackColor.copy(alpha = 0.95f * alpha),
                topLeft = Offset(trackOffsetX, trackInsetY),
                size = Size(trackWidthPx, effectiveHeight),
                cornerRadius = CornerRadius(trackWidthPx / 2f, trackWidthPx / 2f)
            )

            // 2. Nested vibrant pill thumb inside track
            val padPx = thumbPadding.toPx()
            val thumbWidthPx = (trackWidthPx - (padPx * 2)).coerceAtLeast(2f)
            val thumbOffsetX = trackOffsetX + padPx

            drawRoundRect(
                color = theme.primary.copy(alpha = alpha),
                topLeft = Offset(thumbOffsetX, thumbOffsetY + padPx),
                size = Size(thumbWidthPx, (thumbHeightPx - (padPx * 2)).coerceAtLeast(thumbWidthPx)),
                cornerRadius = CornerRadius(thumbWidthPx / 2f, thumbWidthPx / 2f)
            )
        }
    }
}

/**
 * Scrollbar for LazyColumn / LazyListState matching reference image
 */
fun Modifier.contextPulseLazyScrollbar(
    lazyListState: LazyListState,
    trackWidth: Dp = 7.dp,
    thumbPadding: Dp = 1.2.dp,
    verticalInset: Dp = 8.dp,
    minThumbHeight: Dp = 44.dp
): Modifier = composed {
    val theme = LocalContextPulseTheme.current

    val layoutInfo = lazyListState.layoutInfo
    val totalItems = layoutInfo.totalItemsCount
    val visibleItems = layoutInfo.visibleItemsInfo.size

    val isScrollable = totalItems > 0 && (totalItems > visibleItems || lazyListState.firstVisibleItemScrollOffset > 0)

    val alpha by animateFloatAsState(
        targetValue = if (isScrollable) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "LazyScrollbarAlpha"
    )

    drawWithContent {
        drawContent()

        if (isScrollable && totalItems > 0 && alpha > 0f) {
            val viewHeight = size.height
            val trackInsetY = verticalInset.toPx()
            val effectiveHeight = (viewHeight - (2 * trackInsetY)).coerceAtLeast(32.dp.toPx())

            // Active thumb pill: twice as large as before for enhanced visibility
            val maxPillHeight = (effectiveHeight * 0.24f).coerceAtLeast(56.dp.toPx()).coerceAtMost(96.dp.toPx())
            val minPillHeight = 44.dp.toPx()
            val itemRatio = (visibleItems.toFloat() / totalItems.toFloat()).coerceIn(0f, 1f)
            val thumbHeightPx = (minPillHeight + (maxPillHeight - minPillHeight) * itemRatio)
                .coerceIn(minPillHeight, maxPillHeight)

            val maxFirstVisible = (totalItems - visibleItems).coerceAtLeast(1)
            val itemScrollFraction = if (layoutInfo.visibleItemsInfo.isNotEmpty()) {
                val firstItem = layoutInfo.visibleItemsInfo.first()
                if (firstItem.size > 0) {
                    (lazyListState.firstVisibleItemScrollOffset.toFloat() / firstItem.size.toFloat()).coerceIn(0f, 1f)
                } else 0f
            } else 0f

            val overallProgress = ((lazyListState.firstVisibleItemIndex.toFloat() + itemScrollFraction) / maxFirstVisible.toFloat())
                .coerceIn(0f, 1f)

            val scrollableTravel = effectiveHeight - thumbHeightPx
            val thumbOffsetY = trackInsetY + (overallProgress * scrollableTravel)

            val trackWidthPx = trackWidth.toPx()
            val trackOffsetX = size.width - trackWidthPx - 4.dp.toPx()

            // 1. Dark capsule track (matches reference image)
            val trackColor = if (theme.isLight) Color(0xFFD1D5DB) else Color(0xFF2C2D32)
            drawRoundRect(
                color = trackColor.copy(alpha = 0.95f * alpha),
                topLeft = Offset(trackOffsetX, trackInsetY),
                size = Size(trackWidthPx, effectiveHeight),
                cornerRadius = CornerRadius(trackWidthPx / 2f, trackWidthPx / 2f)
            )

            // 2. Nested vibrant pill thumb inside track
            val padPx = thumbPadding.toPx()
            val thumbWidthPx = (trackWidthPx - (padPx * 2)).coerceAtLeast(2f)
            val thumbOffsetX = trackOffsetX + padPx

            drawRoundRect(
                color = theme.primary.copy(alpha = alpha),
                topLeft = Offset(thumbOffsetX, thumbOffsetY + padPx),
                size = Size(thumbWidthPx, (thumbHeightPx - (padPx * 2)).coerceAtLeast(thumbWidthPx)),
                cornerRadius = CornerRadius(thumbWidthPx / 2f, thumbWidthPx / 2f)
            )
        }
    }
}
