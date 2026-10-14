package io.github.thelony97.orbit.core.domain

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Minimal Excel writer (PLAN 3.14 „Export Excel“): one .xlsx with several sheets, bold frozen
 * header row, text as inline strings and numbers as numbers. No library – the format is a ZIP of
 * a few XML parts, which every spreadsheet app opens.
 */
object Xlsx {

    data class Sheet(
        val name: String,
        val header: List<String>,
        /** Cells: null = empty, [Number] = number, anything else = text. */
        val rows: List<List<Any?>>,
        /** Column widths in characters; missing ones get a default. */
        val widths: List<Int> = emptyList(),
    )

    fun write(out: OutputStream, sheets: List<Sheet>) {
        require(sheets.isNotEmpty())
        val names = uniqueNames(sheets.map { it.name })
        // Closing ends the deflater; the caller's stream is closed with it (callers use `use`).
        ZipOutputStream(out).use { zip ->
            zip.put("[Content_Types].xml", contentTypes(sheets.size))
            zip.put("_rels/.rels", ROOT_RELS)
            zip.put("xl/workbook.xml", workbook(names))
            zip.put("xl/_rels/workbook.xml.rels", workbookRels(sheets.size))
            zip.put("xl/styles.xml", STYLES)
            sheets.forEachIndexed { i, s ->
                // Row by row into the ZIP: a long Verlauf never sits in memory as one string.
                zip.putNextEntry(ZipEntry("xl/worksheets/sheet${i + 1}.xml"))
                val w = zip.writer(Charsets.UTF_8).buffered()
                sheet(s, w)
                w.flush()
                zip.closeEntry()
            }
        }
    }

    /** "A", "B", …, "Z", "AA" for the 0-based [index]. */
    fun column(index: Int): String {
        var n = index + 1
        val sb = StringBuilder()
        while (n > 0) {
            val r = (n - 1) % 26
            sb.append('A' + r)
            n = (n - 1) / 26
        }
        return sb.reverse().toString()
    }

    /** XML text: escapes markup and drops control characters Excel refuses. */
    fun escape(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s) {
            when {
                ch == '&' -> sb.append("&amp;")
                ch == '<' -> sb.append("&lt;")
                ch == '>' -> sb.append("&gt;")
                ch == '"' -> sb.append("&quot;")
                ch < ' ' && ch != '\t' && ch != '\n' && ch != '\r' -> {}
                ch == '￾' || ch == '￿' -> {}
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    /** Sheet names: at most 31 characters, none of []:*?/\ and unique (case-insensitive). */
    internal fun uniqueNames(raw: List<String>): List<String> {
        val used = HashSet<String>()
        return raw.mapIndexed { i, r ->
            val base = r.replace(Regex("""[\[\]:*?/\\]"""), " ").trim().ifEmpty { "Tabelle${i + 1}" }.take(31)
            var name = base
            var n = 2
            while (!used.add(name.lowercase())) name = base.take(31 - " ($n)".length) + " (${n++})"
            name
        }
    }

    private fun ZipOutputStream.put(path: String, text: String) {
        putNextEntry(ZipEntry(path))
        write(text.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private const val HEAD = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>"""
    private const val MAIN = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private const val REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

    private const val ROOT_RELS = HEAD +
        """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
        """<Relationship Id="rId1" Type="$REL/officeDocument" Target="xl/workbook.xml"/></Relationships>"""

    private const val STYLES = HEAD +
        """<styleSheet xmlns="$MAIN">""" +
        """<fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts>""" +
        """<fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills>""" +
        """<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>""" +
        """<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>""" +
        """<cellXfs count="3"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>""" +
        """<xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/>""" +
        """<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0" applyAlignment="1"><alignment vertical="top" wrapText="1"/></xf></cellXfs>""" +
        """<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>"""

    private fun contentTypes(count: Int) = buildString {
        append(HEAD)
        append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        append("""<Default Extension="xml" ContentType="application/xml"/>""")
        append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
        append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
        for (i in 1..count) {
            append("""<Override PartName="/xl/worksheets/sheet$i.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
        }
        append("</Types>")
    }

    private fun workbook(names: List<String>) = buildString {
        append(HEAD)
        append("""<workbook xmlns="$MAIN" xmlns:r="$REL"><sheets>""")
        names.forEachIndexed { i, n -> append("""<sheet name="${escape(n)}" sheetId="${i + 1}" r:id="rId${i + 1}"/>""") }
        append("</sheets></workbook>")
    }

    private fun workbookRels(count: Int) = buildString {
        append(HEAD)
        append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        for (i in 1..count) append("""<Relationship Id="rId$i" Type="$REL/worksheet" Target="worksheets/sheet$i.xml"/>""")
        append("""<Relationship Id="rId${count + 1}" Type="$REL/styles" Target="styles.xml"/>""")
        append("</Relationships>")
    }

    private fun sheet(s: Sheet, out: Appendable) = with(out) {
        append(HEAD)
        append("""<worksheet xmlns="$MAIN">""")
        append("""<sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>""")
        val columns = maxOf(s.header.size, s.rows.maxOfOrNull { it.size } ?: 0)
        if (columns > 0) {
            append("<cols>")
            for (i in 0 until columns) {
                val w = s.widths.getOrNull(i) ?: 16
                append("""<col min="${i + 1}" max="${i + 1}" width="$w" customWidth="1"/>""")
            }
            append("</cols>")
        }
        append("<sheetData>")
        row(1, s.header, style = 1)
        s.rows.forEachIndexed { i, r -> row(i + 2, r, style = 2) }
        append("</sheetData></worksheet>")
        Unit
    }

    private fun Appendable.row(r: Int, cells: List<Any?>, style: Int) {
        append("""<row r="$r">""")
        cells.forEachIndexed { i, v ->
            val ref = column(i) + r
            when (v) {
                null -> {}
                is Number -> {
                    val d = v.toDouble()
                    if (d.isFinite()) append("""<c r="$ref" s="$style"><v>${if (d == Math.rint(d) && kotlin.math.abs(d) < 1e15) d.toLong().toString() else d.toString()}</v></c>""")
                }
                else -> {
                    val text = v.toString()
                    if (text.isNotEmpty()) append("""<c r="$ref" t="inlineStr" s="$style"><is><t xml:space="preserve">${escape(text.take(32_767))}</t></is></c>""")
                }
            }
        }
        append("</row>")
    }
}
