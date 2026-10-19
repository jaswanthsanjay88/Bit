# 100% Free 24/7 Discord Bot Hosting (NO Credit Card Required)

Every option in this guide requires **only an email address or Discord login**. No credit card, debit card, or billing information is ever required.

---

## Quick Comparison (Zero Credit Card Required)

| Platform | Type | Best For | 24/7 Uptime | Sign-Up Requirement |
| :--- | :--- | :--- | :--- | :--- |
| **Cloudflare Workers** | Serverless Edge | Slash Commands (`/cmd`) | **100% (Never sleeps)** | Email only |
| **SquareCloud** | Bot Hosting | discord.js / discord.py | **24/7 (Free tier)** | Discord Login only |
| **Hugging Face Spaces** | Docker / Python | Custom bots & AI bots | **24/7** (with keep-alive) | Email / GitHub only |
| **Alwaysdata** | Linux Web / SSH | Lightweight Node / Python | **24/7** (Background process) | Email only |
| **Spare Phone (Termux)** | Local Hardware | Any bot | **100% Local 24/7** | Nothing |

---

## Method 1: Cloudflare Workers (Best for Slash Commands)

If your bot uses Discord Slash Commands (like `/help`, `/ping`), Discord allows interactions over **HTTP Webhooks** instead of continuous WebSockets. 

- **Credit Card Needed:** **NO** (Free plan only requires email verification).
- **Cost:** $0 forever.
- **Limits:** 100,000 requests every single day.
- **Sleep / Spindown:** **Zero.** Edge workers wake up in < 5ms worldwide.

### Quick Setup:
1. Create a free account at [Cloudflare.com](https://dash.cloudflare.com/sign-up).
2. Install Wrangler CLI on your computer:
   ```bash
   npm install -g wrangler
   ```
3. Initialize the official Discord bot worker:
   ```bash
   npm create cloudflare@latest my-discord-bot -- --template https://github.com/cloudflare/worker-template-discord-bot
   cd my-discord-bot
   ```
4. Put your Discord Developer credentials (from [Discord Dev Portal](https://discord.com/developers/applications)):
   ```bash
   npx wrangler secret put DISCORD_TOKEN
   npx wrangler secret put DISCORD_PUBLIC_KEY
   npx wrangler secret put DISCORD_APPLICATION_ID
   ```
5. Deploy:
   ```bash
   npx wrangler deploy
   ```
6. In Discord Developer Portal -> **General Information** -> Set **Interactions Endpoint URL** to your worker URL (`https://my-discord-bot.<user>.workers.dev`).

---

## Method 2: SquareCloud (Specialized for Discord Bots)

[SquareCloud](https://squarecloud.app/) is a dedicated hosting platform built specifically for Discord bots.

- **Credit Card Needed:** **NO** (Log in directly with your Discord account).
- **Free Tier:** Dedicated RAM for 1 bot, standard uptime.
- **Supports:** Node.js, Python, Java, Go.

### Setup Steps:
1. Go to [squarecloud.app](https://squarecloud.app/) and click **Login with Discord**.
2. Prepare your bot folder with a `squarecloud.app` configuration file:
   ```ini
   MAIN=index.js
   MEMORY=100
   VERSION=recommended
   DISPLAY_NAME=MyDiscordBot
   ```
   *(For Python bots, set `MAIN=main.py`)*.
3. Zip your bot files (`index.js`, `package.json`, `squarecloud.app` — do not zip `node_modules`).
4. Click **Upload Application** in the dashboard and drag in your `.zip` file.
5. Add your `DISCORD_TOKEN` in the environment variables tab and click **Start**.

---

## Method 3: Hugging Face Spaces (Docker / Python)

Hugging Face Spaces allows you to deploy containerized applications for free on 2 vCPUs and 16GB RAM.

- **Credit Card Needed:** **NO** (Only an email or GitHub account).
- **Specs:** 2 vCPU, 16GB RAM, 50GB storage.

### Setup Steps:
1. Sign up at [huggingface.co](https://huggingface.co/).
2. Click your profile -> **New Space**.
3. Space SDK: Choose **Docker** (Blank) or **Gradio** (if Python).
4. Set Space hardware to **Free (CPU basic)**.
5. Clone your space git repo locally, add your bot code and `Dockerfile`:
   ```dockerfile
   FROM node:20-slim
   WORKDIR /app
   COPY package*.json ./
   RUN npm install
   COPY . .
   CMD ["node", "index.js"]
   ```
6. Under Space **Settings -> Variables and secrets**, add `DISCORD_TOKEN`.
7. Push to the repo. Your bot starts immediately.
   *(Tip: Add a minimal HTTP server like `http.createServer().listen(7860)` so Hugging Face registers the space as healthy).*

---

## Method 4: Old Android Phone via Termux (100% Free & Local)

If you have an old Android phone sitting unused, it makes an ultra-low power, silent, 24/7 bot server.

- **Credit Card Needed:** **NO**.
- **Electricity Cost:** ~5-10 cents per month.
- **Never sleeps:** Guaranteed 100% uptime with Android wake lock.

### Setup Steps:
1. Install **Termux** from [F-Droid](https://f-droid.org/en/packages/com.termux/) or GitHub Releases (do not use Google Play version).
2. Open Termux and run:
   ```bash
   pkg update && pkg install nodejs git tmux -y
   termux-wake-lock
   ```
3. Disable battery optimization:
   - Android Settings -> Apps -> Termux -> Battery -> Set to **Unrestricted / No restrictions**.
4. Clone your bot into Termux and start it inside `tmux`:
   ```bash
   git clone <your-bot-repo-url>
   cd <your-bot-folder>
   npm install
   tmux new -s bot
   node index.js
   ```
5. Press `Ctrl + B`, then `D` to detach the session. Your bot will run 24/7 even with the screen turned off.

---

## Summary Recommendation (Pick One)
1. **If your bot only needs slash commands:** Pick **Cloudflare Workers** (highest reliability, fastest, zero setup headaches).
2. **If your bot needs standard gateway WebSockets (discord.js / discord.py):** Pick **SquareCloud** or **Hugging Face Spaces**.
3. **If you want full personal control without third-party bans:** Run it on a spare Android phone with **Termux**.
