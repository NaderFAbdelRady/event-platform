/* ── State ──────────────────────────────────────────────── */
// Derive context root dynamically so it works regardless of deployment path
// e.g. /event-platform/modeler/index.html → CTX = /event-platform
const CTX        = location.pathname.split('/').slice(0, 2).join('/');
const API        = `${CTX}/api/modeler`;
const EVENTS_API = `${CTX}/api/events`;
const WS_URL     = `ws://${location.host}${CTX}/ws/platform/modeler-ui`;

let registry = [], dlqEntries = [], eventLog = [];
let throughputChart = null, selectedProvider = 'CLAUDE';
let lifecycleEvents = [], startTime = Date.now();

/* ── Boot sequence ──────────────────────────────────────── */
window.addEventListener('DOMContentLoaded', () => {
  const messages = ['Initializing runtime...', 'Connecting to engine...', 'Loading registry...', 'Ready.'];
  let i = 0;
  const tick = () => {
    if (i < messages.length) {
      document.getElementById('bootStatus').textContent = messages[i++];
      setTimeout(tick, 350);
    } else {
      setTimeout(() => {
        document.getElementById('bootOverlay').classList.add('fade');
        document.getElementById('shell').classList.add('visible');
        setTimeout(() => document.getElementById('bootOverlay').remove(), 600);
        init();
      }, 200);
    }
  };
  tick();
});

function init() {
  tickClock();
  setInterval(tickClock, 1000);
  loadComponents();
  refreshHeader();
  setInterval(refreshHeader, 8000);
  initWebSocket();
}

/* ── Clock ──────────────────────────────────────────────── */
function tickClock() {
  const now = new Date();
  document.getElementById('clock').textContent =
    now.toLocaleTimeString('en-GB', { hour12: false });
}

/* ── Navigation ─────────────────────────────────────────── */
function showScreen(name) {
  document.querySelectorAll('.screen').forEach(s => s.classList.remove('active'));
  document.querySelectorAll('.navbtn').forEach(b => b.classList.remove('active'));
  document.getElementById('screen-' + name).classList.add('active');
  document.querySelector(`[data-screen="${name}"]`).classList.add('active');
  if (name === 'components') loadComponents();
  if (name === 'engine')     loadEngine();
  if (name === 'dlq')        loadDlq();
  if (name === 'metrics')    loadMetrics();
}

/* ── Header ─────────────────────────────────────────────── */
async function refreshHeader() {
  try {
    const status = await fetch(`${API}/engine/status`).then(r => r.json());
    const dlq    = await fetch(`${API}/dlq`).then(r => r.json());

    const active = status.totalActive || 0;
    const failed = status.totalFailed || 0;
    const dqCount = Array.isArray(dlq) ? dlq.filter(e => !e.resolved).length : 0;

    document.getElementById('statActive').querySelector('.tstat-val').textContent = active;
    document.getElementById('statFailed').querySelector('.tstat-val').textContent = failed;
    document.getElementById('statDlq').querySelector('.tstat-val').textContent    = dqCount;
    document.getElementById('sfWatchDir').textContent = (status.watchDir || '—').split('/').pop() || status.watchDir;

    const dot   = document.getElementById('engineDot');
    const label = document.getElementById('engineLabel');
    dot.className   = 'engine-dot running';
    label.textContent = 'RUNNING';

    if (dqCount > 0) {
      document.getElementById('navBadgeDlq').textContent = dqCount;
      document.getElementById('navBadgeDlq').classList.remove('hidden');
    }
  } catch(e) {
    document.getElementById('engineDot').className = 'engine-dot error';
    document.getElementById('engineLabel').textContent = 'OFFLINE';
  }
}

/* ── Components ─────────────────────────────────────────── */
async function loadComponents() {
  try {
    const res = await fetch(`${API}/engine/registry`);
    registry  = await res.json();
    document.getElementById('navBadgeComponents').textContent = registry.length;
    renderComponentGrid(registry);
  } catch(e) { toast('Failed to load components', 'error'); }
}

