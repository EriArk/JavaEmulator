/* SPDX-License-Identifier: GPL-3.0-only */
import {Machine, CPU_CLOCK} from './core/js/emu/machine.js';
import {CPU8088} from './core/js/emu/cpu8088.js';
import {DEFAULT_KEYMAP} from './core/js/emu/keyboard.js';
import {ramDiskImage} from './core/js/ui/permdata.js';
import {encode,decode} from './core/js/ui/statefile.js';

let machine, cpu, active=true, speed=100, previous=0;
const punctuation={' ':'Space','\n':'Enter','.':'Period',',':'Comma','/':'Slash','=':'Equal','-':'Minus',';':'Semicolon',"'":'Quote','[':'BracketLeft',']':'BracketRight','\\':'Backslash'};
const shifted={':':'Semicolon','+':'Equal','?':'Slash','_':'Minus','"':'Quote','!':'Digit1','@':'Digit2','#':'Digit3','$':'Digit4','%':'Digit5','^':'Digit6','&':'Digit7','*':'Digit8','(':'Digit9',')':'Digit0','<':'Comma','>':'Period'};
function advance(ms) {
    let budget=Math.round(ms*CPU_CLOCK/1000);
    while(budget>0) {
        let n=0;
        if (machine.poweredOff || (cpu.halted && !cpu.intLine && !cpu.nmiEdge)) {
            n=Math.min(4096,budget); cpu.cycles+=n;
        } else while(n<4096 && n<budget) n+=cpu.step();
        machine.advance(n); budget-=n;
    }
}
function key(code,down) {
    const scan=DEFAULT_KEYMAP[code];
    if(scan==null)return;
    if(down) { if(machine.wake()!=='off')machine.keyboard.keyDown(scan); }
    else machine.keyboard.keyUp(scan);
}
function press(code) {key(code,true);advance(45);key(code,false);advance(65);}
function type(text) {
    for (const ch of text) {
        const shift=shifted[ch] || /[A-Z]/.test(ch);
        let code=shifted[ch] || punctuation[ch];
        if (!code && /[a-z]/i.test(ch))code='Key'+ch.toUpperCase();
        if (!code && /\d/.test(ch))code='Digit'+ch;
        if (!code)continue;
        if(shift){key('ShiftLeft',true);advance(50);}
        press(code);
        if(shift){key('ShiftLeft',false);advance(50);}
    }
}
function frame() {
    const pixels=new Uint8Array(240*64);
    if(!machine.poweredOff && !machine.offService)machine.lcd.render(pixels);
    postMessage({type:'frame',pixels},[pixels.buffer]);
}
let lastDiskC, checkedAt=0;
function persistDisks() {
    if(machine.cardA?.dirty) {
        machine.cardA.dirty=false;
        postMessage({type:'disk',drive:'a',bytes:machine.cardA.data});
    }
    const c=ramDiskImage(machine.ram);
    if(c && lastDiskC && (c.length!==lastDiskC.length || c.some((v,i)=>v!==lastDiskC[i]))) {
        postMessage({type:'disk',drive:'c',bytes:machine.ram.slice()});
    }
    if(c)lastDiskC=c.slice();
}
function save() { persistDisks();postMessage({type:'state',state:encode(machine.snapshot())}); }
function restore(state) { machine.restore(decode(state)); machine.keyboard.releaseAll(); frame(); }
function tick() {
    const now=performance.now();
    const ms=Math.min(45,now-previous)*speed/100; previous=now;
    if(!active)return;
    const start=machine.cycleCount; advance(ms);
    const events=machine.soundEvents.splice(0).map(e=>({t:(e.cycle-start)/CPU_CLOCK*100/speed,kind:e.kind,f0:e.f0,f1:e.f1}));
    if(events.length)postMessage({type:'audio',events,duration:ms/1000*100/speed});
    if(now-checkedAt>=1000){persistDisks();checkedAt=now;}
    frame();
}
function start(m) {
    machine=new Machine({sysRom:m.sys,lcdCharRom:m.lcdRom,cgRom:m.cg});
    const warm=m.driveC?.length===machine.ram.length;
    if(warm)machine.ram.set(m.driveC);
    machine.insertCardA(m.card,true);
    cpu=new CPU8088(machine.bus);machine.attachCPU(cpu);machine.reset();cpu.reset();
    if(m.state)restore(m.state);
    else {
        advance(2000);
        if(!warm){press('Space');advance(800);press('KeyE');advance(800);}
        // Both a new machine and one with battery-backed memory ask for date and time.
        press('Enter');advance(800);press('Enter');advance(1500);
        const disk=ramDiskImage(machine.ram);
        lastDiskC=disk?.slice();
        type('a:\n'+m.command.toLowerCase()+'\n');advance(250);
    }
    machine.soundEvents.length=0; frame();postMessage({type:'ready'});
    previous=performance.now();setInterval(tick,25);
}
onmessage=({data:m})=>{
    try {
        if(m.type==='start'){start(m);return;}
        if(!machine)return;
        switch(m.type) {
            case 'active': active=m.active; if(!active){machine.keyboard.releaseAll();persistDisks();} break;
            case 'speed': speed=Math.max(1,m.speed);break;
            case 'chord': {
                const keys=m.chord.split('+');
                const modifier=keys.length===2 ? (keys[0]==='Ctrl'?'ControlLeft':'AltLeft') : null;
                if(modifier){key(modifier,true);advance(50);}
                const label=keys[keys.length-1];
                press(label.length===1?'Key'+label:label);
                if(modifier){key(modifier,false);advance(50);}
                frame();break;
            }
            case 'text': type(m.text);frame();break;
            case 'key':
                key(m.code,m.down);
                // Let the BIOS scan even a tap whose two edges arrive in one frame.
                advance(35);
                break;
            case 'save': save();break;
            case 'restore': restore(m.state);break;
        }
    }catch(e){postMessage({type:'error',message:e.message});}
};
