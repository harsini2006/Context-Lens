// Tab Switching Logic
const tabButtons = document.querySelectorAll('.tab-btn');
const tabContents = document.querySelectorAll('.tab-content');

tabButtons.forEach(btn => {
  btn.addEventListener('click', () => {
    tabButtons.forEach(b => b.classList.remove('active'));
    tabContents.forEach(c => c.classList.add('hidden'));

    btn.classList.add('active');
    const viewId = 'view-' + btn.id.split('-')[1];
    document.getElementById(viewId).classList.remove('hidden');

    if (viewId === 'view-logs') {
      renderLogs();
    }
  });
});

// App State variables
let currentAlertId = null;
let currentAlertPermission = "";
let currentAlertOrigin = "";

// DOM Elements
const noAlertsEl = document.getElementById('no-alerts');
const alertCardEl = document.getElementById('alert-card');
const riskBadgeEl = document.getElementById('risk-badge');
const riskTitleEl = document.getElementById('risk-title');
const siteOriginEl = document.getElementById('site-origin');
const consequenceDescEl = document.getElementById('consequence-desc');
const trustSealIconEl = document.getElementById('trust-seal-icon');

// Config mapping for consequence explanations
const permissionConfig = {
  "LOCATION": {
    title: "LOCATION ACCESS",
    desc: "THEY CAN SEE WHERE YOU ARE NOW.",
    risk: "HIGH"
  },
  "CAMERA": {
    title: "CAMERA ACCESS",
    desc: "THEY CAN TAKE PHOTOS AND VIDEOS.",
    risk: "MEDIUM"
  },
  "MICROPHONE": {
    title: "MICROPHONE ACCESS",
    desc: "THEY CAN RECORD YOUR VOICE AND SOUNDS.",
    risk: "HIGH"
  },
  "CAMERA & MICROPHONE": {
    title: "CAMERA & MICROPHONE ACCESS",
    desc: "THEY CAN RECORD BOTH AUDIO AND VIDEO.",
    risk: "HIGH"
  },
  "NOTIFICATIONS": {
    title: "NOTIFICATION ACCESS",
    desc: "THEY CAN SEND POPUPS AND ALERTS.",
    risk: "LOW"
  }
};

// Initialize Settings and active prompts
document.addEventListener('DOMContentLoaded', () => {
  loadSettings();
  checkForActiveAlerts();
});

// Settings handlers
const sealSelect = document.getElementById('seal-select');
const ttsToggle = document.getElementById('tts-toggle');

sealSelect.addEventListener('change', () => {
  const selectedSeal = sealSelect.value;
  trustSealIconEl.textContent = selectedSeal;
  chrome.storage.local.set({ personal_trust_seal: selectedSeal });
});

ttsToggle.addEventListener('change', () => {
  chrome.storage.local.set({ tts_enabled: ttsToggle.checked });
});

function loadSettings() {
  chrome.storage.local.get({
    personal_trust_seal: "🌻",
    tts_enabled: false
  }, (items) => {
    sealSelect.value = items.personal_trust_seal;
    trustSealIconEl.textContent = items.personal_trust_seal;
    ttsToggle.checked = items.tts_enabled;
  });
}

function checkForActiveAlerts() {
  chrome.runtime.sendMessage({ type: "GET_ACTIVE_REQUEST" }, (response) => {
    if (response) {
      renderAlert(response.id, response.origin, response.permission);
    }
  });
}

// Listen for push alerts from background service worker
chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  if (message.type === "RENDER_WARNING") {
    renderAlert(message.data.id, message.data.origin, message.data.permission);
  }
});

function renderAlert(id, origin, permission) {
  currentAlertId = id;
  currentAlertOrigin = origin;
  currentAlertPermission = permission;

  const config = permissionConfig[permission] || {
    title: (permission + " ACCESS").toUpperCase(),
    desc: "WEBSITE REQUESTS SENSITIVE WEB ACCESS.",
    risk: "MEDIUM"
  };

  alertCardEl.setAttribute('data-risk', config.risk);
  riskBadgeEl.textContent = config.risk + " RISK";
  riskTitleEl.textContent = config.title;
  siteOriginEl.textContent = origin.replace(/^https?:\/\//, '');
  consequenceDescEl.textContent = config.desc;

  // Show warning card
  noAlertsEl.classList.add('hidden');
  alertCardEl.classList.remove('hidden');

  // Trigger TTS if enabled
  chrome.storage.local.get({ tts_enabled: false }, (items) => {
    if (items.tts_enabled) {
      speakText(config.desc);
    }
  });
}

// Text-to-Speech logic
const btnSpeak = document.getElementById('btn-speak');
btnSpeak.addEventListener('click', () => {
  const text = consequenceDescEl.textContent;
  speakText(text);
});

function speakText(text) {
  if ('speechSynthesis' in window) {
    window.speechSynthesis.cancel(); // Stop current speech
    const utterance = new SpeechSynthesisUtterance(text);
    utterance.rate = 0.9;
    window.speechSynthesis.speak(utterance);
  }
}

// User decisions
document.getElementById('btn-block').addEventListener('click', () => {
  submitDecision("BLOCKED");
});

document.getElementById('btn-allow').addEventListener('click', () => {
  submitDecision("ALLOWED");
});

function submitDecision(decision) {
  if (!currentAlertId) return;

  chrome.runtime.sendMessage({
    type: "DECISION_MADE",
    data: {
      id: currentAlertId,
      decision: decision
    }
  });

  // Clear UI state
  alertCardEl.classList.add('hidden');
  noAlertsEl.classList.remove('hidden');
  currentAlertId = null;

  // Stop TTS if speaking
  if ('speechSynthesis' in window) {
    window.speechSynthesis.cancel();
  }
}

// History tab
function renderLogs() {
  const container = document.getElementById('logs-container');
  container.innerHTML = "";

  chrome.storage.local.get({ webConsentLogs: [] }, (result) => {
    const logs = result.webConsentLogs;

    if (logs.length === 0) {
      container.innerHTML = "<p class='empty-text'>No actions recorded yet.</p>";
      return;
    }

    logs.forEach(log => {
      const dateStr = new Date(log.timestamp).toLocaleTimeString();
      const div = document.createElement('div');
      div.className = 'log-item';
      div.innerHTML = `
        <div class="log-meta">
          <span>${dateStr}</span>
          <span class="log-decision ${log.decision.toLowerCase()}">${log.decision}</span>
        </div>
        <div class="log-title">${log.permission} REQUEST</div>
        <div style="font-size:12px; color:#555;">Website: ${log.origin.replace(/^https?:\/\//, '')}</div>
      `;
      container.appendChild(div);
    });
  });
}

document.getElementById('btn-clear-logs').addEventListener('click', () => {
  chrome.storage.local.set({ webConsentLogs: [] }, () => {
    renderLogs();
  });
});
