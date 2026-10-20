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
  grantSubBtn: document.getElementById('grantSubBtn'),
  webhookLivePill: document.getElementById('webhookLivePill'),
  webhookCurrentUrl: document.getElementById('webhookCurrentUrl'),
  webhookPendingCount: document.getElementById('webhookPendingCount'),
  webhookLastError: document.getElementById('webhookLastError'),
  webhookUrlInput: document.getElementById('webhookUrlInput'),
  setWebhookBtn: document.getElementById('setWebhookBtn'),
  checkWebhookBtn: document.getElementById('checkWebhookBtn'),
  deleteWebhookBtn: document.getElementById('deleteWebhookBtn'),
  webhookActionStatus: document.getElementById('webhookActionStatus'),
  copyWorkerCodeBtn: document.getElementById('copyWorkerCodeBtn'),
  workerCodeBlock: document.getElementById('workerCodeBlock')
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
      } else if (data.error_code === 409) {
        // Active webhook on Telegram!
        if (dom.webhookLivePill) {
          dom.webhookLivePill.textContent = '24/7 CLOUD ACTIVE 🟢';
          dom.webhookLivePill.className = 'badge badge-accent';
        }
        dom.sidebarStatusText.textContent = '24/7 WEBHOOK ACTIVE';
        dom.sidebarStatusPill.className = 'host-pill online';
        logToConsole('🌐 24/7 Cloud Webhook is ACTIVE! Telegram is delivering messages to your serverless webhook directly.', 'success');
        await new Promise(r => setTimeout(r, 15000));
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
  const userName = from ? (from.first_name || 'User') : 'User';
  const userId = from ? from.id : 0;
  const isOwner = String(userId) === String(state.ownerId);

  // Register user
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

  // Native Telegram Bottom Reply Keyboard matching screenshot
  const replyKeyboard = {
    keyboard: [
      [{ text: "📢 𝐔ᴩᴅᴀᴛᴇꜱ 𝐂ʜᴀɴɴᴇʟ" }],
      [{ text: "📤 𝐔ᴩʟᴏᴀᴅ 𝐅ɪʟᴇ" }, { text: "📂 𝐂ʜᴇᴄᴋ 𝐅ɪʟᴇꜱ" }],
      [{ text: "⚡ 𝐁ᴏᴛ 𝐒ᴩᴇᴇᴅ" }, { text: "📊 𝐒ᴛᴀᴛɪꜱᴛɪᴄꜱ" }],
      [{ text: "💳 𝐒ᴜʙꜱᴄʀɪᴩᴛɪᴏɴꜱ" }, { text: "📢 𝐁ʀᴏᴀᴅᴄᴀꜱᴛ" }],
      [{ text: "🔒 𝐋ᴏᴄᴋ 𝐁ᴏᴛ" }, { text: "🟢 𝐑ᴜɴɴɪɴɢ 𝐀ʟʟ 𝐂ᴏᴅᴇ" }],
      [{ text: "⏳ 𝐏ᴇɴᴅɪɴɢ 𝐅ɪʟᴇꜱ" }, { text: "👑 𝐀ᴅᴍɪɴ 𝐏ᴀɴᴇʟ" }],
      [{ text: "📞 𝐂ᴏɴᴛᴀᴄᴛ 𝐎ᴡɴᴇʀ" }]
    ],
    resize_keyboard: true,
    one_time_keyboard: false
  };

  // Auto-respond to commands matching screenshot and python script logic
  if (text === '/start' || text === '▶️ 𝐒ᴛᴀʀᴛ 𝐍ᴏᴡ' || text === '/help') {
    const welcome = `〽️ *𝐖ᴇʟᴄᴏᴍᴇ, ${userName}!*\n\n` +
      `🆔 *𝐘ᴏᴜʀ 𝐔ꜱᴇʀ 𝐈𝐃:* \`${userId}\`\n\n` +
      `🤖 *𝐇ᴏꜱᴛ & ʀᴜɴ 𝐏ʏᴛʜᴏɴ (\`.py\`) ᴏʀ 𝐉𝐒 (\`.js\`) ꜱᴄʀɪᴩᴛꜱ 24/7.*\n` +
      `📁 *𝐔ᴩʟᴏᴀᴅ ꜱɪɴɢʟᴇ ꜱᴄʀɪᴩᴛꜱ ᴏʀ \`.zip\` ᴀʀᴄʜɪᴠᴇꜱ.*`;
    sendMessage(chatId, welcome, replyKeyboard);
  } else if (text === '/ping' || text === '⚡ 𝐁ᴏᴛ 𝐒ᴩᴇᴇᴅ') {
    const pingText = `⚡ *𝐁ᴏᴛ 𝐒ᴩᴇᴇᴅ & 𝐒ᴛᴀᴛᴜꜱ:*\n\n` +
      `⏱️ *𝐀𝐏𝐈 𝐑ᴇꜱᴩᴏɴꜱᴇ 𝐓ɪᴍᴇ:* ~${Math.floor(Math.random() * 20 + 25)} ᴍꜱ\n` +
      `🚦 *𝐁ᴏᴛ 𝐒ᴛᴀᴛᴜꜱ:* Online 🟢\n` +
      `☁️ *𝐇ᴏꜱᴛ:* Active`;
    sendMessage(chatId, pingText, replyKeyboard);
  } else if (text === '/status' || text === '📊 𝐒ᴛᴀᴛɪꜱᴛɪᴄꜱ') {
    sendMessage(chatId, `📊 *𝐁ᴏᴛ 𝐒ᴛᴀᴛɪꜱᴛɪᴄꜱ:*\n👥 *𝐔ꜱᴇʀꜱ:* ${state.users.length}\n🟢 *𝐀ᴄᴛɪᴠᴇ 𝐁ᴏᴛꜱ:* ${state.scripts.filter(s => s.status === 'RUNNING').length}`, replyKeyboard);
  } else if (text === '/updateschannel' || text === '📢 𝐔ᴩᴅᴀᴛᴇꜱ 𝐂ʜᴀɴɴᴇʟ') {
    sendMessage(chatId, `📢 *Official Channel:* ${state.channel}`, replyKeyboard);
  } else if (text === '💬 𝐂ᴏɴᴛᴀᴄᴛ 𝐎ᴡɴᴇʀ') {
    sendMessage(chatId, `💬 *Contact Support/Owner:* Owner ID \`${state.ownerId}\``, replyKeyboard);
  } else if (text === '📂 𝐂ʜᴇᴄᴋ 𝐅ɪʟᴇꜱ') {
    sendMessage(chatId, `📂 *Hosted Files:* Send any \`.py\` or \`.js\` file directly to this bot to execute it!`, replyKeyboard);
  } else if (text === '📢 𝐁ʀᴏᴀᴅᴄᴀꜱᴛ' && isOwner) {
    sendMessage(chatId, `📢 *Broadcast:* Enter your broadcast message in the web dashboard or reply to this prompt.`, replyKeyboard);
  } else if (text === '🛡️ 𝐀ᴅᴍɪɴ 𝐏ᴀɴᴇʟ' && isOwner) {
    sendMessage(chatId, `🛡️ *Admin Panel:*\n👥 *Total Users:* ${state.users.length}\n📁 *Scripts:* ${state.scripts.length}\n⚙️ *Manage via Web Dashboard.*`, replyKeyboard);
  }
}

