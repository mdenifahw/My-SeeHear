package id.sch.osis.paperless

import id.sch.osis.paperless.core.DeviceType
import id.sch.osis.paperless.data.CsvAccountParser
import id.sch.osis.paperless.document.DocxParser
import id.sch.osis.paperless.domain.HighlightAnchor
import id.sch.osis.paperless.domain.SampleData
import id.sch.osis.paperless.domain.SessionStatus
import id.sch.osis.paperless.domain.UnlockStatus
import id.sch.osis.paperless.domain.ViolationKind
import id.sch.osis.paperless.engine.MeetingEngine
import id.sch.osis.paperless.protocol.ClientCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MeetingEngineTest {
    @Test
    fun meetingLifecycleAndUnlockFlow() {
        var clock = 1_000_000L
        val engine = MeetingEngine(
            seedAccounts = SampleData.accounts(),
            initialDocument = SampleData.sampleDocument(),
            clock = { clock },
        )
        val ketum = engine.authenticate("ketum", "1234")!!
        val sekbid = engine.authenticate("sekbid4", "1234")!!

        assertNull(engine.handle(ketum.id, ClientCommand.JoinRoom(DeviceType.ANDROID)))
        assertNull(engine.handle(sekbid.id, ClientCommand.JoinRoom(DeviceType.ANDROID)))
        assertNull(engine.handle(ketum.id, ClientCommand.StartMeeting))
        assertEquals(SessionStatus.LIVE, engine.state.value.session.status)

        // Peserta meminta izin 2 menit
        assertNull(engine.handle(sekbid.id, ClientCommand.RequestUnlock("Hubungi orang tua", 2)))
        val req = engine.state.value.unlockRequests.first()
        assertEquals(UnlockStatus.PENDING, req.status)

        // Ketua Umum menyetujui 2 menit
        assertNull(engine.handle(ketum.id, ClientCommand.DecideUnlock(req.id, approve = true, minutes = 2)))
        assertNotNull(engine.state.value.activeGrantFor(sekbid.id, clock))

        // Setelah 2 menit + 1 detik, izin otomatis kadaluarsa & kembali terkunci
        clock += 121_000L
        engine.tick()
        assertNull(engine.state.value.activeGrantFor(sekbid.id, clock))
        assertEquals(UnlockStatus.EXPIRED, engine.state.value.unlockRequests.first().status)

        // Pelanggaran keluar paksa tercatat di audit log
        assertNull(engine.handle(sekbid.id, ClientCommand.ReportViolation(ViolationKind.HOME_OR_RECENTS, "Test")))
        assertTrue(engine.state.value.auditLog.any { it.type.isViolation && it.actorId == sekbid.id })
    }

    @Test
    fun highlightAndCsvImportWork() {
        val engine = MeetingEngine(SampleData.accounts(), SampleData.sampleDocument())
        val sek = engine.authenticate("sekretaris", "1234")!!
        val doc = engine.state.value.document!!
        val firstBlock = doc.pages.first().blocks.first()

        assertNull(
            engine.handle(
                sek.id,
                ClientCommand.SendHighlight(HighlightAnchor(1, firstBlock.id, 0, 0, firstBlock.sentences.first()), "Periksa judul"),
            ),
        )
        val msg = engine.state.value.messages.last()
        assertNull(engine.handle(sek.id, ClientCommand.ToggleRevision(msg.id, true, "Perbaiki huruf kapital")))
        assertTrue(engine.state.value.messages.last().revision)

        val parsed = CsvAccountParser.parse(CsvAccountParser.TEMPLATE)
        assertEquals(4, parsed.accounts.size)
        assertTrue(parsed.errors.isEmpty())
    }

    @Test
    fun docxParserExtractsParagraphsAndHeadings() {
        val xml = """
            <w:document><w:body>
              <w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr><w:r><w:t>Bab I Pendahuluan</w:t></w:r></w:p>
              <w:p><w:r><w:t>Laporan kegiatan OSIS telah selesai disusun. Semua data telah diverifikasi.</w:t></w:r></w:p>
            </w:body></w:document>
        """.trimIndent()
        val pages = DocxParser.parse(xml)
        assertEquals(1, pages.size)
        assertEquals(2, pages.first().size)
    }
}
