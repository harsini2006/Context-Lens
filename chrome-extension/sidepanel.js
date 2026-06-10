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
    titleHindi: "स्थान पहुंच",
    desc: "THEY CAN SEE WHERE YOU ARE NOW.",
    descHindi: "वे देख सकते हैं कि आप अभी कहाँ हैं।",
    risk: "HIGH"
  },
  "CAMERA": {
    title: "CAMERA ACCESS",
    titleHindi: "कैमरा पहुंच",
    desc: "THEY CAN TAKE PHOTOS AND VIDEOS.",
    descHindi: "वे तस्वीरें और वीडियो ले सकते हैं।",
    risk: "MEDIUM"
  },
  "MICROPHONE": {
    title: "MICROPHONE ACCESS",
    titleHindi: "माइक पहुंच",
    desc: "THEY CAN RECORD YOUR VOICE AND SOUNDS.",
    descHindi: "वे आपकी आवाज़ और आवाज़ें रिकॉर्ड कर सकते हैं।",
    risk: "HIGH"
  },
  "CAMERA & MICROPHONE": {
    title: "CAMERA & MICROPHONE ACCESS",
    titleHindi: "कैमरा और माइक पहुंच",
    desc: "THEY CAN RECORD BOTH AUDIO AND VIDEO.",
    descHindi: "वे ऑडियो और वीडियो दोनों रिकॉर्ड कर सकते हैं।",
    risk: "HIGH"
  },
  "NOTIFICATIONS": {
    title: "NOTIFICATION ACCESS",
    titleHindi: "नोटिफिकेशन पहुंच",
    desc: "THEY CAN SEND POPUPS AND ALERTS.",
    descHindi: "वे पॉपअप और अलर्ट भेज सकते हैं।",
    risk: "LOW"
  }
};

let isHindi = false;

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

// Listen for push alerts and decisions from background service worker/content script
chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  if (message.type === "RENDER_WARNING") {
    renderAlert(message.data.id, message.data.origin, message.data.permission);
  } else if (message.type === "DECISION_MADE" || message.type === "PERMISSION_RESOLVED") {
    const resolvedId = message.type === "DECISION_MADE" ? message.data.id : message.id;
    if (currentAlertId === resolvedId) {
      alertCardEl.classList.add('hidden');
      noAlertsEl.classList.remove('hidden');
      currentAlertId = null;
      if ('speechSynthesis' in window) {
        window.speechSynthesis.cancel();
      }
      // Refresh log list if currently viewed
      const activeTab = document.querySelector('.tab-btn.active');
      if (activeTab && activeTab.id === 'tab-logs') {
        renderLogs();
      }
    }
  }
});

function updateAlertText(config) {
  riskTitleEl.textContent = isHindi ? config.titleHindi : config.title;
  consequenceDescEl.textContent = isHindi ? config.descHindi : config.desc;
}

function renderAlert(id, origin, permission) {
  currentAlertId = id;
  currentAlertOrigin = origin;
  currentAlertPermission = permission;

  const config = permissionConfig[permission] || {
    title: (permission + " ACCESS").toUpperCase(),
    titleHindi: (permission + " पहुंच").toUpperCase(),
    desc: "WEBSITE REQUESTS SENSITIVE WEB ACCESS.",
    descHindi: "वेबसाइट संवेदनशील पहुंच की मांग कर रही है।",
    risk: "MEDIUM"
  };

  alertCardEl.setAttribute('data-risk', config.risk);
  riskBadgeEl.textContent = config.risk + " RISK";
  
  updateAlertText(config);
  
  siteOriginEl.textContent = origin.replace(/^https?:\/\//, '');

  // Show warning card
  noAlertsEl.classList.add('hidden');
  alertCardEl.classList.remove('hidden');

  // Trigger TTS if enabled
  chrome.storage.local.get({ tts_enabled: false }, (items) => {
    if (items.tts_enabled) {
      speakText(isHindi ? config.descHindi : config.desc, isHindi ? 'hi-IN' : 'en-US');
    }
  });
}

// Language Toggle
const btnLang = document.getElementById('btn-lang');
btnLang.addEventListener('click', () => {
  isHindi = !isHindi;
  
  const config = permissionConfig[currentAlertPermission] || {
    title: (currentAlertPermission + " ACCESS").toUpperCase(),
    titleHindi: (currentAlertPermission + " पहुंच").toUpperCase(),
    desc: "WEBSITE REQUESTS SENSITIVE WEB ACCESS.",
    descHindi: "वेबसाइट संवेदनशील पहुंच की मांग कर रही है।",
    risk: "MEDIUM"
  };
  
  updateAlertText(config);
  
  btnLang.textContent = isHindi ? "English" : "हिंदी";
  const btnSpeak = document.getElementById('btn-speak');
  btnSpeak.textContent = isHindi ? "🔊 सुनें" : "🔊 Read Out Loud";

  chrome.storage.local.get({ tts_enabled: false }, (items) => {
    if (items.tts_enabled) {
      speakText(isHindi ? config.descHindi : config.desc, isHindi ? 'hi-IN' : 'en-US');
    }
  });
});

// Text-to-Speech logic
const btnSpeak = document.getElementById('btn-speak');
btnSpeak.addEventListener('click', () => {
  const config = permissionConfig[currentAlertPermission] || {
    desc: consequenceDescEl.textContent,
    descHindi: consequenceDescEl.textContent
  };
  const text = isHindi ? config.descHindi : config.desc;
  speakText(text, isHindi ? 'hi-IN' : 'en-US');
});

function speakText(text, langCode) {
  if ('speechSynthesis' in window) {
    window.speechSynthesis.cancel(); // Stop current speech
    const utterance = new SpeechSynthesisUtterance(text);
    utterance.lang = langCode || 'en-US';
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
