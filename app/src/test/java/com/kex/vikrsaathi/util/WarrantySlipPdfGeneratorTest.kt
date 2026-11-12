package com.kex.vikrsaathi.util

import android.app.Application
import android.graphics.Paint
import com.kex.vikrsaathi.data.entity.WarrantyClaim
import com.kex.vikrsaathi.domain.bill.WarrantyClaimPolicy
import com.kex.vikrsaathi.domain.bill.WarrantySlip
import com.kex.vikrsaathi.util.WarrantySlipPdfGenerator.Kind
import com.kex.vikrsaathi.util.WarrantySlipPdfGenerator.SlipDraw
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)

class WarrantySlipPdfGeneratorTest {
    @Test
    fun longSlipKeepsEveryLineAndTheFooter() {
        val body = mutableListOf(SlipDraw(Kind.TITLE, WarrantySlip.TITLE))
        repeat(80) { index ->
            body += SlipDraw(Kind.ROW, "line $index", label = if (index == 0) "Issue" else "")
        }
        body += SlipDraw(Kind.FOOTER, WarrantySlip.FOOTER)
        val pages = WarrantySlipPdfGenerator.assignPages(body, "${WarrantySlip.TITLE} · WC-26-00001")
        val drawn = pages.flatten()
        assertTrue(pages.size > 1)
        assertEquals(80, drawn.count { it.kind == Kind.ROW })
        assertEquals("line 0", drawn.first { it.kind == Kind.ROW }.text)
        assertEquals("line 79", drawn.last { it.kind == Kind.ROW }.text)
        assertEquals(WarrantySlip.FOOTER, drawn.last().text)
        assertTrue(pages.drop(1).all { it.first().kind == Kind.CONTINUATION })
    }

    @Test
    fun longClaimTextKeepsTheOutcomeAndFooterOnLaterPages() {
        val claim = WarrantyClaim(
            reference = "WC-26-00009",
            sequence = 9,
            billNumber = "INV-9",
            saleDateMillis = 1L,
            itemName = "Pen",
            issue = "The ink stops halfway through a sentence. ".repeat(40),
            providerOutcome = WarrantyClaimPolicy.REPAIRED,
            providerOutcomeNote = "The nib was replaced and tested. ".repeat(30),
            status = WarrantyClaimPolicy.RESOLVED,
            createdAt = 1L,
            updatedAt = 1L
        )
        val rows = WarrantySlip.rows(claim, "VikrSaathi")
        val body = mutableListOf(SlipDraw(Kind.TITLE, WarrantySlip.TITLE))
        rows.forEach { row ->
            row.value.chunked(28).forEachIndexed { index, chunk ->
                body += SlipDraw(Kind.ROW, chunk, label = if (index == 0) row.label else "")
            }
        }
        body += SlipDraw(Kind.FOOTER, WarrantySlip.FOOTER)
        val pages = WarrantySlipPdfGenerator.assignPages(
            body,
            "${WarrantySlip.TITLE} · ${claim.reference}"
        )
        val drawn = pages.flatten()
        assertTrue(pages.size > 1)
        assertTrue(drawn.any { it.label == "Provider outcome" && it.text == "Repaired" })
        assertEquals(WarrantySlip.FOOTER, drawn.last().text)
        assertTrue(pages.drop(1).all { it.first().text.contains(claim.reference) })
        assertTrue(drawn.any { it.text.contains("The ink stops") })
    }

    @Test
    fun wrapKeepsEveryWordIncludingALongUnbrokenToken() {
        val paint = Paint().apply { textSize = 12f }
        val words = WarrantySlipPdfGenerator.wrap("alpha beta gamma", paint, 10_000f)
        assertTrue(words.single().contains("alpha"))
        assertTrue(words.single().contains("gamma"))
        val token = "A".repeat(200)
        val pieces = WarrantySlipPdfGenerator.wrap(token, paint, 40f)
        assertTrue(pieces.size > 1)
        assertEquals(token, pieces.joinToString(""))
    }
}
