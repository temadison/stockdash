const titleEl = document.getElementById("history-title");
const metaEl = document.getElementById("history-meta");
const summaryEl = document.getElementById("history-summary");
const chartWrap = document.getElementById("history-chart-wrap");
const canvas = document.getElementById("history-chart");
const tooltip = document.getElementById("chart-tooltip");
const demoBanner = document.querySelector(".demo-banner");

const demoData = window.StockdashDemoData || null;
const money = new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" });
const percent = new Intl.NumberFormat("en-US", { style: "percent", minimumFractionDigits: 2, maximumFractionDigits: 2 });
let lastSeries = [];
let hoverIndex = -1;

function setDemoMode(active) {
  if (!demoBanner) return;
  demoBanner.style.display = active ? "" : "none";
}

function symbolFromQuery() {
  const url = new URL(window.location.href);
  return (url.searchParams.get("symbol") || "").trim().toUpperCase();
}

function dateRangeFromQuery() {
  const url = new URL(window.location.href);
  const startDate = (url.searchParams.get("startDate") || "").trim();
  const endDate = (url.searchParams.get("endDate") || "").trim();
  return { startDate, endDate };
}

function accountFromQuery() {
  const url = new URL(window.location.href);
  return (url.searchParams.get("account") || "").trim();
}

function buildHistoryApiUrl(symbol, startDate, endDate) {
  const params = new URLSearchParams();
  params.set("symbol", symbol);
  if (startDate) params.set("startDate", startDate);
  if (endDate) params.set("endDate", endDate);
  return `/api/portfolio/prices/history?${params.toString()}`;
}

async function fetchJson(url) {
  const res = await fetch(url);
  let data = null;
  try {
    data = await res.json();
  } catch (err) {
    data = null;
  }
  if (!res.ok) {
    const msg = data?.detail || data?.message || `Request failed (${res.status})`;
    throw new Error(msg);
  }
  return data;
}

function daysBetween(startIso, endIso) {
  const start = new Date(`${startIso}T00:00:00`);
  const end = new Date(`${endIso}T00:00:00`);
  return Math.max(0, Math.round((end.getTime() - start.getTime()) / 86400000));
}

function formatSignedMoney(value) {
  if (value === 0) return money.format(0);
  return `${value > 0 ? "+" : "-"}${money.format(Math.abs(value))}`;
}

function formatSignedPercent(value) {
  if (value === null || Number.isNaN(value)) return "N/A";
  if (value === 0) return percent.format(0);
  return `${value > 0 ? "+" : "-"}${percent.format(Math.abs(value))}`;
}

function gainLossClass(value) {
  if (value > 0) return "status-ok";
  if (value < 0) return "status-bad";
  return "";
}

function renderSummary(series) {
  if (!series.length) {
    summaryEl.innerHTML = "";
    return;
  }

  const startPrice = Number(series[0].closePrice);
  const endPrice = Number(series[series.length - 1].closePrice);
  const net = endPrice - startPrice;
  const totalReturn = startPrice > 0 ? net / startPrice : null;

  const elapsedDays = daysBetween(series[0].date, series[series.length - 1].date);
  const years = elapsedDays / 365.2425;
  const cagr = startPrice > 0 && endPrice > 0 && years > 0 ? Math.pow(endPrice / startPrice, 1 / years) - 1 : null;

  summaryEl.innerHTML = `
    <article class="summary-card">
      <div class="summary-label">Net Gain/Loss</div>
      <div class="summary-value ${gainLossClass(net)}">${formatSignedMoney(net)}</div>
    </article>
    <article class="summary-card">
      <div class="summary-label">Return</div>
      <div class="summary-value ${gainLossClass(totalReturn ?? 0)}">${formatSignedPercent(totalReturn)}</div>
    </article>
    <article class="summary-card">
      <div class="summary-label">CAGR</div>
      <div class="summary-value ${gainLossClass(cagr ?? 0)}">${formatSignedPercent(cagr)}</div>
    </article>
  `;
}

