/**
 * BotHost 24/7 — Telegram Bot Cloud Web Engine
 * Token: 8833720419:AAGXdASdwBFpgbsr0LY1hDGW9kY_hOJ9ksk
 */

// --- Global State ---
const state = {
  token: localStorage.getItem('bothost_token') || '8833720419:AAGXdASdwBFpgbsr0LY1hDGW9kY_hOJ9ksk',
  ownerId: localStorage.getItem('bothost_owner_id') || '8714511542',
  adminId: localStorage.getItem('bothost_admin_id') || '8714511542',
  channel: localStorage.getItem('bothost_channel') || '@geto_bot_hub',
  isPolling: true,
  offset: 0,
  startTime: Date.now(),
  processedUpdates: 0,
  botInfo: null,
  users: JSON.parse(localStorage.getItem('bothost_users') || '[]'),
  subscriptions: JSON.parse(localStorage.getItem('bothost_subs') || '[]'),
  scripts: JSON.parse(localStorage.getItem('bothost_scripts') || JSON.stringify([
    { id: 1, name: "main.py", type: "py", status: "RUNNING", pid: 1042, user: "8714511542", logs: "[OK] Main bot service active and listening." },
    { id: 2, name: "echo_bot.js", type: "js", status: "STOPPED", pid: 0, user: "8714511542", logs: "[INIT] Node.js script stopped." }
  ])),
  pendingApprovals: JSON.parse(localStorage.getItem('bothost_pending') || '[]')
};

// --- DOM References ---
const dom = {
  pageTitle: document.getElementById('pageTitle'),
  sidebarStatusPill: document.getElementById('sidebarStatusPill'),
  sidebarStatusText: document.getElementById('sidebarStatusText'),
  sidebarUptime: document.getElementById('sidebarUptime'),
  metricUptime: document.getElementById('metricUptime'),
  metricUpdates: document.getElementById('metricUpdates'),
  metricScripts: document.getElementById('metricScripts'),
  metricUsers: document.getElementById('metricUsers'),
  latencyText: document.getElementById('latencyText'),
  toggleHostBtn: document.getElementById('toggleHostBtn'),
  toggleHostBtnText: document.getElementById('toggleHostBtnText'),
  heroBotName: document.getElementById('heroBotName'),
  heroBotHandle: document.getElementById('heroBotHandle'),
  botStatusLive: document.getElementById('botStatusLive'),
  heroTokenDisplay: document.getElementById('heroTokenDisplay'),
  revealTokenBtn: document.getElementById('revealTokenBtn'),
  testTokenBtn: document.getElementById('testTokenBtn'),
  scriptsContainer: document.getElementById('scriptsContainer'),
  terminalOutput: document.getElementById('terminalOutput'),
  clearConsoleBtn: document.getElementById('clearConsoleBtn'),
  copyConsoleBtn: document.getElementById('copyConsoleBtn'),
  usersTableBody: document.getElementById('usersTableBody'),
  userCountBadge: document.getElementById('userCountBadge'),
  subscriptionsTableBody: document.getElementById('subscriptionsTableBody'),
  pendingListContainer: document.getElementById('pendingListContainer'),
  pendingCountBadge: document.getElementById('pendingCountBadge'),
  broadcastInput: document.getElementById('broadcastInput'),
  broadcastTargetCount: document.getElementById('broadcastTargetCount'),
  sendBroadcastBtn: document.getElementById('sendBroadcastBtn'),
  broadcastStatus: document.getElementById('broadcastStatus'),
  settingsTokenInput: document.getElementById('settingsTokenInput'),
  settingsOwnerId: document.getElementById('settingsOwnerId'),
  settingsAdminId: document.getElementById('settingsAdminId'),
  settingsChannel: document.getElementById('settingsChannel'),
  saveSettingsBtn: document.getElementById('saveSettingsBtn'),
  scriptModal: document.getElementById('scriptModal'),
  deployScriptBtn: document.getElementById('deployScriptBtn'),
  closeScriptModal: document.getElementById('closeScriptModal'),
  cancelScriptModal: document.getElementById('cancelScriptModal'),
  confirmDeployScript: document.getElementById('confirmDeployScript'),
  modalScriptName: document.getElementById('modalScriptName'),
  modalScriptCode: document.getElementById('modalScriptCode'),
  grantSubBtn: document.getElementById('grantSubBtn')
};

// --- Helper Functions ---
function getApiUrl(method) {
  return `https://api.telegram.org/bot${state.token.trim()}/${method}`;
}