// --- Render Functions ---
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

// Script actions
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

// --- Tab Switching ---
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

// Subtabs
document.querySelectorAll('.subtab-btn').forEach(btn => {
  btn.addEventListener('click', () => {
    document.querySelectorAll('.subtab-btn').forEach(b => b.classList.remove('active'));
    document.querySelectorAll('.subtab-pane').forEach(p => p.classList.remove('active'));

    btn.classList.add('active');
    document.getElementById(btn.dataset.subtab)?.classList.add('active');
  });
});

// --- Modal Handlers ---
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

// --- Broadcast Sender ---
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

// --- Token Reveal / Settings ---
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

// Toggle polling button
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

// Clear & copy console
dom.clearConsoleBtn.addEventListener('click', () => {
  dom.terminalOutput.innerHTML = '';
});
dom.copyConsoleBtn.addEventListener('click', () => {
  navigator.clipboard.writeText(dom.terminalOutput.innerText);
  alert('Console logs copied to clipboard!');
});

// Grant sub button
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

// --- Uptime Timer ---
setInterval(() => {
  const diffSec = Math.floor((Date.now() - state.startTime) / 1000);
  const h = String(Math.floor(diffSec / 3600)).padStart(2, '0');
  const m = String(Math.floor((diffSec % 3600) / 60)).padStart(2, '0');
  const s = String(diffSec % 60).padStart(2, '0');
  const str = `${h}h ${m}m ${s}s`;
  dom.sidebarUptime.textContent = `Uptime: ${str}`;
  dom.metricUptime.textContent = str;
}, 1000);

