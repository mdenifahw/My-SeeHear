package id.sch.osis.paperless.platform

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import id.sch.osis.paperless.core.Ids
import id.sch.osis.paperless.core.formatDate
import id.sch.osis.paperless.core.nowMillis
import id.sch.osis.paperless.document.DocumentFactory
import id.sch.osis.paperless.document.DocxParser
import id.sch.osis.paperless.document.PdfTextLayout
import id.sch.osis.paperless.document.RawBlock
import id.sch.osis.paperless.domain.BlockKind
import id.sch.osis.paperless.domain.DocumentFormat
import id.sch.osis.paperless.domain.MeetingDocument
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/**
 * Bridge dokumen Android:
 * - Memilih PDF / Microsoft Word (.docx / .doc) via Storage Access Framework.
 * - Mengonversi `.docx` (ZIP + `word/document.xml`) menjadi blok kalimat terstandarisasi lewat [DocxParser].
 * - Membaca metadata halaman `.pdf` via `PdfRenderer` + ekstraksi teks dasar.
 * - Mengekspor daftar poin revisi / notulensi rapat ke file PDF nyata menggunakan `android.graphics.pdf.PdfDocument`.
 */
class AndroidDocumentBridge(private val activity: ComponentActivity) : DocumentBridge {

    private var pendingPick: CompletableDeferred<Uri?>? = null