async function loadHistory() {
  const symbol = symbolFromQuery();
  const { startDate, endDate } = dateRangeFromQuery();
  const account = accountFromQuery();
  if (!symbol) {
    titleEl.textContent = "Missing Symbol";
    metaEl.textContent = "No symbol was provided in the URL.";
    return;
  }

  titleEl.textContent = symbol;
  metaEl.textContent = "Loading history...";

  try {
    const apiUrl = buildHistoryApiUrl(symbol, startDate, endDate);
    let fromDemo = false;
    let data;
    try {
      data = await fetchJson(apiUrl);
      setDemoMode(false);
    } catch (err) {
      if (!demoData) throw err;
      data = demoData.history(symbol, startDate || undefined, endDate || undefined);
      fromDemo = true;
      setDemoMode(true);
    }

    const rangeLabel = startDate && endDate
      ? ` (${formatDateLabel(startDate)} to ${formatDateLabel(endDate)})`
      : "";
    const accountLabel = account ? ` for ${account}` : "";
    metaEl.textContent = `${data.length} daily close record(s)${accountLabel}${rangeLabel}${fromDemo ? " · Demo data" : ""}`;
    if (!data.length) {
      renderSummary([]);
      chartWrap.innerHTML = "<div class='meta'>No stored historical prices found for this symbol.</div>";
      return;
    }
    lastSeries = [...data].reverse();
    renderSummary(lastSeries);
    drawChart(lastSeries);
  } catch (err) {
    metaEl.textContent = `Error: ${err.message}`;
    renderSummary([]);
  }
}

function drawChart(series) {
  const ctx = canvas.getContext("2d");
  const dpr = window.devicePixelRatio || 1;
  const cssWidth = canvas.clientWidth;
  const cssHeight = canvas.clientHeight;
  canvas.width = Math.floor(cssWidth * dpr);
  canvas.height = Math.floor(cssHeight * dpr);
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  ctx.clearRect(0, 0, cssWidth, cssHeight);

  const pad = { top: 18, right: 16, bottom: 34, left: 58 };
  const w = cssWidth - pad.left - pad.right;
  const h = cssHeight - pad.top - pad.bottom;
  if (w <= 0 || h <= 0 || !series.length) return;

  const prices = series.map((d) => Number(d.closePrice));
  const min = Math.min(...prices);
  const max = Math.max(...prices);
  const spread = Math.max(max - min, 0.01);

  const xAt = (i) => pad.left + (i / Math.max(series.length - 1, 1)) * w;
  const yAt = (p) => pad.top + (1 - (p - min) / spread) * h;

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
  const yTicks = 5;
  for (let i = 0; i < yTicks; i++) {
    const ratio = i / (yTicks - 1);
    const value = max - ratio * spread;
    const y = pad.top + ratio * h;
    ctx.fillText(money.format(value), pad.left - 8, y + 4);
    if (i > 0 && i < yTicks - 1) {
      ctx.beginPath();
      ctx.moveTo(pad.left, y);
      ctx.lineTo(pad.left + w, y);
      ctx.strokeStyle = "rgba(214,221,212,0.6)";
      ctx.lineWidth = 1;
      ctx.stroke();
    }
  }

  const startLabel = formatDateLabel(series[0].date);
  const endLabel = formatDateLabel(series[series.length - 1].date);
  ctx.textAlign = "left";
  ctx.fillText(startLabel, pad.left, pad.top + h + 18);
  ctx.textAlign = "right";
  ctx.fillText(endLabel, pad.left + w, pad.top + h + 18);
  if (series.length > 2) {
    const midIndex = Math.floor((series.length - 1) / 2);
    ctx.textAlign = "center";
    ctx.fillText(formatDateLabel(series[midIndex].date), xAt(midIndex), pad.top + h + 18);
  }

  ctx.fillStyle = "#3f4c44";
  ctx.font = "600 12px Avenir Next, sans-serif";
  ctx.textAlign = "center";
  ctx.fillText("Date", pad.left + w / 2, pad.top + h + 30);

  ctx.save();
  ctx.translate(16, pad.top + h / 2);
  ctx.rotate(-Math.PI / 2);
  ctx.textAlign = "center";
  ctx.fillText("Closing Price (USD)", 0, 0);
  ctx.restore();

  const gradient = ctx.createLinearGradient(0, pad.top, 0, pad.top + h);
  gradient.addColorStop(0, "rgba(15,118,110,0.24)");
  gradient.addColorStop(1, "rgba(15,118,110,0.02)");

  ctx.beginPath();
  series.forEach((point, i) => {
    const x = xAt(i);
    const y = yAt(Number(point.closePrice));
    if (i === 0) ctx.moveTo(x, y);
    else ctx.lineTo(x, y);
  });
  ctx.lineTo(xAt(series.length - 1), pad.top + h);
  ctx.lineTo(xAt(0), pad.top + h);
  ctx.closePath();
  ctx.fillStyle = gradient;
  ctx.fill();

  ctx.beginPath();
  series.forEach((point, i) => {
    const x = xAt(i);
    const y = yAt(Number(point.closePrice));
    if (i === 0) ctx.moveTo(x, y);
    else ctx.lineTo(x, y);
  });
  ctx.strokeStyle = "#0f766e";
  ctx.lineWidth = 2;
  ctx.stroke();

  if (hoverIndex >= 0 && hoverIndex < series.length) {
    const point = series[hoverIndex];
    const x = xAt(hoverIndex);
    const y = yAt(Number(point.closePrice));

    ctx.beginPath();
    ctx.moveTo(x, pad.top);
    ctx.lineTo(x, pad.top + h);
    ctx.strokeStyle = "rgba(11,95,89,0.4)";
    ctx.lineWidth = 1;
    ctx.stroke();

    ctx.beginPath();
    ctx.arc(x, y, 4, 0, Math.PI * 2);
    ctx.fillStyle = "#0b5f59";
    ctx.fill();
    ctx.strokeStyle = "#ffffff";
    ctx.lineWidth = 2;
    ctx.stroke();
  }
}