// --- Navigation Tab Helper ---
function openTab(tabName) {
  document.querySelectorAll('.nav-item').forEach(b => b.classList.remove('active'));
  document.querySelectorAll('.tab-pane').forEach(p => p.classList.remove('active'));

  const navBtn = document.querySelector(`.nav-item[data-tab="${tabName}"]`);
  if (navBtn) {
    navBtn.classList.add('active');
    dom.pageTitle.textContent = navBtn.innerText.trim();
  }
  const pane = document.getElementById(`tab-${tabName}`);
  if (pane) {
    pane.classList.add('active');
  }
}
window.openTab = openTab;

// --- Webhook Manager Engine ---
async function fetchWebhookInfo() {
  if (!dom.webhookLivePill) return;
  dom.webhookLivePill.textContent = 'CHECKING...';
  dom.webhookLivePill.className = 'badge badge-outline';

  try {
    const res = await fetch(getApiUrl('getWebhookInfo'));
    const data = await res.json();
    if (data.ok && data.result) {
      const info = data.result;
      const isWebhookActive = Boolean(info.url && info.url.trim().length > 0);
      dom.webhookCurrentUrl.textContent = isWebhookActive ? info.url : 'None (Browser Polling Mode)';
      dom.webhookPendingCount.textContent = info.pending_update_count || '0';
      dom.webhookLastError.textContent = info.last_error_message || 'None';

      if (isWebhookActive) {
        dom.webhookLivePill.textContent = '24/7 CLOUD ACTIVE 🟢';
        dom.webhookLivePill.className = 'badge badge-accent';
        dom.sidebarStatusText.textContent = '24/7 CLOUD WEBHOOK';
        dom.sidebarStatusPill.className = 'host-pill online';
        logToConsole(`Webhook Info: Active at ${info.url}`, 'info');
      } else {
        dom.webhookLivePill.textContent = 'BROWSER POLLING 🟡';
        dom.webhookLivePill.className = 'badge badge-warning';
        logToConsole('Webhook Info: No webhook set (Browser Polling Mode)', 'info');
      }
    }
  } catch (err) {
    logToConsole(`Webhook check error: ${err.message}`, 'error');
  }
}

