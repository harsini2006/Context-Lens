let activeRequests = new Map();

// Listen for messages from content scripts or sidepanel
chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  console.log("[SheSafe Service Worker] Message received:", message);

  if (message.type === "PERMISSION_INTERCEPTED") {
    const tabId = sender.tab ? sender.tab.id : null;
    const requestData = message.data;
    
    // Store request mapping to handle multiple tabs concurrently
    activeRequests.set(requestData.id, {
      tabId: tabId,
      origin: requestData.origin,
      permission: requestData.permission
    });

    // 1. Open the side panel for this tab (will fail on background trigger, which is fine since we fallback to in-page overlay)
    if (tabId && chrome.sidePanel && chrome.sidePanel.open) {
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
          console.warn("[SheSafe Service Worker] Auto-opening side panel blocked (missing gesture). Falling back to in-page overlay. Details:", err.message);
          // Send warning anyway in case the sidepanel is already open manually
          chrome.runtime.sendMessage({
            type: "RENDER_WARNING",
            data: {
              id: requestData.id,
              origin: requestData.origin,
              permission: requestData.permission
            }
          });
        });
    } else {
      chrome.runtime.sendMessage({
        type: "RENDER_WARNING",
        data: {
          id: requestData.id,
          origin: requestData.origin,
          permission: requestData.permission
        }
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
      if (request.tabId) {
        chrome.tabs.sendMessage(request.tabId, {
          type: "PERMISSION_RESOLVED",
          id: id,
          decision: decision
        });
      }

      // Save log to local storage for local audit history
      chrome.storage.local.get({ webConsentLogs: [] }, (result) => {
        const logs = result.webConsentLogs;
        // Avoid duplicate logging
        if (!logs.some(l => l.id === id)) {
          logs.unshift({
            id: id,
            timestamp: Date.now(),
            origin: request.origin,
            permission: request.permission,
            decision: decision
          });
          chrome.storage.local.set({ webConsentLogs: logs });
        }
      });

      activeRequests.delete(id);
    }
  }
});
