const titleEl = document.getElementById("perf-title");
const perfStart = document.getElementById("perf-start");
const perfEnd = document.getElementById("perf-end");
const loadBtn = document.getElementById("load-performance");
const perfMeta = document.getElementById("perf-meta");
const perfLegend = document.getElementById("perf-legend");
const perfCanvas = document.getElementById("performance-chart");

const money = new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" });
let lastPoints = [];

function todayIso() {
  const d = new Date();
  const month = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${d.getFullYear()}-${month}-${day}`;
}

function getAccountParam() {
  const url = new URL(window.location.href);
  const account = (url.searchParams.get("account") || "TOTAL").trim();
  return account || "TOTAL";
}

function formatDateLabel(isoDate) {
  const parsed = new Date(`${isoDate}T00:00:00`);
  return parsed.toLocaleDateString("en-US", { month: "short", day: "numeric", year: "numeric" });
}

function seriesColor(index) {
  const palette = ["#0f766e", "#2563eb", "#d97706", "#be185d", "#16a34a", "#9333ea", "#0284c7", "#ca8a04", "#7c3aed", "#ef4444"];
  return palette[index % palette.length];
}

function drawPerformanceChart(points) {
  const ctx = perfCanvas.getContext("2d");
  const dpr = window.devicePixelRatio || 1;
  const cssWidth = perfCanvas.clientWidth;
  const cssHeight = perfCanvas.clientHeight;
  perfCanvas.width = Math.floor(cssWidth * dpr);
  perfCanvas.height = Math.floor(cssHeight * dpr);
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  ctx.clearRect(0, 0, cssWidth, cssHeight);

  const pad = { top: 18, right: 18, bottom: 34, left: 60 };
  const w = cssWidth - pad.left - pad.right;
  const h = cssHeight - pad.top - pad.bottom;
  if (!points.length || w <= 0 || h <= 0) return;

  const symbols = Array.from(new Set(points.flatMap((p) => p.stocks.map((s) => s.symbol)))).sort();
  const totals = points.map((p) => Number(p.totalValue));
  const max = Math.max(...totals, 1);
  const xAt = (i) => pad.left + (i / Math.max(points.length - 1, 1)) * w;
  const yAt = (value) => pad.top + (1 - value / max) * h;

  ctx.strokeStyle = "#d6ddd4";
  ctx.lineWidth = 1;
  ctx.beginPath();
  ctx.moveTo(pad.left, pad.top);
  ctx.lineTo(pad.left, pad.top + h);
  ctx.lineTo(pad.left + w, pad.top + h);
  ctx.stroke();

  ctx.fillStyle = "#5f6f64";
  ctx.font = "12px Avenir Next, sans-serif";
  ctx.textAlign = "right";
  for (let i = 0; i < 5; i++) {
    const ratio = i / 4;
    const value = max * (1 - ratio);
    const y = pad.top + ratio * h;
    ctx.fillText(money.format(value), pad.left - 8, y + 4);
    if (i > 0 && i < 4) {
      ctx.beginPath();
      ctx.moveTo(pad.left, y);
      ctx.lineTo(pad.left + w, y);
      ctx.strokeStyle = "rgba(214,221,212,0.6)";
      ctx.stroke();
    }
  }

  ctx.textAlign = "left";
  ctx.fillText(formatDateLabel(points[0].date), pad.left, pad.top + h + 18);
  ctx.textAlign = "center";
  ctx.fillText(formatDateLabel(points[Math.floor((points.length - 1) / 2)].date), pad.left + w / 2, pad.top + h + 18);
  ctx.textAlign = "right";
  ctx.fillText(formatDateLabel(points[points.length - 1].date), pad.left + w, pad.top + h + 18);

  const valuesBySymbol = new Map();
  for (const symbol of symbols) valuesBySymbol.set(symbol, points.map(() => 0));
  points.forEach((point, idx) => {
    point.stocks.forEach((s) => {
      valuesBySymbol.get(s.symbol)[idx] = Number(s.marketValue);
    });
  });

  let baseline = points.map(() => 0);
  symbols.forEach((symbol, symbolIndex) => {
    const vals = valuesBySymbol.get(symbol);
    const top = vals.map((v, i) => v + baseline[i]);
    const color = seriesColor(symbolIndex);

    ctx.beginPath();
    top.forEach((v, i) => {
      const x = xAt(i);
      const y = yAt(v);
      if (i === 0) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    });
    for (let i = points.length - 1; i >= 0; i--) {
      ctx.lineTo(xAt(i), yAt(baseline[i]));
    }
    ctx.closePath();
    ctx.fillStyle = `${color}44`;
    ctx.fill();

    ctx.beginPath();
    top.forEach((v, i) => {
      const x = xAt(i);
      const y = yAt(v);
      if (i === 0) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    });
    ctx.strokeStyle = color;
    ctx.lineWidth = 1.5;
    ctx.stroke();

    baseline = top;
  });

  perfLegend.innerHTML = symbols
    .map((symbol, i) => `<span class="legend-item"><span class="legend-dot" style="background:${seriesColor(i)}"></span>${symbol}</span>`)
    .join("");
}

async function loadPerformance() {
  const account = getAccountParam();
  const params = new URLSearchParams();
  params.set("account", account);
  if (perfStart.value) params.set("startDate", perfStart.value);
  if (perfEnd.value) params.set("endDate", perfEnd.value);
  const url = `/api/portfolio/performance?${params.toString()}`;
  perfMeta.textContent = "Loading chart...";
  loadBtn.disabled = true;

  try {
    const res = await fetch(url);
    const data = await res.json();
    if (!res.ok) throw new Error(data.message || "Failed to load performance.");
    lastPoints = data;
    if (!data.length) {
      perfMeta.textContent = "No performance data available for this account/range.";
      perfLegend.innerHTML = "";
      const ctx = perfCanvas.getContext("2d");
      ctx.clearRect(0, 0, perfCanvas.clientWidth, perfCanvas.clientHeight);
      return;
    }
    perfMeta.textContent = `${data.length} day(s) shown`;
    drawPerformanceChart(data);
  } catch (err) {
    perfMeta.textContent = `Error: ${err.message}`;
  } finally {
    loadBtn.disabled = false;
  }
}

window.addEventListener("resize", () => {
  if (lastPoints.length) drawPerformanceChart(lastPoints);
});

const account = getAccountParam();
titleEl.textContent = account === "TOTAL" ? "Total Portfolio" : `${account} Performance`;
perfEnd.value = todayIso();
loadBtn.addEventListener("click", loadPerformance);

void loadPerformance();
