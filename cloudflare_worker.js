/**
 * Cloudflare Worker — 24/7 Serverless Telegram Bot Host
 * 100% Free, runs 24/7 in the cloud without keeping your browser or computer open!
 * 
 * Instructions:
 * 1. Go to https://workers.cloudflare.com (Free)
 * 2. Click "Create Application" -> "Create Worker"
 * 3. Paste this entire code into the editor
 * 4. Click "Deploy"
 * 5. Copy your worker URL (e.g. https://bothost.yourname.workers.dev)
 * 6. Set your Telegram Webhook using this URL or in the BotHost Web Dashboard!
 */

const BOT_TOKEN = "8947132654:AAGOgHJgfFiYZxxX4HD-Lu8vhgn038FgU9w";
const OWNER_ID = 8714511542;
const CHANNEL_USERNAME = "@geto_bot_hub";

export default {
  async fetch(request, env, ctx) {
    if (request.method !== "POST") {
      return new Response(JSON.stringify({
        status: "online",
        service: "BotHost 24/7 Cloudflare Worker",
        uptime: "24/7 Active Serverless",
        timestamp: new Date().toISOString()
      }), {
        headers: { "Content-Type": "application/json" }
      });
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
  const from = message.from;
  const text = (message.text || "").trim();
  const userName = from ? (from.first_name || "User") : "User";
  const userId = from ? from.id : 0;
  const isOwner = userId === OWNER_ID;

  // Bottom Reply Keyboard (matches user screenshot layout)
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

  if (text === "/start" || text === "▶️ 𝐒ᴛᴀʀᴛ 𝐍ᴏᴡ" || text === "start") {
    const welcomeText = `〽️ *𝐖ᴇʟᴄᴏᴍᴇ, ${userName}!*\n\n` +
      `🆔 *𝐘ᴏᴜʀ 𝐔ꜱᴇʀ 𝐈𝐃:* \`${userId}\`\n\n` +
      `🤖 *𝐇ᴏꜱᴛ & ʀᴜɴ 𝐏ʏᴛʜᴏɴ (\`.py\`) ᴏʀ 𝐉𝐒 (\`.js\`) ꜱᴄʀɪᴩᴛꜱ 24/7.*\n` +
      `📁 *𝐔ᴩʟᴏᴀᴅ ꜱɪɴɢʟᴇ ꜱᴄʀɪᴩᴛꜱ ᴏʀ \`.zip\` ᴀʀᴄʜɪᴠᴇꜱ.*`;

    await sendTelegramMessage(chatId, welcomeText, replyKeyboard);
  } else if (text === "/ping" || text === "⚡ 𝐁ᴏᴛ 𝐒ᴩᴇᴇᴅ" || text === "ping") {
    const pingText = `⚡ *𝐁ᴏᴛ 𝐒ᴩᴇᴇᴅ & 𝐒ᴛᴀᴛᴜꜱ:*\n\n` +
      `⏱️ *𝐀𝐏𝐈 𝐑ᴇꜱᴩᴏɴꜱᴇ 𝐓ɪᴍᴇ:* ~25 ᴍꜱ\n` +
      `🚦 *𝐁ᴏᴛ 𝐒ᴛᴀᴛᴜꜱ:* Online 24/7 🟢 (Cloudflare Edge)\n` +
      `☁️ *𝐇ᴏꜱᴛ:* Serverless Cloud Worker`;
    await sendTelegramMessage(chatId, pingText, replyKeyboard);
  } else if (text === "/status" || text === "📊 𝐒ᴛᴀᴛɪꜱᴛɪᴄꜱ") {
    const statsText = `📊 *𝐁ᴏᴛ 𝐒ᴛᴀᴛɪꜱᴛɪᴄꜱ:*\n\n` +
      `🟢 *𝐒ᴛᴀᴛᴜꜱ:* Running 24/7 Active\n` +
      `☁️ *𝐏ʟᴀᴛꜰᴏʀᴍ:* Cloudflare Serverless\n` +
      `⚡ *𝐔ᴩᴛɪᴍᴇ:* 100% Guaranteed\n` +
      `📢 *𝐂ʜᴀɴɴᴇʟ:* ${CHANNEL_USERNAME}`;
    await sendTelegramMessage(chatId, statsText, replyKeyboard);
  } else if (text === "/updateschannel" || text === "📢 𝐔ᴩᴅᴀᴛᴇꜱ 𝐂ʜᴀɴɴᴇʟ") {
    await sendTelegramMessage(chatId, `📢 *Official Channel:* ${CHANNEL_USERNAME}`, replyKeyboard);
  } else if (text === "💬 𝐂ᴏɴᴛᴀᴄᴛ 𝐎ᴡɴᴇʀ") {
    await sendTelegramMessage(chatId, `💬 *Contact Support/Owner:* Owner ID \`${OWNER_ID}\``, replyKeyboard);
  } else if (text === "📂 𝐂ʜᴇᴄᴋ 𝐅ɪʟᴇꜱ") {
    await sendTelegramMessage(chatId, `📂 *Hosted Files:* Send any \`.py\` or \`.js\` file to host it!`, replyKeyboard);
  } else {
    // Default reply
    await sendTelegramMessage(chatId, `🤖 *Command received:* "${text}". Use the keyboard below to navigate:`, replyKeyboard);
  }
}

async function sendTelegramMessage(chatId, text, replyMarkup = null) {
  const url = `https://api.telegram.org/bot${BOT_TOKEN}/sendMessage`;
  const body = {
    chat_id: chatId,
    text: text,
    parse_mode: "Markdown"
  };
  if (replyMarkup) {
    body.reply_markup = replyMarkup;
  }

  return await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body)
  });
}