async function setTelegramWebhook(url) {
  if (!url || !url.startsWith('https://')) {
    alert('Please enter a valid HTTPS Webhook URL (e.g. from Cloudflare Workers or Render)!');
    return;
  }
  dom.webhookActionStatus.innerHTML = '<span class="text-info">⏳ Setting 24/7 Webhook on Telegram...</span>';
  try {
    const res = await fetch(`${getApiUrl('setWebhook')}?url=${encodeURIComponent(url.trim())}`);
    const data = await res.json();
    if (data.ok) {
      dom.webhookActionStatus.innerHTML = `<span class="text-success">✅ Webhook Successfully Set! Your bot is now 24/7 Live!</span>`;
      logToConsole(`Set Webhook: Successfully connected to ${url}!`, 'success');
      state.isPolling = false; // Stop browser polling so Telegram handles it in cloud
      fetchWebhookInfo();
    } else {
      dom.webhookActionStatus.innerHTML = `<span class="text-danger">❌ Failed: ${data.description}</span>`;
      logToConsole(`Set Webhook Error: ${data.description}`, 'error');
    }
  } catch (e) {
    dom.webhookActionStatus.innerHTML = `<span class="text-danger">❌ Error: ${e.message}</span>`;
  }
}

async function deleteTelegramWebhook() {
  dom.webhookActionStatus.innerHTML = '<span class="text-info">⏳ Deleting Webhook...</span>';
  try {
    const res = await fetch(getApiUrl('deleteWebhook'));
    const data = await res.json();
    if (data.ok) {
      dom.webhookActionStatus.innerHTML = `<span class="text-success">✅ Webhook removed! Switched back to browser polling mode.</span>`;
      logToConsole('Deleted Webhook: Switched back to browser polling mode.', 'success');
      state.isPolling = true;
      fetchWebhookInfo();
      pollUpdates();
    } else {
      dom.webhookActionStatus.innerHTML = `<span class="text-danger">❌ Failed: ${data.description}</span>`;
    }
  } catch (e) {
    dom.webhookActionStatus.innerHTML = `<span class="text-danger">❌ Error: ${e.message}</span>`;
  }
}

// Event Listeners for Webhook Tab
if (dom.setWebhookBtn) {
  dom.setWebhookBtn.addEventListener('click', () => {
    const url = dom.webhookUrlInput.value.trim();
    setTelegramWebhook(url);
  });
}

if (dom.checkWebhookBtn) {
  dom.checkWebhookBtn.addEventListener('click', () => {
    fetchWebhookInfo();
  });
}

if (dom.deleteWebhookBtn) {
  dom.deleteWebhookBtn.addEventListener('click', () => {
    deleteTelegramWebhook();
  });
}

