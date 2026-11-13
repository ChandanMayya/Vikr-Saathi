package com.kex.vikrsaathi.ui.dashboard

/**
 * Positions for the quick-action morph. Each primary action has one frame at
 * every progress value, interpolated from its compact slot to its expanded tile.
 * Extra actions keep their expanded slots and only fade and rise.
 */
internal object QuickActionsMotion {
    const val COMPACT_HEIGHT = 72f
    const val COMPACT_TOP = 4f
    const val GAP = 8f
    /** Stroke width. Matches @dimen/quick_actions_outline. */
    const val OUTLINE_DP = 1f
    /** Clear space between the outline's inner edge and the actions. */
    const val CONTENT_GAP_DP = 8f
    /**
     * Outer tray edge to the action content.
     * Matches @dimen/quick_actions_content_inset (outline plus the gap inside it).
     */
    const val INSET_DP = OUTLINE_DP + CONTENT_GAP_DP
    const val SIDE = INSET_DP
    const val ROW1_HEIGHT = 92f
    const val MASTER_HEIGHT_PAIR = 92f
    const val MASTER_HEIGHT_TRIO = 96f
    const val TOOL_HEIGHT_PAIR = 92f
    const val TOOL_HEIGHT_TRIO = 108f
    const val WARRANTY_HEIGHT = 108f
    const val SETTINGS_HEIGHT = 76f
    const val EXTRA_SHIFT_DP = 18f
    /** Keep this much of the dashboard visible above a fully open tray. */
    const val MIN_VISIBLE_DASHBOARD_DP = 72
    const val VENDOR_FADE_START = 0.9f
    const val OTHER_EXTRA_FADE_START = 0.5f
    const val COMPACT_ICON_DP = 24f

    val primary = listOf(
        QuickAction.NEW_BILL,
        QuickAction.BILLS_HISTORY,
        QuickAction.ITEMS,
        QuickAction.CUSTOMERS
    )

    val extras = listOf(
        QuickAction.VENDORS,
        QuickAction.INVENTORY,
        QuickAction.ANALYSIS,
        QuickAction.ORDERS,
        QuickAction.WARRANTY,
        QuickAction.SETTINGS
    )

    /** Collapsed tray keeps “Quick Actions”; the open tray is “Actions”. */
    fun useExpandedTitle(offset: Float): Boolean = offset.coerceIn(0f, 1f) >= 0.5f

    /** Mix from the resting outline color toward the open outline color. The line stays visible. */
    fun trayBorderAlpha(offset: Float): Float = offset.coerceIn(0f, 1f)

    /** The outline stays fully visible. Width does not change as the tray opens. */
    fun trayOutlineAlpha(offset: Float): Float = 1f

    /**
     * Height of the tray that is on screen. The sheet view stays tall while collapsed,
     * so the outline uses this height to keep its bottom edge on the visible tray.
     */
    fun visibleTrayHeight(sheetPx: Int, peekPx: Int, offset: Float): Float {
        val full = sheetPx.coerceAtLeast(0).toFloat()
        val peek = peekPx.coerceIn(0, sheetPx.coerceAtLeast(0)).toFloat()
        val t = offset.coerceIn(0f, 1f)
        return peek + (full - peek) * t
    }

    /** Centerline inset that keeps the whole stroke inside the tray bounds. */
    fun outlineCenterInset(strokeDp: Float): Float = strokeDp.coerceAtLeast(0f) / 2f

    /** Collapsed chevron points down; the open tray points up. */
    fun chevronRotation(offset: Float): Float = 180f * (1f - offset.coerceIn(0f, 1f))

    /**
     * Shortcut ink stays on the tray surface until the shared card has become its tile,
     * then moves to the on-tile color across a short part of the drag.
     */
    fun tileForegroundMix(offset: Float): Float {
        val t = offset.coerceIn(0f, 1f)
        val start = 0.8f
        val end = 1f
        if (t <= start) return 0f
        if (t >= end) return 1f
        val u = (t - start) / (end - start)
        return u * u * (3f - 2f * u)
    }

    fun tileInk(offset: Float, resting: Int, onTile: Int): Int =
        blendArgb(resting, onTile, tileForegroundMix(offset))

    /** Orange icon chip fades out as the shared card becomes its tile. */
    fun iconBackingAlpha(offset: Float): Float = 1f - tileForegroundMix(offset)

