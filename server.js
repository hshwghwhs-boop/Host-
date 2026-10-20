/**
 * BotHost 24/7 — Node.js Background Server
 * Deploy to Render.com, Koyeb, Railway, Glitch, or VPS for 24/7 continuous operation.
 */

const https = require('https');

const TOKEN = process.env.BOT_TOKEN || '8833720419:AAGXdASdwBFpgbsr0LY1hDGW9kY_hOJ9ksk';
const OWNER_ID = parseInt(process.env.OWNER_ID || '8714511542', 10);
const CHANNEL = process.env.CHANNEL || '@geto_bot_hub';

let offset = 0;

function apiRequest(method, payload = {}) {
  return new Promise((resolve, reject) => {
    const data = JSON.stringify(payload);
    const options = {
      hostname: 'api.telegram.org',
      port: 443,
      path: `/bot${TOKEN}/${method}`,
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Content-Length': Buffer.byteLength(data)
      }
    };

    const req = https.request(options, (res) => {
      let body = '';
      res.on('data', chunk => body += chunk);
      res.on('end', () => {
        try {
          resolve(JSON.parse(body));
        } catch (e) {
          resolve({ ok: false, error: e.message });
        }
      });
    });

    req.on('error', reject);
    req.write(data);
    req.end();
  });
}

async function sendMessage(chatId, text, replyMarkup = null) {
  const payload = {
    chat_id: chatId,
    text: text,
    parse_mode: 'Markdown'
  };
  if (replyMarkup) payload.reply_markup = replyMarkup;
  return await apiRequest('sendMessage', payload);
}

const replyKeyboard = {
  keyboard: [
    [{ text: "▶️ 𝐒ᴛᴀʀᴛ 𝐍ᴏᴡ" }, { text: "⚡ 𝐁ᴏᴛ 𝐒ᴩᴇᴇᴅ" }],
    [{ text: "📢 𝐔ᴩᴅᴀᴛᴇꜱ 𝐂ʜᴀɴɴᴇʟ" }, { text: "💬 𝐂ᴏɴᴛᴀᴄᴛ 𝐎ᴡɴᴇʀ" }],
    [{ text: "📂 𝐂ʜᴇᴄᴋ 𝐅ɪʟᴇꜱ" }, { text: "📊 𝐒ᴛᴀᴛɪꜱᴛɪᴄꜱ" }]
  ],
  resize_keyboard: true,
  one_time_keyboard: false
};

async function handleUpdate(update) {
  const msg = update.message;
  if (!msg) return;

  const chatId = msg.chat.id;
  const from = msg.from;
  const text = (msg.text || '').trim();
  const userName = from ? (from.first_name || 'User') : 'User';
  const userId = from ? from.id : 0;

  console.log(`[MSG] From ${userName} (${userId}): ${text}`);

  if (text === '/start' || text === '▶️ 𝐒ᴛᴀʀᴛ 𝐍ᴏᴡ') {
    const welcome = `〽️ *𝐖ᴇʟᴄᴏᴍᴇ, ${userName}!*\n\n` +
      `🆔 *𝐘ᴏᴜʀ 𝐔ꜱᴇʀ 𝐈𝐃:* \`${userId}\`\n\n` +
      `🤖 *𝐇ᴏꜱᴛ & ʀᴜɴ 𝐏ʏᴛʜᴏɴ (\`.py\`) ᴏʀ 𝐉𝐒 (\`.js\`) ꜱᴄʀɪᴩᴛꜱ 24/7.*\n` +
      `📁 *𝐔ᴩʟᴏᴀᴅ ꜱɪɴɢʟᴇ ꜱᴄʀɪᴩᴛꜱ ᴏʀ \`.zip\` ᴀʀᴄʜɪᴠᴇꜱ.*`;
    await sendMessage(chatId, welcome, replyKeyboard);
  } else if (text === '/ping' || text === '⚡ 𝐁ᴏᴛ 𝐒ᴩᴇᴇᴅ') {
    await sendMessage(chatId, `⚡ *𝐁ᴏᴛ 𝐒ᴩᴇᴇᴅ & 𝐒ᴛᴀᴛᴜꜱ:*\n⏱️ *Response:* ~18ms\n🟢 *Status:* Running 24/7 Cloud Node.js Server!`, replyKeyboard);
  } else if (text === '/status' || text === '📊 𝐒ᴛᴀᴛɪꜱᴛɪᴄꜱ') {
    await sendMessage(chatId, `📊 *Statistics:* Bot is 100% Online on Cloud Server!`, replyKeyboard);
  } else if (text === '/updateschannel' || text === '📢 𝐔ᴩᴅᴀᴛᴇꜱ 𝐂ʜᴀɴɴᴇʟ') {
    await sendMessage(chatId, `📢 *Official Channel:* ${CHANNEL}`, replyKeyboard);
  }
}

async function poll() {
  console.log('[INIT] BotHost 24/7 Node.js Server Started...');
  // First clear any old webhook so long polling works
  await apiRequest('deleteWebhook');

  while (true) {
    try {
      const res = await apiRequest('getUpdates', { offset, timeout: 20 });
      if (res.ok && Array.isArray(res.result)) {
        for (const update of res.result) {
          if (update.update_id >= offset) {
            offset = update.update_id + 1;
          }
          await handleUpdate(update);
        }
      }
    } catch (err) {
      console.error('[POLL ERROR]', err.message);
      await new Promise(r => setTimeout(r, 4000));
    }
  }
}

// Simple HTTP server so cloud hosts (Render/Koyeb) detect open port
const http = require('http');
const PORT = process.env.PORT || 8080;
http.createServer((req, res) => {
  res.writeHead(200, { 'Content-Type': 'text/plain' });
  res.end('BotHost 24/7 Node.js Server is Running!');
}).listen(PORT, () => {
  console.log(`Web health check listening on port ${PORT}`);
  poll();
});
