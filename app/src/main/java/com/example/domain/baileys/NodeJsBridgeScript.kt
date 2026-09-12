package com.example.domain.baileys

object NodeJsBridgeScript {

    /**
     * Complete production-grade Node.js script for Termux using @whiskeysockets/baileys
     * Functions both as a Webhook sender to the Android App AND a local HTTP API server.
     */
    val SCRIPT_CONTENT = """// =========================================================================
// AI Edge WhatsApp - Baileys Node.js Bridge for Termux / Local Server
// Multi-Device WhatsApp Pairing Code (8 Chiffres) & Synchronisation
// =========================================================================

const {
  default: makeWASocket,
  useMultiFileAuthState,
  DisconnectReason,
  fetchLatestBaileysVersion,
  fetchLatestWaWebVersion,
  downloadMediaMessage,
  Browsers
} = require('@whiskeysockets/baileys');
const pino = require('pino');
const qrcode = require('qrcode-terminal');
const http = require('http');
const fs = require('fs');
const path = require('path');
const readline = require('readline');

// Configuration
const APP_URLS = [
  'http://127.0.0.1:8081',
  'http://127.0.0.1:8080',
  'http://127.0.0.1:8082',
  'http://localhost:8081',
  'http://localhost:8080'
];
const INSTANCE_ID = process.env.INSTANCE_ID || 'digitalsolverland';
const LOCAL_HTTP_PORT = process.env.PORT ? parseInt(process.env.PORT) : 8080;
const AUTH_DIR = path.join(__dirname, 'auth_info_baileys');
const PHONE_FILE = path.join(__dirname, 'phone.txt');

// Mode: Pairing code (8 digits) by default, QR terminal code only if QR_MODE is explicitly set to 'true'
const USE_PAIRING_CODE = process.env.QR_MODE !== 'true';

let sock = null;
let authStatus = 'DISCONNECTED';
let lastQr = '';
let lastPairingCode = '';
let isPairingRequested = false;
let configuredPhoneNumber = '';
const messageBuffer = [];

// Helper: Resolve phone number with strict priority
async function resolvePhoneNumber() {
  // 1. Command-line argument: node server.js 33773163772
  if (process.argv[2]) {
    const cleanArg = process.argv[2].replace(/[^0-9]/g, '');
    if (cleanArg.length >= 7) {
      console.log('📌 Numéro WhatsApp fourni en ligne de commande : ' + cleanArg + ' (chiffres uniquement)');
      if (fs.existsSync(PHONE_FILE)) {
        try {
          const oldPhone = fs.readFileSync(PHONE_FILE, 'utf8').trim().replace(/[^0-9]/g, '');
          if (oldPhone && oldPhone !== cleanArg) {
            console.log('🔄 Changement de numéro détecté (' + oldPhone + ' -> ' + cleanArg + '). Réinitialisation...');
            if (fs.existsSync(AUTH_DIR)) fs.rmSync(AUTH_DIR, { recursive: true, force: true });
          }
        } catch (_) {}
      }
      savePhoneNumber(cleanArg);
      return cleanArg;
    }
  }

  // 2. Environment variable PHONE_NUMBER
  if (process.env.PHONE_NUMBER) {
    const cleanEnv = process.env.PHONE_NUMBER.replace(/[^0-9]/g, '');
    if (cleanEnv.length >= 7) {
      savePhoneNumber(cleanEnv);
      return cleanEnv;
    }
  }

  // 3. Query Android App configuration dynamically
  try {
    const fromApp = await fetchConfigFromApp();
    if (fromApp) {
      const cleanApp = fromApp.replace(/[^0-9]/g, '');
      if (cleanApp.length >= 7) {
        console.log('📱 Numéro WhatsApp récupéré depuis l\'application Android : ' + cleanApp);
        savePhoneNumber(cleanApp);
        return cleanApp;
      }
    }
  } catch (_) {}

  // 4. Saved phone.txt file
  if (fs.existsSync(PHONE_FILE)) {
    try {
      const saved = fs.readFileSync(PHONE_FILE, 'utf8').trim().replace(/[^0-9]/g, '');
      if (saved.length >= 7) return saved;
    } catch (_) {}
  }

  // 5. Default user number
  return '33773163772';
}

function getSavedPhoneNumber() {
  if (process.argv[2] && process.argv[2].replace(/[^0-9]/g, '').length >= 7) {
    return process.argv[2].replace(/[^0-9]/g, '');
  }
  if (fs.existsSync(PHONE_FILE)) {
    try {
      const saved = fs.readFileSync(PHONE_FILE, 'utf8').trim().replace(/[^0-9]/g, '');
      if (saved.length >= 7) return saved;
    } catch (_) {}
  }
  return '33773163772';
}

// Helper: Save phone number to file
function savePhoneNumber(num) {
  try {
    const clean = num.replace(/[^0-9]/g, '');
    if (clean) {
      fs.writeFileSync(PHONE_FILE, clean, 'utf8');
      configuredPhoneNumber = clean;
    }
  } catch (_) {}
}

let activeAppUrl = null;

// Helper: Send event or message to Android App
async function sendToApp(endpoint, payload) {
  const urls = activeAppUrl ? [activeAppUrl, ...APP_URLS.filter(u => u !== activeAppUrl)] : APP_URLS;
  for (const baseUrl of urls) {
    try {
      const res = await fetch(`${'$'}{baseUrl}${'$'}{endpoint}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
        signal: AbortSignal.timeout(15000)
      });
      if (res.ok) {
        activeAppUrl = baseUrl;
        return await res.json();
      }
    } catch (_) {}
  }
  return null;
}

// Helper: Fetch config from Android App
async function fetchConfigFromApp() {
  for (const baseUrl of APP_URLS) {
    try {
      const res = await fetch(`${'$'}{baseUrl}/api/config?instanceId=${'$'}{INSTANCE_ID}`, {
        signal: AbortSignal.timeout(3000)
      });
      if (res.ok) {
        const data = await res.json();
        if (data && data.phoneNumber) return data.phoneNumber;
      }
    } catch (_) {}
  }
  return null;
}

// Trigger Pairing Code from Baileys
async function triggerPairingCode(phoneToUse) {
  if (isPairingRequested || (sock && sock.authState && sock.authState.creds && sock.authState.creds.registered)) {
    return;
  }

  const cleanPhone = String(phoneToUse || configuredPhoneNumber || '33773163772').replace(/[^0-9]/g, '').trim();
  if (!cleanPhone || cleanPhone.length < 7) {
    console.log('\n⚠️ Numéro WhatsApp non configuré pour le code d\'appairage.');
    console.log('👉 Entrez votre numéro ci-dessous ou relancez avec : node server.js <numéro>');
    console.log('   Exemple : node server.js 33773163772\n');
    promptUserForPhone();
    return;
  }

  isPairingRequested = true;
  savePhoneNumber(cleanPhone);

  console.log('\n⏳ Demande du code d\'appairage à WhatsApp pour le numéro : ' + cleanPhone + ' (sans signe +)...');

  try {
    // Wait until socket is ready
    await new Promise(resolve => setTimeout(resolve, 2000));
    if (!sock) {
      isPairingRequested = false;
      return;
    }

    const rawCode = await sock.requestPairingCode(cleanPhone);
    const formattedCode = rawCode && rawCode.length === 8
      ? `${'$'}{rawCode.slice(0, 4)}-${'$'}{rawCode.slice(4)}`
      : (rawCode || '');

    lastPairingCode = formattedCode;

    console.log('\n╔══════════════════════════════════════════════════════════════════╗');
    console.log('║                                                                  ║');
    console.log('║         🔑 VOTRE CODE D\'APPAIRAGE WHATSAPP (8 CHIFFRES) :        ║');
    console.log('║                                                                  ║');
    console.log(`║                    👉   ${'$'}{formattedCode.padEnd(10)}   👈                    ║`);
    console.log('║                                                                  ║');
    console.log('╚══════════════════════════════════════════════════════════════════╝');
    console.log('📞 Numéro lié : ' + cleanPhone + ' (chiffres purs, aucun signe +)');
    console.log('\n📲 COMMENT L\'UTILISER SUR VOTRE SMARTPHONE :');
    console.log(' 1. Ouvrez WhatsApp sur votre téléphone');
    console.log(' 2. Touchez les 3 points ⋮ (ou Réglages) > Appareils connectés');
    console.log(' 3. Touchez "Connecter un appareil"');
    console.log(' 4. En bas de l\'écran du scanner, touchez :');
    console.log('    👉 "Lier avec un numéro de téléphone"');
    console.log(` 5. Saisissez ce code à 8 chiffres : ${'$'}{formattedCode}`);
    console.log('==================================================================\n');

    await sendToApp('/api/event', {
      instanceId: INSTANCE_ID,
      event: 'pairing_code',
      pairingCode: formattedCode,
      phoneNumber: cleanPhone
    });
  } catch (err) {
    isPairingRequested = false;
    console.error('❌ Impossible d\'obtenir le code d\'appairage :', err.message);
  }
}

function promptUserForPhone() {
  const rl = readline.createInterface({
    input: process.stdin,
    output: process.stdout
  });
  rl.question('👉 Entrez votre numéro WhatsApp avec indicatif (ex: 33773163772) : ', async (answer) => {
    rl.close();
    const clean = answer.trim().replace(/[^0-9]/g, '');
    if (clean.length >= 7) {
      configuredPhoneNumber = clean;
      savePhoneNumber(clean);
      await triggerPairingCode(clean);
    } else {
      console.log('⚠️ Numéro trop court. Relancez avec : node server.js <numéro>');
    }
  });
}

// Start mini HTTP server in Termux
function startHttpServer(portToTry) {
  const server = http.createServer(async (req, res) => {
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type');

    if (req.method === 'OPTIONS') {
      res.writeHead(204);
      res.end();
      return;
    }

    // GET /status
    if (req.method === 'GET' && req.url === '/status') {
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({
        status: authStatus === 'CONNECTED' ? 'online' : 'waiting_pairing',
        authStatus: authStatus,
        instanceId: INSTANCE_ID,
        phone: configuredPhoneNumber || sock?.user?.id || '',
        pairingCode: lastPairingCode,
        messageCount: messageBuffer.length
      }));
      return;
    }

    // GET /pairing-code
    if (req.method === 'GET' && req.url === '/pairing-code') {
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({
        pairingCode: lastPairingCode,
        phone: configuredPhoneNumber
      }));
      return;
    }

    // POST /pairing-code (Trigger pairing code with a new phone number from Android App)
    if (req.method === 'POST' && req.url === '/pairing-code') {
      let body = '';
      req.on('data', chunk => { body += chunk; });
      req.on('end', async () => {
        try {
          const data = JSON.parse(body);
          if (data && data.phoneNumber) {
            isPairingRequested = false;
            configuredPhoneNumber = data.phoneNumber.replace(/[^0-9]/g, '');
            savePhoneNumber(configuredPhoneNumber);
            triggerPairingCode(configuredPhoneNumber);
            res.writeHead(200, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: true, phone: configuredPhoneNumber }));
          } else {
            res.writeHead(400, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ error: 'Numéro de téléphone requis' }));
          }
        } catch (e) {
          res.writeHead(500, { 'Content-Type': 'application/json' });
          res.end(JSON.stringify({ error: e.message }));
        }
      });
      return;
    }

    // GET /messages
    if (req.method === 'GET' && req.url === '/messages') {
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify(messageBuffer.slice(-50)));
      return;
    }

    // POST /send (Outgoing WhatsApp message / image from Android App via Baileys)
    if (req.method === 'POST' && req.url === '/send') {
      let body = '';
      req.on('data', chunk => { body += chunk; });
      req.on('end', async () => {
        try {
          const data = JSON.parse(body);
          if (!sock) {
            res.writeHead(400, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ error: 'Socket WhatsApp Baileys non connecté' }));
            return;
          }
          const targetJid = data.remoteJid || '18002428478@s.whatsapp.net';
          const captionText = data.text || data.caption || '';

          if (data.imageBase64) {
            const imageBuffer = Buffer.from(data.imageBase64, 'base64');
            console.log(`📤 [BAILEYS ENVOI IMAGE] Vers : ${'$'}{targetJid} (${'$'}{imageBuffer.length} octets) | Légende : "${'$'}{captionText.slice(0, 60)}..."`);
            const sent = await sock.sendMessage(targetJid, {
              image: imageBuffer,
              caption: captionText
            });
            res.writeHead(200, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: true, messageId: sent?.key?.id }));
          } else if (data.imagePath && fs.existsSync(data.imagePath)) {
            const imageBuffer = fs.readFileSync(data.imagePath);
            console.log(`📤 [BAILEYS ENVOI FICHIER] Vers : ${'$'}{targetJid} (${'$'}{data.imagePath})`);
            const sent = await sock.sendMessage(targetJid, {
              image: imageBuffer,
              caption: captionText
            });
            res.writeHead(200, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: true, messageId: sent?.key?.id }));
          } else if (captionText) {
            console.log(`📤 [BAILEYS ENVOI TEXTE] Vers : ${'$'}{targetJid} | Texte : "${'$'}{captionText}"`);
            try {
              await sock.sendPresenceUpdate('composing', targetJid);
              const textLen = captionText.length;
              const typingDelay = Math.min(2000, Math.max(500, Math.floor(textLen * 12)));
              await new Promise(resolve => setTimeout(resolve, typingDelay));
            } catch (e) {}

            const sent = await sock.sendMessage(targetJid, { text: captionText });

            try {
              await sock.sendPresenceUpdate('paused', targetJid);
            } catch (e) {}

            res.writeHead(200, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: true, messageId: sent?.key?.id }));
          } else {
            res.writeHead(400, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ error: 'Paramètres manquants (image ou texte)' }));
          }
        } catch (e) {
          console.error('Erreur /send Baileys :', e);
          res.writeHead(500, { 'Content-Type': 'application/json' });
          res.end(JSON.stringify({ error: e.message }));
        }
      });
      return;
    }

    // GET /media/:filename (Serve downloaded media)
    if (req.method === 'GET' && req.url.startsWith('/media/')) {
      const fileName = path.basename(req.url.replace('/media/', ''));
      const filePath = path.join(__dirname, 'received_media', fileName);
      if (fs.existsSync(filePath)) {
        res.writeHead(200, { 'Content-Type': 'image/jpeg' });
        fs.createReadStream(filePath).pipe(res);
        return;
      }
    }

    res.writeHead(404, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ error: 'Route introuvable' }));
  });

  server.on('error', (err) => {
    if (err.code === 'EADDRINUSE') {
      console.log(`ℹ️ Port ${'$'}{portToTry} déjà utilisé, tentative sur le port ${'$'}{portToTry + 5}...`);
      startHttpServer(portToTry + 5);
    } else {
      console.error('Erreur serveur HTTP local :', err.message);
    }
  });

  server.listen(portToTry, '0.0.0.0', () => {
    console.log(`🌐 Serveur API Termux prêt sur http://0.0.0.0:${'$'}{portToTry}`);
  });
}

async function startBaileys() {
  console.log(`\n======================================================`);
  console.log(`🤖 Pont Baileys WhatsApp Multi-Device pour AI Edge`);
  console.log(`🆔 Instance : ${'$'}{INSTANCE_ID}`);
  console.log(`🔑 Mode : Code d'Appairage à 8 Chiffres (QR Masqué)`);
  console.log(`📡 Passerelle App : ${'$'}{APP_URLS[0]}`);
  console.log(`======================================================\n`);

  // Load configured phone number
  configuredPhoneNumber = await resolvePhoneNumber();
  if (configuredPhoneNumber) {
    savePhoneNumber(configuredPhoneNumber);
    console.log(`📞 Numéro WhatsApp configuré : ${'$'}{configuredPhoneNumber} (chiffres uniquement)`);
  }

  const { state, saveCreds } = await useMultiFileAuthState(AUTH_DIR);

  // Fetch WhatsApp Web protocol version
  let version = [2, 3000, 1015901307];
  try {
    const wa = await fetchLatestWaWebVersion();
    if (wa && wa.version) version = wa.version;
  } catch (_) {
    try {
      const b = await fetchLatestBaileysVersion();
      if (b && b.version) version = b.version;
    } catch (_) {}
  }

  // Use canonical browser signature accepted by WhatsApp for pairing codes
  const browserConfig = (typeof Browsers !== 'undefined' && Browsers && Browsers.ubuntu)
    ? Browsers.ubuntu('Chrome')
    : ['Ubuntu', 'Chrome', '20.0.04'];

  sock = makeWASocket({
    version,
    auth: state,
    logger: pino({ level: 'silent' }),
    printQRInTerminal: false,
    browser: browserConfig,
    syncFullHistory: false,
    generateHighQualityLinkPreview: false,
    markOnlineOnConnect: true,
    connectTimeoutMs: 60000,
    defaultQueryTimeoutMs: 60000,
    keepAliveIntervalMs: 25000
  });

  sock.ev.on('creds.update', saveCreds);

  // If phone number is available, request pairing code
  if (USE_PAIRING_CODE && configuredPhoneNumber && !sock.authState.creds.registered) {
    setTimeout(() => {
      triggerPairingCode(configuredPhoneNumber);
    }, 2000);
  } else if (USE_PAIRING_CODE && !configuredPhoneNumber && !sock.authState.creds.registered) {
    setTimeout(promptUserForPhone, 1200);
  }

  // Connection Updates
  sock.ev.on('connection.update', async (update) => {
    const { connection, lastDisconnect, qr } = update;

    if (qr) {
      lastQr = qr;
      if (!USE_PAIRING_CODE) {
        console.log('\n📱 NOUVEAU QR CODE WHATSAPP :');
        qrcode.generate(qr, { small: true });
      } else {
        // Pairing code mode is active
        if (!isPairingRequested && configuredPhoneNumber && !sock.authState.creds.registered) {
          triggerPairingCode(configuredPhoneNumber);
        }
      }
      await sendToApp('/api/event', {
        instanceId: INSTANCE_ID,
        event: 'qr',
        qr: qr
      });
    }

    if (connection === 'close') {
      authStatus = 'DISCONNECTED';
      const statusCode = lastDisconnect?.error?.output?.statusCode;
      const shouldReconnect = statusCode !== DisconnectReason.loggedOut;
      console.log(`❌ Connexion fermée (code: ${'$'}{statusCode || 'non spécifié'}). Reconnexion : ${'$'}{shouldReconnect}`);
      await sendToApp('/api/event', {
        instanceId: INSTANCE_ID,
        event: 'connection.update',
        status: 'DISCONNECTED'
      });
      if (shouldReconnect) {
        isPairingRequested = false;
        setTimeout(startBaileys, 3000);
      }
    } else if (connection === 'open') {
      authStatus = 'CONNECTED';
      console.log('\n╔════════════════════════════════════════════════════════╗');
      console.log('║                                                        ║');
      console.log('║    ✅ [SUCCÈS] WHATSAPP CONNECTÉ ET AUTHENTIFIÉ !      ║');
      console.log('║                                                        ║');
      console.log('╚════════════════════════════════════════════════════════╝');
      console.log('🤖 Les messages reçus seront automatiquement traités par vos agents IA.\n');
      await sendToApp('/api/event', {
        instanceId: INSTANCE_ID,
        event: 'connection.update',
        status: 'CONNECTED'
      });
    }
  });

  // Handle incoming messages
  const processedMsgIds = new Set();

  sock.ev.on('messages.upsert', async (m) => {
    if (m.type !== 'notify') return;

    for (const msg of m.messages) {
      if (msg.key.fromMe) continue;

      const msgId = msg.key?.id;
      if (msgId && processedMsgIds.has(msgId)) {
        continue;
      }
      if (msgId) {
        processedMsgIds.add(msgId);
        if (processedMsgIds.size > 500) {
          const first = processedMsgIds.values().next().value;
          processedMsgIds.delete(first);
        }
      }

      const remoteJid = msg.key.remoteJid;
      const senderName = msg.pushName || 'Client WhatsApp';
      const text =
        msg.message?.conversation ||
        msg.message?.extendedTextMessage?.text ||
        msg.message?.imageMessage?.caption ||
        '';

      const isImage = !!(msg.message?.imageMessage);
      if (!text.trim() && !isImage) continue;

      let imageBase64 = null;
      let savedImagePath = null;

      if (isImage) {
        console.log(`🖼️ [BAILEYS IMAGE ENTRANTE] Téléchargement du média depuis WhatsApp...`);
        try {
          const buffer = await downloadMediaMessage(
            msg,
            'buffer',
            {},
            {
              logger: pino({ level: 'silent' }),
              reuploadRequest: sock.updateMediaMessage
            }
          );
          if (buffer && buffer.length > 0) {
            const mediaDir = path.join(__dirname, 'received_media');
            if (!fs.existsSync(mediaDir)) fs.mkdirSync(mediaDir, { recursive: true });
            const fileName = `img_${'$'}{Date.now()}_${'$'}{msgId || 'res'}.jpg`;
            savedImagePath = path.join(mediaDir, fileName);
            fs.writeFileSync(savedImagePath, buffer);
            imageBase64 = buffer.toString('base64');
            console.log(`✅ [BAILEYS IMAGE REÇUE ET SAUVEGARDÉE] : ${'$'}{savedImagePath} (${'$'}{buffer.length} octets)`);
          }
        } catch (mediaErr) {
          console.error(`⚠️ Erreur téléchargement image Baileys :`, mediaErr.message);
        }
      }

      console.log('--------------------------------------------------');
      console.log('[NOUVEAU MESSAGE REÇU]');
      console.log(`De      : ${'$'}{senderName} (${'$'}{remoteJid})`);
      console.log(`Message : "${'$'}{text}" ${'$'}{isImage ? '[Contient Image]' : ''}`);

      // Store in buffer for pulling
      messageBuffer.push({
        messageId: msgId || ('msg_' + Date.now()),
        remoteJid,
        senderName,
        text,
        isImage,
        imageBase64,
        imagePath: savedImagePath,
        timestamp: Date.now(),
        alreadyHandled: true
      });
      if (messageBuffer.length > 100) messageBuffer.shift();

      console.log(`📡 [ENVOI APP] Transfert vers l'application Android AI Edge...`);

      // Forward to Android App via Webhook
      const response = await sendToApp('/api/message', {
        instanceId: INSTANCE_ID,
        remoteJid: remoteJid,
        senderName: senderName,
        text: text,
        messageId: msgId || '',
        isImage: isImage,
        imageBase64: imageBase64,
        imagePath: savedImagePath
      });

      if (response && response.replyText && response.replyText.trim().length > 0 && !response.skipped && !response.humanMode) {
        console.log(`🤖 [RÉPONSE IA - ${'$'}{response.agentName || 'Agent'} | ${'$'}{response.latencyMs || 0}ms] : "${'$'}{response.replyText}"`);

        // Indicateur de frappe (typing indicator Baileys)
        try {
          await sock.sendPresenceUpdate('composing', remoteJid);
        } catch (presErr) {}

        // Délai de frappe proportionnel et crédible (entre 800ms et 2500ms selon longueur)
        const textLen = response.replyText.length;
        const typingDelay = Math.min(2500, Math.max(800, Math.floor(textLen * 15)));
        await new Promise(resolve => setTimeout(resolve, typingDelay));

        await sock.sendMessage(remoteJid, { text: response.replyText }, { quoted: msg });

        try {
          await sock.sendPresenceUpdate('paused', remoteJid);
        } catch (presErr) {}

        console.log(`🚀 [WHATSAPP] Réponse envoyée avec succès sur WhatsApp !`);
      } else {
        console.log(`👤 [MODE REPRISE HUMAINE] Aucun message IA envoyé à ${'$'}{remoteJid} (IA désactivée ou en attente)`);
      }
      console.log('--------------------------------------------------');
    }
  });
}

// Start HTTP server and Baileys
startHttpServer(LOCAL_HTTP_PORT);
startBaileys().catch(console.error);
"""

