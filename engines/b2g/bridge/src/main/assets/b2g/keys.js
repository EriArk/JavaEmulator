/* SPDX-License-Identifier: GPL-3.0-only */
(() => {
    window.abyssPhoneKey = (key, down) => {
        const code = {SoftLeft: 112, SoftRight: 113, Backspace: 8, "*": 42, "#": 35}[key];
        const event = new KeyboardEvent(down ? "keydown" : "keyup", {
            key, code: key, keyCode: code, which: code, bubbles: true, cancelable: true
        });
        (document.activeElement || document.body || document).dispatchEvent(event);
    };
    // Legacy B2G apps can inspect their own packaged manifest without a store or a network.
    if (document.currentScript.dataset.legacy === "true" && !navigator.mozApps) {
        const manifestURL = new URL("/manifest.webapp", location.origin).href;
        Object.defineProperty(navigator, "mozApps", {value: {
            getSelf() {
                const request = new EventTarget();
                request.readyState = "pending";
                fetch(manifestURL).then(response => response.json()).then(manifest => {
                    request.result = {manifest, manifestURL, origin: location.origin, installOrigin: location.origin};
                    request.readyState = "done";
                    const event = new Event("success");
                    request.dispatchEvent(event);
                    request.onsuccess?.call(request, event);
                }, () => {
                    request.error = new DOMException("Cannot read package manifest", "NotFoundError");
                    request.readyState = "done";
                    const event = new Event("error");
                    request.dispatchEvent(event);
                    request.onerror?.call(request, event);
                });
                return request;
            }
        }});
    }
})();