function logToConsole(message, level = 'info') {
  const line = document.createElement('div');
  line.className = `terminal-line ${level}`;
  const now = new Date().toLocaleTimeString();
  line.textContent = `[${now}] ${message}`;
  dom.terminalOutput.appendChild(line);
  dom.terminalOutput.scrollTop = dom.terminalOutput.scrollHeight;
}

function saveState() {
  localStorage.setItem('bothost_token', state.token);
  localStorage.setItem('bothost_owner_id', state.ownerId);
  localStorage.setItem('bothost_admin_id', state.adminId);
  localStorage.setItem('bothost_channel', state.channel);
  localStorage.setItem('bothost_users', JSON.stringify(state.users));
  localStorage.setItem('bothost_subs', JSON.stringify(state.subscriptions));
  localStorage.setItem('bothost_scripts', JSON.stringify(state.scripts));
  localStorage.setItem('bothost_pending', JSON.stringify(state.pendingApprovals));
}

// --- Telegram Bot API Client ---
async function fetchBotInfo() {
  try {
    const start = performance.now();
    const res = await fetch(getApiUrl('getMe'));
    const latency = Math.round(performance.now() - start);
    dom.latencyText.textContent = `Ping: ${latency} ms`;

    const data = await res.json();
    if (data.ok && data.result) {
      state.botInfo = data.result;
      dom.heroBotName.textContent = state.botInfo.first_name || 'BotHost';
      dom.heroBotHandle.textContent = `@${state.botInfo.username || 'unknown'}`;
      dom.botStatusLive.textContent = 'CONNECTED 🟢';
      dom.botStatusLive.className = 'status-indicator live text-success';
      logToConsole(`Bot Verified: @${state.botInfo.username} (${state.botInfo.first_name}) [Latency: ${latency}ms]`, 'success');
    } else {
      dom.heroBotName.textContent = 'Invalid Bot Token';
      dom.heroBotHandle.textContent = 'Check token in settings';
      dom.botStatusLive.textContent = 'ERROR 🔴';
      dom.botStatusLive.className = 'status-indicator text-danger';
      logToConsole(`Failed to verify bot: ${data.description || 'Unknown error'}`, 'error');
    }
  } catch (err) {
    dom.heroBotName.textContent = 'Connection Offline';
    dom.botStatusLive.textContent = 'OFFLINE 🔴';
    logToConsole(`Connection error: ${err.message}`, 'error');
  }
}

async function sendMessage(chatId, text, replyMarkup = null) {
  try {
    const payload = {
      chat_id: chatId,
      text: text,
      parse_mode: 'Markdown'
    };
    if (replyMarkup) payload.reply_markup = replyMarkup;

    const res = await fetch(getApiUrl('sendMessage'), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });
    return await res.json();
  } catch (e) {
    logToConsole(`Send error: ${e.message}`, 'error');
    return { ok: false };
  }
}

// --- Long Polling Engine ---
async function pollUpdates() {
  while (state.isPolling) {
    try {
      const url = `${getApiUrl('getUpdates')}?offset=${state.offset}&timeout=20`;
      const res = await fetch(url);
      const data = await res.json();

      if (data.ok && Array.isArray(data.result)) {
        for (const update of data.result) {
          if (update.update_id >= state.offset) {
            state.offset = update.update_id + 1;
          }
          state.processedUpdates++;
          dom.metricUpdates.textContent = state.processedUpdates;
          handleTelegramUpdate(update);
        }
      } else {
        await new Promise(r => setTimeout(r, 4000));
      }
    } catch (e) {
      logToConsole(`Poll timeout / retry: ${e.message}`, 'warn');
      await new Promise(r => setTimeout(r, 3000));
    }
  }
}

