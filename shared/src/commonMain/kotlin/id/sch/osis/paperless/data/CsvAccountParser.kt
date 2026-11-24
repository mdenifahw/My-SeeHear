package id.sch.osis.paperless.data

import id.sch.osis.paperless.domain.AccountInput
import id.sch.osis.paperless.domain.OrgCatalog
import id.sch.osis.paperless.domain.OrgCategory

/**
 * Parser CSV sederhana untuk impor massal akun peserta.
 * Format kolom: `nama,kelas,jabatan,kategori,unit,username,pin`
 * Nilai kategori yang diterima: SEKBID, FRAKSI / OSIS_FRAKSI, EKSKUL, MPK.
 */
object CsvAccountParser {
    const val TEMPLATE =
        "nama,kelas,jabatan,kategori,unit,username,pin\n" +
            "Bima Sakti,X-2,Anggota Sekbid 2,SEKBID,Sekbid 2,bima.sakti,1234\n" +
            "Nadine Larasati,XI-1,Anggota Fraksi 1,FRAKSI,Fraksi 1,nadine.f1,1234\n" +
            "Farhan Maulana,X-4,Perwakilan Ekskul Robotik,EKSKUL,Robotik,farhan.rob,1234\n" +
            "Siti Rahma,XI-5,Anggota MPK Komisi C,MPK,MPK,siti.mpk,1234"

    data class ParseResult(val accounts: List<AccountInput>, val errors: List<String>)

    fun parse(csv: String): ParseResult {
        val accounts = mutableListOf<AccountInput>()
        val errors = mutableListOf<String>()
        val lines = csv.replace("\r", "").lines().map { it.trim() }.filter { it.isNotEmpty() }
        lines.forEachIndexed { index, raw ->
            val cols = splitCsvLine(raw)
            if (index == 0 && cols.firstOrNull()?.lowercase() in setOf("nama", "name")) return@forEachIndexed
            if (cols.size < 6) {
                errors += "Baris ${index + 1}: minimal 6 kolom (nama,kelas,jabatan,kategori,unit,username[,pin])."
                return@forEachIndexed
            }
            val category = parseCategory(cols[3])
            if (category == null) {
                errors += "Baris ${index + 1}: kategori '${cols[3]}' tidak dikenal (gunakan SEKBID/FRAKSI/EKSKUL/MPK)."
                return@forEachIndexed
            }
            val unit = cols[4].ifBlank { OrgCatalog.unitsFor(category).first() }
            val pin = cols.getOrNull(6)?.ifBlank { "1234" } ?: "1234"
            accounts += AccountInput(
                name = cols[0],
                kelas = cols[1],
                jabatan = cols[2].ifBlank { "Peserta Rapat" },
                category = category,
                unit = unit,
                username = cols[5].lowercase(),
                pin = pin,
            )
        }
        return ParseResult(accounts, errors)
    }

    private fun parseCategory(raw: String): OrgCategory? = when (raw.trim().uppercase()) {
        "SEKBID", "SEKSI BIDANG" -> OrgCategory.SEKBID
        "FRAKSI", "OSIS", "OSIS_FRAKSI", "OSIS FRAKSI" -> OrgCategory.OSIS_FRAKSI
        "EKSKUL", "EKSTRAKURIKULER" -> OrgCategory.EKSKUL
        "MPK" -> OrgCategory.MPK
        else -> null
    }

    private fun splitCsvLine(line: String): List<String> {
        val sep = if (line.count { it == ';' } > line.count { it == ',' }) ';' else ','
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var inQuotes = false
        for (ch in line) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == sep && !inQuotes -> {
                    out += cur.toString().trim()
                    cur.clear()
                }
                else -> cur.append(ch)
            }
        }
        out += cur.toString().trim()
        return out
    }
}
