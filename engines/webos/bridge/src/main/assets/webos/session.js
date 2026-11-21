/* SPDX-License-Identifier: GPL-3.0-only */
(() => {
    if (window !== top) return;
    const store = localStorage;
    const {setItem, removeItem, clear} = Storage.prototype;
    const previous = prompt("abyss-webos-restore", "");
    if (previous) {
        clear.call(store);
        for (const [key, value] of Object.entries(JSON.parse(previous))) setItem.call(store, key, value);
    }
    let last = previous;
    function save() {
        const encoded = JSON.stringify(store);
        if (encoded !== last && prompt("abyss-webos-save", encoded) === "ok") last = encoded;
    }
    for (const [name, method] of Object.entries({setItem, removeItem, clear})) {
        Storage.prototype[name] = function(...args) {
            const result = method.apply(this, args);
            if (this === store) save();
            return result;
        };
    }
    // Property assignment (localStorage.score = ...) bypasses the Storage methods.
    window.addEventListener("pagehide", save);
    setInterval(save, 1000);
    let factor = 1;
    const timeout = window.setTimeout.bind(window), interval = window.setInterval.bind(window);
    window.setTimeout = (fn, ms, ...args) => timeout(fn, ms / factor, ...args);
    window.setInterval = (fn, ms, ...args) => interval(fn, ms / factor, ...args);
    window.abyssWebSpeed = percent => { factor = percent / 100; };
    window.abyssWebActive = active => {
        if (!active) save();
        document.dispatchEvent(new Event(active ? "mojo-activate" : "mojo-deactivate"));
    };
})();