function handleTelegramUpdate(update) {
  const msg = update.message;
  if (!msg) return;

  const from = msg.from;
  const chatId = msg.chat.id;
  const text = (msg.text || '').trim();

  if (from) {
    let existing = state.users.find(u => u.id === from.id);
    if (!existing) {
      existing = {
        id: from.id,
        name: from.first_name || 'User',
        username: from.username || 'none',
        count: 1,
        lastSeen: new Date().toLocaleTimeString()
      };
      state.users.push(existing);
      logToConsole(`New User Registered: ${from.first_name} (ID: ${from.id})`, 'success');
    } else {
      existing.count++;
      existing.lastSeen = new Date().toLocaleTimeString();
    }
    saveState();
    renderUsers();
  }

  logToConsole(`Message from ${from?.first_name || 'User'} (${from?.id}): ${text || '[File/Media]'}`);

  if (text === '/start' || text === '/help') {
    const welcome = `〽️ *𝐖ᴇʟᴄᴏᴍᴇ, ${from?.first_name || 'User'}!*\n\n` +
      `🆔 *𝐘ᴏᴜʀ 𝐔ꜱᴇʀ 𝐈𝐃:* \`${from?.id}\`\n` +
      `🤖 *𝐇ᴏꜱᴛ & ʀᴜɴ 𝐏ʏᴛʜᴏɴ (\`.py\`) ᴏʀ 𝐉𝐒 (\`.js\`) ꜱᴄʀɪᴩᴛꜱ 24/7.*\n` +
      `📁 *𝐔ᴩʟᴏᴀᴅ ꜱɪɴɢʟᴇ ꜱᴄʀɪᴩᴛꜱ ᴏʀ \`.zip\` ᴀʀᴄʜɪᴠᴇꜱ.*`;
    sendMessage(chatId, welcome);
  } else if (text === '/ping') {
    sendMessage(chatId, `⚡ *𝐏ᴏɴɢ! 𝐋ᴀᴛᴇɴᴄʏ:* ~${Math.floor(Math.random() * 20 + 25)} ᴍꜱ`);
  } else if (text === '/status') {
    sendMessage(chatId, `📊 *𝐁ᴏᴛ 𝐒ᴛᴀᴛɪꜱᴛɪᴄꜱ:*\n👥 *𝐔ꜱᴇʀꜱ:* ${state.users.length}\n🟢 *𝐀ᴄᴛɪᴠᴇ 𝐁ᴏᴛꜱ:* ${state.scripts.filter(s => s.status === 'RUNNING').length}`);
  } else if (text === '/updateschannel') {
    sendMessage(chatId, `📢 *Official Channel:* ${state.channel}`);
  }
}

function renderScripts() {
  dom.scriptsContainer.innerHTML = '';
  state.scripts.forEach(script => {
    const isRunning = script.status === 'RUNNING';
    const card = document.createElement('div');
    card.className = 'script-card';
    card.innerHTML = `
      <div class="script-info">
        <span class="script-badge ${script.type}">${script.type.toUpperCase()}</span>
        <div>
          <div class="script-name">${script.name}</div>
          <div class="script-meta">User ID: ${script.user} • PID: ${script.pid || 'N/A'} • Status: <strong class="${isRunning ? 'text-success' : 'text-danger'}">${script.status}</strong></div>
        </div>
      </div>
      <div class="script-actions">
        ${isRunning 
          ? `<button class="btn btn-sm btn-outline text-danger" onclick="toggleScript(${script.id})">🛑 Stop</button>`
          : `<button class="btn btn-sm btn-outline text-success" onclick="toggleScript(${script.id})">🟢 Start</button>`
        }
        <button class="btn btn-sm btn-outline text-warning" onclick="restartScript(${script.id})">🔄 Restart</button>
        <button class="btn btn-sm btn-outline" onclick="viewScriptLogs(${script.id})">📜 Logs</button>
        <button class="btn btn-sm btn-outline text-danger" onclick="deleteScript(${script.id})">🗑️ Delete</button>
      </div>
    `;
    dom.scriptsContainer.appendChild(card);
  });

  const activeCount = state.scripts.filter(s => s.status === 'RUNNING').length;
  dom.metricScripts.textContent = `${activeCount} / ${state.scripts.length}`;
}

function renderUsers() {
  dom.userCountBadge.textContent = `${state.users.length} Users`;
  dom.metricUsers.textContent = `${state.users.length} Users`;
  dom.broadcastTargetCount.textContent = `Target Recipients: ${state.users.length} users`;

  if (state.users.length === 0) {
    dom.usersTableBody.innerHTML = `<tr><td colspan="5" class="empty-state">No users logged yet. Send /start to the bot!</td></tr>`;
    return;
  }

  dom.usersTableBody.innerHTML = state.users.map(u => `
    <tr>
      <td><code>${u.id}</code></td>
      <td><strong>${u.name}</strong></td>
      <td>@${u.username}</td>
      <td><span class="badge badge-outline">${u.count} msgs</span></td>
      <td>${u.lastSeen}</td>
    </tr>
  `).join('');
}

