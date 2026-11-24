import crypto from "node:crypto";

const DEFAULT_SECRET = "osis-paperless-dev-secret-change-in-vercel-env";
const TOKEN_TTL_MS = 5 * 60 * 1000; // 5 menit

function getSecret() {
  return process.env.QR_TOKEN_SECRET || DEFAULT_SECRET;
}

/**
 * Daftar kode sesi rapat yang valid untuk membuka QR unduhan.
 * Atur melalui Environment Variable `MEETING_ACCESS_CODES` di Vercel (pisahkan dengan koma),
 * misal: `OSIS26,RAPAT1,K7Q2PX`. Default: `OSIS26,DEMO26`.
 */
export function isValidSessionCode(code) {
  const clean = String(code || "").trim().toUpperCase();
  if (clean.length < 4 || clean.length > 16) return false;
  const configured = (process.env.MEETING_ACCESS_CODES || "OSIS26,DEMO26")
    .split(",")
    .map((s) => s.trim().toUpperCase())
    .filter(Boolean);
  if (configured.includes("*")) return true;
  return configured.includes(clean);
}

function base64UrlEncode(input) {
  return Buffer.from(input).toString("base64url");
}

function base64UrlDecode(input) {
  return Buffer.from(input, "base64url").toString("utf8");
}

function hmac(data) {
  return crypto.createHmac("sha256", getSecret()).update(data).digest("base64url");
}

/** Membuat signed token sementara (berlaku 5 menit). */
export function signDownloadToken({ platform, sessionCode, kind = "download", verifyCode = null }) {
  const now = Date.now();
  const payload = {
    p: platform,
    sc: String(sessionCode).trim().toUpperCase(),
    k: kind,
    vc: verifyCode,
    iat: now,
    exp: now + TOKEN_TTL_MS,
    jti: crypto.randomBytes(8).toString("hex"),
  };
  const encoded = base64UrlEncode(JSON.stringify(payload));
  const sig = hmac(encoded);
  return {
    token: `${encoded}.${sig}`,
    expiresAt: payload.exp,
    ttlSeconds: Math.floor(TOKEN_TTL_MS / 1000),
  };
}

/** Memverifikasi tanda tangan HMAC dan masa berlaku token. */
export function verifyDownloadToken(token) {
  if (!token || typeof token !== "string" || !token.includes(".")) {
    return { ok: false, reason: "Token unduhan tidak ditemukan atau format tidak valid." };
  }
  const [encoded, sig] = token.split(".");
  const expected = hmac(encoded);
  const sigBuf = Buffer.from(sig || "");
  const expBuf = Buffer.from(expected);
  if (sigBuf.length !== expBuf.length || !crypto.timingSafeEqual(sigBuf, expBuf)) {
    return { ok: false, reason: "Tanda tangan token tidak valid." };
  }
  let payload;
  try {
    payload = JSON.parse(base64UrlDecode(encoded));
  } catch {
    return { ok: false, reason: "Payload token rusak." };
  }
  if (!payload.exp || Date.now() > payload.exp) {
    return { ok: false, reason: "QR Code sudah kedaluwarsa (batas 5 menit). Silakan buat QR baru di portal." };
  }
  return { ok: true, payload };
}

/** Kode verifikasi 6 digit untuk alur Desktop (dipindai dari HP). */
export function generateVerifyCode() {
  return String(crypto.randomInt(100000, 999999));
}

export function getOrigin(req) {
  const proto = req.headers["x-forwarded-proto"] || "https";
  const host = req.headers["x-forwarded-host"] || req.headers.host || "localhost:3000";
  return `${proto}://${host}`;
}
