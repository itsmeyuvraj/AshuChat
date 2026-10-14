# AshuChat Signaling Relay Server 📡

The relay server is used **strictly for the initial 2-second WebRTC peer handshake** (exchanging SDP offer/answer tokens and ICE candidates). Once connected, 100% of messages, media, photos, and files stream **directly phone-to-phone via WebRTC DataChannel (DTLS-SRTP 256-bit encryption)**.

---

## 🚀 Option 1: Built-in Serverless Cloud (Zero Setup - Default)

AshuChat comes pre-configured to use **`https://ntfy.sh`** as the default public serverless relay.
- ✅ **Zero servers to host**
- ✅ **Zero accounts or API keys**
- ✅ **100% Free & Open-Source**
- Both phones can connect immediately out of the box!

---

## 💻 Option 2: Run Locally on Your Laptop (Home Wi-Fi)

You can run a private signaling relay right on your Mac/PC on your home Wi-Fi network.

### Using Python 3 (Zero Dependencies!):
```bash
cd relay-server
python3 server.py 8080
```

### Using Node.js:
```bash
cd relay-server
npm install
npm start
```

### In AshuChat App:
1. Open **Settings (⚙️)** in AshuChat.
2. Under **Signaling Relay Server**, enter:
   - For phone on same Wi-Fi: `http://<YOUR_LAPTOP_LOCAL_IP>:8080` (e.g. `http://192.168.1.15:8080`)
   - For Android Emulator: `http://10.0.2.2:8080`
3. Tap **"Test Relay Reachability"** to verify connection.

---

## ☁️ Option 3: Deploy Free to Cloud (Render / Railway / Fly.io)

### Deploy to Render:
1. Push your repository to GitHub.
2. In Render Dashboard, click **New > Web Service**.
3. Select your repository and set:
   - **Root Directory**: `relay-server`
   - **Runtime**: `Node`
   - **Build Command**: `npm install`
   - **Start Command**: `node server.js`
4. Copy your live Render URL (e.g. `wss://my-ashuchat-relay.onrender.com` or `https://...`).
5. Paste it into AshuChat's Settings!
