// SPDX-License-Identifier: GPL-3.0-only
import { RP2040 } from './rp2040/rp2040.js';
import { USBCDC } from './rp2040/usb/cdc.js';
import { ConsoleLogger, LogLevel } from './rp2040/utils/logging.js';
import littlefs from './littlefs.js';

const offset = 0xa0000, sectorSize = 4096;
async function bytes(url) {
    const response = await fetch(url);
    if (!response.ok) throw Error('Cannot open ' + url);
    return new Uint8Array(await response.arrayBuffer());
}
function check(status, action) {
    if (status < 0) throw Error(action + ' (LittleFS ' + status + ')');
}
async function install(flash, game) {
    const fs = await littlefs({wasmBinary: await bytes('/littlefs.wasm')});
    const pos = (block, off) => offset + block * sectorSize + off;
    const read = fs.addFunction((cfg, block, off, ptr, len) => {
        fs.HEAPU8.set(flash.subarray(pos(block,off), pos(block,off)+len), ptr);
        return 0;
    }, 'iiiiii');
    const write = fs.addFunction((cfg, block, off, ptr, len) => {
        flash.set(fs.HEAPU8.subarray(ptr,ptr+len), pos(block,off));
        return 0;
    }, 'iiiiii');
    const erase = fs.addFunction((cfg, block) => {
        flash.fill(255, pos(block,0), pos(block+1,0));
        return 0;
    }, 'iii');
    const sync = fs.addFunction(() => 0, 'ii');
    check(fs._start_disk(read,write,erase,sync,(flash.length-offset)/sectorSize,sectorSize), 'Prepare flash');
    const mkdir = fs.cwrap('make_directory','number',['string']);
    const store = fs.cwrap('store_file','number',['string','number','number']);
    const libs = await (await fetch('/lib.json')).json();
    const entries = libs.map(n => ['/lib/'+n, '/lib/'+n]);
    game.files.forEach((name,i) => entries.push(['/Games/'+game.title+'/'+name, '/file/'+i]));
    for (const [name, url] of entries) {
        const data = await bytes(url);
        for (let i = name.indexOf('/',1); i >= 0; i = name.indexOf('/',i+1)) {
            const rc = mkdir(name.slice(0,i));
            if (rc !== -17) check(rc,'Create directory');
        }
        const ptr = fs._malloc(data.length || 1);
        fs.HEAPU8.set(data,ptr);
        check(store(name,ptr,data.length), 'Install '+name);
        fs._free(ptr);
    }
    check(fs._finish_disk(), 'Unmount');
}

// Restore the first private build's save if present, then apply newer atomic sector files.
async function attachSaves(mcu, sectors) {
    const changes = JSON.parse(localStorage.getItem('flash-v1') || '{}');
    for (const [key, encoded] of Object.entries(changes)) {
        const start = offset + Number(key)*sectorSize;
        const data = Uint8Array.from(atob(encoded), c => c.charCodeAt(0));
        if (!Number.isInteger(Number(key)) || start < offset || start + sectorSize > mcu.flash.length || data.length !== sectorSize)
            throw Error('Invalid saved flash sector');
        mcu.flash.set(data,start);
    }
    for (const sector of sectors) {
        const data = await bytes('/disk/' + sector);
        if (data.length !== sectorSize) throw Error('Cannot read saved Thumby flash');
        mcu.flash.set(data, offset + Number(sector)*sectorSize);
    }
    const original = mcu.onBreak;
    mcu.onBreak = code => {
        const start = mcu.registers[0], count = mcu.registers[2];
        original(code);
        if (code !== 27 || start < offset || !count) return;
        for (let sector = Math.floor((start-offset)/sectorSize); sector <= Math.floor((start+count-1-offset)/sectorSize); sector++) {
            const addr = offset + sector*sectorSize;
            const encoded = btoa(String.fromCharCode(...mcu.flash.subarray(addr,addr+sectorSize)));
            if (prompt('thumby-write', sector + ':' + encoded) !== 'ok') throw Error('Cannot save Thumby flash');
        }
    };
}