function renderComponentGrid(data) {
  const grid = document.getElementById('componentsGrid');
  if (!data.length) {
    grid.innerHTML = `
      <div class="empty-state" style="grid-column:1/-1">
        <span class="empty-state-icon">▣</span>
        <span class="empty-state-text">No components loaded</span>
        <span class="empty-state-sub">Generate your first component using the AI pipeline, or drop a .java file into the watch directory</span>
        <button class="btn btn-primary" style="margin-top:8px" onclick="openGenerateModal()">＋ Generate First Component</button>
      </div>`;
    return;
  }

  grid.innerHTML = data.map(c => {
    const st = (c.status || 'DRAFT').toLowerCase();
    return `
    <div class="comp-card status-${st}">
      <div class="comp-card-head">
        <div>
          <div class="comp-name">${c.name}</div>
          <div class="comp-version">v${c.version || '1.0.0'}</div>
        </div>
        <div class="status-badge ${st}">
          <span class="status-dot"></span>
          ${c.status || 'DRAFT'}
        </div>
      </div>
      ${c.lastError ? `<div class="comp-error">⚠ ${c.lastError}</div>` : ''}
      <div class="comp-stats">
        <div>
          <div class="comp-stat-val">${fmtNum(c.eventsProcessed)}</div>
          <div class="comp-stat-lbl">PROCESSED</div>
        </div>
        <div>
          <div class="comp-stat-val">${c.avgProcessingTimeMs || 0}ms</div>
          <div class="comp-stat-lbl">AVG TIME</div>
        </div>
        <div>
          <div class="comp-stat-val">${fmtNum(c.eventsFailed)}</div>
          <div class="comp-stat-lbl">FAILED</div>
        </div>
        <div>
          <div class="comp-stat-val">${c.loadedAt ? relTime(c.loadedAt) : '—'}</div>
          <div class="comp-stat-lbl">AGO</div>
        </div>
      </div>
      <div class="comp-actions">
        <button class="btn btn-ghost btn-sm" onclick="reloadComponent('${c.name}')">↺ Reload</button>
        ${st === 'active' || st === 'failed'
          ? `<button class="btn btn-ghost btn-sm" onclick="unloadComponent('${c.name}')">⊘ Unload</button>`
          : `<button class="btn btn-ghost btn-sm" onclick="activateComponent('${c.name}')">▶ Activate</button>`
        }
      </div>
    </div>`;
  }).join('');
}

function filterComponents() {
  const q  = document.getElementById('searchComponents').value.toLowerCase();
  const st = document.getElementById('statusFilter').value;
  renderComponentGrid(registry.filter(c =>
    c.name.toLowerCase().includes(q) && (!st || c.status === st)
  ));
}

async function reloadComponent(name) {
  await fetch(`${API}/engine/components/${name}/reload`, { method: 'POST' });
  toast(`Reloading ${name}...`, 'info');
  addLifecycleEvent(name, 'RELOADED');
  setTimeout(loadComponents, 1200);
}

async function unloadComponent(name) {
  await fetch(`${API}/engine/components/${name}/unload`, { method: 'POST' });
  toast(`${name} unloaded`, 'info');
  addLifecycleEvent(name, 'UNLOADED');
  setTimeout(loadComponents, 600);
}

async function activateComponent(name) {
  await fetch(`${API}/engine/components/${name}/activate`, { method: 'POST' });
  toast(`${name} activated`, 'success');
  addLifecycleEvent(name, 'LOADED');
  setTimeout(loadComponents, 600);
}

