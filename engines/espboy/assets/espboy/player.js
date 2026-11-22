// SPDX-License-Identifier: GPL-3.0-only
const sound = new Sound();
let globalJKey=0, queuedPress=0, globalKey=0, timeForRedraw=48;
let firstFrame=true, soundTimer=0, frameTimer=0, running=false, active=true, handle;
function stopCpu() { running=false; prompt('esp-exit',''); }
// Keep a quick touch until the game next executes its joystick instruction.
function readButtons() {
    const mask=globalJKey | queuedPress;
    queuedPress=0;
    return mask;
}
function key(mask,down) {
    if (!down) { globalJKey &= ~mask; return; }
    if (keyboard) {
        if(mask===1) cursor=(cursor+36)%48;
        else if(mask===2) cursor=(cursor+12)%48;
        else if(mask===4) cursor=(cursor+47)%48;
        else if(mask===8) cursor=(cursor+1)%48;
        else if(mask===32) shifted=!shifted;
        else if(mask===16 || mask===128) {
            const ch=(shifted ? shiftedRows : keyboardRows)[Math.floor(cursor/12)][cursor%12];
            globalKey=mask===128 || ch==='↵' ? 10 : ch==='⌫' ? 8 : ch.charCodeAt(0);
            keyboard=false;
        }
    } else {
        queuedPress |= mask & ~globalJKey;
        globalJKey |= mask;
    }
}
window.espKey=key;
function tick() {
    if (!running || !active) return;
    try {
        for(let i=0;i<8;i++) timers[i]=Math.max(0,timers[i]-16);
        soundTimer-=16;
        if(soundTimer<=0) soundTimer=Math.min(2000,sound.playRtttl());
        for(let budget=8000; budget>0 && running;) {
            cpu.step(); budget-=1+cpuLostCycle; cpuLostCycle=0;
        }
        frameTimer-=16;
        if(frameTimer<=0) {
            sprites.fill(0);
            cpu.redrawSprite(); cpu.testSpriteCollision(false);
            cpu.redrawParticle(); display.redraw(); cpu.setRedraw();
            if (firstFrame) { firstFrame=false; prompt("esp-frame",""); }
            frameTimer=Math.max(1,timeForRedraw);
        }
        if(running) handle=setTimeout(tick,16);
    } catch(e) { running=false; prompt('abyss-webos-error',String(e.stack || e)); }
}
document.addEventListener('mojo-deactivate',()=>{active=false;globalJKey=0;queuedPress=0;clearTimeout(handle);sound.active(false);});
document.addEventListener('mojo-activate',()=>{if(!active){active=true;sound.active(true);tick();}});
(async()=>{
    const response=await fetch('program.bin');
    if(!response.ok) throw Error('Missing installed ESPboy program');
    cpu.load(new Uint8Array(await response.arrayBuffer()));
    sound.initAudio(); running=true; tick();
})().catch(e=>prompt('abyss-webos-error',String(e)));