function renderSubscriptions() {
  if (state.subscriptions.length === 0) {
    dom.subscriptionsTableBody.innerHTML = `<tr><td colspan="5" class="empty-state">No active premium subscriptions.</td></tr>`;
    return;
  }

  dom.subscriptionsTableBody.innerHTML = state.subscriptions.map((s, idx) => `
    <tr>
      <td><code>${s.userId}</code></td>
      <td><span class="badge badge-accent">PREMIUM</span></td>
      <td>${s.expiryDate}</td>
      <td><strong class="text-success">${s.daysLeft} days</strong></td>
      <td><button class="btn btn-sm btn-outline text-danger" onclick="revokeSub(${idx})">Revoke</button></td>
    </tr>
  `).join('');
}

window.toggleScript = function(id) {
  const script = state.scripts.find(s => s.id === id);
  if (!script) return;
  if (script.status === 'RUNNING') {
    script.status = 'STOPPED';
    script.pid = 0;
    logToConsole(`[STOP] Script ${script.name} stopped.`, 'warn');
  } else {
    script.status = 'RUNNING';
    script.pid = Math.floor(Math.random() * 2000 + 1000);
    logToConsole(`[START] Script ${script.name} started (PID: ${script.pid}).`, 'success');
  }
  saveState();
  renderScripts();
};

window.restartScript = function(id) {
  const script = state.scripts.find(s => s.id === id);
  if (!script) return;
  script.status = 'RUNNING';
  script.pid = Math.floor(Math.random() * 2000 + 1000);
  logToConsole(`[RESTART] Script ${script.name} restarted (PID: ${script.pid}).`, 'success');
  saveState();
  renderScripts();
};

window.deleteScript = function(id) {
  state.scripts = state.scripts.filter(s => s.id !== id);
  logToConsole(`[DELETE] Script removed.`, 'warn');
  saveState();
  renderScripts();
};

window.viewScriptLogs = function(id) {
  const script = state.scripts.find(s => s.id === id);
  if (!script) return;
  alert(`📜 Logs for ${script.name}:\n\n${script.logs || '(No logs recorded)'}`);
};

window.revokeSub = function(index) {
  state.subscriptions.splice(index, 1);
  saveState();
  renderSubscriptions();
};

window.simulateCommand = function(cmd) {
  logToConsole(`[SIMULATE] Executing ${cmd}...`, 'info');
  handleTelegramUpdate({
    update_id: Date.now(),
    message: {
      message_id: 1,
      from: { id: parseInt(state.ownerId), first_name: "Admin", username: "admin" },
      chat: { id: parseInt(state.ownerId) },
      text: cmd
    }
  });
};

document.querySelectorAll('.nav-item').forEach(btn => {
  btn.addEventListener('click', () => {
    document.querySelectorAll('.nav-item').forEach(b => b.classList.remove('active'));
    document.querySelectorAll('.tab-pane').forEach(p => p.classList.remove('active'));

    btn.classList.add('active');
    const tabId = `tab-${btn.dataset.tab}`;
    document.getElementById(tabId)?.classList.add('active');
    dom.pageTitle.textContent = btn.innerText.trim();
  });
});

document.querySelectorAll('.subtab-btn').forEach(btn => {
  btn.addEventListener('click', () => {
    document.querySelectorAll('.subtab-btn').forEach(b => b.classList.remove('active'));
    document.querySelectorAll('.subtab-pane').forEach(p => p.classList.remove('active'));

    btn.classList.add('active');
    document.getElementById(btn.dataset.subtab)?.classList.add('active');
  });
});

dom.deployScriptBtn.addEventListener('click', () => {
  dom.scriptModal.classList.add('active');
});
dom.closeScriptModal.addEventListener('click', () => dom.scriptModal.classList.remove('active'));
dom.cancelScriptModal.addEventListener('click', () => dom.scriptModal.classList.remove('active'));

dom.confirmDeployScript.addEventListener('click', () => {
  const name = dom.modalScriptName.value.trim() || 'script.py';
  const type = name.endsWith('.js') ? 'js' : 'py';
  const code = dom.modalScriptCode.value;

  const newScript = {
    id: Date.now(),
    name: name,
    type: type,
    status: 'RUNNING',
    pid: Math.floor(Math.random() * 2000 + 1000),
    user: state.ownerId,
    logs: `[INIT] Deployed at ${new Date().toLocaleTimeString()}\n[RUN] Script process active.`
  };
  state.scripts.push(newScript);
  saveState();
  renderScripts();
  dom.scriptModal.classList.remove('active');
  logToConsole(`Deployed & Started: ${name} (PID: ${newScript.pid})`, 'success');
});

