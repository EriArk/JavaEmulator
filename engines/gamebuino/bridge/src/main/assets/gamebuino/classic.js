// SPDX-License-Identifier: GPL-3.0-only
(async () => {
    const response = await fetch("/game.hex");
    if (!response.ok) throw Error("Cannot read game program");
    const records = (await response.text()).split(/\r?\n/).map(line => line.trim()).filter(Boolean);
    AtmelContext.Init();
    AtmelProcessor.Init();
    ADC.Init();
    Buttons.Init();
    SPI.Init();
    USART.Init();
    EEPROM.Init();
    Lcd.Init(document.querySelector("canvas").getContext("2d"));
    SdDevice.Init();
    AtmelContext.Reset();
    HexDecoder.Decode(Bootloader);
    if (!HexDecoder.Decode(records)) throw Error("Cannot decode Gamebuino program");
    AtmelProcessor.InitInstrTable();
    Lcd.Persistence = true;
    Lcd.Reset();
    Buttons.Reset();
    SdDevice.Reset();

    const encode = bytes => btoa(String.fromCharCode(...bytes));
    const decode = text => Uint8Array.from(atob(text), c => c.charCodeAt(0));
    const saved = localStorage.getItem("eeprom");
    if (saved) AtmelContext.EEPROM = Array.from(decode(saved));
    let dirty = false;
    AtmelContext.RAM[AtmelIO.EECR].OnRegisterChanged.push((old, value) => {
        if (value & (1 << AtmelIO.EEPE)) dirty = true;
    });
    function flush() {
        if (!dirty) return;
        localStorage.setItem("eeprom", encode(AtmelContext.EEPROM));
        dirty = false;
    }

    // The SPI device presents a byte-addressed card. Cache the current sector so a guest
    // transfers 512 bytes per host call, even when mounting a large user-supplied image.
    let cachedBlock = -1, bytes;
    function sector(address) {
        const block = Math.floor((address >>> 0) / 512);
        if (block !== cachedBlock) {
            const raw = prompt("classic-read", String(block));
            if (!raw) throw Error("SD card read failed");
            bytes = decode(raw);
            cachedBlock = block;
        }
        return bytes;
    }
    SdDevice.ReadBuffer = new Proxy({}, {
        get(_, index) {
            if (index === "length") return 0x100000000;
            return sector(Number(index))[Number(index) & 511];
        },
        set(_, index, value) {
            const address = Number(index);
            sector(address)[address & 511] = value;
            if (SdDevice.WriteCount === 511 &&
                    prompt("classic-write", cachedBlock + "," + encode(bytes)) !== "ok")
                throw Error("SD card write failed");
            return true;
        }
    });
    let frame = 0, speed = 100, active = true;
    const pressed = new Map(), released = new Set();
    window.classicKey = (key, down) => {
        if (down) {
            Buttons[key]().set(true);
            pressed.set(key, frame);
            released.delete(key);
        } else released.add(key);
    };
    const audio = AudioPlayer.create().Context;
    if (audio) audio.resume();
    document.addEventListener("mojo-deactivate", () => {
        active = false;
        Buttons.Reset(); pressed.clear(); released.clear();
        flush();
        if (audio) audio.suspend();
    });
    document.addEventListener("mojo-activate", () => { active = true; if (audio) audio.resume(); });
    window.abyssWebSpeed = percent => { speed = percent; };
    prompt("classic-ready", "");
    let previousTime = performance.now();
    const timer = setInterval(() => {
        try {
            const now = performance.now();
            const elapsed = Math.min(40, now - previousTime);
            previousTime = now;
            if (!active) return;
            AtmelProcessor.RunTo(AtmelContext.Clock + Math.round(elapsed * AtmelProcessor.ClockSpeed * speed / 100000));
            frame++;
            for (const key of released) {
                if (frame - (pressed.get(key) || 0) >= 4) {
                    Buttons[key]().set(false); pressed.delete(key); released.delete(key);
                }
            }
            flush();
        } catch (e) {
            clearInterval(timer);
            if (audio) audio.suspend();
            prompt("abyss-webos-error", e.message);
        }
    }, 1000 / 60);
})().catch(e => prompt("abyss-webos-error", e.message));
