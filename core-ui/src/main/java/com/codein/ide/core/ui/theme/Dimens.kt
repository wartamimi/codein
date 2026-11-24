package com.codein.ide.core.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.dp

/** The strict 4dp/8dp spacing scale. Margins, paddings and gaps use only these values. */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
}

/** Fixed component heights and widths that keep the workspace proportional. */
object Sizes {
    val TopBar = 52.dp
    val TabStrip = 36.dp
    val Breadcrumb = 26.dp
    val StatusBar = 38.dp
    val ExtraKeysBar = 42.dp
    val KeyPillMinWidth = 36.dp
    val KeyPillHeight = 32.dp
    val KeyPillCorner = 6.dp

    /** Minimum clickable hitbox for every icon button and tab close button. */
    val MinTouch = 44.dp
    val IconSm = 16.dp
    val Icon = 18.dp
    val IconLg = 20.dp
    val Pill = 32.dp
    val Chip = 28.dp
    val TreeRow = 32.dp
    val TreeIndent = 16.dp

    val DrawerWidth = 300.dp
    val ExplorerPane = 260.dp
    val AiPane = 340.dp
    val PaletteMaxWidth = 540.dp
    val DragHandleWidth = 32.dp
    val DragHandleHeight = 4.dp

    /** Bottom dock detents as a fraction of the available height. */
    const val DockHalf = 0.42f
    const val DockFull = 0.88f
}

/** Motion tokens: one physics spring for spatial movement, one short tween for content swaps. */
object Motion {
    const val SwitchMillis = 180

    /** No-bounce, medium-low stiffness spring for sheets, drawers, indicators and chevrons. */
    fun <T> spatial(): FiniteAnimationSpec<T> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

    fun <T> switch(): FiniteAnimationSpec<T> = tween(SwitchMillis)
}
