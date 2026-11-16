package com.example.core

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.StringReader
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class TabularImportData(
    val headers: List<String>,
    val rows: List<List<String>>,
    val sourceFormat: String
)

object ExcelXmlUtils {
    private const val MAX_ZIP_ENTRY_BYTES = 10 * 1024 * 1024

    fun parseCsv(text: String): TabularImportData {
        val clean = text.removePrefix("\uFEFF")
        val allRows = mutableListOf<List<String>>()
        val currentRow = mutableListOf<String>()
        val cell = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < clean.length) {
            val ch = clean[i]
            when {
                ch == '"' -> {
                    if (inQuotes && i + 1 < clean.length && clean[i + 1] == '"') {
                        cell.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                ch == ',' && !inQuotes -> {
                    currentRow += cell.toString().trim()
                    cell.clear()
                }
                (ch == '\n' || ch == '\r') && !inQuotes -> {
                    if (ch == '\r' && i + 1 < clean.length && clean[i + 1] == '\n') i++
                    currentRow += cell.toString().trim()
                    cell.clear()
                    if (currentRow.any { it.isNotEmpty() }) {
                        allRows += currentRow.toList()
                    }
                    currentRow.clear()
                }
                else -> cell.append(ch)
            }
            i++
        }
        if (cell.isNotEmpty() || currentRow.isNotEmpty()) {
            currentRow += cell.toString().trim()
            if (currentRow.any { it.isNotEmpty() }) {
                allRows += currentRow.toList()
            }
        }
        if (allRows.isEmpty()) return TabularImportData(emptyList(), emptyList(), "CSV")
        val headers = allRows.first()
        val dataRows = allRows.drop(1).map { row ->
            if (row.size < headers.size) row + List(headers.size - row.size) { "" } else row.take(headers.size)
        }
        return TabularImportData(headers, dataRows, "CSV")
    }

    fun writeCsvBytes(headers: List<String>, rows: List<List<String>>): ByteArray {
        fun esc(v: String) = "\"${v.replace("\"", "\"\"")}\""
        val sb = StringBuilder()
        sb.appendLine(headers.joinToString(",") { esc(it) })
        rows.forEach { row ->
            sb.appendLine(row.joinToString(",") { esc(it) })
        }
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    fun parseXlsx(bytes: ByteArray): TabularImportData {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            var count = 0
            while (entry != null && count < 200) {
                count++
                val name = entry.name
                require(!name.contains("..")) { "Unsafe archive path detected" }
                if (!entry.isDirectory) {
                    val out = ByteArrayOutputStream()
                    val buf = ByteArray(8192)
                    var total = 0
                    while (true) {
                        val read = zis.read(buf)
                        if (read <= 0) break
                        total += read
                        require(total <= MAX_ZIP_ENTRY_BYTES) { "Spreadsheet entry exceeds safe size limit" }
                        out.write(buf, 0, read)
                    }
                    entries[name] = out.toByteArray()
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        val sharedStrings = entries["xl/sharedStrings.xml"]?.let { parseSharedStrings(String(it, Charsets.UTF_8)) }.orEmpty()
        val sheetBytes = entries["xl/worksheets/sheet1.xml"]
            ?: entries.entries.firstOrNull { it.key.startsWith("xl/worksheets/sheet") && it.key.endsWith(".xml") }?.value
            ?: error("No worksheet found inside XLSX archive")

        val rawRows = parseWorksheetXml(String(sheetBytes, Charsets.UTF_8), sharedStrings)
        if (rawRows.isEmpty()) return TabularImportData(emptyList(), emptyList(), "XLSX")
        val headers = rawRows.first()
        val dataRows = rawRows.drop(1).map { row ->
            if (row.size < headers.size) row + List(headers.size - row.size) { "" } else row.take(headers.size)
        }
        return TabularImportData(headers, dataRows, "XLSX")
    }

    fun writeXlsxBytes(sheetName: String, headers: List<String>, rows: List<List<String>>): ByteArray {
        val allRows = listOf(headers) + rows
        val safeSheetName = sheetName.replace(Regex("[\\\\/*?:\\[\\]]"), "_").take(30).ifBlank { "Data" }

        val contentTypes = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>"""

        val rootRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

        val workbookXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="${escapeXml(safeSheetName)}" sheetId="1" r:id="rId1"/>
  </sheets>
</workbook>"""

        val workbookRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""

        val stylesXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="1"><font><sz val="11"/><name val="Calibri"/></font></fonts>
  <fills count="1"><fill><patternFill patternType="none"/></fill></fills>
  <borders count="1"><border/></borders>
  <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
  <cellXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/></cellXfs>
</styleSheet>"""

        val sheetXml = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
            allRows.forEachIndexed { rIdx, row ->
                val rNum = rIdx + 1
                append("""<row r="$rNum">""")
                row.forEachIndexed { cIdx, cellVal ->
                    val colRef = "${colIndexToLetters(cIdx)}$rNum"
                    append("""<c r="$colRef" t="inlineStr"><is><t>${escapeXml(cellVal)}</t></is></c>""")
                }
                append("</row>")
            }
            append("</sheetData></worksheet>")
        }

        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            fun addEntry(path: String, content: String) {
                zos.putNextEntry(ZipEntry(path))
                zos.write(content.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
            addEntry("[Content_Types].xml", contentTypes)
            addEntry("_rels/.rels", rootRels)
            addEntry("xl/workbook.xml", workbookXml)
            addEntry("xl/_rels/workbook.xml.rels", workbookRels)
            addEntry("xl/styles.xml", stylesXml)
            addEntry("xl/worksheets/sheet1.xml", sheetXml)
        }
        return baos.toByteArray()
    }

    private fun parseSharedStrings(xmlText: String): List<String> {
        val result = mutableListOf<String>()
        val parser = createPullParser(xmlText)
        var inSi = false
        var inT = false
        val current = StringBuilder()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "si" -> {
                        inSi = true
                        current.clear()
                    }
                    "t" -> if (inSi) inT = true
                }
                XmlPullParser.TEXT -> if (inSi && inT) current.append(parser.text ?: "")
                XmlPullParser.END_TAG -> when (parser.name) {
                    "t" -> inT = false
                    "si" -> {
                        result += current.toString()
                        inSi = false
                    }
                }
            }
            event = parser.next()
        }
        return result
    }

    private fun parseWorksheetXml(xmlText: String, sharedStrings: List<String>): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val parser = createPullParser(xmlText)
        var currentRowMap = mutableMapOf<Int, String>()
        var currentColIdx = 0
        var cellType = ""
        var inValue = false
        var inInlineText = false
        val textBuf = StringBuilder()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "row" -> currentRowMap = mutableMapOf()
                    "c" -> {
                        val ref = parser.getAttributeValue(null, "r") ?: ""
                        currentColIdx = colLettersToIndex(ref)
                        cellType = parser.getAttributeValue(null, "t") ?: ""
                        textBuf.clear()
                    }
                    "v" -> {
                        inValue = true
                        textBuf.clear()
                    }
                    "t" -> {
                        inInlineText = true
                        textBuf.clear()
                    }
                }
                XmlPullParser.TEXT -> if (inValue || inInlineText) {
                    textBuf.append(parser.text ?: "")
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "v" -> {
                        inValue = false
                        val raw = textBuf.toString().trim()
                        val resolved = when (cellType) {
                            "s" -> raw.toIntOrNull()?.let { sharedStrings.getOrNull(it) } ?: ""
                            "b" -> if (raw == "1") "true" else "false"
                            else -> raw
                        }
                        currentRowMap[currentColIdx] = resolved
                    }
                    "t" -> {
                        inInlineText = false
                        if (cellType == "inlineStr") {
                            currentRowMap[currentColIdx] = textBuf.toString().trim()
                        }
                    }
                    "row" -> {
                        if (currentRowMap.isNotEmpty()) {
                            val maxIdx = currentRowMap.keys.maxOrNull() ?: 0
                            val list = (0..maxIdx).map { currentRowMap[it].orEmpty() }
                            if (list.any { it.isNotBlank() }) {
                                rows += list
                            }
                        }
                    }
                }
            }
            event = parser.next()
        }
        return rows
    }

    private fun createPullParser(xmlText: String): XmlPullParser {
        return try {
            Xml.newPullParser().apply {
                setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                setInput(StringReader(xmlText))
            }
        } catch (_: Throwable) {
            XmlPullParserFactory.newInstance().apply {
                isNamespaceAware = false
            }.newPullParser().apply {
                setInput(StringReader(xmlText))
            }
        }
    }

    private fun colIndexToLetters(index: Int): String {
        var n = index
        val sb = StringBuilder()
        while (n >= 0) {
            sb.insert(0, ('A' + (n % 26)))
            n = (n / 26) - 1
        }
        return sb.toString()
    }

    private fun colLettersToIndex(cellRef: String): Int {
        val letters = cellRef.takeWhile { it.isLetter() }.uppercase()
        if (letters.isEmpty()) return 0
        var acc = 0
        for (ch in letters) {
            acc = acc * 26 + (ch - 'A' + 1)
        }
        return (acc - 1).coerceAtLeast(0)
    }

    private fun escapeXml(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
