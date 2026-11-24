import { signDownloadToken, verifyDownloadToken } from "./_lib/token.js";

/**
 * Endpoint dua arah untuk alur Desktop:
 * - GET  /api/verify-desktop?token=...  -> dipanggil halaman verify.html di HP setelah scan QR; mengembalikan kode 6 digit.
 * - POST /api/verify-desktop            -> dipanggil laptop dengan { challengeToken, verifyCode }; mengembalikan token unduhan sekali pakai.
 */
export default async function handler(req, res) {
  if (req.method === "GET") {
    const check = verifyDownloadToken(req.query?.token);
    if (!check.ok || check.payload.k !== "desktop-verify") {
      return res.status(403).json({ error: check.reason || "Token verifikasi tidak valid." });
    }
    return res.status(200).json({
      verifyCode: check.payload.vc,
      sessionCode: check.payload.sc,
      expiresAt: check.payload.exp,
    });
  }

  if (req.method === "POST") {
    const body = typeof req.body === "string" ? JSON.parse(req.body || "{}") : req.body || {};
    const check = verifyDownloadToken(body.challengeToken);
    if (!check.ok || check.payload.k !== "desktop-verify") {
      return res.status(403).json({ error: check.reason || "QR Desktop sudah kedaluwarsa. Muat ulang QR." });
    }
    const entered = String(body.verifyCode || "").trim();
    if (entered !== String(check.payload.vc)) {
      return res.status(403).json({ error: "Kode verifikasi 6 digit tidak cocok." });
    }
    const download = signDownloadToken({
      platform: "desktop",
      sessionCode: check.payload.sc,
      kind: "download",
    });
    return res.status(200).json({
      downloadUrl: `/api/download?token=${encodeURIComponent(download.token)}`,
      expiresAt: download.expiresAt,
    });
  }

  return res.status(405).json({ error: "Method Not Allowed" });
}