    private val openDocLauncher: ActivityResultLauncher<Array<String>> =
        activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            pendingPick?.complete(uri)
            pendingPick = null
        }

    override suspend fun pickAndConvert(uploaderId: String, uploaderName: String): Result<MeetingDocument?> {
        val deferred = CompletableDeferred<Uri?>()
        pendingPick = deferred
        withContext(Dispatchers.Main) {
            openDocLauncher.launch(
                arrayOf(
                    "application/pdf",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/msword",
                    "text/plain",
                ),
            )
        }
        val uri = deferred.await() ?: return Result.success(null)
        return withContext(Dispatchers.IO) {
            runCatching { convertUri(uri, uploaderId, uploaderName) }
        }
    }

    private fun convertUri(uri: Uri, uploaderId: String, uploaderName: String): MeetingDocument {
        val fileName = resolveFileName(uri)
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val title = fileName.substringBeforeLast('.').ifBlank { fileName }
        val now = nowMillis()

        return when (ext) {
            "docx" -> {
                val xml = extractDocxXml(uri) ?: error("Berkas .docx tidak memiliki word/document.xml yang valid.")
                val pages = DocxParser.parse(xml)
                DocumentFactory.build(
                    id = Ids.newId("doc"),
                    title = title,
                    fileName = fileName,
                    format = DocumentFormat.DOCX,
                    pages = pages,
                    uploaderId = uploaderId,
                    uploaderName = uploaderName,
                    uploadedAt = now,
                    sourceKey = uri.toString(),
                    conversionNote = "Dikonversi dari Word (.docx) ke kanvas kalimat terstandarisasi.",
                )
            }
            "pdf" -> {
                val pages = extractPdfPages(uri, title)
                DocumentFactory.build(
                    id = Ids.newId("doc"),
                    title = title,
                    fileName = fileName,
                    format = DocumentFormat.PDF,
                    pages = pages,
                    uploaderId = uploaderId,
                    uploaderName = uploaderName,
                    uploadedAt = now,
                    sourceKey = uri.toString(),
                    conversionNote = "Dokumen PDF (${pages.size} halaman).",
                )
            }
            else -> {
                val text = activity.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
                val blocks = PdfTextLayout.toBlocks(text)
                DocumentFactory.build(
                    id = Ids.newId("doc"),
                    title = title,
                    fileName = fileName,
                    format = if (ext == "doc") DocumentFormat.DOC else DocumentFormat.PDF,
                    pages = DocumentFactory.paginate(blocks),
                    uploaderId = uploaderId,
                    uploaderName = uploaderName,
                    uploadedAt = now,
                    sourceKey = uri.toString(),
                )
            }
        }
    }

    private fun extractDocxXml(uri: Uri): String? {
        activity.contentResolver.openInputStream(uri)?.use { stream ->
            ZipInputStream(stream).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (entry.name == "word/document.xml") {
                        return zis.bufferedReader(Charsets.UTF_8).readText()
                    }
                    entry = zis.nextEntry
                }
            }
        }
        return null
    }

    private fun extractPdfPages(uri: Uri, title: String): List<List<RawBlock>> {
        var pageCount = 1
        runCatching {
            activity.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    pageCount = renderer.pageCount.coerceAtLeast(1)
                }
            }
        }
        // Ekstraksi teks ringan dari stream PDF ASCII bila tersedia; fallback ke blok halaman terindeks.
        val rawBytes = activity.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
        val ascii = String(rawBytes, Charsets.ISO_8859_1)
        val extracted = Regex("\\(([^()]{4,240})\\)\\s*Tj").findAll(ascii)
            .map { it.groupValues[1].replace("\\(", "(").replace("\\)", ")").trim() }
            .filter { line -> line.count { it.isLetterOrDigit() } >= 3 }
            .toList()

        if (extracted.size >= 4) {
            val blocks = PdfTextLayout.toBlocks(extracted.joinToString("\n"))
            return DocumentFactory.paginate(blocks)
        }
        return (1..pageCount).map { p ->
            listOf(
                RawBlock(BlockKind.HEADING, "$title — Halaman $p"),
                RawBlock(
                    BlockKind.PARAGRAPH,
                    "Halaman $p dari dokumen PDF '$title' terdeteksi ($pageCount halaman). Untuk sorotan kalimat penuh pada PDF terkompresi/hasil pindai, unggah versi .docx atau aktifkan konverter server.",
                ),
            )
        }
    }

    private fun resolveFileName(uri: Uri): String {
        activity.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return c.getString(idx)
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "Laporan_OSIS.docx"
    }

    override suspend fun exportRevisionPdf(
        sessionTitle: String,
        sessionCode: String,
        documentTitle: String,
        lines: List<RevisionExportItem>,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val pdf = PdfDocument()
            val pageWidth = 595 // A4 72dpi
            val pageHeight = 842
            val margin = 44f
            val maxWidth = pageWidth - margin * 2

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#1E293B")
                textSize = 16f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#64748B")
                textSize = 10.5f
            }
            val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#1E293B")
                textSize = 11.5f
            }
            val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#4A7CE0")
                textSize = 11f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }

            var pageNum = 1
            var page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
            var canvas = page.canvas
            var y = margin + 12f

            fun newPageIfNeeded(required: Float) {
                if (y + required > pageHeight - margin) {
                    pdf.finishPage(page)
                    pageNum++
                    page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
                    canvas = page.canvas
                    y = margin + 12f
                }
            }

            fun drawWrapped(text: String, paint: Paint, lineHeight: Float = 16f) {
                val words = text.split(' ')
                var line = ""
                for (w in words) {
                    val candidate = if (line.isEmpty()) w else "$line $w"
                    if (paint.measureText(candidate) > maxWidth && line.isNotEmpty()) {
                        newPageIfNeeded(lineHeight)
                        canvas.drawText(line, margin, y, paint)
                        y += lineHeight
                        line = w
                    } else {
                        line = candidate
                    }
                }
                if (line.isNotEmpty()) {
                    newPageIfNeeded(lineHeight)
                    canvas.drawText(line, margin, y, paint)
                    y += lineHeight
                }
            }

            drawWrapped("NOTULENSI & POIN REVISI — OSISPAPERLESS", titlePaint, 22f)
            drawWrapped("Rapat: $sessionTitle (Kode: $sessionCode)", metaPaint, 15f)
            drawWrapped("Dokumen: $documentTitle • Tanggal: ${formatDate(nowMillis())}", metaPaint, 18f)
            y += 8f

            lines.forEachIndexed { idx, item ->
                newPageIfNeeded(54f)
                val header = "${idx + 1}. " + (item.page?.let { "Halaman $it • " } ?: "") + item.author
                drawWrapped(header, accentPaint, 16f)
                item.quote?.let { drawWrapped("Kutipan: \"$it\"", bodyPaint, 15f) }
                if (item.comment.isNotBlank()) drawWrapped("Tanggapan: ${item.comment}", bodyPaint, 15f)
                item.note?.let { drawWrapped("Catatan Sekretaris: $it", metaPaint, 15f) }
                y += 10f
            }

            pdf.finishPage(page)
            val dir = File(activity.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "OSISPaperless").apply { mkdirs() }
            val outFile = File(dir, "Notulensi_${sessionCode}_${nowMillis()}.pdf")
            FileOutputStream(outFile).use { pdf.writeTo(it) }
            pdf.close()
            outFile.absolutePath
        }
    }
}
