// Inject inject.js into the web page's execution context
try {
    const script = document.createElement('script');
    script.src = chrome.runtime.getURL('inject.js');
    script.onload = function() {
        this.remove();
    };
    (document.head || document.documentElement).appendChild(script);
} catch (e) {
    console.error("[SheSafe Companion] Script injection failed:", e);
}

// Config mapping for consequence explanations (matching AppDatabase and themes)
const permissionConfig = {
  "LOCATION": {
    title: "LOCATION ACCESS",
    titleHindi: "स्थान पहुंच",
    desc: "THEY CAN SEE WHERE YOU ARE NOW.",
    descHindi: "वे देख सकते हैं कि आप अभी कहाँ हैं।",
    risk: "HIGH",
    recommend: "RECOMMENDED ACTION: BLOCK",
    recommendHindi: "सलाह: अनुमति ब्लॉक करें (BLOCK)"
  },
  "CAMERA": {
    title: "CAMERA ACCESS",
    titleHindi: "कैमरा पहुंच",
    desc: "THEY CAN TAKE PHOTOS AND VIDEOS.",
    descHindi: "वे तस्वीरें और वीडियो ले सकते हैं।",
    risk: "MEDIUM",
    recommend: "RECOMMENDED ACTION: ALLOW",
    recommendHindi: "सलाह: अनुमति प्रदान करें (ALLOW)"
  },
  "MICROPHONE": {
    title: "MICROPHONE ACCESS",
    titleHindi: "माइक पहुंच",
    desc: "THEY CAN RECORD YOUR VOICE AND SOUNDS.",
    descHindi: "वे आपकी आवाज़ और आवाज़ें रिकॉर्ड कर सकते हैं।",
    risk: "HIGH",
    recommend: "RECOMMENDED ACTION: BLOCK",
    recommendHindi: "सलाह: अनुमति ब्लॉक करें (BLOCK)"
  },
  "CAMERA & MICROPHONE": {
    title: "CAMERA & MICROPHONE ACCESS",
    titleHindi: "कैमरा और माइक पहुंच",
    desc: "THEY CAN RECORD BOTH AUDIO AND VIDEO.",
    descHindi: "वे ऑडियो और वीडियो दोनों रिकॉर्ड कर सकते हैं।",
    risk: "HIGH",
    recommend: "RECOMMENDED ACTION: BLOCK",
    recommendHindi: "सलाह: अनुमति ब्लॉक करें (BLOCK)"
  },
  "NOTIFICATIONS": {
    title: "NOTIFICATION ACCESS",
    titleHindi: "नोटिफिकेशन पहुंच",
    desc: "THEY CAN SEND POPUPS AND ALERTS.",
    descHindi: "वे पॉपअप और अलर्ट भेज सकते हैं।",
    risk: "LOW",
    recommend: "RECOMMENDED ACTION: ALLOW",
    recommendHindi: "सलाह: अनुमति प्रदान करें (ALLOW)"
  }
};

let activeOverlay = null;

// Listen for intercepted events from inject.js
window.addEventListener("shesafe-permission-request", function(event) {
    const requestDetails = event.detail;
    console.log("[SheSafe Companion] Content script received request event:", requestDetails);

    // 1. Forward to background to ensure logs and side panel checks are up to date
    chrome.runtime.sendMessage({
        type: "PERMISSION_INTERCEPTED",
        data: requestDetails
    });

    // 2. Render the beautiful, secure in-page overlay directly
    showInPageOverlay(requestDetails.id, requestDetails.permission, requestDetails.origin);
});

// Listen for messages from background.js (sidepanel user action responses)
chrome.runtime.onMessage.addListener(function(message, sender, sendResponse) {
    if (message.type === "PERMISSION_RESOLVED") {
        console.log("[SheSafe Companion] Content script forwarding decision back to page context:", message);
        
        // Remove active overlay if it was resolved from the sidepanel
        if (activeOverlay && activeOverlay.dataset.requestId === message.id) {
            removeOverlay();
        }

        // Dispatch event back to page context (inject.js listener)
        dispatchResponseToPage(message.id, message.decision);
    }
});

function dispatchResponseToPage(id, decision) {
    const responseEvent = new CustomEvent("shesafe-permission-response", {
        detail: {
            id: id,
            decision: decision
        }
    });
    window.dispatchEvent(responseEvent);
}

function removeOverlay() {
    if (activeOverlay) {
        if ('speechSynthesis' in window) {
            window.speechSynthesis.cancel();
        }
        activeOverlay.remove();
        activeOverlay = null;
    }
}