    const val DEFAULT_PHONE_NUMBER = "33773163772"

    /**
     * 1-line Termux fast update command that updates server.js while KEEPING existing WhatsApp auth session!
     */
    const val FAST_UPDATE_COMMAND = "killall node 2>/dev/null ; cd ~/wa-bridge && (curl -s http://127.0.0.1:8081/server.js > server.js 2>/dev/null || curl -s http://127.0.0.1:8080/server.js > server.js) && node server.js 33773163772"

    /**
     * Termux command to reset auth and request fresh 8-digit pairing code
     */
    const val PAIRING_CODE_COMMAND = "killall node 2>/dev/null ; cd ~/wa-bridge && rm -rf auth_info_baileys phone.txt auth_* && (curl -s http://127.0.0.1:8081/server.js > server.js 2>/dev/null || curl -s http://127.0.0.1:8080/server.js > server.js) && node server.js 33773163772"

    fun buildPairingCommand(phoneNumber: String = DEFAULT_PHONE_NUMBER): String {
        val clean = phoneNumber.replace(Regex("[^0-9]"), "").ifBlank { DEFAULT_PHONE_NUMBER }
        return "killall node 2>/dev/null ; cd ~/wa-bridge && rm -rf auth_info_baileys phone.txt auth_* && (curl -s http://127.0.0.1:8081/server.js > server.js 2>/dev/null || curl -s http://127.0.0.1:8080/server.js > server.js) && node server.js $clean"
    }

