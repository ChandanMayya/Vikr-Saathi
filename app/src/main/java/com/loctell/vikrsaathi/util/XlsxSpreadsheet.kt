package com.loctell.vikrsaathi.util

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object XlsxSpreadsheet {

    fun write(file: File, sheetName: String, headers: List<String>, rows: List<List<CellValue>>) {
        val sheetXml = buildSheetXml(headers, rows)
        ZipOutputStream(FileOutputStream(file)).use { zip ->
            writeEntry(zip, "[Content_Types].xml", contentTypesXml())
            writeEntry(zip, "_rels/.rels", rootRelsXml())
            writeEntry(zip, "xl/workbook.xml", workbookXml(sheetName))
            writeEntry(zip, "xl/_rels/workbook.xml.rels", workbookRelsXml())
            writeEntry(zip, "xl/styles.xml", stylesXml())
            writeEntry(zip, "xl/worksheets/sheet1.xml", sheetXml)
        }
    }

    fun read(input: InputStream): List<List<String>> {
        var sharedStrings = emptyList<String>()
        var sheetXml: String? = null

        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                val bytes = zip.readBytes()
                when (name) {
                    "xl/sharedStrings.xml" -> sharedStrings = parseSharedStrings(bytes)
                    "xl/worksheets/sheet1.xml" -> sheetXml = bytes.toString(Charsets.UTF_8)
                    else -> {
                        if (sheetXml == null && name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml")) {
                            sheetXml = bytes.toString(Charsets.UTF_8)
                        }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        val xml = sheetXml ?: return emptyList()
        return parseSheetXml(xml, sharedStrings)
    }

    sealed class CellValue {
        data class Text(val value: String) : CellValue()
        data class Number(val value: Double) : CellValue()
        data class IntNum(val value: Int) : CellValue()
    }

    private fun buildSheetXml(headers: List<String>, rows: List<List<CellValue>>): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        sb.append("<sheetData>")
        appendRow(sb, 1, headers.map { CellValue.Text(it) })
        rows.forEachIndexed { index, row ->
            appendRow(sb, index + 2, row)
        }
        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    private fun appendRow(sb: StringBuilder, rowNum: Int, cells: List<CellValue>) {
        sb.append("""<row r="$rowNum">""")
        cells.forEachIndexed { colIndex, cell ->
            val ref = "${columnName(colIndex)}$rowNum"
            when (cell) {
                is CellValue.Text -> {
                    sb.append("""<c r="$ref" t="inlineStr"><is><t>${escapeXml(cell.value)}</t></is></c>""")
                }
                is CellValue.IntNum -> {
                    sb.append("""<c r="$ref"><v>${cell.value}</v></c>""")
                }
                is CellValue.Number -> {
                    sb.append("""<c r="$ref"><v>${cell.value}</v></c>""")
                }
            }
        }
        sb.append("</row>")
    }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val result = mutableListOf<String>()
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(bytes.inputStream(), "UTF-8")
        var event = parser.eventType
        var inT = false
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> if (parser.name == "t") inT = true
                XmlPullParser.TEXT -> if (inT) result.add(parser.text)
                XmlPullParser.END_TAG -> if (parser.name == "t") inT = false
            }
            event = parser.next()
        }
        return result
    }

    private fun parseSheetXml(xml: String, sharedStrings: List<String>): List<List<String>> {
        val rows = mutableListOf<MutableList<String>>()
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(xml.reader())

        var event = parser.eventType
        var inRow = false
        var inValue = false
        var inInlineText = false
        var currentRow = mutableListOf<String>()
        var cellType: String? = null
        var cellValue = StringBuilder()

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "row" -> {
                        inRow = true
                        currentRow = mutableListOf()
                    }
                    "c" -> {
                        cellType = parser.getAttributeValue(null, "t")
                        cellValue.clear()
                    }
                    "v" -> inValue = true
                    "t" -> if (inRow) inInlineText = true
                }
                XmlPullParser.TEXT -> {
                    if (inValue || inInlineText) cellValue.append(parser.text)
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "v" -> inValue = false
                    "t" -> inInlineText = false
                    "c" -> {
                        val raw = cellValue.toString()
                        val text = when (cellType) {
                            "s" -> sharedStrings.getOrNull(raw.toIntOrNull() ?: -1).orEmpty()
                            else -> raw
                        }
                        currentRow.add(text)
                        cellValue.clear()
                        cellType = null
                    }
                    "row" -> {
                        if (currentRow.isNotEmpty()) rows.add(currentRow)
                        inRow = false
                    }
                }
            }
            event = parser.next()
        }
        return rows
    }

    private fun writeEntry(zip: ZipOutputStream, path: String, content: String) {
        zip.putNextEntry(ZipEntry(path))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun contentTypesXml() = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
          <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
          <Default Extension="xml" ContentType="application/xml"/>
          <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
          <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
          <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
        </Types>
    """.trimIndent()

    private fun rootRelsXml() = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
        </Relationships>
    """.trimIndent()

    private fun workbookXml(sheetName: String) = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
          xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
          <sheets>
            <sheet name="${escapeXml(sheetName)}" sheetId="1" r:id="rId1"/>
          </sheets>
        </workbook>
    """.trimIndent()

    private fun workbookRelsXml() = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
          <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
        </Relationships>
    """.trimIndent()

    private fun stylesXml() = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
          <fonts count="1"><font/></fonts>
          <fills count="1"><fill><patternFill patternType="none"/></fill></fills>
          <borders count="1"><border/></borders>
          <cellStyleXfs count="1"><xf/></cellStyleXfs>
          <cellXfs count="1"><xf/></cellXfs>
        </styleSheet>
    """.trimIndent()

    private fun columnName(index: Int): String {
        var i = index
        val sb = StringBuilder()
        while (i >= 0) {
            sb.insert(0, ('A'.code + (i % 26)).toChar())
            i = i / 26 - 1
        }
        return sb.toString()
    }

    private fun escapeXml(value: String): String =
        value.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
}
