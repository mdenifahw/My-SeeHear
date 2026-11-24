// ── Accessible ARIA validation sync (validate-input-after-interaction) ──
const linkError = (el, shown) => {
  const errorId = el.dataset.error;
  if (!errorId) return;
  const ids = (el.getAttribute("aria-describedby") || "")
    .split(/\s+/)
    .filter((id) => id && id !== errorId);
  if (shown) ids.push(errorId);
  if (ids.length) el.setAttribute("aria-describedby", ids.join(" "));
  else el.removeAttribute("aria-describedby");
};

const announcer = document.createElement("div");
announcer.setAttribute("aria-live", "polite");
announcer.classList.add("visually-hidden");
document.body.append(announcer);

let announceTimer;
const flagged = new WeakSet();
const syncAria = (el, announce = false) => {
  if (!el.matches?.("input, select, textarea")) return;
  const invalid = el.matches(":user-invalid");
  if (invalid) {
    el.setAttribute("aria-invalid", "true");
    linkError(el, true);
    flagged.add(el);
    if (announce) {
      const errNode = document.getElementById(el.dataset.error);
      if (errNode) {
        clearTimeout(announceTimer);
        announcer.textContent = "";
        announceTimer = setTimeout(() => {
          announcer.textContent = errNode.textContent.trim();
        }, 450);
      }
    }
  } else if (flagged.has(el) || el.getAttribute("aria-invalid") !== "true") {
    el.setAttribute("aria-invalid", "false");
    linkError(el, false);
    flagged.delete(el);
  }
};

document.querySelectorAll("input, select, textarea").forEach((el) => syncAria(el));
document.addEventListener("blur", (e) => syncAria(e.target, true), true);
document.addEventListener("input", (e) => {
  if (e.target.hasAttribute("aria-invalid")) syncAria(e.target);
});

// ── Accessible Tabs (tabs guide: aria-selected, hidden="until-found", beforematch, arrow keys) ──
const tabs = [...document.querySelectorAll('[role="tab"]')];
const panels = {
  "tab-mobile": document.getElementById("panel-mobile"),
  "tab-desktop": document.getElementById("panel-desktop"),
};

let activePlatform = "android";
let activeSessionCode = "";
let desktopChallengeToken = "";
let countdownInterval = null;

function selectTab(targetTab, moveFocus = false) {
  for (const tab of tabs) {
    const selected = tab === targetTab;
    tab.setAttribute("aria-selected", String(selected));
    tab.tabIndex = selected ? 0 : -1;
    const panel = panels[tab.id];
    if (selected) {
      panel.removeAttribute("hidden");
    } else {
      panel.setAttribute("hidden", "until-found");
    }
  }
  if (moveFocus) targetTab.focus();
  activePlatform = targetTab.id === "tab-desktop" ? "desktop" : "android";
  if (activeSessionCode) {
    requestQr(activeSessionCode, activePlatform);
  }
}

tabs.forEach((tab, idx) => {
  tab.addEventListener("click", () => selectTab(tab));
  tab.addEventListener("keydown", (e) => {
    if (e.key === "ArrowRight") {
      e.preventDefault();
      selectTab(tabs[(idx + 1) % tabs.length], true);
    } else if (e.key === "ArrowLeft") {
      e.preventDefault();
      selectTab(tabs[(idx - 1 + tabs.length) % tabs.length], true);
    } else if (e.key === "Home") {
      e.preventDefault();
      selectTab(tabs[0], true);
    } else if (e.key === "End") {
      e.preventDefault();
      selectTab(tabs[tabs.length - 1], true);
    }
  });
});

Object.entries(panels).forEach(([tabId, panel]) => {
  panel.addEventListener("beforematch", () => selectTab(document.getElementById(tabId)));
});

// ── QR Token Generator & Countdown ──
const sessionForm = document.getElementById("session-form");
const sessionInput = document.getElementById("session-code");
const generateBtn = document.getElementById("generate-btn");
const formStatus = document.getElementById("form-status");
const mobileStage = document.getElementById("mobile-qr-stage");
const desktopStage = document.getElementById("desktop-qr-stage");
const verifyBtn = document.getElementById("verify-btn");
const desktopVerifyForm = document.getElementById("desktop-verify-form");
const verifyInput = document.getElementById("verify-code");
const desktopStatus = document.getElementById("desktop-status");

