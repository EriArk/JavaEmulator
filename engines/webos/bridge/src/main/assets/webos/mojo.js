/* SPDX-License-Identifier: GPL-3.0-only */
(() => {
    if (window.Mojo) return;
    const element = id => typeof id === "string" ? document.getElementById(id) : id;
    window.$ = element;
    const Event = {
        tap: "mojo-tap", dragStart: "mojo-dragstart", dragEnd: "mojo-dragend",
        keydown: "keydown", keyup: "keyup", stageActivate: "mojo-activate",
        stageDeactivate: "mojo-deactivate", command: "mojo-command", back: "mojo-back",
        listen: (el, type, fn, capture) => element(el).addEventListener(type, fn, !!capture),
        stopListening: (el, type, fn, capture) => element(el).removeEventListener(type, fn, !!capture)
    };
    function send(target, type, props) {
        const event = new CustomEvent(type, {bubbles: true, cancelable: true});
        Object.assign(event, props);
        target.dispatchEvent(event);
    }
    let pointer;
    const point = e => ({x: e.clientX, y: e.clientY, pageX: e.pageX, pageY: e.pageY});
    document.addEventListener("pointerdown", e => {
        if (!e.isPrimary) return;
        pointer = {id: e.pointerId, down: point(e), target: e.target};
        send(e.target, Event.dragStart, {down: pointer.down, originalEvent: e});
    }, true);
    document.addEventListener("pointerup", e => {
        if (!pointer || e.pointerId !== pointer.id) return;
        const props = {down: pointer.down, up: point(e), originalEvent: e};
        send(pointer.target, Event.dragEnd, props);
        if (Math.hypot(props.up.x - props.down.x, props.up.y - props.down.y) < 12)
            send(pointer.target, Event.tap, props);
        pointer = null;
    }, true);
    document.addEventListener("pointercancel", () => { pointer = null; });
    for (const type of ["keydown", "keyup"])
        document.addEventListener(type, e => { e.originalEvent = e; }, true);

    class Cookie {
        constructor(name) { this.name = "mojo.cookie." + name; }
        get() { return JSON.parse(localStorage.getItem(this.name)); }
        put(value) { localStorage.setItem(this.name, JSON.stringify(value)); }
        remove() { localStorage.removeItem(this.name); }
    }
    let audio;
    const pitches = {back_01: 270, down2: 420, focusing: 780, card_02: 540};
    function serviceRequest(uri, request) {
        if (uri === "palm://com.palm.audio/systemsounds" && request.method === "playFeedback") {
            audio ||= new AudioContext();
            audio.resume();
            const start = audio.currentTime, tone = audio.createOscillator(), gain = audio.createGain();
            tone.frequency.value = pitches[request.parameters.name] || 600;
            gain.gain.setValueAtTime(0.14, start);
            gain.gain.exponentialRampToValueAtTime(0.001, start + 0.1);
            tone.connect(gain).connect(audio.destination);
            tone.start(start); tone.stop(start + 0.1);
            request.onSuccess?.({returnValue: true});
        } else request.onFailure?.({returnValue: false, errorText: "Unavailable webOS service"});
    }

    const widgets = new WeakMap(), scenes = [];
    function redraw(model) {
        const el = widgets.get(model);
        if (!el) return;
        el.textContent = model.label || "";
        el.setAttribute("aria-disabled", String(!!model.disabled));
        el.style.pointerEvents = model.disabled ? "none" : "";
    }
    function template(name) {
        const req = new XMLHttpRequest();
        req.open("GET", "/app/views/" + name + ".html", false);
        req.send();
        if (req.status !== 200) throw Error("Missing webOS view: " + name);
        return req.responseText;
    }
    const stage = {
        document, window, get: element, listen: Event.listen, stopListening: Event.stopListening,
        enableFullScreenMode() {}, setWindowProperties() {}, getSceneScroller: () => document.scrollingElement,
        setupWidget(id, attributes, model) {
            const el = element(id);
            if (el && model && el.getAttribute("x-mojo-element") === "Button") {
                widgets.set(model, el); el.setAttribute("role", "button"); redraw(model);
            }
        },
        modelChanged: redraw, serviceRequest,
        showDialog({template: view, assistant}) {
            const el = document.createElement("dialog");
            el.innerHTML = template(view);
            document.body.appendChild(el);
            el.mojo = {close() { assistant.deactivate?.(); assistant.cleanup?.(); el.close(); el.remove(); }};
            assistant.controller = stage;
            el.showModal(); assistant.setup?.(el); assistant.activate?.();
            return el;
        },
        pushScene(options, ...args) {
            const name = typeof options === "string" ? options : options.name;
            const previous = scenes.at(-1);
            if (previous) { previous.assistant.deactivate?.(); previous.el.hidden = true; }
            const el = document.createElement("section");
            el.innerHTML = template(name + "/" + name + "-scene");
            document.body.appendChild(el);
            const ctor = window[name.charAt(0).toUpperCase() + name.slice(1) + "Assistant"];
            const assistant = ctor ? new ctor(...args) : {};
            scenes.push({el, assistant}); assistant.controller = stage;
            assistant.setup?.(); assistant.activate?.();
        },
        popScene() {
            if (scenes.length < 2) return;
            const last = scenes.pop(); last.assistant.deactivate?.(); last.assistant.cleanup?.(); last.el.remove();
            const previous = scenes.at(-1); previous.el.hidden = false; previous.assistant.activate?.();
        }
    };
    stage.stageController = stage;
    window.Mojo = {Event, Model: {Cookie}, Menu: {appMenu: "appMenu", commandMenu: "commandMenu"},
        Char: {spaceBar: 32, p: 80, q: 81}, Log: {info: console.info, warn: console.warn, error: console.error},
        Environment: {DeviceInfo: {screenWidth: innerWidth, screenHeight: innerHeight, modelNameAscii: "Pre"}},
        Controller: {stageController: stage}, appPath: location.origin + "/"};
    document.addEventListener("DOMContentLoaded", async () => {
        try {
            const style = document.createElement("style");
            style.textContent = "html {touch-action:none} body {margin:0} dialog {box-sizing:border-box;max-width:96%;max-height:92%;overflow:auto} [x-mojo-element=Button] {padding:.45em;text-align:center;cursor:pointer}";
            document.head.appendChild(style);
            const response = await fetch("/sources.json");
            if (!response.ok) return;
            for (const entry of await response.json()) {
                if (!entry.source) continue;
                await new Promise((resolve, reject) => {
                    const script = document.createElement("script");
                    script.src = "/" + entry.source.replace(/^\//, "");
                    script.onload = resolve; script.onerror = () => reject(Error("Missing webOS script: " + entry.source));
                    document.head.appendChild(script);
                });
            }
            if (window.StageAssistant) {
                const assistant = new StageAssistant(stage); assistant.controller = stage; assistant.setup?.();
            }
        } catch (e) { prompt("abyss-webos-error", e.message); }
    });
})();