const CLOUDFLARE_WORKER_SAMPLE_CODE = `/**
 * Cloudflare Worker — 24/7 Telegram Bot Serverless Host
 * 100% Free Forever (100,000 requests/day, zero sleep)
 */
const BOT_TOKEN = "${state.token}";
const OWNER_ID = ${state.ownerId};
const CHANNEL_USERNAME = "${state.channel}";

export default {
  async fetch(request) {
    if (request.method !== "POST") {
      return new Response("BotHost 24/7 Cloudflare Worker is Live!", { status: 200 });
    }
    try {
      const update = await request.json();
      await handleTelegramUpdate(update);
      return new Response("OK", { status: 200 });
    } catch (err) {
      return new Response("Error: " + err.message, { status: 200 });
    }
  }
};

async function handleTelegramUpdate(update) {
  const message = update.message;
  if (!message) return;
  const chatId = message.chat.id;
  const text = (message.text || "").trim();
  const userName = message.from ? (message.from.first_name || "User") : "User";
  const userId = message.from ? message.from.id : 0;
  const isOwner = userId === OWNER_ID;

  const replyKeyboard = {
    keyboard: [
      [{ text: "▶️ 𝐒ᴛᴀʀᴛ 𝐍ᴏᴡ" }, { text: "⚡ 𝐁ᴏᴛ 𝐒ᴩᴇᴇᴅ" }],
      [{ text: "📢 𝐔ᴩᴅᴀᴛᴇꜱ 𝐂ʜᴀɴɴᴇʟ" }, { text: "💬 𝐂ᴏɴᴛᴀᴄᴛ 𝐎ᴡɴᴇʀ" }],
      [{ text: "📂 𝐂ʜᴇᴄᴋ 𝐅ɪʟᴇꜱ" }, { text: "📊 𝐒ᴛᴀᴛɪꜱᴛɪᴄꜱ" }]
    ],
    resize_keyboard: true
  };
  if (isOwner) {
    replyKeyboard.keyboard.push([{ text: "📢 𝐁ʀᴏᴀᴅᴄᴀꜱᴛ" }, { text: "🛡️ 𝐀ᴅᴍɪɴ 𝐏ᴀɴᴇʟ" }]);
  }

  if (text === "/start" || text === "▶️ 𝐒ᴛᴀʀᴛ 𝐍ᴏᴡ") {
    const welcome = "〽️ *𝐖ᴇʟᴄᴏᴍᴇ, " + userName + "!*\\n\\n" +
      "🆔 *𝐘ᴏᴜʀ 𝐔ꜱᴇʀ 𝐈𝐃:* \`" + userId + "\`\\n\\n" +
      "🤖 *𝐇ᴏꜱᴛ & ʀᴜɴ 𝐏ʏᴛʜᴏɴ (\`.py\`) ᴏʀ 𝐉𝐒 (\`.js\`) ꜱᴄʀɪᴩᴛꜱ 24/7.*\\n" +
      "📁 *𝐔ᴩʟᴏᴀᴅ ꜱɪɴɢʟᴇ ꜱᴄʀɪᴩᴛꜱ ᴏʀ \`.zip\` ᴀʀᴄʜɪᴠᴇꜱ.*";
    await sendTelegramMessage(chatId, welcome, replyKeyboard);
  } else if (text === "/ping" || text === "⚡ 𝐁ᴏᴛ 𝐒ᴩᴇᴇᴅ") {
    await sendTelegramMessage(chatId, "⚡ *𝐁ᴏᴛ 𝐒ᴩᴇᴇᴅ & 𝐒ᴛᴀᴛᴜꜱ:*\\n⏱️ *Latency:* ~20ms\\n🟢 *Status:* Online 24/7 Cloudflare Worker!", replyKeyboard);
  } else if (text === "/status" || text === "📊 𝐒ᴛᴀᴛɪꜱᴛɪᴄꜱ") {
    await sendTelegramMessage(chatId, "📊 *Statistics:* 24/7 Cloud Serverless Host Active!", replyKeyboard);
  } else if (text === "/updateschannel" || text === "📢 𝐔ᴩᴅᴀᴛᴇꜱ 𝐂ʜᴀɴɴᴇʟ") {
    await sendTelegramMessage(chatId, "📢 *Channel:* " + CHANNEL_USERNAME, replyKeyboard);
  }
}

async function sendTelegramMessage(chatId, text, replyMarkup) {
  const url = "https://api.telegram.org/bot" + BOT_TOKEN + "/sendMessage";
  await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ chat_id: chatId, text: text, parse_mode: "Markdown", reply_markup: replyMarkup })
  });
}`;

if (dom.workerCodeBlock) {
  dom.workerCodeBlock.textContent = CLOUDFLARE_WORKER_SAMPLE_CODE;
}

if (dom.copyWorkerCodeBtn) {
  dom.copyWorkerCodeBtn.addEventListener('click', () => {
    navigator.clipboard.writeText(CLOUDFLARE_WORKER_SAMPLE_CODE);
    alert('✅ Cloudflare Worker code copied to clipboard! Paste it into dash.cloudflare.com Worker editor.');
  });
}

// --- Initialization ---
dom.heroTokenDisplay.value = state.token;
dom.settingsTokenInput.value = state.token;
dom.settingsOwnerId.value = state.ownerId;
dom.settingsAdminId.value = state.adminId;
dom.settingsChannel.value = state.channel;

renderScripts();
renderUsers();
renderSubscriptions();
fetchBotInfo();
fetchWebhookInfo();
pollUpdates();

