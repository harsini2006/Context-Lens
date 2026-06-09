(function() {
    // Registry for holding original callbacks and promises
    const pendingRequests = new Map();

    // Store references to original native APIs
    const originalGetCurrentPosition = navigator.geolocation.getCurrentPosition;
    const originalGetUserMedia = navigator.mediaDevices ? navigator.mediaDevices.getUserMedia : null;
    const originalRequestPermission = typeof Notification !== 'undefined' ? Notification.requestPermission : null;

    console.log("[SheSafe Companion] Interception script injected successfully.");

    // Helper to send message to Content Script via DOM event
    function dispatchInterceptEvent(id, permissionType) {
        const event = new CustomEvent("shesafe-permission-request", {
            detail: {
                id: id,
                permission: permissionType,
                origin: window.location.origin
            }
        });
        window.dispatchEvent(event);
    }

    // 1. Geolocation Interception
    navigator.geolocation.getCurrentPosition = function(successCallback, errorCallback, options) {
        const id = "geo_" + Math.random().toString(36).substring(2, 11);
        console.log(`[SheSafe Companion] Intercepted Geolocation request on ${window.location.origin} (ID: ${id})`);
        
        pendingRequests.set(id, {
            resolve: (pos) => successCallback(pos),
            reject: (err) => {
                if (errorCallback) {
                    errorCallback(err || { code: 1, message: "User denied Geolocation" });
                }
            },
            fallback: () => {
                // Call original Geolocation
                originalGetCurrentPosition.call(navigator.geolocation, successCallback, errorCallback, options);
            }
        });

        dispatchInterceptEvent(id, "LOCATION");
    };

    // 2. Camera & Microphone (getUserMedia) Interception
    if (navigator.mediaDevices && originalGetUserMedia) {
        navigator.mediaDevices.getUserMedia = function(constraints) {
            const id = "media_" + Math.random().toString(36).substring(2, 11);
            const mediaType = (constraints.video && constraints.audio) ? "CAMERA & MICROPHONE" 
                            : constraints.video ? "CAMERA" 
                            : constraints.audio ? "MICROPHONE" 
                            : "MEDIA";

            console.log(`[SheSafe Companion] Intercepted getUserMedia request (${mediaType}) on ${window.location.origin} (ID: ${id})`);

            return new Promise((resolve, reject) => {
                pendingRequests.set(id, {
                    resolve: (stream) => resolve(stream),
                    reject: (err) => reject(err || new DOMException("Permission denied by user", "NotAllowedError")),
                    fallback: () => {
                        originalGetUserMedia.call(navigator.mediaDevices, constraints)
                            .then(resolve)
                            .catch(reject);
                    }
                });

                dispatchInterceptEvent(id, mediaType);
            });
        };
    }

    // 3. Notification Interception
    if (typeof Notification !== 'undefined' && originalRequestPermission) {
        Notification.requestPermission = function(callback) {
            const id = "notify_" + Math.random().toString(36).substring(2, 11);
            console.log(`[SheSafe Companion] Intercepted Notification permission request on ${window.location.origin} (ID: ${id})`);

            const promise = new Promise((resolve, reject) => {
                pendingRequests.set(id, {
                    resolve: (permissionState) => {
                        resolve(permissionState);
                        if (callback) callback(permissionState);
                    },
                    reject: () => {
                        resolve("denied");
                        if (callback) callback("denied");
                    },
                    fallback: () => {
                        originalRequestPermission().then(state => {
                            resolve(state);
                            if (callback) callback(state);
                        }).catch(reject);
                    }
                });

                dispatchInterceptEvent(id, "NOTIFICATIONS");
            });

            return promise;
        };
    }

    // Listen for responses back from Content Script
    window.addEventListener("shesafe-permission-response", function(event) {
        const { id, decision } = event.detail;
        const request = pendingRequests.get(id);
        
        if (request) {
            console.log(`[SheSafe Companion] Action response received for ID ${id}: ${decision}`);
            if (decision === "ALLOWED") {
                request.fallback(); // Delegate to native browser handler
            } else {
                request.reject();
            }
            pendingRequests.delete(id);
        }
    });
})();