    fun buildTermuxOneLiner(phoneNumber: String = DEFAULT_PHONE_NUMBER): String {
        val clean = phoneNumber.replace(Regex("[^0-9]"), "").ifBlank { DEFAULT_PHONE_NUMBER }
        return "killall node 2>/dev/null ; pkg update -y && pkg install -y nodejs curl && mkdir -p ~/wa-bridge && cd ~/wa-bridge && rm -rf auth_info_baileys phone.txt auth_* && curl -s http://127.0.0.1:8081/server.js > server.js && npm install --no-audit @whiskeysockets/baileys pino qrcode-terminal && node server.js $clean"
    }

    fun buildFastUpdateCommand(phoneNumber: String = DEFAULT_PHONE_NUMBER): String {
        val clean = phoneNumber.replace(Regex("[^0-9]"), "").ifBlank { DEFAULT_PHONE_NUMBER }
        return "killall node 2>/dev/null ; cd ~/wa-bridge && (curl -s http://127.0.0.1:8081/server.js > server.js 2>/dev/null || curl -s http://127.0.0.1:8080/server.js > server.js) && node server.js $clean"
    }

    /**
     * 1-line Termux complete initialization command
     */
    const val TERMUX_ONE_LINER = "killall node 2>/dev/null ; pkg update -y && pkg install -y nodejs curl && mkdir -p ~/wa-bridge && cd ~/wa-bridge && rm -rf auth_info_baileys phone.txt auth_* && curl -s http://127.0.0.1:8081/server.js > server.js && npm install --no-audit @whiskeysockets/baileys pino qrcode-terminal && node server.js 33773163772"
}

