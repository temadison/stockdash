const summaryDate = document.getElementById("summary-date");
const loadSummaryBtn = document.getElementById("load-summary");
const summaryMeta = document.getElementById("summary-meta");
const summaryGrid = document.getElementById("summary-grid");

const uploadForm = document.getElementById("upload-form");
const csvFile = document.getElementById("csv-file");
const uploadResult = document.getElementById("upload-result");

const syncForm = document.getElementById("sync-form");
const syncStocks = document.getElementById("sync-stocks");
const syncSummary = document.getElementById("sync-summary");
const syncTable = document.getElementById("sync-table");
const demoBanner = document.querySelector(".demo-banner");

const demoData = window.StockdashDemoData || null;
const money = new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" });
let summaryRequestToken = 0;

function setDemoMode(active) {
  if (!demoBanner) return;
  demoBanner.style.display = active ? "" : "none";
}

function todayIso() {
  const d = new Date();
  const month = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${d.getFullYear()}-${month}-${day}`;
}

function statusClass(status) {
  if (["stored", "already_up_to_date", "no_new_rows"].includes(status)) return "status-ok";
  if (["rate_limited", "no_data", "no_purchase_history"].includes(status)) return "status-warn";
  return "status-bad";
}

async function fetchJson(url, options) {
  const res = await fetch(url, options);
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

async function fetchFromApiOrDemo(url, options, demoLoader) {
  try {
    const data = await fetchJson(url, options);
    setDemoMode(false);
    return { data, fromDemo: false };
  } catch (err) {
    if (!demoData || !demoLoader) throw err;
    setDemoMode(true);
    return { data: demoLoader(), fromDemo: true };
  }
}

async function loadSummary() {
  const requestToken = ++summaryRequestToken;
  const date = summaryDate.value;
  const url = date ? `/api/portfolio/daily-summary?date=${encodeURIComponent(date)}` : "/api/portfolio/daily-summary";
  summaryMeta.textContent = "Loading...";
  summaryGrid.innerHTML = "";
  loadSummaryBtn.disabled = true;

  try {
    const { data, fromDemo } = await fetchFromApiOrDemo(url, undefined, () => demoData.dailySummary(date));
    if (requestToken !== summaryRequestToken) return;

    summaryMeta.textContent = `${data.length} account snapshot(s)${fromDemo ? " · Demo data" : ""}`;
    if (!data.length) {
      summaryGrid.innerHTML = "<div class='meta'>No portfolio data found for this date.</div>";
      return;
    }

    for (const snapshot of data) {
      const card = document.createElement("article");
      card.className = "account";
      const accountUrl = `/performance.html?account=${encodeURIComponent(snapshot.accountName)}`;
      card.innerHTML = `
        <h3><a class="account-link" href="${accountUrl}">${snapshot.accountName}</a></h3>
        <div class="total">${money.format(snapshot.totalValue)}</div>
        <table>
          <thead>
            <tr><th>Symbol</th><th class="num">Qty</th><th class="num">Price</th><th class="num">Value</th></tr>
          </thead>
          <tbody></tbody>
        </table>
      `;
      const tbody = card.querySelector("tbody");
      for (const p of snapshot.positions) {
        const row = document.createElement("tr");
        const historyUrl = `/history.html?symbol=${encodeURIComponent(p.symbol)}`;
        row.innerHTML = `
          <td><a href="${historyUrl}" class="symbol-link">${p.symbol}</a></td>
          <td class="num">${p.quantity}</td>
          <td class="num">${money.format(p.currentPrice)}</td>
          <td class="num">${money.format(p.marketValue)}</td>
        `;
        tbody.appendChild(row);
      }
      summaryGrid.appendChild(card);
    }
  } catch (err) {
    if (requestToken !== summaryRequestToken) return;
    summaryMeta.textContent = `Error: ${err.message}`;
  } finally {
    if (requestToken === summaryRequestToken) {
      loadSummaryBtn.disabled = false;
    }
  }
}

async function loadDefaultSyncSymbols() {
  try {
    const { data } = await fetchFromApiOrDemo(
      "/api/portfolio/symbols",
      undefined,
      () => (typeof demoData?.defaultSymbols === "function" ? demoData.defaultSymbols() : [])
    );
    if (Array.isArray(data) && data.length) {
      syncStocks.value = data.join(", ");
    }
  } catch (err) {
    // Keep placeholder when no symbol source is available.
  }
}

async function uploadCsv(event) {
  event.preventDefault();
  uploadResult.textContent = "";
  const file = csvFile.files?.[0];
  if (!file) {
    uploadResult.textContent = "Select a CSV file first.";
    return;
  }

  const form = new FormData();
  form.append("file", file);

  try {
    const { data, fromDemo } = await fetchFromApiOrDemo(
      "/api/portfolio/transactions/upload",
      { method: "POST", body: form },
      () => demoData.uploadUnavailable()
    );
    uploadResult.textContent = JSON.stringify(data, null, 2);
    if (fromDemo) {
      uploadResult.textContent = `${uploadResult.textContent}\n\nDemo mode note: live CSV upload is disabled on static hosting.`;
      return;
    }
    if (data) {
      await loadSummary();
      await loadDefaultSyncSymbols();
    }
  } catch (err) {
    uploadResult.textContent = err.message;
  }
}

async function runSync(event) {
  event.preventDefault();
  syncSummary.textContent = "";
  syncTable.innerHTML = "";
  const stocks = syncStocks.value
    .split(",")
    .map((s) => s.trim())
    .filter(Boolean);

  if (!stocks.length) {
    syncSummary.textContent = "Enter at least one symbol.";
    return;
  }

  try {
    const { data, fromDemo } = await fetchFromApiOrDemo(
      "/api/portfolio/prices/sync",
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ stocks }),
      },
      () => demoData.sync(stocks)
    );

    syncSummary.textContent = `${data.pricesStored} new prices stored across ${data.symbolsWithPurchases}/${data.symbolsRequested} symbols with purchases.${fromDemo ? " (Demo mode)" : ""}`;
    const statuses = data.statusBySymbol || {};
    const stored = data.storedBySymbol || {};
    const symbols = Array.from(new Set([...Object.keys(statuses), ...Object.keys(stored), ...(data.skippedSymbols || [])]));
    symbols.sort();

    const table = document.createElement("table");
    table.innerHTML = `
      <thead>
        <tr><th>Symbol</th><th class="num">Inserted</th><th>Status</th></tr>
      </thead>
      <tbody></tbody>
    `;

    const tbody = table.querySelector("tbody");
    for (const symbol of symbols) {
      const status = statuses[symbol] || (data.skippedSymbols?.includes(symbol) ? "no_purchase_history" : "unknown");
      const count = stored[symbol] ?? 0;
      const row = document.createElement("tr");
      row.innerHTML = `
        <td>${symbol}</td>
        <td class="num">${count}</td>
        <td class="${statusClass(status)}">${status}</td>
      `;
      tbody.appendChild(row);
    }
    syncTable.appendChild(table);
  } catch (err) {
    syncSummary.textContent = `Error: ${err.message}`;
  }
}

summaryDate.value = todayIso();
setDemoMode(false);
loadSummaryBtn.addEventListener("click", loadSummary);
uploadForm.addEventListener("submit", uploadCsv);
syncForm.addEventListener("submit", runSync);

void loadDefaultSyncSymbols();
void loadSummary();