let active = true, ready = false, generation = 0, factor = 1;
const board = new RP2040(), serial = new USBCDC(board.usbCtrl);
const audio = new AudioContext(), volume = audio.createGain(), buzzer = audio.createOscillator();
volume.gain.value = 0;
volume.connect(audio.destination);
buzzer.type = 'triangle';
buzzer.connect(volume);
buzzer.start();
board.onAudioFreq = freq => {
    volume.gain.value = freq && active ? 0.24 : 0;
    if (freq) buzzer.frequency.value = freq;
};
const screen = document.querySelector('canvas').getContext('2d');
const frame = screen.createImageData(72,40);
let firstFrame = true;
screen.font = '7px sans-serif';
screen.fillStyle = 'white';
screen.textAlign = 'center';
screen.fillText('Starting game…', 36, 22);
let address = -1, gray = false, brightness = 1;
board.onScreenAddr = value => {
    if (value < 2) gray = !!value;
    else { address = value - 0x20000000; gray = false; }
};
board.onBrightness = value => { brightness = Math.min(1, Math.max(0, value / 127)); };
board.gpio[2].addListener(() => {
    if (address < 0) return;
    for (let y = 0; y < 40; y++) for (let x = 0; x < 72; x++) {
        const index = address + (y >> 3)*72 + x, mask = 1 << (y & 7);
        const bit = !!(board.sram[index] & mask), shade = gray && !!(board.sram[index+360] & mask);
        const color = (shade ? (bit ? 170 : 85) : bit ? 255 : 0) * brightness;
        const p = (y*72+x)*4;
        frame.data[p] = frame.data[p+1] = frame.data[p+2] = color;
        frame.data[p+3] = 255;
    }
    screen.putImageData(frame,0,0);
    if (firstFrame) { firstFrame = false; prompt('thumby-frame',''); }
});
const pins = [3,4,5,6,24,27];
window.thumbyKey = (pin,down) => { if (pins.includes(pin)) board.gpio[pin].setInputValue(!down); };
function run() {
    if (!active || !ready) return;
    const token = ++generation;
    board.stopped = false;
    board.clock.resume();
    audio.resume();
    function tick() {
        if (generation !== token || !active) return;
        for (let i=0; i < 100000*factor && !board.waiting && !board.stopped; i++) board.executeInstruction();
        if (!board.stopped) window.setZeroTimeout(tick);
    }
    tick();
}
document.addEventListener('mojo-deactivate', () => {
    active = false;
    generation++;
    board.stop();
    audio.suspend();
    for (const pin of pins) board.gpio[pin].setInputValue(true);
});
document.addEventListener('mojo-activate', () => { if (!active) { active = true; run(); } });
const sessionSpeed = window.abyssWebSpeed;
let clockStart = performance.now(), elapsed = 0;
const clock = board.clock;
Object.defineProperty(clock,'micros',{get() {
    return (elapsed + (performance.now()-clockStart)*factor)*1000 - clock.baseTime;
}});
// Keep the guest timer and the host alarm scheduler on the same time scale.
clock.resume = function() {
    if (!this.paused) return;
    this.baseTime += this.micros - this.pauseTime;
    this.paused = false;
    for (const timer of this.timers) timer.resume(this.micros);
};
window.abyssWebSpeed = percent => {
    const now = performance.now(), running = !clock.paused;
    if (running) clock.pause();
    elapsed += (now-clockStart)*factor;
    clockStart = now;
    factor = percent/100;
    sessionSpeed(percent);
    if (running) clock.resume();
};

async function boot() {
    const game = await (await fetch('/game.json')).json();
    board.loadBootrom(new Uint32Array((await bytes('/bootrom.bin')).buffer));
    board.logger = new ConsoleLogger(LogLevel.Error);
    const uf2 = await bytes('/emulator-firmware.uf2');
    const view = new DataView(uf2.buffer);
    for (let i=0; i<uf2.length; i+=512) {
        const addr = view.getUint32(i+12,true)-0x10000000, n = view.getUint32(i+16,true);
        board.flash.set(uf2.subarray(i+32,i+32+n),addr);
    }
    await install(board.flash,game);
    await attachSaves(board,game.sectors);
    const dir = '/Games/'+game.title;
    serial.onDeviceConnected = () => {
        const cmd = '\r\nimport sys; sys.path.append('+JSON.stringify(dir)+'); execfile('+JSON.stringify(dir+'/'+game.main)+"); print('__ABYSS_EXIT__')\r";
        for (const b of new TextEncoder().encode(cmd)) serial.sendSerialByte(b);
    };
    let tail = '';
    serial.onSerialData = chunk => {
        tail = (tail + new TextDecoder().decode(chunk)).slice(-1536);
        if (tail.includes('\r\n__ABYSS_EXIT__\r\n')) { tail = ''; prompt('thumby-exit',''); }
        if (tail.includes('Traceback (most recent call last)') && tail.endsWith('>>> ')) {
            const trace = tail.slice(tail.indexOf('Traceback'),-4).trim();
            prompt('abyss-webos-error',trace);
            tail = '';
        }
    };
    for (const pin of pins) board.gpio[pin].setInputValue(true);
    board.PC = 0x10000000;
    ready = true;
    run();
}
window.addEventListener('error', event => prompt('abyss-webos-error', event.message));
boot().catch(e => { console.error(String(e)); prompt('abyss-webos-error',String(e)); });
