/* SPDX-License-Identifier: GPL-3.0-only */
'use strict';
const settings = JSON.parse(PalmHost.config());
let emu, hardware, busy = false, framePending = false, hostPaused = false;
async function setPaused(value) {
    hostPaused = value;
    if (value) await emu.pause(); else if (!busy) await emu.resume();
}
const problem = error => PalmHost.error(String(error));
async function bytes(path) {
    const response = await fetch(path);
    if (!response.ok) throw Error('Missing Palm file: ' + path);
    return new Uint8Array(await response.arrayBuffer());
}
function paint() {
    const canvas = emu.emulationService.getCanvas();
    PalmHost.picture(canvas.toDataURL('image/png').split(',')[1]);
}
function refresh() {
    if (framePending) return;
    framePending = true;
    setTimeout(() => { framePending = false; paint(); }, 32);
}
async function installGame() {
    for (let index = 0; index < settings.count; index++) {
        const path = 'db' + index;
        if (path !== settings.app) await emu.installDatabase(await bytes(path));
    }
    if (settings.app) await emu.installAndLaunchDatabase(await bytes(settings.app));
}
function key(id, down) {
    // Palm Calendar, Contacts, To Do, Notes and page keys on the common input deck.
    const mapping = {8:0, 0:1, 9:2, 1:3, 4:4, 5:5};
    if (id in mapping) {
        if (down) emu.buttonDown(mapping[id]); else emu.buttonUp(mapping[id]);
    }
}
async function saveSession(exitAfter = false) {
    if (busy) throw Error('A Palm session is already being saved');
    busy = true;
    let writing = false;
    try {
        await emu.pause();
        const state = await emu.emulationService.getEngine().getFullState();
        const data = emu.cloudpilot.serializeSessionImage({...state, engine:hardware.engine,
            deviceId:emu.session.deviceId, ramSize:hardware.recommendedRamSize || state.memory.byteLength,
            metadata:{hotsyncName:await emu.getHotsyncName()}});
        if (!data || !PalmHost.begin()) throw Error('Cannot create Palm session');
        writing = true;
        for (let i=0; i<data.length; i+=24576) {
            if (!PalmHost.chunk(btoa(String.fromCharCode(...data.subarray(i,i+24576))))) throw Error('Cannot write Palm session');
        }
        PalmHost.end(true);
        writing = false;
        if (exitAfter) PalmHost.savedAndClosed();
        else PalmHost.notice('Palm session saved');
    } finally {
        if (writing) PalmHost.end(false);
        busy = false;
        if (!hostPaused && !exitAfter) await emu.resume();
    }
}
async function boot() {
    emu = await cloudpilot.createEmulator();
    const rom = await bytes('rom');
    hardware = emu.cloudpilot.getRomInfo(rom);
    if (!hardware?.supportedDevices?.length) throw Error('CloudpilotEmu could not identify this device ROM.');
    if (settings.checkpoint) await emu.loadSession(await bytes(settings.checkpoint));
    else {
        const nand = hardware.needsNand ? await bytes('nand') : undefined;
        if (nand && hardware.supportedDevices.includes('Tungsten-E2') && nand.length !== 528 * 32 * 2048)
            throw Error('Tungsten E2 needs a 34,603,008-byte NAND image. The selected file contains ' +
                nand.length.toLocaleString('en-US') + ' bytes.');
        await emu.loadRom(rom, nand);
    }
    const service = emu.emulationService;
    service.newFrameEvent.addHandler(refresh);
    const canvas = service.getCanvas();
    if (hardware.engine === 'cloudpilot') {
        service.pwmUpdateEvent().addHandler(({frequency,dutyCycle}) => PalmHost.tone(frequency,dutyCycle));
    } else await emu.initializeAudio();
    await emu.resume();
    PalmHost.ready(canvas.width, canvas.height);
    if (settings.count && settings.checkpoint !== 'state') {
        // A fresh ROM may still be in digitizer setup. The menu can reopen the
        // installed app after setup; a prepared device session launches directly.
        while (!(await emu.isUiInitialized()) || (hardware.engine === 'cloudpilot' && !emu.cloudpilot.isSetupComplete())) await new Promise(resolve => setTimeout(resolve, 150));
        await installGame();
    }
}
boot().catch(problem);
