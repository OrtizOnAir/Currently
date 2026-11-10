package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.CurrentsBlue
import com.example.ui.theme.CurrentsGreen
import com.example.ui.theme.CurrentsIndigo
import com.example.ui.theme.CurrentsRed
import kotlin.math.abs

/**
 * Cool Spectrum 4-Segment Accent Bar (Sky Cyan, Cobalt Blue, Royal Indigo, Deep Teal).
 */
@Composable
fun CurrentsFourColorBar(
    modifier: Modifier = Modifier,
    height: Dp = 3.dp
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        Box(modifier = Modifier.weight(1f).fillMaxHeight().background(CurrentsBlue))
        Box(modifier = Modifier.weight(1f).fillMaxHeight().background(CurrentsRed))
        Box(modifier = Modifier.weight(1f).fillMaxHeight().background(CurrentsIndigo))
        Box(modifier = Modifier.weight(1f).fillMaxHeight().background(CurrentsGreen))
    }
}

/**
 * Displays the Currently app icon in the top Action Bar, sized and framed to stand out
 * clearly alongside the two-line masthead text.
 */
@Composable
fun CurrentsPrismLogoBadge(
    modifier: Modifier = Modifier,
    badgeSize: Dp = 36.dp
) {
    Box(
        modifier = modifier
            .size(badgeSize)
            .clip(RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.currently_app_icon_fg_1794263322610),
            contentDescription = "Currently App Icon",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = 1.35f
                    scaleY = 1.35f
                }
        )
    }
}

/**
 * Renders a crisp, cool-toned magazine/edition sheet surface and strictly clips all content inside
 * the sheet bounds so text never bleeds or comes off the page.
 */
fun Modifier.skeuomorphicNewsprintSheet(
    isDarkInk: Boolean = false,
    showStackedSheetEdges: Boolean = true,
    showCenterFoldCrease: Boolean = false
): Modifier = this
    .clipToBounds()
    .drawWithContent {
        val w = size.width
        val h = size.height

        val paperBase = if (isDarkInk) Color(0xFF131C2E) else Color(0xFFFFFFFF)
        val borderColor = if (isDarkInk) Color(0xFF26334D) else Color(0xFFCBD5E1)

        drawRect(color = paperBase)

        clipRect(
            left = 1f,
            top = 1f,
            right = (w - 1f).coerceAtLeast(1f),
            bottom = (h - 1f).coerceAtLeast(1f)
        ) {
            this@drawWithContent.drawContent()
        }

        drawRect(
            color = borderColor,
            topLeft = Offset.Zero,
            size = Size(w, h),
            style = Stroke(width = 1f)
        )
    }

/**
 * Draws a crisp dashed border for saved clippings.
 */
fun Modifier.skeuomorphicClippingBorder(
    inkColor: Color
): Modifier = this.drawBehind {
    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 4f), 0f)
    drawRect(
        color = inkColor.copy(alpha = 0.55f),
        style = Stroke(width = 1.2f, pathEffect = dashEffect)
    )
}

/**
 * Sleek horizontal slide depth effect for HorizontalPager pages (no flip/curl distortion).
 */
fun Modifier.newspaperPageTurnEffect(
    pageOffset: Float,
    isDarkInk: Boolean = false,
    cancelPagerTranslation: Boolean = true
): Modifier {
    val absOffset = abs(pageOffset).coerceIn(0f, 1f)
    return this
        .clipToBounds()
        .graphicsLayer {
            val scale = 1f - (absOffset * 0.025f)
            scaleX = scale
            scaleY = scale
            alpha = 1f - (absOffset * 0.12f)
        }
}

/**
 * Sleek horizontal slide transition when switching between tabs or screens (replaces page flip).
 */
@Composable
fun <T> NewspaperSectionTurnTransition(
    targetState: T,
    modifier: Modifier = Modifier,
    isDarkInk: Boolean = false,
    content: @Composable (T) -> Unit
) {
    AnimatedContent(
        targetState = targetState,
        modifier = modifier.clipToBounds(),
        transitionSpec = {
            val slideIn = slideInHorizontally(
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
                initialOffsetX = { fullWidth -> fullWidth / 4 }
            ) + fadeIn(animationSpec = tween(durationMillis = 220))

            val slideOut = slideOutHorizontally(
                animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                targetOffsetX = { fullWidth -> -fullWidth / 4 }
            ) + fadeOut(animationSpec = tween(durationMillis = 180))

            slideIn togetherWith slideOut
        },
        label = "SleekSlideTabTransition"
    ) { state ->
        Box(modifier = Modifier.fillMaxSize()) {
            content(state)
        }
    }
}

/**
 * Smooth vertical slide/expand transition for inline collapsible drawers (no 3D flip).
 */
@Composable
fun NewspaperUnfoldVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier.clipToBounds(),
        enter = expandVertically(
            animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing)
        ) + fadeIn(animationSpec = tween(durationMillis = 200)),
        exit = shrinkVertically(
            animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
        ) + fadeOut(animationSpec = tween(durationMillis = 160))
    ) {
        content()
    }
}

/**
 * Clean editorial divider rule.
 */
@Composable
fun BroadsheetDoubleRule(
    modifier: Modifier = Modifier,
    topThickness: Dp = 1.5.dp,
    bottomThickness: Dp = 0.6.dp
) {
    val ruleColor = MaterialTheme.colorScheme.outline
    Box(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(thickness = topThickness, color = ruleColor)
            Box(modifier = Modifier.height(2.dp))
            HorizontalDivider(thickness = bottomThickness, color = ruleColor.copy(alpha = 0.6f))
        }
    }
}

/**
 * Section divider with centered symbol.
 */
@Composable
fun BroadsheetOrnamentalDivider(
    modifier: Modifier = Modifier,
    symbol: String = "•"
) {
    val ruleColor = MaterialTheme.colorScheme.outline
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = 0.8.dp,
            color = ruleColor
        )
        Text(
            text = "  $symbol  ",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = 0.8.dp,
            color = ruleColor
        )
    }
}

/**
 * No-op corner indicator now that page flip/curl visuals are replaced with sleek slide animations.
 */
@Composable
fun PageCornerCurlIndicator(
    modifier: Modifier = Modifier,
    curlColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    shadowColor: Color = MaterialTheme.colorScheme.outline
) {
    // Intentionally empty: sleek slide design removes folded corner curl graphics.
}
