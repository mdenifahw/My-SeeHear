import http from "node:http";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import qrTokenHandler from "./api/qr-token.js";
import downloadHandler from "./api/download.js";
import verifyDesktopHandler from "./api/verify-desktop.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const publicDir = path.join(__dirname, "public");
const PORT = Number(process.env.PORT || 3000);

const MIME = {
  ".html": "text/html; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".js": "application/javascript; charset=utf-8",
  ".txt": "text/plain; charset=utf-8",
  ".svg": "image/svg+xml",
};

function enhanceRes(res) {
  res.status = (code) => {
    res.statusCode = code;
    return res;
  };
  res.json = (obj) => {
    res.setHeader("Content-Type", "application/json; charset=utf-8");
    res.end(JSON.stringify(obj));
  };
  res.send = (body) => {
    res.end(body);
  };
  return res;
}

const server = http.createServer(async (req, res) => {
  enhanceRes(res);
  const url = new URL(req.url, `http://localhost:${PORT}`);
  req.query = Object.fromEntries(url.searchParams.entries());

  if (url.pathname.startsWith("/api/")) {
    const chunks = [];
    for await (const c of req) chunks.push(c);
    req.body = Buffer.concat(chunks).toString("utf8");
    if (url.pathname === "/api/qr-token") return qrTokenHandler(req, res);
    if (url.pathname === "/api/download") return downloadHandler(req, res);
    if (url.pathname === "/api/verify-desktop") return verifyDesktopHandler(req, res);
    return res.status(404).json({ error: "API tidak ditemukan" });
  }

  const rel = url.pathname === "/" ? "index.html" : url.pathname.replace(/^\/+/, "");
  const filePath = path.join(publicDir, rel);
  if (fs.existsSync(filePath) && fs.statSync(filePath).isFile()) {
    res.setHeader("Content-Type", MIME[path.extname(filePath)] || "application/octet-stream");
    res.setHeader("X-Robots-Tag", "noindex, nofollow");
    return fs.createReadStream(filePath).pipe(res);
  }
  res.status(404).send("Not Found");
});

server.listen(PORT, () => {
  console.log(`OSISPaperless QR Portal running at http://localhost:${PORT}`);
});
