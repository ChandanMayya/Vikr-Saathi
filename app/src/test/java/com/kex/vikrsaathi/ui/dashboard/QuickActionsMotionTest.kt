package com.kex.vikrsaathi.ui.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickActionsMotionTest {
    private val width = 360f
    private val allOn = ActionFlags(vendors = true, orders = true, warranty = true)

    @Test
    fun collapsedRowHasOneCompactInstanceOfEachPrimaryAction() {
        val frames = QuickActionsMotion.primary.map { QuickActionsMotion.frame(it, 0f, width, allOn) }
        assertEquals(1, frames.map { it.top }.distinct().size)
        assertEquals(QuickActionsMotion.COMPACT_HEIGHT, frames.first().height, 0.01f)
        frames.forEachIndexed { index, frame ->
            if (index > 0) assertFalse(frames[index - 1].overlaps(frame))
        }
        assertTrue(frames[0].right <= frames[1].left + 1f)
        assertTrue(frames[2].left > frames[1].right - 1f)
        assertTrue(frames[3].left > frames[2].right - 1f)
    }

    @Test
    fun intermediateProgressGrowsTheSameFramesInsteadOfReplacingThem() {
        QuickActionsMotion.primary.forEach { action ->
            val start = QuickActionsMotion.frame(action, 0f, width, allOn)
            val middle = QuickActionsMotion.frame(action, 0.5f, width, allOn)
            val end = QuickActionsMotion.frame(action, 1f, width, allOn)
            val expected = start.lerp(end, 0.5f)
            assertEquals(expected.left, middle.left, 0.01f)
            assertEquals(expected.top, middle.top, 0.01f)
            assertEquals(expected.width, middle.width, 0.01f)
            assertEquals(expected.height, middle.height, 0.01f)
            assertTrue(end.width > start.width)
            assertTrue(end.height > start.height)
        }
    }

    @Test
    fun expandedPrimariesMatchTheFullSizeGrid() {
        val newBill = QuickActionsMotion.expanded(QuickAction.NEW_BILL, width, allOn)
        val history = QuickActionsMotion.expanded(QuickAction.BILLS_HISTORY, width, allOn)
        val items = QuickActionsMotion.expanded(QuickAction.ITEMS, width, allOn)
        val customers = QuickActionsMotion.expanded(QuickAction.CUSTOMERS, width, allOn)
        assertEquals(QuickActionsMotion.ROW1_HEIGHT, newBill.height, 0.01f)
        assertTrue(newBill.right <= history.left + 1f)
        assertTrue(items.top >= newBill.bottom)
        assertTrue(customers.left > items.right - 1f)
        assertTrue(items.top == customers.top)
        assertEquals(QuickActionsMotion.MASTER_HEIGHT_TRIO, items.height, 0.01f)
    }

    @Test
    fun extrasAppearWithoutOverlappingTheMovingCards() {
        val steps = (0..20).map { it / 20f }
        steps.forEach { progress ->
            val primaries = QuickActionsMotion.primary.map {
                QuickActionsMotion.frame(it, progress, width, allOn)
            }
            QuickActionsMotion.extras.forEach { extra ->
                val alpha = QuickActionsMotion.extraAlpha(extra, progress)
                if (alpha <= 0.4f) return@forEach
                val box = QuickActionsMotion.frame(extra, progress, width, allOn)
                primaries.forEach { primary ->
                    assertFalse(
                        "$extra overlaps a primary action at $progress",
                        primary.overlaps(box)
                    )
                }
            }
        }
        assertEquals(0f, QuickActionsMotion.extraAlpha(QuickAction.INVENTORY, 0f), 0.001f)
        assertEquals(1f, QuickActionsMotion.extraAlpha(QuickAction.SETTINGS, 1f), 0.001f)
        assertEquals(0f, QuickActionsMotion.extraAlpha(QuickAction.VENDORS, 0.85f), 0.001f)
        assertTrue(QuickActionsMotion.extraShiftDp(QuickAction.ANALYSIS, 0.2f) > 0f)
        assertEquals(0f, QuickActionsMotion.extraShiftDp(QuickAction.ANALYSIS, 1f), 0.001f)
    }

    @Test
    fun featureFlagsKeepTheExpandedRowsIntact() {
        val noVendors = ActionFlags(vendors = false, orders = true, warranty = true)
        val items = QuickActionsMotion.expanded(QuickAction.ITEMS, width, noVendors)
        val customers = QuickActionsMotion.expanded(QuickAction.CUSTOMERS, width, noVendors)
        assertTrue(items.width > width / 3f)
        assertTrue(customers.left >= items.right - 1f)
        assertEquals(QuickActionsMotion.MASTER_HEIGHT_PAIR, items.height, 0.01f)

        val noOrders = ActionFlags(vendors = true, orders = false, warranty = false)
        val stock = QuickActionsMotion.expanded(QuickAction.INVENTORY, width, noOrders)
        val analysis = QuickActionsMotion.expanded(QuickAction.ANALYSIS, width, noOrders)
        val settings = QuickActionsMotion.expanded(QuickAction.SETTINGS, width, noOrders)
        assertTrue(analysis.left >= stock.right - 1f)
        assertEquals(stock.bottom + QuickActionsMotion.GAP, settings.top, 0.01f)

        val warranty = QuickActionsMotion.expanded(QuickAction.WARRANTY, width, allOn)
        val settingsBelow = QuickActionsMotion.expanded(QuickAction.SETTINGS, width, allOn)
        assertEquals(warranty.bottom + QuickActionsMotion.GAP, settingsBelow.top, 0.01f)
        assertTrue(warranty.width > width * 0.8f)
    }

    @Test
    fun draggingDownUsesTheSamePathAndSettledClicks() {
        val flags = ActionFlags(vendors = false, orders = true, warranty = true)
        val upward = QuickActionsMotion.frame(QuickAction.CUSTOMERS, 0.35f, width, flags)
        val downward = QuickActionsMotion.frame(QuickAction.CUSTOMERS, 0.35f, width, flags)
        assertEquals(upward, downward)
        assertFalse(QuickActionsMotion.clicksSettled(0.4f))
        assertTrue(QuickActionsMotion.clicksSettled(0f))
        assertTrue(QuickActionsMotion.clicksSettled(1f))
        assertFalse(QuickActionsMotion.useExpandedTitle(0f))
        assertTrue(QuickActionsMotion.useExpandedTitle(1f))
        assertEquals(0f, QuickActionsMotion.trayBorderAlpha(0f), 0.001f)
        assertEquals(0.4f, QuickActionsMotion.trayBorderAlpha(0.4f), 0.001f)
        assertEquals(1f, QuickActionsMotion.trayBorderAlpha(1f), 0.001f)
        assertEquals(1f, QuickActionsMotion.trayOutlineAlpha(0f), 0.001f)
        assertEquals(1f, QuickActionsMotion.trayOutlineAlpha(1f), 0.001f)
        assertEquals(0.5f, QuickActionsMotion.outlineCenterInset(QuickActionsMotion.OUTLINE_DP), 0.001f)
        assertEquals(180f, QuickActionsMotion.visibleTrayHeight(900, 180, 0f), 0.01f)
        assertEquals(900f, QuickActionsMotion.visibleTrayHeight(900, 180, 1f), 0.01f)
        assertEquals(540f, QuickActionsMotion.visibleTrayHeight(900, 180, 0.5f), 0.01f)
        assertEquals(180f, QuickActionsMotion.chevronRotation(0f), 0.01f)
        assertEquals(0f, QuickActionsMotion.chevronRotation(1f), 0.01f)
        assertEquals(0f, QuickActionsMotion.tileForegroundMix(0f), 0.001f)
        assertEquals(0f, QuickActionsMotion.tileForegroundMix(0.8f), 0.001f)
        assertEquals(1f, QuickActionsMotion.tileForegroundMix(1f), 0.001f)
        assertEquals(1f, QuickActionsMotion.iconBackingAlpha(0f), 0.001f)
        assertEquals(0f, QuickActionsMotion.iconBackingAlpha(1f), 0.001f)
        assertEquals(1f, QuickActionsMotion.iconScale(0f, 36f), 0.01f)
        assertTrue(QuickActionsMotion.iconScale(1f, 36f) > QuickActionsMotion.iconScale(1f, 28f))
        assertTrue(QuickActionsMotion.labelScale(1f) > QuickActionsMotion.labelScale(0f))
    }

    @Test
    fun expandedTrayHugsVisibleTilesAndCapsOnAShortScreen() {
        val withWarranty = QuickActionsMotion.contentHeight(allOn)
        val withoutWarranty = QuickActionsMotion.contentHeight(
            ActionFlags(vendors = true, orders = true, warranty = false)
        )
        val withoutOrders = QuickActionsMotion.contentHeight(
            ActionFlags(vendors = true, orders = false, warranty = false)
        )
        assertTrue(withoutWarranty < withWarranty - QuickActionsMotion.WARRANTY_HEIGHT)
        assertTrue(withoutOrders < withoutWarranty)

        val peek = 220
        val strip = QuickActionsMotion.MIN_VISIBLE_DASHBOARD_DP
        val fitted = QuickActionsMotion.expandedTrayHeight(640, 1600, peek, strip)
        assertEquals(640, fitted)
        val viewport = QuickActionsMotion.actionViewportHeight(480, fitted, 160)
        assertEquals(480, viewport)
        assertFalse(QuickActionsMotion.trayScrolls(480, viewport))

        val capped = QuickActionsMotion.expandedTrayHeight(900, 700, peek, strip)
        assertEquals(700 - strip, capped)
        val scrolling = QuickActionsMotion.actionViewportHeight(760, capped, 160)
        assertTrue(scrolling < 760)
        assertTrue(QuickActionsMotion.trayScrolls(760, scrolling))
        assertTrue(capped >= peek)
    }

    @Test
    fun visibleStrokeKeepsADividerThatAlreadyContrastsAndStrengthensAPaleOne() {
        val ink = 0xFF212121.toInt()
        val white = 0xFFFFFFFF.toInt()
        assertEquals(ink, QuickActionsMotion.visibleStroke(ink, white, ink))
        val pale = 0xFFFFE0B2.toInt()
        val strengthened = QuickActionsMotion.visibleStroke(pale, white, ink)
        assertTrue(QuickActionsMotion.contrastRatio(strengthened, white) >= 3.0)
        val nightSurface = 0xFF1E1E1E.toInt()
        val nightInk = 0xFFF5F5F5.toInt()
        val nightStroke = QuickActionsMotion.visibleStroke(0xFF5D4037.toInt(), nightSurface, nightInk)
        assertTrue(QuickActionsMotion.contrastRatio(nightStroke, nightSurface) >= 3.0)
    }

    @Test
    fun shortcutInkStaysReadableWhileTheSharedCardBecomesItsTile() {
        val resting = 0xFF212121.toInt()
        val onTile = 0xFFFFFFFF.toInt()
        val accent = 0xFFFF9800.toInt()
        assertEquals(resting, QuickActionsMotion.tileInk(0f, resting, onTile))
        assertEquals(resting, QuickActionsMotion.tileInk(0.8f, resting, onTile))
        assertEquals(onTile, QuickActionsMotion.tileInk(1f, resting, onTile))
        assertEquals(accent, QuickActionsMotion.tileInk(0.8f, accent, onTile))
        listOf(0f, 0.4f, 0.8f).forEach { progress ->
            val fill = QuickActionsMotion.blendArgb(0xFFFFF3E0.toInt(), 0xFFF57C00.toInt(), progress)
            val label = QuickActionsMotion.tileInk(progress, resting, onTile)
            val ratio = QuickActionsMotion.contrastRatio(label, fill)
            assertTrue("light label contrast at $progress was $ratio", ratio >= 3.0)
        }
        val openTile = QuickActionsMotion.blendArgb(0xFFFFF3E0.toInt(), 0xFFF57C00.toInt(), 1f)
        val openRatio = QuickActionsMotion.contrastRatio(onTile, openTile)
        assertTrue("open tile contrast was $openRatio", openRatio >= 2.5)
        val nightTray = 0xFF2C2419.toInt()
        val nightIcon = QuickActionsMotion.contrastRatio(accent, nightTray)
        assertTrue("night accent on the tray was $nightIcon", nightIcon >= 2.5)
    }

    @Test
    fun actionFramesStayInsideTheTrayInsetWhenCollapsedExpandedAndMidMorph() {
        val variants = listOf(
            allOn,
            ActionFlags(vendors = false, orders = true, warranty = true),
            ActionFlags(vendors = true, orders = false, warranty = false),
            ActionFlags(vendors = false, orders = false, warranty = false)
        )
        val progress = listOf(0f, 0.35f, 0.5f, 0.8f, 1f)
        variants.forEach { flags ->
            val limit = QuickActionsMotion.contentHeight(flags) - QuickActionsMotion.INSET_DP
            progress.forEach { offset ->
                QuickActionsMotion.primary.forEach { action ->
                    val frame = QuickActionsMotion.frame(action, offset, width, flags)
                    assertInside(frame, "primary $action at $offset with $flags")
                    assertTrue(frame.bottom <= limit + 0.01f)
                }
                QuickActionsMotion.extras.forEach { action ->
                    if (!shown(action, flags)) return@forEach
                    val frame = QuickActionsMotion.expanded(action, width, flags)
                    assertInside(frame, "extra $action with $flags")
                    assertTrue(frame.bottom <= limit + 0.01f)
                }
            }
            val settings = QuickActionsMotion.expanded(QuickAction.SETTINGS, width, flags)
            assertEquals(QuickActionsMotion.INSET_DP, QuickActionsMotion.contentHeight(flags) - settings.bottom, 0.01f)
            assertTrue(settings.bottom + QuickActionsMotion.CONTENT_GAP_DP < QuickActionsMotion.contentHeight(flags))
        }
        val collapsed = QuickActionsMotion.primary.map { QuickActionsMotion.collapsed(it, width) }
        assertEquals(QuickActionsMotion.INSET_DP, collapsed.first().left, 0.01f)
        assertEquals(width - QuickActionsMotion.INSET_DP, collapsed.last().right, 0.01f)
        assertEquals(QuickActionsMotion.GAP, collapsed[1].left - collapsed[0].right, 0.01f)
    }

    private fun assertInside(frame: ActionFrame, label: String) {
        assertTrue("$label starts before the inset", frame.left >= QuickActionsMotion.INSET_DP - 0.01f)
        assertTrue("$label crosses the inset", frame.right <= width - QuickActionsMotion.INSET_DP + 0.01f)
    }

    private fun shown(action: QuickAction, flags: ActionFlags): Boolean = when (action) {
        QuickAction.VENDORS -> flags.vendors
        QuickAction.ORDERS -> flags.orders
        QuickAction.WARRANTY -> flags.warranty
        else -> true
    }

    @Test
    fun closeDragDoesNotRetargetListScrollingUntilTheSheetSettles() {
        assertFalse(QuickActionsMotion.shouldRetargetListScroll(gestureActive = true))
        assertTrue(QuickActionsMotion.shouldRetargetListScroll(gestureActive = false))
        assertTrue(QuickActionsMotion.listScrollEnabled(expanded = true))
        assertFalse(QuickActionsMotion.listScrollEnabled(expanded = false))
    }
}