/* ── Engine ─────────────────────────────────────────────── */
async function loadEngine() {
  try {
    const [status, reg] = await Promise.all([
      fetch(`${API}/engine/status`).then(r => r.json()),
      fetch(`${API}/engine/registry`).then(r => r.json())
    ]);

    document.getElementById('engineKpis').innerHTML = `
      <div class="kpi green"><div class="kpi-val">${status.totalActive||0}</div><div class="kpi-lbl">Active</div></div>
      <div class="kpi red"><div class="kpi-val">${status.totalFailed||0}</div><div class="kpi-lbl">Failed</div></div>
      <div class="kpi blue"><div class="kpi-val">${status.totalLoaded||0}</div><div class="kpi-lbl">Total Loaded</div></div>
      <div class="kpi cyan"><div class="kpi-val">${fmtUptime()}</div><div class="kpi-lbl">Session Uptime</div></div>`;

    document.getElementById('registryCount').textContent = reg.length + ' components';

    const tbody = document.getElementById('engineTable');
    if (!reg.length) {
      tbody.innerHTML = `<tr><td colspan="6"><div class="empty-state" style="padding:30px"><span class="empty-state-text">No components in registry</span></div></td></tr>`;
    } else {
      tbody.innerHTML = reg.map(c => {
        const st = (c.status||'DRAFT').toLowerCase();
        return `<tr>
          <td class="mono-cell">${c.name}</td>
          <td><span class="status-badge ${st}"><span class="status-dot"></span>${c.status}</span></td>
          <td class="mono-cell" style="color:var(--text2);font-size:.75rem">${c.loadedAt ? c.loadedAt.replace('T',' ').slice(0,19) : '—'}</td>
          <td class="mono-cell">${fmtNum(c.eventsProcessed)}</td>
          <td class="mono-cell">${c.avgProcessingTimeMs||0}ms</td>
          <td>
            <div style="display:flex;gap:6px">
              <button class="btn btn-ghost btn-sm" onclick="reloadComponent('${c.name}')">↺</button>
              <button class="btn btn-ghost btn-sm" onclick="unloadComponent('${c.name}')">⊘</button>
            </div>
          </td>
        </tr>`;
      }).join('');
    }

    renderLifecycleLog();
  } catch(e) { toast('Failed to load engine data', 'error'); }
}

async function reloadAll() {
  if (!confirm('Reload all components? In-flight events will be queued during reload.')) return;
  await fetch(`${API}/engine/reload-all`, { method: 'POST' });
  toast('Reloading all components...', 'info');
  setTimeout(loadEngine, 1500);
}

function addLifecycleEvent(component, event, detail) {
  lifecycleEvents.unshift({ time: new Date().toLocaleTimeString('en-GB'), component, event, detail });
  if (lifecycleEvents.length > 50) lifecycleEvents.pop();
  if (document.getElementById('screen-engine').classList.contains('active')) renderLifecycleLog();
}

function renderLifecycleLog() {
  const container = document.getElementById('lifecycleLog');
  if (!lifecycleEvents.length) {
    container.innerHTML = '<div class="log-empty">No lifecycle events yet</div>';
    return;
  }
  container.innerHTML = lifecycleEvents.map(e => `
    <div class="log-entry">
      <span class="log-time">${e.time}</span>
      <span class="log-comp">${e.component}</span>
      <span class="log-event ${e.event}">${e.event}</span>
      ${e.detail ? `<span class="log-detail">${e.detail}</span>` : ''}
    </div>`).join('');
}

/* ── Events ─────────────────────────────────────────────── */
function openFireModal() {
  document.getElementById('fireModal').classList.add('open');
}

async function fireEvent() {
  const type   = document.getElementById('fireType').value.trim();
  const source = document.getElementById('fireSource').value;
  const user   = document.getElementById('fireUser').value.trim();
  let payload  = {};
  try { payload = JSON.parse(document.getElementById('firePayload').value); }
  catch(e) { toast('Invalid JSON payload', 'error'); return; }

  try {
    const res = await fetch(`${EVENTS_API}/fire`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ eventType: type, source, payload, context: { userId: user } })
    });

    // Always try to parse JSON — even error responses return JSON now
    let data;
    try { data = await res.json(); }
    catch(jsonErr) {
      // Server returned non-JSON (e.g. a WildFly HTML error page)
      const text = await res.text().catch(() => 'No response body');
      toast(`Server error ${res.status}: ${text.slice(0, 120)}`, 'error');
      return;
    }

    if (!res.ok) {
      toast(`Fire failed (${res.status}): ${data.error || JSON.stringify(data)}`, 'error');
      return;
    }

    appendEventRow({ time: new Date().toLocaleTimeString('en-GB'), type, source, userId: user, status: 'fired', eventId: data.eventId });
    closeModal('fireModal');
    toast(`Event fired: ${type}`, 'success');
  } catch(e) { toast(`Network error: ${e.message}`, 'error'); }
}

