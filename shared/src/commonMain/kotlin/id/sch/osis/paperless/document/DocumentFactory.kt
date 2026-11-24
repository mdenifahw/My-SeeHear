package id.sch.osis.paperless.document

import id.sch.osis.paperless.domain.BlockKind
import id.sch.osis.paperless.domain.DocBlock
import id.sch.osis.paperless.domain.DocPage
import id.sch.osis.paperless.domain.DocumentFormat
import id.sch.osis.paperless.domain.MeetingDocument

/** Blok mentah sebelum dipecah per kalimat. */
data class RawBlock(val kind: BlockKind, val text: String)

/**
 * Pipeline standardisasi dokumen: PDF / DOCX / markup → halaman berisi blok kalimat.
 * Koordinat sorotan (halaman, blok, indeks kalimat) identik di semua platform.
 */
object DocumentFactory {

    private val abbreviations = setOf(
        "rp", "no", "hal", "dll", "dsb", "dkk", "bpk", "sdr", "yth", "tgl", "jl", "s.pd", "m.pd", "drs", "dr", "st", "pt", "cv",
    )

    /** Memecah paragraf menjadi kalimat tanpa regex lookbehind (aman untuk semua target KMP). */
    fun splitSentences(text: String): List<String> {
        val clean = text.replace('\n', ' ').replace(Regex("\\s+"), " ").trim()
        if (clean.isEmpty()) return emptyList()
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var i = 0
        while (i < clean.length) {
            val c = clean[i]
            current.append(c)
            if (c == '.' || c == '!' || c == '?') {
                // ikutkan tanda kutip/kurung penutup
                while (i + 1 < clean.length && clean[i + 1] in "\"”')") {
                    i++; current.append(clean[i])
                }
                val nextIsSpace = i + 1 < clean.length && clean[i + 1] == ' '
                val nextChar = if (i + 2 < clean.length) clean[i + 2] else null
                val lastWord = current.toString().trimEnd('.', '!', '?', '"', '”', '\'', ')')
                    .substringAfterLast(' ').lowercase()
                val isAbbrev = c == '.' && (lastWord in abbreviations || (lastWord.length == 1 && lastWord[0].isLetter()))
                val startsNew = nextChar != null && (nextChar.isUpperCase() || nextChar.isDigit() || nextChar in "\"“(")
                if (nextIsSpace && startsNew && !isAbbrev) {
                    result += current.toString().trim()
                    current.clear()
                    i++ // lewati spasi
                }
            }
            i++
        }
        if (current.isNotBlank()) result += current.toString().trim()
        return result
    }

    /**
     * Markup sederhana untuk dokumen contoh:
     * `# Judul`, `## Subjudul`, `- butir`, `| sel | sel`, `===` pemisah halaman, selain itu paragraf.
     */
    fun parseMarkup(markup: String): List<List<RawBlock>> {
        val pages = mutableListOf<MutableList<RawBlock>>(mutableListOf())
        markup.lines().map { it.trim() }.forEach { line ->
            when {
                line.isEmpty() -> Unit
                line == "===" -> pages += mutableListOf<RawBlock>()
                line.startsWith("## ") -> pages.last() += RawBlock(BlockKind.HEADING, line.removePrefix("## "))
                line.startsWith("# ") -> pages.last() += RawBlock(BlockKind.TITLE, line.removePrefix("# "))
                line.startsWith("- ") -> pages.last() += RawBlock(BlockKind.BULLET, line.removePrefix("- "))
                line.startsWith("|") -> pages.last() += RawBlock(
                    BlockKind.TABLE_ROW,
                    line.trim('|').split('|').joinToString("  |  ") { it.trim() },
                )
                else -> pages.last() += RawBlock(BlockKind.PARAGRAPH, line)
            }
        }
        return pages.filter { it.isNotEmpty() }
    }

    /** Memecah daftar blok panjang menjadi halaman ±[maxChars] karakter. */
    fun paginate(blocks: List<RawBlock>, maxChars: Int = 1500): List<List<RawBlock>> {
        val pages = mutableListOf<MutableList<RawBlock>>()
        var current = mutableListOf<RawBlock>()
        var size = 0
        blocks.forEach { block ->
            val weight = block.text.length + if (block.kind == BlockKind.HEADING || block.kind == BlockKind.TITLE) 120 else 40
            if (size + weight > maxChars && current.isNotEmpty()) {
                pages += current; current = mutableListOf(); size = 0
            }
            current += block; size += weight
        }
        if (current.isNotEmpty()) pages += current
        return pages
    }

    fun build(
        id: String,
        title: String,
        fileName: String,
        format: DocumentFormat,
        pages: List<List<RawBlock>>,
        uploaderId: String,
        uploaderName: String,
        uploadedAt: Long,
        sourceKey: String? = null,
        conversionNote: String? = null,
    ): MeetingDocument {
        val docPages = pages.mapIndexed { pageIndex, raw ->
            DocPage(
                number = pageIndex + 1,
                blocks = raw.mapIndexedNotNull { blockIndex, block ->
                    val sentences = if (block.kind == BlockKind.PARAGRAPH || block.kind == BlockKind.BULLET) {
                        splitSentences(block.text)
                    } else listOf(block.text.trim())
                    if (sentences.isEmpty() || sentences.all { it.isBlank() }) null
                    else DocBlock(id = "p${pageIndex + 1}b$blockIndex", kind = block.kind, sentences = sentences)
                },
            )
        }.ifEmpty {
            listOf(DocPage(1, listOf(DocBlock("p1b0", BlockKind.PARAGRAPH, listOf("(Dokumen kosong)")))))
        }
        return MeetingDocument(
            id = id, title = title, fileName = fileName, format = format, pages = docPages,
            uploadedById = uploaderId, uploadedByName = uploaderName, uploadedAt = uploadedAt,
            sourceKey = sourceKey, conversionNote = conversionNote,
        )
    }
}

