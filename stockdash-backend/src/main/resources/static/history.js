const titleEl = document.getElementById("history-title");
const metaEl = document.getElementById("history-meta");
const chartWrap = document.getElementById("history-chart-wrap");
const canvas = document.getElementById("history-chart");
const tooltip = document.getElementById("chart-tooltip");

const money = new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" });
let lastSeries = [];
let hoverIndex = -1;

function symbolFromQuery() {
  const url = new URL(window.location.href);
  return (url.searchParams.get("symbol") || "").trim().toUpperCase();
}

async function loadHistory() {
  const symbol = symbolFromQuery();
  if (!symbol) {
    titleEl.textContent = "Missing Symbol";
    metaEl.textContent = "No symbol was provided in the URL.";
    return;
  }

  titleEl.textContent = symbol;
  metaEl.textContent = "Loading history...";

  try {
    const res = await fetch(`/api/portfolio/prices/history?symbol=${encodeURIComponent(symbol)}`);
    const data = await res.json();
    if (!res.ok) throw new Error(data.message || "Failed to load history.");

    metaEl.textContent = `${data.length} daily close record(s)`;
    if (!data.length) {
      chartWrap.innerHTML = "<div class='meta'>No stored historical prices found for this symbol.</div>";
      return;
    }
    lastSeries = [...data].reverse();
    drawChart(lastSeries);
  } catch (err) {
    metaEl.textContent = `Error: ${err.message}`;
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

void loadHistory();