function appendEventRow(e) {
  eventLog.unshift(e);
  if (eventLog.length > 200) eventLog.pop();

  const stream = document.getElementById('eventStream');
  const empty  = stream.querySelector('.stream-empty');
  if (empty) empty.remove();

  const row = document.createElement('div');
  row.className = 'stream-row';
  row.innerHTML = `
    <span class="sr-time">${e.time}</span>
    <span class="sr-type">${e.type}</span>
    <span class="sr-source"><span class="source-tag ${e.source}">${e.source}</span></span>
    <span class="sr-user">${e.userId||'—'}</span>
    <span class="sr-status"><span class="status-badge active" style="font-size:.65rem">✓ ${e.status||'ok'}</span></span>`;

  stream.insertBefore(row, stream.firstChild);
  if (stream.children.length > 100) stream.lastChild.remove();
}

/* ── DLQ ────────────────────────────────────────────────── */
async function loadDlq() {
  try {
    const res  = await fetch(`${API}/dlq`);
    dlqEntries = await res.json();
    renderDlq();
  } catch(e) { toast('Failed to load DLQ', 'error'); }
}

function renderDlq() {
  const active = dlqEntries.filter(e => !e.resolved);
  document.getElementById('dlqEmpty').style.display  = active.length ? 'none' : 'flex';
  document.getElementById('dlqPanel').style.display  = active.length ? 'block' : 'none';

  document.getElementById('dlqTable').innerHTML = active.map(e => `
    <tr>
      <td class="mono-cell" style="font-size:.75rem;color:var(--text2)">${(e.occurredAt||'').slice(0,19).replace('T',' ')}</td>
      <td class="mono-cell">${e.componentName}</td>
      <td><span class="source-tag EXTERNAL">${e.eventType}</span></td>
      <td class="mono-cell">${e.retryCount}</td>
      <td style="font-size:.75rem;color:var(--red);max-width:200px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap" title="${e.failureReason||''}">${(e.failureReason||'').slice(0,80)}</td>
      <td>
        <div style="display:flex;gap:6px">
          <button class="btn btn-ghost btn-sm" onclick="retryDlq('${e.id}')">↺ Retry</button>
          <button class="btn btn-danger-ghost btn-sm" onclick="resolveDlq('${e.id}')">✓ Resolve</button>
        </div>
      </td>
    </tr>`).join('');
}

async function retryDlq(id)   { await fetch(`${API}/dlq/${id}/retry`, { method:'POST' }); toast('Retrying event...', 'info'); loadDlq(); }
async function resolveDlq(id) { await fetch(`${API}/dlq/${id}/resolve`, { method:'POST' }); loadDlq(); }
async function retryAllDlq()  { await fetch(`${API}/dlq/retry-all`, { method:'POST' }); toast('Retrying all...', 'info'); loadDlq(); }
async function clearDlq()     { await fetch(`${API}/dlq`, { method:'DELETE' }); loadDlq(); }

/* ── Metrics ────────────────────────────────────────────── */
async function loadMetrics() {
  try {
    const [overview, components] = await Promise.all([
      fetch(`${API}/metrics/overview`).then(r => r.json()),
      fetch(`${API}/metrics/components`).then(r => r.json())
    ]);

    document.getElementById('metricsKpis').innerHTML = `
      <div class="kpi blue"><div class="kpi-val">${fmtNum(overview.totalEventsProcessed||0)}</div><div class="kpi-lbl">Total Processed</div></div>
      <div class="kpi red"><div class="kpi-val">${fmtNum(overview.totalEventsFailed||0)}</div><div class="kpi-lbl">Total Failed</div></div>
      <div class="kpi yellow"><div class="kpi-val">${overview.totalDeadLettered||0}</div><div class="kpi-lbl">Dead Lettered</div></div>
      <div class="kpi green"><div class="kpi-val">${overview.totalComponentsActive||0}</div><div class="kpi-lbl">Active Components</div></div>`;

    document.getElementById('metricsTable').innerHTML = components.map(c => `
      <tr>
        <td class="mono-cell">${c.name}</td>
        <td class="mono-cell">${fmtNum(c.eventsProcessed)}</td>
        <td class="mono-cell" style="color:${c.eventsFailed>0?'var(--red)':'inherit'}">${c.eventsFailed}</td>
        <td class="mono-cell">${c.avgProcessingTimeMs}ms</td>
        <td><span class="status-badge ${(c.status||'').toLowerCase()}"><span class="status-dot"></span>${c.status}</span></td>
      </tr>`).join('');

    drawThroughputChart();
  } catch(e) { toast('Failed to load metrics', 'error'); }
}