window.addEventListener("resize", () => {
  if (lastSeries.length) drawChart(lastSeries);
});

function setTooltip(point, x, y) {
  tooltip.innerHTML = `<strong>${formatDateLabel(point.date)}</strong><br>${money.format(point.closePrice)}`;
  tooltip.hidden = false;
  const wrapRect = chartWrap.getBoundingClientRect();
  const tipRect = tooltip.getBoundingClientRect();

  let left = x + 12;
  let top = y - tipRect.height - 10;
  if (left + tipRect.width > wrapRect.width - 8) left = x - tipRect.width - 12;
  if (left < 8) left = 8;
  if (top < 8) top = y + 10;
  if (top + tipRect.height > wrapRect.height - 8) top = wrapRect.height - tipRect.height - 8;

  tooltip.style.left = `${left}px`;
  tooltip.style.top = `${top}px`;
}

canvas.addEventListener("mousemove", (event) => {
  if (!lastSeries.length) return;
  const rect = canvas.getBoundingClientRect();
  const x = event.clientX - rect.left;
  const padLeft = 58;
  const padRight = 16;
  const usableWidth = rect.width - padLeft - padRight;
  const ratio = Math.min(1, Math.max(0, (x - padLeft) / Math.max(usableWidth, 1)));
  const index = Math.round(ratio * Math.max(lastSeries.length - 1, 0));
  hoverIndex = index;
  drawChart(lastSeries);

  const xAt = padLeft + (index / Math.max(lastSeries.length - 1, 1)) * usableWidth;
  const point = lastSeries[index];
  const prices = lastSeries.map((d) => Number(d.closePrice));
  const min = Math.min(...prices);
  const max = Math.max(...prices);
  const spread = Math.max(max - min, 0.01);
  const y = 18 + (1 - (Number(point.closePrice) - min) / spread) * (rect.height - 18 - 34);
  setTooltip(point, xAt, y);
});

canvas.addEventListener("mouseleave", () => {
  hoverIndex = -1;
  tooltip.hidden = true;
  if (lastSeries.length) drawChart(lastSeries);
});

function formatDateLabel(isoDate) {
  const parsed = new Date(`${isoDate}T00:00:00`);
  return parsed.toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
  });
}

setDemoMode(false);
void loadHistory();
