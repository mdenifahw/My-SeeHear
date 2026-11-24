package id.sch.osis.paperless.core

import kotlinx.serialization.Serializable

/** Jenis perangkat yang terhubung ke ruang rapat. */
@Serializable
enum class DeviceType(val label: String) {
    ANDROID("Android"),
    IOS("iOS"),
    DESKTOP("Desktop"),
    WEB("Web"),
}

/** Waktu epoch (ms) dari platform. */
expect fun nowMillis(): Long

/** Jenis perangkat yang sedang menjalankan aplikasi. */
expect val currentDeviceType: DeviceType

/** Format jam lokal, mis. "19:42". */
expect fun formatTime(epochMillis: Long): String

/** Format tanggal lokal (id-ID), mis. "24 Nov 2026". */
expect fun formatDate(epochMillis: Long): String

object Ids {
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    fun newId(prefix: String): String =
        prefix + "_" + nowMillis().toString(36) + (1..5).map { ALPHABET.random() }.joinToString("")

    /** Kode sesi rapat 6 karakter yang mudah dibacakan, mis. "K7Q2PX". */
    fun sessionCode(): String = (1..6).map { ALPHABET.random() }.joinToString("")
}

private fun Long.pad2(): String = if (this < 10) "0$this" else toString()

/** mm:ss untuk hitung mundur. */
fun formatCountdown(remainingMillis: Long): String {
    val totalSeconds = (remainingMillis / 1000).coerceAtLeast(0)
    return "${(totalSeconds / 60).pad2()}:${(totalSeconds % 60).pad2()}"
}

/** "2 menit lalu", "baru saja" — relatif terhadap [now]. */
fun formatRelative(epochMillis: Long, now: Long): String {
    val diff = (now - epochMillis).coerceAtLeast(0) / 1000
    return when {
        diff < 10 -> "baru saja"
        diff < 60 -> "$diff dtk lalu"
        diff < 3600 -> "${diff / 60} mnt lalu"
        diff < 86_400 -> "${diff / 3600} jam lalu"
        else -> formatDate(epochMillis)
    }
}
