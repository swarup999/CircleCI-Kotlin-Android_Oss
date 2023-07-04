package com.kickstarter.ui.compose

import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material.ripple.RippleTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
object KSRippleThemeWhite: RippleTheme {

    val alpha = RippleAlpha(
        focusedAlpha = .32f,
        draggedAlpha = .32f,
        hoveredAlpha = .32f,
        pressedAlpha = .32f
    )

    @Composable
    override fun defaultColor(): Color = KSTheme.colors.kds_white

    @Composable
    override fun rippleAlpha(): RippleAlpha = alpha
}

@Immutable
object KSRippleThemeGrey: RippleTheme {

    val alpha = RippleAlpha(
        focusedAlpha = .13f,
        draggedAlpha = .13f,
        hoveredAlpha = .13f,
        pressedAlpha = .13f
    )

    @Composable
    override fun defaultColor(): Color = KSTheme.colors.kds_support_700

    @Composable
    override fun rippleAlpha(): RippleAlpha = alpha
}