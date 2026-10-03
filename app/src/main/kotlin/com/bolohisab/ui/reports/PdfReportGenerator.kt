package com.bolohisab.ui.reports

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.bolohisab.R
import com.bolohisab.data.CustomerBalance
import com.bolohisab.data.Summary
import com.bolohisab.ui.format.Bn
import java.io.File
import java.io.FileOutputStream

/**
 * Renders one A4-ish page with Android's built-in [PdfDocument] — no PDF library needed for
 * a single generated summary page, and it keeps the app fully offline.
 */
object PdfReportGenerator {
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f

    fun generate(
        context: Context,
        periodLabel: String,
        generatedAt: String,
        summary: Summary,
        topDebtors: List<CustomerBalance>,
    ): File {
        val document = PdfDocument()
        val page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create())
        val canvas = page.canvas

        val title = Paint().apply { textSize = 22f; isFakeBoldText = true; color = Color.BLACK }
        val label = Paint().apply { textSize = 12f; color = Color.DKGRAY }
        val sectionHeader = Paint().apply { textSize = 14f; isFakeBoldText = true; color = Color.BLACK }
        val rowLabel = Paint().apply { textSize = 13f; color = Color.DKGRAY }
        val rowValue = Paint().apply { textSize = 13f; isFakeBoldText = true; color = Color.BLACK }
        val rule = Paint().apply { color = Color.LTGRAY; strokeWidth = 1f }
        val right = PAGE_WIDTH - MARGIN

        var y = MARGIN + 20f
        canvas.drawText(context.getString(R.string.pdf_title, context.getString(R.string.app_name)), MARGIN, y, title)
        y += 24f
        canvas.drawText(periodLabel, MARGIN, y, label)
        y += 16f
        canvas.drawText(context.getString(R.string.pdf_generated_at, generatedAt), MARGIN, y, label)
        y += 20f
        canvas.drawLine(MARGIN, y, right, y, rule)
        y += 28f

        canvas.drawText(context.getString(R.string.reports_title), MARGIN, y, sectionHeader)
        y += 22f
        val totals = listOf(
            context.getString(R.string.stat_sales) to Bn.taka(summary.sales),
            context.getString(R.string.stat_credit) to Bn.taka(summary.creditGiven),
            context.getString(R.string.stat_collected) to Bn.taka(summary.collected),
            context.getString(R.string.stat_expense) to Bn.taka(summary.expenses),
        )
        totals.forEach { (rowTitle, value) ->
            canvas.drawText(rowTitle, MARGIN, y, rowLabel)
            canvas.drawText(value, right - rowValue.measureText(value), y, rowValue)
            y += 20f
        }
        y += 6f
        canvas.drawText(context.getString(R.string.reports_entry_count, Bn.number(summary.entries.toLong())), MARGIN, y, label)
        y += 30f
        canvas.drawLine(MARGIN, y, right, y, rule)
        y += 28f

        canvas.drawText(context.getString(R.string.reports_top_debtors), MARGIN, y, sectionHeader)
        y += 22f
        if (topDebtors.isEmpty()) {
            canvas.drawText(context.getString(R.string.answer_no_debtors), MARGIN, y, rowLabel)
            y += 20f
        } else {
            topDebtors.forEach { row ->
                canvas.drawText(row.customer.name, MARGIN, y, rowLabel)
                val value = Bn.taka(row.due)
                canvas.drawText(value, right - rowValue.measureText(value), y, rowValue)
                y += 20f
            }
        }

        document.finishPage(page)

        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        val file = File(dir, "bolo-hisab-report-${System.currentTimeMillis()}.pdf")
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()
        return file
    }
}