    /**
     * Keeps [stroke] when it already reads on [surface]. A pale divider is walked
     * toward [onSurface] until the contrast is clear, still using those theme colors.
     */
    fun visibleStroke(stroke: Int, surface: Int, onSurface: Int, minimum: Double = 3.0): Int {
        if (contrastRatio(stroke, surface) >= minimum) return stroke
        var low = 0f
        var high = 1f
        var best = onSurface
        repeat(8) {
            val mid = (low + high) / 2f
            val mixed = blendArgb(stroke, onSurface, mid)
            if (contrastRatio(mixed, surface) >= minimum) {
                best = mixed
                high = mid
            } else {
                low = mid
            }
        }
        return best
    }

    fun contrastRatio(foreground: Int, background: Int): Double {
        val lighter = maxOf(luminance(foreground), luminance(background))
        val darker = minOf(luminance(foreground), luminance(background))
        return (lighter + 0.05) / (darker + 0.05)
    }

    internal fun blendArgb(start: Int, end: Int, ratio: Float): Int {
        val t = ratio.coerceIn(0f, 1f)
        val inverse = 1f - t
        val a = ((start ushr 24) * inverse + (end ushr 24) * t).toInt()
        val r = (channel(start, 16) * inverse + channel(end, 16) * t).toInt()
        val g = (channel(start, 8) * inverse + channel(end, 8) * t).toInt()
        val b = (channel(start, 0) * inverse + channel(end, 0) * t).toInt()
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun channel(color: Int, shift: Int): Int = (color shr shift) and 0xFF

    private fun luminance(color: Int): Double {
        return 0.2126 * linear(channel(color, 16)) +
            0.7152 * linear(channel(color, 8)) +
            0.0722 * linear(channel(color, 0))
    }

    private fun linear(channel: Int): Double {
        val value = channel / 255.0
        return if (value <= 0.04045) value / 12.92 else Math.pow((value + 0.055) / 1.055, 2.4)
    }

    fun extraAlpha(action: QuickAction, offset: Float): Float {
        val t = offset.coerceIn(0f, 1f)
        val start = if (action == QuickAction.VENDORS) VENDOR_FADE_START else OTHER_EXTRA_FADE_START
        if (t <= start) return 0f
        return ((t - start) / (1f - start)).coerceIn(0f, 1f)
    }

    fun extraShiftDp(action: QuickAction, offset: Float): Float =
        EXTRA_SHIFT_DP * (1f - extraAlpha(action, offset))

    fun clicksSettled(offset: Float): Boolean {
        val t = offset.coerceIn(0f, 1f)
        return t <= 0.04f || t >= 0.96f
    }

    /**
     * The action list joins nested scrolling only after the sheet settles.
     * Turning it off while a close drag is in progress drops the rest of the gesture.
     */
    fun shouldRetargetListScroll(gestureActive: Boolean): Boolean = !gestureActive

    /** Scroll the action list only while the sheet is settled open. */
    fun listScrollEnabled(expanded: Boolean): Boolean = expanded

    fun iconScale(offset: Float, expandedDp: Float): Float {
        val t = offset.coerceIn(0f, 1f)
        return (COMPACT_ICON_DP + (expandedDp - COMPACT_ICON_DP) * t) / COMPACT_ICON_DP
    }

    fun labelScale(offset: Float): Float {
        val t = offset.coerceIn(0f, 1f)
        return 1f + 0.18f * t
    }

    fun frame(action: QuickAction, offset: Float, width: Float, flags: ActionFlags): ActionFrame {
        val t = offset.coerceIn(0f, 1f)
        val end = expanded(action, width, flags)
        val start = if (action in primary) collapsed(action, width) else end
        val moved = start.lerp(end, t)
        return if (action in primary) moved else moved.copy(top = moved.top + extraShiftDp(action, offset))
    }

    fun collapsed(action: QuickAction, width: Float): ActionFrame {
        val index = primary.indexOf(action)
        require(index >= 0) { "$action is not a compact shortcut" }
        val inner = width - SIDE * 2f
        val cell = (inner - GAP * 3f) / 4f
        return ActionFrame(
            left = SIDE + index * (cell + GAP),
            top = COMPACT_TOP,
            width = cell,
            height = COMPACT_HEIGHT
        )
    }

    fun expanded(action: QuickAction, width: Float, flags: ActionFlags): ActionFrame {
        val inner = width - SIDE * 2f
        val masterHeight = if (flags.vendors) MASTER_HEIGHT_TRIO else MASTER_HEIGHT_PAIR
        val toolHeight = if (flags.orders) TOOL_HEIGHT_TRIO else TOOL_HEIGHT_PAIR
        val row1Top = COMPACT_TOP
        val row2Top = row1Top + ROW1_HEIGHT + GAP
        val row3Top = row2Top + masterHeight + GAP
        val warrantyTop = row3Top + toolHeight + GAP
        val settingsTop = if (flags.warranty) warrantyTop + WARRANTY_HEIGHT + GAP else row3Top + toolHeight + GAP
        fun cell(index: Int, count: Int, top: Float, height: Float): ActionFrame {
            val tile = (inner - GAP * (count - 1)) / count
            return ActionFrame(SIDE + index * (tile + GAP), top, tile, height)
        }
        return when (action) {
            QuickAction.NEW_BILL -> cell(0, 2, row1Top, ROW1_HEIGHT)
            QuickAction.BILLS_HISTORY -> cell(1, 2, row1Top, ROW1_HEIGHT)
            QuickAction.ITEMS -> cell(0, if (flags.vendors) 3 else 2, row2Top, masterHeight)
            QuickAction.CUSTOMERS -> cell(1, if (flags.vendors) 3 else 2, row2Top, masterHeight)
            QuickAction.VENDORS -> cell(2, 3, row2Top, masterHeight)
            QuickAction.INVENTORY -> cell(0, if (flags.orders) 3 else 2, row3Top, toolHeight)
            QuickAction.ANALYSIS -> cell(1, if (flags.orders) 3 else 2, row3Top, toolHeight)
            QuickAction.ORDERS -> cell(2, 3, row3Top, toolHeight)
            QuickAction.WARRANTY -> ActionFrame(SIDE, warrantyTop, inner, WARRANTY_HEIGHT)
            QuickAction.SETTINGS -> ActionFrame(SIDE, settingsTop, inner, SETTINGS_HEIGHT)
        }
    }

    fun contentHeight(flags: ActionFlags): Float {
        val settings = expanded(QuickAction.SETTINGS, 360f, flags)
        return settings.top + settings.height + INSET_DP
    }

    /**
     * Expanded tray height. [naturalPx] is the header plus the visible tiles.
     * The result stays within the collapsed peek and the space that still leaves
     * a strip of the dashboard on screen.
     */
    fun expandedTrayHeight(
        naturalPx: Int,
        availablePx: Int,
        peekPx: Int,
        minVisibleDashboardPx: Int
    ): Int {
        val max = (availablePx - minVisibleDashboardPx).coerceAtLeast(peekPx)
        return naturalPx.coerceIn(peekPx, max)
    }

    /** Viewport for the action list. Shorter than the tiles only when the tray is capped. */
    fun actionViewportHeight(contentPx: Int, sheetPx: Int, headerPx: Int): Int {
        val room = (sheetPx - headerPx).coerceAtLeast(0)
        return contentPx.coerceAtMost(room)
    }

    fun trayScrolls(contentPx: Int, viewportPx: Int): Boolean = contentPx > viewportPx
}

internal enum class QuickAction {
    NEW_BILL,
    BILLS_HISTORY,
    ITEMS,
    CUSTOMERS,
    VENDORS,
    INVENTORY,
    ANALYSIS,
    ORDERS,
    WARRANTY,
    SETTINGS
}

internal data class ActionFlags(
    val vendors: Boolean,
    val orders: Boolean,
    val warranty: Boolean
)

internal data class ActionFrame(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float
) {
    val right: Float get() = left + width
    val bottom: Float get() = top + height

    fun lerp(other: ActionFrame, t: Float): ActionFrame = ActionFrame(
        left = left + (other.left - left) * t,
        top = top + (other.top - top) * t,
        width = width + (other.width - width) * t,
        height = height + (other.height - height) * t
    )

    fun overlaps(other: ActionFrame, slop: Float = 2f): Boolean {
        return right > other.left + slop &&
            other.right > left + slop &&
            bottom > other.top + slop &&
            other.bottom > top + slop
    }
}
