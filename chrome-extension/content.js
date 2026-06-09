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

// Listen for intercepted events from inject.js
window.addEventListener("shesafe-permission-request", function(event) {
    const requestDetails = event.detail;
    console.log("[SheSafe Companion] Content script received request event:", requestDetails);

    // Forward the request event to the service worker background.js
    chrome.runtime.sendMessage({
        type: "PERMISSION_INTERCEPTED",
        data: requestDetails
    });
});

// Listen for messages from background.js (sidepanel user action responses)
chrome.runtime.onMessage.addListener(function(message, sender, sendResponse) {
    if (message.type === "PERMISSION_RESOLVED") {
        console.log("[SheSafe Companion] Content script forwarding decision back to page context:", message);
        
        // Dispatch event back to page context (inject.js listener)
        const responseEvent = new CustomEvent("shesafe-permission-response", {
            detail: {
                id: message.id,
                decision: message.decision
            }
        });
        window.dispatchEvent(responseEvent);
    }
});
