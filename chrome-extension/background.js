let activeRequests = new Map();

// Listen for messages from content scripts or sidepanel
chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  console.log("[SheSafe Service Worker] Message received:", message);

  if (message.type === "PERMISSION_INTERCEPTED") {
    const tabId = sender.tab.id;
    const requestData = message.data;
    
    // Store request mapping to handle multiple tabs concurrently
    activeRequests.set(requestData.id, {
      tabId: tabId,
      origin: requestData.origin,
      permission: requestData.permission
    });

    // 1. Open the side panel for this tab
    if (chrome.sidePanel && chrome.sidePanel.open) {
      chrome.sidePanel.open({ tabId: tabId })
        .then(() => {
          console.log("[SheSafe Service Worker] Side Panel opened successfully.");
          // Wait briefly for sidepanel to load and request active data
          setTimeout(() => {
            chrome.runtime.sendMessage({
              type: "RENDER_WARNING",
              data: {
                id: requestData.id,
                origin: requestData.origin,
                permission: requestData.permission
              }
            });
          }, 300);
        })
        .catch((err) => {
          console.error("[SheSafe Service Worker] Error opening side panel:", err);
        });
    }
  }

  else if (message.type === "GET_ACTIVE_REQUEST") {
    // Send the latest request details back to the newly opened side panel
    const latestRequest = Array.from(activeRequests.entries()).pop();
    if (latestRequest) {
      sendResponse({
        id: latestRequest[0],
        origin: latestRequest[1].origin,
        permission: latestRequest[1].permission
      });
    } else {
      sendResponse(null);
    }
  }

  else if (message.type === "DECISION_MADE") {
    const { id, decision } = message.data;
    const request = activeRequests.get(id);

    if (request) {
      // Forward the decision to the specific content script tab
      chrome.tabs.sendMessage(request.tabId, {
        type: "PERMISSION_RESOLVED",
        id: id,
        decision: decision
      });

      // Save log to local storage for local audit history
      chrome.storage.local.get({ webConsentLogs: [] }, (result) => {
        const logs = result.webConsentLogs;
        logs.unshift({
          id: id,
          timestamp: Date.now(),
          origin: request.origin,
          permission: request.permission,
          decision: decision
        });
        chrome.storage.local.set({ webConsentLogs: logs });
      });

      activeRequests.delete(id);
    }
  }
});
