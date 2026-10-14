package io.github.thelony97.orbit.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class XlsxTest {

    private fun parts(bytes: ByteArray): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                out[e.name] = zip.readBytes().toString(Charsets.UTF_8)
            }
        }
        return out
    }

    @Test fun columnsCountLikeExcel() {
        assertEquals("A", Xlsx.column(0))
        assertEquals("Z", Xlsx.column(25))
        assertEquals("AA", Xlsx.column(26))
        assertEquals("AZ", Xlsx.column(51))
        assertEquals("BA", Xlsx.column(52))
    }

    @Test fun writesAllPartsWithEscapedTextAndNumbers() {
        val bytes = ByteArrayOutputStream().also { out ->
            Xlsx.write(
                out,
                listOf(
                    Xlsx.Sheet("Kontakte", listOf("Name", "Notiz", "Minuten"), listOf(listOf("Anna & Tom", "<b>\u0001Zeile\nzwei", 12), listOf("Bäckerei", null, 2.5))),
                    Xlsx.Sheet("Kontakte", listOf("Datum"), emptyList()),
                ),
            )
        }.toByteArray()
        val p = parts(bytes)
        assertEquals(
            setOf("[Content_Types].xml", "_rels/.rels", "xl/workbook.xml", "xl/_rels/workbook.xml.rels", "xl/styles.xml", "xl/worksheets/sheet1.xml", "xl/worksheets/sheet2.xml"),
            p.keys,
        )
        val s1 = p.getValue("xl/worksheets/sheet1.xml")
        assertTrue(s1.contains("""<c r="A1" t="inlineStr" s="1"><is><t xml:space="preserve">Name</t></is></c>"""))
        assertTrue(s1.contains("Anna &amp; Tom"))
        assertTrue(s1.contains("&lt;b&gt;Zeile\nzwei"))
        assertFalse(s1.contains("\u0001"))
        assertTrue(s1.contains("""<c r="C2" s="2"><v>12</v></c>"""))
        assertTrue(s1.contains("""<c r="C3" s="2"><v>2.5</v></c>"""))
        // The empty cell is left out, the row keeps its number.
        assertFalse(s1.contains("B3"))
        // Same sheet name twice: the second one gets a suffix.
        val wb = p.getValue("xl/workbook.xml")
        assertTrue(wb.contains("""name="Kontakte" sheetId="1""""))
        assertTrue(wb.contains("""name="Kontakte (2)" sheetId="2""""))
        assertTrue(p.getValue("[Content_Types].xml").contains("/xl/worksheets/sheet2.xml"))
    }

    @Test fun sheetNamesAreCleanedAndShortened() {
        assertEquals(listOf("a b", "Tabelle2", "x".repeat(31)), Xlsx.uniqueNames(listOf("a/b", "  ", "x".repeat(40))))
    }
}