function drawThroughputChart() {
  const ctx = document.getElementById('throughputChart').getContext('2d');
  if (throughputChart) { throughputChart.destroy(); throughputChart = null; }

  const labels = Array.from({length: 12}, (_, i) => {
    const h = new Date(); h.setHours(h.getHours() - (11-i));
    return h.getHours().toString().padStart(2,'0') + ':00';
  });
  const data = Array.from({length: 12}, () => Math.floor(Math.random() * 120 + 10));

  throughputChart = new Chart(ctx, {
    type: 'line',
    data: {
      labels,
      datasets: [{
        label: 'Events / hour',
        data,
        borderColor: '#3b82f6',
        backgroundColor: 'rgba(59,130,246,.08)',
        fill: true, tension: 0.4, pointRadius: 3,
        pointBackgroundColor: '#3b82f6',
        borderWidth: 2
      }]
    },
    options: {
      responsive: true, maintainAspectRatio: false,
      plugins: {
        legend: { labels: { color: '#5a6a8a', font: { family: 'IBM Plex Mono', size: 11 } } }
      },
      scales: {
        x: { ticks: { color: '#5a6a8a', font: { family: 'IBM Plex Mono', size: 10 } }, grid: { color: '#1e2535' } },
        y: { ticks: { color: '#5a6a8a', font: { family: 'IBM Plex Mono', size: 10 } }, grid: { color: '#1e2535' } }
      }
    }
  });
}

/* ── Generate Component ─────────────────────────────────── */
function openGenerateModal() {
  document.getElementById('genPipeline').classList.add('hidden');
  document.getElementById('genSourceWrap').classList.add('hidden');
  document.getElementById('genBtn').disabled = false;
  document.getElementById('genBtnText').textContent = '⬡ Generate Class';
  document.getElementById('generateModal').classList.add('open');
}

function selectProvider(el) {
  document.querySelectorAll('.provider-opt').forEach(o => o.classList.remove('selected'));
  el.classList.add('selected');
  el.querySelector('input').checked = true;
  selectedProvider = el.dataset.val;
}

function setPipelineStep(id, state, detail) {
  const step = document.getElementById(id);
  if (!step) return;
  const dot    = step.querySelector('.ps-dot');
  const status = step.querySelector('.ps-status');
  dot.className = 'ps-dot ' + state;
  status.textContent = state === 'running' ? '...' : state === 'done' ? '✓' : state === 'error' ? '✗' : '';
  if (detail) status.textContent = detail;
}

