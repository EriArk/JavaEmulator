// SPDX-License-Identifier: GPL-3.0-only
import {loadMicroPython} from './micropython.js';

let mp, mask=0, pendingPress=0, speed=1, elapsed=0, mark=performance.now(), audioTime=0;
self.colorPaused=false;
self.colorRunning=false;
self.colorMillis=()=>elapsed+(self.colorPaused ? 0 : (performance.now()-mark)*speed);
// The game samples once per tick. Preserve taps that begin and end between ticks.
self.get_pressed_buttons=()=>{
    const pressed=mask|pendingPress;
    pendingPress=0;
    return pressed;
};
self.onmessage=({data})=>{
    if ('mask' in data) {
        if (data.down && !self.colorPaused) {
            pendingPress|=data.mask & ~mask;
            mask|=data.mask;
        } else mask&=~data.mask;
    }
    else {
        elapsed=self.colorMillis();
        mark=performance.now();
        if ('speed' in data) speed=data.speed;
        if ('active' in data) { self.colorPaused=!data.active; if (!data.active) { mask=0; pendingPress=0; } audioTime=mark; }
    }
};
self.update_display=ptr=>{
    const frame=mp._module.HEAPU16.slice(ptr>>1,(ptr>>1)+128*128);
    self.postMessage({frame},[frame.buffer]);
};
self.colorPump=()=>{
    const now=performance.now();
    const count=Math.min(2048,Math.floor((now-audioTime)*22.05));
    if (count>0 && !self.colorPaused) {
        audioTime=count===2048 ? now : audioTime+count/22.05;
        const ptr=mp._module._abyss_color_audio(count);
        const pcm=mp._module.HEAPF32.slice(ptr>>2,(ptr>>2)+count);
        self.postMessage({pcm},[pcm.buffer]);
    }
};
async function resource(url, json=false) {
    const response=await fetch(url);
    if (!response.ok) throw Error('Unable to load '+url);
    return json ? response.json() : new Uint8Array(await response.arrayBuffer());
}
function mkdirFor(fs,path) { fs.mkdirTree(path.slice(0,path.lastIndexOf('/')) || '/'); }
function binary(data) {
    let result='';
    for (let i=0;i<data.length;i+=8192) result+=String.fromCharCode(...data.subarray(i,i+8192));
    return btoa(result);
}
// Keep mutable guest files in the portable game-library save, including games
// which use open() directly beside their scripts instead of engine_save.
function watch(fs, overlay, gameRoot) {
    let dirty=new Set();
    function changed(path) {
        if (typeof path!=='string') return;
        path=mp._module.PATH_FS.resolve(path);
        if (path.startsWith(gameRoot+'/') || path.startsWith('/Saves/') || path==='/settings.txt') dirty.add(path);
    }
    for (const name of ['close','rename','unlink','rmdir','mkdir']) {
        const original=fs[name];
        fs[name]=function(...args) {
            const written=name==='close' && (args[0].flags & 3)!==0 ? args[0].path : null;
            const result=original.apply(fs,args);
            if (name==='close') changed(written);
            else { changed(args[0]); if (name==='rename') changed(args[1]); }
            return result;
        };
    }
    return ()=>{
        if (!dirty.size) return;
        function record(path) {
            // A directory entry represents the entire subtree as of this write.
            for (const saved of Object.keys(overlay)) if (saved.startsWith(path+'/')) delete overlay[saved];
            let state;
            try { state=fs.stat(path); } catch(e) { overlay[path]=null; return; }
            if (fs.isDir(state.mode)) {
                overlay[path]=true;
                for (const child of fs.readdir(path)) if (child!=='.' && child!=='..') record(path+'/'+child);
            } else overlay[path]=binary(fs.readFile(path));
        }
        for (const path of dirty) record(path);
        dirty.clear();
        self.postMessage({save:JSON.stringify(overlay)});
    };
}
async function boot() {
    const game=await resource('/game.json',true);
    const overlay=await resource('/saved.json',true);
    const root='/Games/'+game.title;
    mp=await loadMicroPython({url:'/micropython.wasm',heapsize:520000+2*1024*1024});
    const fs=mp.FS;
    const system=await resource('/system.json',true);
    for (const name of system) {
        const path='/'+name;
        mkdirFor(fs,path); fs.writeFile(path,await resource(path));
    }
    for (let i=0;i<game.files.length;i++) {
        const path=root+'/'+game.files[i];
        mkdirFor(fs,path); fs.writeFile(path,await resource('/file/'+i));
    }
    function remove(path) {
        if (!fs.analyzePath(path).exists) return;
        if (fs.isDir(fs.stat(path).mode)) {
            for (const child of fs.readdir(path)) if (child!=='.' && child!=='..') remove(path+'/'+child);
            fs.rmdir(path);
        } else fs.unlink(path);
    }
    const saved=Object.entries(overlay);
    for (const [path] of saved) {
        if (!(path.startsWith(root+'/') || path.startsWith('/Saves/') || path==='/settings.txt') || path.split('/').includes('..')) throw Error('Invalid saved path');
    }
    // Remove replaced subtrees before creating any children, independently of JSON order.
    for (const [path] of saved) remove(path);
    for (const [path,entry] of saved) {
        if (entry===true) fs.mkdirTree(path);
        else if (entry!==null) { mkdirFor(fs,path); fs.writeFile(path,Uint8Array.from(atob(entry),c=>c.charCodeAt(0))); }
    }
    const flush=watch(fs,overlay,root), pump=self.colorPump;
    self.colorPump=()=>{pump();flush();};
    const code='import os, sys, engine_save\nengine_save._init_saves_dir('+JSON.stringify('/Saves/'+game.title)+')\nos.chdir('+JSON.stringify(root)+')\nsys.path.append('+JSON.stringify(root)+')\nexecfile('+JSON.stringify(root+'/'+game.main)+')';
    self.colorRunning=true;
    audioTime=performance.now();
    try { await mp.runPythonAsync(code); }
    finally { self.colorRunning=false; flush(); }
    self.postMessage({exit:true});
}
boot().catch(e=>self.postMessage(e.status===93 ? {exit:true} : {error:String(e.stack || e)}));
