import QRCode from "qrcode";
import { generateVerifyCode, getOrigin, isValidSessionCode, signDownloadToken } from "./_lib/token.js";

export default async function handler(req, res) {
  if (req.method !== "POST") {
    return res.status(405).json({ error: "Gunakan metode POST." });
  }

  const body = typeof req.body === "string" ? JSON.parse(req.body || "{}") : req.body || {};
  const sessionCode = String(body.sessionCode || "").trim().toUpperCase();
  const platform = body.platform === "desktop" ? "desktop" : "android";

  if (!isValidSessionCode(sessionCode)) {
    return res.status(403).json({
      error: "Kode Sesi Rapat tidak valid. Minta kode sesi aktif dari Pimpinan Rapat (contoh demo: OSIS26).",
    });
  }

  const origin = getOrigin(req);

  if (platform === "android") {
    const signed = signDownloadToken({ platform: "android", sessionCode, kind: "download" });
    const scanTarget = `${origin}/api/download?token=${encodeURIComponent(signed.token)}`;
    const qrSvg = await QRCode.toString(scanTarget, {
      type: "svg",
      width: 280,
      margin: 2,
      color: { dark: "#1E293B", light: "#FFFFFF" },
    });
    return res.status(200).json({
      platform: "android",
      qrSvg,
      expiresAt: signed.expiresAt,
      ttlSeconds: signed.ttlSeconds,
      sessionCode,
    });
  }

  // Desktop: HP memindai QR -> tampilkan Kode Verifikasi 6 digit -> ketik di laptop untuk mengunduh
  const verifyCode = generateVerifyCode();
  const qrSigned = signDownloadToken({ platform: "desktop", sessionCode, kind: "desktop-verify", verifyCode });
  const scanTarget = `${origin}/verify.html?token=${encodeURIComponent(qrSigned.token)}`;
  const qrSvg = await QRCode.toString(scanTarget, {
    type: "svg",
    width: 280,
    margin: 2,
    color: { dark: "#1E293B", light: "#FFFFFF" },
  });

  // Challenge token disimpan di sisi browser laptop (tanpa menyertakan verifyCode mentah di plaintext JSON)
  return res.status(200).json({
    platform: "desktop",
    qrSvg,
    challengeToken: qrSigned.token,
    expiresAt: qrSigned.expiresAt,
    ttlSeconds: qrSigned.ttlSeconds,
    sessionCode,
  });
}