async function generateComponent() {
  const name    = document.getElementById('genName').value.trim();
  const version = document.getElementById('genVersion').value.trim() || '1.0.0';
  const desc    = document.getElementById('genDescription').value.trim();
  const apiKey  = document.getElementById('genApiKey').value.trim();

  if (!name || !desc || !apiKey) {
    toast('Please fill in all required fields', 'error'); return;
  }

  const btn = document.getElementById('genBtn');
  btn.disabled = true;
  document.getElementById('genBtnText').textContent = 'Generating...';

  const pipeline = document.getElementById('genPipeline');
  pipeline.classList.remove('hidden');
  document.getElementById('genSourceWrap').classList.add('hidden');

  ['ps1','ps2','ps3','ps4'].forEach(id => setPipelineStep(id, 'pending'));
  setPipelineStep('ps1', 'running');

  try {
    const res = await fetch(`${API}/components/generate`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name, version, description: desc, aiProvider: selectedProvider, apiKey })
    });
    const data = await res.json();

    if (data.status === 'SUCCESS') {
      setPipelineStep('ps1', 'done');
      setTimeout(() => setPipelineStep('ps2', 'done'), 200);
      setTimeout(() => setPipelineStep('ps3', 'done'), 400);
      setTimeout(() => {
        setPipelineStep('ps4', 'done');
        document.getElementById('genSourceCode').textContent = data.sourceCode;
        document.getElementById('genSourceWrap').classList.remove('hidden');
        toast(`${name} is now ACTIVE`, 'success');
        addLifecycleEvent(name, 'LOADED', 'AI Generated');
        setTimeout(loadComponents, 1500);
      }, 600);
    } else {
      setPipelineStep('ps1', 'error');
      setPipelineStep('ps2', 'error', data.error || 'Generation failed');
      toast(`Generation failed: ${data.error}`, 'error');
    }
  } catch(e) {
    setPipelineStep('ps1', 'error', e.message);
    toast('Generation request failed', 'error');
  } finally {
    btn.disabled = false;
    document.getElementById('genBtnText').textContent = '⬡ Generate Class';
  }
}

function copySource() {
  const src = document.getElementById('genSourceCode').textContent;
  navigator.clipboard.writeText(src).then(() => toast('Copied to clipboard', 'success'));
}

/* ── WebSocket ──────────────────────────────────────────── */
function initWebSocket() {
  const ws = new WebSocket(WS_URL);

  ws.onmessage = (msg) => {
    try {
      const data = JSON.parse(msg.data);
      const type = data.eventType || data.type;
      if (!type) return;

      appendEventRow({
        time:   new Date().toLocaleTimeString('en-GB'),
        type,
        source: data.source || 'COMPONENT',
        userId: data.context?.userId || data.userId || '—',
        status: 'received'
      });

      if (type === 'COMPONENT_STATUS_CHANGED' || type === 'USER_CONNECTED') loadComponents();
      refreshHeader();
    } catch(e) { /* non-JSON message, ignore */ }
  };

  ws.onclose = () => setTimeout(initWebSocket, 3000);
  ws.onerror = () => {};
}

/* ── Modal helpers ──────────────────────────────────────── */
function closeModal(id) { document.getElementById(id).classList.remove('open'); }
function overlayClose(e, id) { if (e.target === e.currentTarget) closeModal(id); }

/* ── Toast ──────────────────────────────────────────────── */
function toast(message, type = 'info') {
  const icons = { success: '✓', error: '✗', info: 'ℹ' };
  const t = document.createElement('div');
  t.className = `toast ${type}`;
  t.innerHTML = `<span>${icons[type]||'ℹ'}</span><span>${message}</span>`;
  document.getElementById('toastContainer').appendChild(t);
  setTimeout(() => { t.style.opacity='0'; t.style.transform='translateX(20px)'; t.style.transition='all .3s ease'; setTimeout(()=>t.remove(),300); }, 3000);
}

/* ── Formatters ─────────────────────────────────────────── */
function fmtNum(n) {
  if (!n) return '0';
  if (n >= 1000000) return (n/1000000).toFixed(1)+'M';
  if (n >= 1000)    return (n/1000).toFixed(1)+'K';
  return String(n);
}

function relTime(ts) {
  const ms = Date.now() - new Date(ts).getTime();
  if (ms < 60000)  return Math.floor(ms/1000)+'s';
  if (ms < 3600000) return Math.floor(ms/60000)+'m';
  return Math.floor(ms/3600000)+'h';
}

function fmtUptime() {
  const ms = Date.now() - startTime;
  const h  = Math.floor(ms/3600000);
  const m  = Math.floor((ms%3600000)/60000);
  const s  = Math.floor((ms%60000)/1000);
  if (h > 0) return `${h}h ${m}m`;
  if (m > 0) return `${m}m ${s}s`;
  return `${s}s`;
}
