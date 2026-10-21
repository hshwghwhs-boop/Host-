# ⚡ BotHost 24/7 — Telegram Bot Cloud Host & Webhook Platform

A complete, high-performance Telegram Bot Hosting Platform with a responsive Web Dashboard, Cloudflare Worker Serverless Webhook (24/7 always on), Node.js server (`server.js`), script manager, broadcast tool, and live terminal console.

Pre-configured with Bot Token:
```
8947132654:AAGOgHJgfFiYZxxX4HD-Lu8vhgn038FgU9w
```
Pre-configured Worker URL: `https://host.baruahbhaskar821.workers.dev`

---

## ⚠️ Important: Why does GitHub Pages stop when you close the browser tab?

**GitHub Pages is a Static Host.** 
- While your browser tab is open, the browser polls Telegram via JavaScript.
- As soon as you **close the browser tab** or **lock your phone**, the browser stops running JavaScript, so Telegram updates stop.

---

## 🚀 How to Make the Bot Run 24/7 Always (Even when tab/browser is closed)

### Option 1: Cloudflare Worker 24/7 Serverless Webhook (100% Free Forever) — ⭐ RECOMMENDED

Cloudflare Workers run in the cloud 24/7 with zero sleep and 100,000 free requests per day.

1. Open [workers.cloudflare.com](https://workers.cloudflare.com) and log in.
2. Click **Create Application** &rarr; **Create Worker** &rarr; **Deploy**.
3. Click **Edit code**, delete existing code, and paste the contents of `cloudflare_worker.js`.
4. Click **Deploy**.
5. Copy your worker link (e.g. `https://host.baruahbhaskar821.workers.dev`).
6. In your web dashboard, go to the **🌐 24/7 Cloud & Webhook** tab, paste your worker URL, and click **⚡ Set 24/7 Webhook**!
7. **Done!** Telegram will now deliver messages directly to your Cloudflare Worker 24/7, completely free, with zero browser or server needed!

---

### Option 2: Render.com / Koyeb (Free Node.js 24/7 Server)

This repository includes `server.js`, `package.json`, and `Procfile`.

1. Push this repo to GitHub.
2. Go to [render.com](https://render.com) &rarr; **New Web Service** &rarr; Connect your GitHub Repo.
3. Build command: `npm install` (or blank)
4. Start command: `node server.js`
5. Render will run the bot continuously 24/7 in the cloud!

---

### Option 3: Host Web Dashboard on GitHub Pages

1. Push `index.html`, `style.css`, and `app.js` to GitHub.
2. Go to **Settings** &rarr; **Pages** &rarr; Source: **Deploy from main / root**.
3. Use this web dashboard to monitor your bot, view registered users, test commands, and manage your 24/7 Webhook!
