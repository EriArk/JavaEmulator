/* SPDX-License-Identifier: GPL-3.0-only */
import {AudioOutput} from './core/js/ui/audio.js';
import {formatCard, addFile, toShortName} from './core/js/ui/fat12.js';

const lcd = document.querySelector('canvas').getContext('2d');
const image = lcd.createImageData(240, 64), pixels = new Uint32Array(image.data.buffer);
const sound = new AudioOutput();
const worker = new Worker('./runtime.js', {type:'module'});
const send = (type, args={}) => worker.postMessage({type, ...args});
function fail(message) { prompt('abyss-webos-error', String(message)); }
worker.onerror = e => fail(e.message);
let cardKey;
let ready = false, active = true, speed = 100;
worker.onmessage = ({data}) => {
    switch (data.type) {
        case 'frame':
            for (let i=0;i<pixels.length;i++) pixels[i] = data.pixels[i] ? 0xff263327 : 0xff9fc4b7;
            lcd.putImageData(image,0,0); break;
        case 'audio': if (active) sound.schedule(data.events, data.duration); break;
        case 'ready':
            ready = true; document.getElementById('status').remove(); prompt('portfolio-ready', '');
            send('active',{active}); send('speed',{speed}); break;
        case 'disk': {
            let raw='';
            for (let i=0;i<data.bytes.length;i+=16384) raw+=String.fromCharCode(...data.bytes.subarray(i,i+16384));
            localStorage.setItem(data.drive==='a' ? cardKey : 'drive-c',btoa(raw)); break;
        }
        case 'state': prompt('portfolio-state', data.state); break;
        case 'error': fail(data.message); break;
    }
};
window.portfolioSave = () => send('save');
window.portfolioLoad = async () => {
    const response=await fetch('/snapshot');
    if (response.ok) send('restore',{state:await response.text()});
};
window.portfolioChord = chord => send('chord',{chord});
window.portfolioType = text => send('text',{text});
window.portfolioSpeed = percent => { speed=percent; if(ready)send('speed',{speed}); };
window.portfolioKey = (code,down) => send('key',{code,down});
for (const event of ['keydown','keyup']) document.addEventListener(event,e=>{
    e.preventDefault(); if (!e.repeat) window.portfolioKey(e.code || e.key,event === 'keydown');
});
for(const state of ['activate','deactivate']) document.addEventListener('mojo-'+state,()=>{
    active=state==='activate'; if(ready)send('active',{active}); if(!active)sound.silence();
});
async function bytes(path) {
    const response = await fetch(path);
    if (!response.ok) throw Error('Missing Portfolio file: '+path);
    return new Uint8Array(await response.arrayBuffer());
}
async function start() {
    const manifest=await (await fetch('/package.json')).json();
    cardKey='drive-a'+manifest.folder;
    const [app,bios,lcdRom,cg]=await Promise.all(['c101782-007.u4','c101781-007.u3','c101783-001a-01.u3','hd61830.bin'].map(n=>bytes('/rom/'+n)));
    const sys=new Uint8Array(262144);sys.set(app);sys.set(bios,131072);
    const card=formatCard(262144);
    const names=new Set();
    for (const [i,name] of manifest.files.entries()) {
        const short=toShortName(name);
        if(names.has(short)) throw Error('Two package files use the same DOS name: '+name);
        names.add(short);
        addFile(card,name,await bytes('/file/'+i),new Date());
    }
    const short=toShortName(manifest.program);
    const command=short.slice(0,8).trim()+'.'+short.slice(8).trim();
    const previous=await fetch('/snapshot');
    await sound.start();
    function disk(letter) {
        const value=localStorage.getItem(letter==='a' ? cardKey : 'drive-c');
        return value ? Uint8Array.from(atob(value),ch=>ch.charCodeAt(0)) : null;
    }
    send('start',{sys,lcdRom,cg,card:disk('a') || card,driveC:disk('c'),command,
        state:previous.ok ? await previous.text() : null});
}
start().catch(e=>fail(e.message));