function setNotice(el, message, type = "") {
  if (!message) {
    el.hidden = true;
    return;
  }
  el.hidden = false;
  el.className = `notice ${type}`.trim();
  el.textContent = message;
}

function formatMMSS(ms) {
  const sec = Math.max(0, Math.floor(ms / 1000));
  const m = String(Math.floor(sec / 60)).padStart(2, "0");
  const s = String(sec % 60).padStart(2, "0");
  return `${m}:${s}`;
}

async function requestQr(sessionCode, platform) {
  generateBtn.disabled = true;
  setNotice(formStatus, "Membuat Dynamic QR Code terenkripsi…");

  try {
    const res = await fetch("/api/qr-token", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ sessionCode, platform }),
    });
    const data = await res.json();
    if (!res.ok) {
      setNotice(formStatus, data.error || "Kode sesi ditolak.", "error");
      return;
    }

    activeSessionCode = data.sessionCode;
    setNotice(formStatus, `QR Code aktif untuk Kode Sesi ${data.sessionCode}. Berlaku 5 menit.`, "success");

    const stage = platform === "desktop" ? desktopStage : mobileStage;
    stage.innerHTML = `
      <div class="qr-box" aria-label="Dynamic QR Code ${platform}">${data.qrSvg}</div>
      <div class="timer" id="qr-timer">Berlaku: 05:00</div>
      <button type="button" class="btn secondary" id="refresh-qr-btn">Perbarui QR Code</button>
    `;

    if (platform === "desktop") {
      desktopChallengeToken = data.challengeToken;
      verifyBtn.disabled = false;
      setNotice(desktopStatus, "");
    }

    document.getElementById("refresh-qr-btn").addEventListener("click", () => {
      requestQr(activeSessionCode, platform);
    });

    clearInterval(countdownInterval);
    const timerEl = document.getElementById("qr-timer");
    const updateTimer = () => {
      const rem = data.expiresAt - Date.now();
      if (rem <= 0) {
        clearInterval(countdownInterval);
        timerEl.textContent = "QR Code kedaluwarsa — klik Perbarui QR Code";
        timerEl.style.color = "var(--danger)";
        if (platform === "desktop") verifyBtn.disabled = true;
      } else {
        timerEl.textContent = `Berlaku: ${formatMMSS(rem)}`;
      }
    };
    updateTimer();
    countdownInterval = setInterval(updateTimer, 1000);
  } catch {
    setNotice(formStatus, "Gagal menghubungi server portal. Periksa koneksi internet Anda.", "error");
  } finally {
    generateBtn.disabled = false;
  }
}

sessionForm.addEventListener("submit", (e) => {
  e.preventDefault();
  if (!sessionInput.checkValidity()) {
    sessionInput.focus();
    syncAria(sessionInput, true);
    return;
  }
  requestQr(sessionInput.value.trim().toUpperCase(), activePlatform);
});

desktopVerifyForm.addEventListener("submit", async (e) => {
  e.preventDefault();
  if (!verifyInput.checkValidity()) {
    verifyInput.focus();
    syncAria(verifyInput, true);
    return;
  }
  if (!desktopChallengeToken) {
    setNotice(desktopStatus, "Buat QR Desktop terlebih dahulu.", "error");
    return;
  }
  verifyBtn.disabled = true;
  setNotice(desktopStatus, "Memverifikasi kode 6 digit…");
  try {
    const res = await fetch("/api/verify-desktop", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        challengeToken: desktopChallengeToken,
        verifyCode: verifyInput.value.trim(),
      }),
    });
    const data = await res.json();
    if (!res.ok) {
      setNotice(desktopStatus, data.error || "Kode verifikasi salah.", "error");
      verifyBtn.disabled = false;
      return;
    }
    setNotice(desktopStatus, "Verifikasi berhasil! Unduhan installer Desktop sedang dimulai…", "success");
    window.location.assign(data.downloadUrl);
  } catch {
    setNotice(desktopStatus, "Gagal memverifikasi kode.", "error");
    verifyBtn.disabled = false;
  }
});