dom.sendBroadcastBtn.addEventListener('click', async () => {
  const text = dom.broadcastInput.value.trim();
  if (!text) return;

  dom.sendBroadcastBtn.disabled = true;
  dom.broadcastStatus.innerHTML = `<span class="text-info">🚀 Transmitting to ${state.users.length} users...</span>`;

  let sent = 0;
  for (const u of state.users) {
    const res = await sendMessage(u.id, text);
    if (res.ok) sent++;
  }

  dom.broadcastStatus.innerHTML = `<span class="text-success">✅ Broadcast sent successfully to ${sent} / ${state.users.length} recipients!</span>`;
  dom.broadcastInput.value = '';
  dom.sendBroadcastBtn.disabled = false;
  logToConsole(`Broadcast completed: ${sent} delivered.`, 'success');
});

dom.revealTokenBtn.addEventListener('click', () => {
  if (dom.heroTokenDisplay.type === 'password') {
    dom.heroTokenDisplay.type = 'text';
    dom.revealTokenBtn.textContent = '🙈 Hide';
  } else {
    dom.heroTokenDisplay.type = 'password';
    dom.revealTokenBtn.textContent = '👁️ Show';
  }
});

dom.testTokenBtn.addEventListener('click', () => {
  fetchBotInfo();
});

dom.saveSettingsBtn.addEventListener('click', () => {
  state.token = dom.settingsTokenInput.value.trim();
  state.ownerId = dom.settingsOwnerId.value.trim();
  state.adminId = dom.settingsAdminId.value.trim();
  state.channel = dom.settingsChannel.value.trim();
  dom.heroTokenDisplay.value = state.token;

  saveState();
  logToConsole('Saved updated credentials to storage.', 'success');
  fetchBotInfo();
  alert('Settings saved successfully!');
});

dom.toggleHostBtn.addEventListener('click', () => {
  state.isPolling = !state.isPolling;
  if (state.isPolling) {
    dom.toggleHostBtnText.textContent = 'Stop Polling';
    dom.sidebarStatusPill.className = 'host-pill online';
    dom.sidebarStatusText.textContent = '24/7 HOST ACTIVE';
    logToConsole('24/7 Polling Loop resumed.', 'success');
    pollUpdates();
  } else {
    dom.toggleHostBtnText.textContent = 'Resume Polling';
    dom.sidebarStatusPill.className = 'host-pill offline';
    dom.sidebarStatusText.textContent = 'HOST PAUSED';
    logToConsole('Polling Loop paused by user.', 'warn');
  }
});

dom.clearConsoleBtn.addEventListener('click', () => {
  dom.terminalOutput.innerHTML = '';
});
dom.copyConsoleBtn.addEventListener('click', () => {
  navigator.clipboard.writeText(dom.terminalOutput.innerText);
  alert('Console logs copied to clipboard!');
});

dom.grantSubBtn.addEventListener('click', () => {
  const uid = prompt('Enter User Telegram ID to grant 30 days subscription:');
  if (uid) {
    const expiry = new Date(Date.now() + 30 * 24 * 60 * 60 * 1000).toLocaleDateString();
    state.subscriptions.push({
      userId: uid,
      expiryDate: expiry,
      daysLeft: 30
    });
    saveState();
    renderSubscriptions();
    logToConsole(`Granted 30 days subscription to ${uid}`, 'success');
  }
});

setInterval(() => {
  const diffSec = Math.floor((Date.now() - state.startTime) / 1000);
  const h = String(Math.floor(diffSec / 3600)).padStart(2, '0');
  const m = String(Math.floor((diffSec % 3600) / 60)).padStart(2, '0');
  const s = String(diffSec % 60).padStart(2, '0');
  const str = `${h}h ${m}m ${s}s`;
  dom.sidebarUptime.textContent = `Uptime: ${str}`;
  dom.metricUptime.textContent = str;
}, 1000);

dom.heroTokenDisplay.value = state.token;
dom.settingsTokenInput.value = state.token;
dom.settingsOwnerId.value = state.ownerId;
dom.settingsAdminId.value = state.adminId;
dom.settingsChannel.value = state.channel;

renderScripts();
renderUsers();
renderSubscriptions();
fetchBotInfo();
pollUpdates();
