import { verifyDownloadToken } from "./_lib/token.js";

/**
 * Mencari aset installer dari GitHub Releases (mendukung repo publik maupun privat dengan GITHUB_TOKEN)
 * atau langsung dari env APK_DOWNLOAD_URL / DESKTOP_DOWNLOAD_URL.
 */
async function resolveInstallerStream(platform) {
  const directEnv = platform === "desktop" ? process.env.DESKTOP_DOWNLOAD_URL : process.env.APK_DOWNLOAD_URL;
  const filename = platform === "desktop" ? "OSISPaperless-Desktop.zip" : "OSISPaperless-Android.apk";
  const contentType =
    platform === "desktop" ? "application/octet-stream" : "application/vnd.android.package-archive";

  if (directEnv) {
    const headers = {};
    if (process.env.GITHUB_TOKEN && directEnv.includes("github.com")) {
      headers.Authorization = `Bearer ${process.env.GITHUB_TOKEN}`;
      headers.Accept = "application/octet-stream";
    }
    const upstream = await fetch(directEnv, { headers, redirect: "follow" });
    if (!upstream.ok) {
      throw new Error(`Gagal mengambil file installer (${upstream.status}).`);
    }
    return { upstream, filename, contentType };
  }

  const repo = process.env.GITHUB_REPO; // format: "username/repo-name"
  if (!repo) {
    return null;
  }

  const ghHeaders = {
    Accept: "application/vnd.github+json",
    "User-Agent": "OSISPaperless-QR-Portal",
  };
  if (process.env.GITHUB_TOKEN) {
    ghHeaders.Authorization = `Bearer ${process.env.GITHUB_TOKEN}`;
  }

  const relRes = await fetch(`https://api.github.com/repos/${repo}/releases/latest`, { headers: ghHeaders });
  if (!relRes.ok) {
    throw new Error(`Release terbaru di GitHub (${repo}) belum tersedia (HTTP ${relRes.status}).`);
  }
  const release = await relRes.json();
  const extMatch = platform === "desktop" ? /\.(exe|msi|dmg|appimage|zip)$/i : /\.apk$/i;
  const asset = (release.assets || []).find((a) => extMatch.test(a.name));
  if (!asset) {
    throw new Error(`Aset installer untuk ${platform} tidak ditemukan di GitHub Release ${release.tag_name}.`);
  }

  const assetHeaders = {
    Accept: "application/octet-stream",
    "User-Agent": "OSISPaperless-QR-Portal",
  };
  if (process.env.GITHUB_TOKEN) {
    assetHeaders.Authorization = `Bearer ${process.env.GITHUB_TOKEN}`;
  }

  const upstream = await fetch(asset.url, { headers: assetHeaders, redirect: "follow" });
  if (!upstream.ok) {
    throw new Error(`Gagal mengunduh aset dari GitHub Release (HTTP ${upstream.status}).`);
  }
  return { upstream, filename: asset.name, contentType };
}

export default async function handler(req, res) {
  if (req.method !== "GET") {
    return res.status(405).send("Method Not Allowed");
  }

  const token = req.query?.token;
  const check = verifyDownloadToken(token);
  if (!check.ok) {
    res.setHeader("Content-Type", "text/html; charset=utf-8");
    return res.status(403).send(`<!doctype html>
<html lang="id"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="robots" content="noindex,nofollow"><title>Akses Ditolak — OSISPaperless</title>
<style>body{font-family:system-ui,sans-serif;background:#F4F7FC;color:#1E293B;display:grid;place-items:center;min-height:100vh;margin:0;padding:20px}
.card{background:#fff;border:1px solid #DCE6F7;border-radius:16px;padding:24px;max-width:420px;text-align:center}
h1{font-size:1.2rem;color:#B91C1C;margin:0 0 8px}p{font-size:.95rem;color:#64748B;line-height:1.5}</style></head>
<body><div class="card"><h1>Unduhan Ditolak (403)</h1><p>${check.reason}</p><p>File installer OSISPaperless hanya dapat diunduh melalui pindai <strong>Dynamic QR Code</strong> di Portal Resmi.</p></div></body></html>`);
  }

  if (check.payload.k !== "download") {
    return res.status(403).send("Token ini bukan token unduhan langsung.");
  }

  try {
    const resolved = await resolveInstallerStream(check.payload.p);
    if (!resolved) {
      res.setHeader("Content-Type", "text/html; charset=utf-8");
      return res.status(503).send(`<!doctype html>
<html lang="id"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Konfigurasi Installer — OSISPaperless</title>
<style>body{font-family:system-ui,sans-serif;background:#F4F7FC;color:#1E293B;display:grid;place-items:center;min-height:100vh;margin:0;padding:20px}
.card{background:#fff;border:1px solid #DCE6F7;border-radius:16px;padding:24px;max-width:460px}code{background:#EAF1FE;color:#4A7CE0;padding:2px 6px;border-radius:6px}</style></head>
<body><div class="card"><h2>Token QR Valid ✓</h2><p>Namun sumber file installer belum dihubungkan di Vercel Environment Variables.</p>
<p>Atur <code>GITHUB_REPO</code> (mis. <code>username/osis-paperless</code>) atau <code>APK_DOWNLOAD_URL</code> di pengaturan proyek Vercel Anda.</p></div></body></html>`);
    }

    const { upstream, filename, contentType } = resolved;
    res.setHeader("Content-Type", contentType);
    res.setHeader("Content-Disposition", `attachment; filename="${filename}"`);
    res.setHeader("Cache-Control", "no-store, max-age=0");
    const len = upstream.headers.get("content-length");
    if (len) res.setHeader("Content-Length", len);

    const buf = Buffer.from(await upstream.arrayBuffer());
    return res.status(200).send(buf);
  } catch (err) {
    return res.status(502).send(`Gagal menyiapkan unduhan: ${err.message}`);
  }
}