function showInPageOverlay(id, permission, origin) {
    // Prevent duplicate overlays
    removeOverlay();

    const config = permissionConfig[permission] || {
        title: (permission + " ACCESS").toUpperCase(),
        titleHindi: (permission + " पहुंच").toUpperCase(),
        desc: "WEBSITE REQUESTS SENSITIVE WEB ACCESS.",
        descHindi: "वेबसाइट संवेदनशील पहुंच की मांग कर रही है।",
        risk: "MEDIUM",
        recommend: "RECOMMENDED ACTION: BLOCK",
        recommendHindi: "सलाह: अनुमति ब्लॉक करें (BLOCK)"
    };

    // Retrieve storage settings
    chrome.storage.local.get({
        personal_trust_seal: "🌻",
        tts_enabled: false
    }, (settings) => {
        createOverlayDOM(id, config, origin, settings);
    });
}

function createOverlayDOM(id, config, origin, settings) {
    const host = document.createElement('div');
    host.id = 'shesafe-overlay-host';
    host.dataset.requestId = id;
    activeOverlay = host;

    const shadowRoot = host.attachShadow({ mode: 'closed' });

    // Inject styles and HTML
    shadowRoot.innerHTML = `
        <style>
            .overlay-backdrop {
                position: fixed;
                top: 0;
                left: 0;
                width: 100vw;
                height: 100vh;
                background-color: rgba(11, 15, 25, 0.85);
                backdrop-filter: blur(8px);
                display: flex;
                align-items: center;
                justify-content: center;
                z-index: 2147483647;
                font-family: 'Outfit', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                animation: fadeIn 0.25s ease-out;
            }
            @keyframes fadeIn {
                from { opacity: 0; }
                to { opacity: 1; }
            }
            .alert-card {
                position: relative;
                background: #161E30;
                border-radius: 24px;
                padding: 28px;
                width: 440px;
                max-width: 90%;
                color: #ECEFF1;
                text-align: center;
                box-shadow: 0 20px 50px rgba(0, 0, 0, 0.5);
                display: flex;
                flex-direction: column;
                align-items: center;
                gap: 16px;
                animation: slideIn 0.3s cubic-bezier(0.16, 1, 0.3, 1);
                box-sizing: border-box;
            }
            @keyframes slideIn {
                from { transform: translateY(24px); opacity: 0; }
                to { transform: translateY(0); opacity: 1; }
            }
            .trust-seal-wrapper {
                position: absolute;
                top: 18px;
                right: 18px;
                background: #0B0F19;
                border: 1px solid #00B0FF;
                width: 34px;
                height: 34px;
                display: flex;
                align-items: center;
                justify-content: center;
                border-radius: 8px;
                font-size: 18px;
                box-shadow: 0 4px 10px rgba(0,0,0,0.3);
            }
            .badge {
                padding: 6px 14px;
                font-size: 11px;
                font-weight: 800;
                border-radius: 12px;
                text-transform: uppercase;
                letter-spacing: 0.5px;
            }
            .alert-card[data-risk="HIGH"] {
                border: 2px solid #FF1744;
            }
            .alert-card[data-risk="HIGH"] .badge {
                background: rgba(255, 23, 68, 0.12);
                border: 1.2px solid #FF1744;
                color: #FF1744;
            }
            .alert-card[data-risk="MEDIUM"] {
                border: 2px solid #FFB300;
            }
            .alert-card[data-risk="MEDIUM"] .badge {
                background: rgba(255, 179, 0, 0.12);
                border: 1.2px solid #FFB300;
                color: #FFB300;
            }
            .alert-card[data-risk="LOW"] {
                border: 2px solid #00E676;
            }
            .alert-card[data-risk="LOW"] .badge {
                background: rgba(0, 230, 118, 0.12);
                border: 1.2px solid #00E676;
                color: #00E676;
            }
            .origin-tag {
                font-size: 13px;
                color: #90A4AE;
                margin: 0;
                font-weight: 700;
            }
            .site-origin {
                color: #ECEFF1;
            }
            .risk-title {
                font-size: 24px;
                font-weight: 900;
                margin: 0;
                letter-spacing: 0.5px;
            }
            .consequence-container {
                background: #0B0F19;
                border: 1px solid #23304A;
                padding: 18px;
                border-radius: 16px;
                width: 100%;
                box-sizing: border-box;
            }
            .consequence-desc {
                font-size: 16px;
                font-weight: 600;
                line-height: 1.5;
                color: #ECEFF1;
                margin: 0;
            }
            .recommended-action {
                font-weight: 800;
                font-size: 14px;
                margin-top: 4px;
                letter-spacing: 0.5px;
            }
            .alert-card[data-risk="HIGH"] .recommended-action {
                color: #FF1744;
            }
            .alert-card[data-risk="MEDIUM"] .recommended-action {
                color: #FFB300;
            }
            .alert-card[data-risk="LOW"] .recommended-action {
                color: #00E676;
            }
            .action-row {
                display: flex;
                gap: 12px;
                width: 100%;
            }
            .btn-lang, .btn-speak {
                flex: 1;
                padding: 10px 16px;
                font-size: 12px;
                font-weight: 700;
                border-radius: 20px;
                cursor: pointer;
                background: rgba(0, 176, 255, 0.08);
                border: 1px solid #00B0FF;
                color: #00B0FF;
                transition: all 0.2s;
                font-family: inherit;
            }
            .btn-speak {
                background: rgba(0, 230, 118, 0.08);
                border: 1px solid #00E676;
                color: #00E676;
            }
            .btn-lang:hover, .btn-speak:hover {
                filter: brightness(1.2);
            }
            .decision-buttons {
                display: flex;
                gap: 12px;
                width: 100%;
                margin-top: 4px;
            }
            .btn-block, .btn-allow {
                flex: 1;
                padding: 12px;
                font-size: 13px;
                font-weight: 900;
                border-radius: 24px;
                border: none;
                cursor: pointer;
                transition: all 0.2s;
                font-family: inherit;
            }
            .btn-block {
                background: #FF1744;
                color: #0B0F19;
            }
            .btn-allow {
                background: #00E676;
                color: #0B0F19;
            }
            .btn-block:hover, .btn-allow:hover {
                filter: brightness(1.1);
                transform: translateY(-1px);
            }
        </style>
        <div class="overlay-backdrop">
            <div class="alert-card" data-risk="${config.risk}">
                <div class="trust-seal-wrapper" title="Personal Trust Seal">
                    <span class="trust-seal-icon">${settings.personal_trust_seal}</span>
                </div>
                <div class="alert-header">
                    <span class="badge">${config.risk} RISK</span>
                    <h2 class="risk-title" id="el-title">${config.title}</h2>
                </div>
                <div class="card-body">
                    <p class="origin-tag">Website: <strong class="site-origin">${origin.replace(/^https?:\/\//, '')}</strong></p>
                    <div class="consequence-container">
                        <p class="consequence-desc" id="el-desc">${config.desc}</p>
                    </div>
                    <div class="recommended-action" id="el-recommend">${config.recommend}</div>
                </div>
                <div class="action-row">
                    <button class="btn-lang" id="btn-toggle-lang">हिंदी</button>
                    <button class="btn-speak" id="btn-play-audio">🔊 Read Aloud</button>
                </div>
                <div class="decision-buttons">
                    <button class="btn-block" id="btn-action-block">BLOCK ACCESS</button>
                    <button class="btn-allow" id="btn-action-allow">ALLOW ACCESS</button>
                </div>
            </div>
        </div>
    `;

    document.documentElement.appendChild(host);

    // Add interactivity
    let isHindi = false;
    const titleEl = shadowRoot.getElementById('el-title');
    const descEl = shadowRoot.getElementById('el-desc');
    const recommendEl = shadowRoot.getElementById('el-recommend');
    const langBtn = shadowRoot.getElementById('btn-toggle-lang');
    const speakBtn = shadowRoot.getElementById('btn-play-audio');
    const blockBtn = shadowRoot.getElementById('btn-action-block');
    const allowBtn = shadowRoot.getElementById('btn-action-allow');

    function speakText(text, langCode) {
        if ('speechSynthesis' in window) {
            window.speechSynthesis.cancel();
            const utterance = new SpeechSynthesisUtterance(text);
            utterance.lang = langCode;
            utterance.rate = 0.95;
            window.speechSynthesis.speak(utterance);
        }
    }

    langBtn.addEventListener('click', () => {
        isHindi = !isHindi;
        if (isHindi) {
            titleEl.textContent = config.titleHindi;
            descEl.textContent = config.descHindi;
            recommendEl.textContent = config.recommendHindi;
            langBtn.textContent = "English";
            speakBtn.textContent = "🔊 सुनें";
        } else {
            titleEl.textContent = config.title;
            descEl.textContent = config.desc;
            recommendEl.textContent = config.recommend;
            langBtn.textContent = "हिंदी";
            speakBtn.textContent = "🔊 Read Aloud";
        }
        if (settings.tts_enabled) {
            const voiceText = isHindi ? config.descHindi : config.desc;
            const langCode = isHindi ? 'hi-IN' : 'en-US';
            speakText(voiceText, langCode);
        }
    });

    speakBtn.addEventListener('click', () => {
        const voiceText = isHindi ? config.descHindi : config.desc;
        const langCode = isHindi ? 'hi-IN' : 'en-US';
        speakText(voiceText, langCode);
    });

    function submitDecision(decision) {
        // 1. Dispatch response event to inject.js
        dispatchResponseToPage(id, decision);

        // 2. Notify background service worker to update storage logs
        chrome.runtime.sendMessage({
            type: "DECISION_MADE",
            data: {
                id: id,
                decision: decision
            }
        });

        // 3. Remove overlay from page
        removeOverlay();
    }

    blockBtn.addEventListener('click', () => submitDecision("BLOCKED"));
    allowBtn.addEventListener('click', () => submitDecision("ALLOWED"));

    // Automatically speak if TTS enabled
    if (settings.tts_enabled) {
        speakText(config.desc, 'en-US');
    }
}