/** Parser Microsoft Word (.docx → word/document.xml) murni Kotlin common. */
object DocxParser {
    private val rowRegex = Regex("<w:tr[ >][\\s\\S]*?</w:tr>")
    private val cellRegex = Regex("<w:tc[ >][\\s\\S]*?</w:tc>")
    private val paragraphRegex = Regex("<w:p[ >][\\s\\S]*?</w:p>|<w:p/>")
    private val textRegex = Regex("<w:t(?: [^>]*)?>([^<]*)</w:t>|<w:tab/>|<w:br w:type=\"page\"/>|<w:lastRenderedPageBreak/>")
    private val styleRegex = Regex("<w:pStyle w:val=\"([^\"]+)\"")

    private const val PAGE_BREAK = "\u000C"

    fun parse(documentXml: String): List<List<RawBlock>> {
        val body = documentXml.substringAfter("<w:body>", documentXml).substringBefore("</w:body>")
        // Ganti setiap baris tabel menjadi paragraf sintetis bergaya __ROW
        val flattened = rowRegex.replace(body) { row ->
            val cells = cellRegex.findAll(row.value).map { cell -> extractText(cell.value).trim() }.toList()
            "<w:p><w:pPr><w:pStyle w:val=\"__ROW\"/></w:pPr><w:r><w:t>" +
                escape(cells.joinToString("  |  ")) + "</w:t></w:r></w:p>"
        }
        val blocks = mutableListOf<RawBlock?>() // null = pemisah halaman
        var hasExplicitBreaks = false
        paragraphRegex.findAll(flattened).forEach { match ->
            val p = match.value
            val style = styleRegex.find(p)?.groupValues?.get(1)?.lowercase().orEmpty()
            if (p.contains("w:pageBreakBefore")) { blocks += null; hasExplicitBreaks = true }
            val raw = extractText(p)
            val segments = raw.split(PAGE_BREAK)
            segments.forEachIndexed { index, segment ->
                if (index > 0) { blocks += null; hasExplicitBreaks = true }
                val text = segment.trim()
                if (text.isNotEmpty()) {
                    val kind = when {
                        style == "__row" -> BlockKind.TABLE_ROW
                        style == "title" || style == "judul" -> BlockKind.TITLE
                        style.startsWith("heading") || style.startsWith("judul") -> BlockKind.HEADING
                        p.contains("<w:numPr>") || style.contains("list") -> BlockKind.BULLET
                        else -> BlockKind.PARAGRAPH
                    }
                    blocks += RawBlock(kind, text)
                }
            }
        }
        if (!hasExplicitBreaks) return DocumentFactory.paginate(blocks.filterNotNull())
        val pages = mutableListOf<List<RawBlock>>()
        var current = mutableListOf<RawBlock>()
        blocks.forEach { b ->
            if (b == null) {
                if (current.isNotEmpty()) pages += current
                current = mutableListOf()
            } else current += b
        }
        if (current.isNotEmpty()) pages += current
        // Halaman yang terlalu panjang tetap dipecah agar nyaman dibaca di layar kecil
        return pages.flatMap { DocumentFactory.paginate(it, maxChars = 2200) }
    }

    private fun extractText(xml: String): String = buildString {
        textRegex.findAll(xml).forEach { m ->
            when {
                m.value == "<w:tab/>" -> append(' ')
                m.value.startsWith("<w:br") || m.value.startsWith("<w:lastRendered") -> append(PAGE_BREAK)
                else -> append(unescape(m.groupValues[1]))
            }
        }
    }.let { text ->
        // lastRenderedPageBreak bisa muncul di awal paragraf; rapikan pemisah ganda
        text.replace(Regex("$PAGE_BREAK+"), PAGE_BREAK)
    }

    private fun unescape(s: String) = s.replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")

    private fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}

/** Mengubah teks mentah satu halaman PDF menjadi blok terstruktur (heuristik tata letak). */
object PdfTextLayout {
    private val bulletPrefixes = listOf("•", "◦", "▪", "-", "–", "*")

    fun toBlocks(pageText: String): List<RawBlock> {
        val lines = pageText.replace("\r", "").lines().map { it.trim() }
        val blocks = mutableListOf<RawBlock>()
        val paragraph = StringBuilder()
        fun flush() {
            if (paragraph.isNotBlank()) blocks += RawBlock(BlockKind.PARAGRAPH, paragraph.toString().trim())
            paragraph.clear()
        }
        lines.forEach { line ->
            when {
                line.isEmpty() -> flush()
                bulletPrefixes.any { line.startsWith("$it ") } || Regex("^\\d{1,2}[.)] ").containsMatchIn(line) -> {
                    flush(); blocks += RawBlock(BlockKind.BULLET, line.trimStart('•', '◦', '▪', '-', '–', '*', ' '))
                }
                line.length < 70 && !line.endsWith('.') && !line.endsWith(',') && paragraph.isEmpty() &&
                    line.firstOrNull()?.isUpperCase() == true && line.split(' ').size <= 10 -> {
                    flush(); blocks += RawBlock(BlockKind.HEADING, line)
                }
                else -> {
                    if (paragraph.isNotEmpty()) paragraph.append(' ')
                    paragraph.append(line)
                }
            }
        }
        flush()
        return blocks
    }
}
