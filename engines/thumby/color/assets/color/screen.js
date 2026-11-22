// SPDX-License-Identifier: GPL-3.0-only
(() => {
    const screen = document.querySelector('canvas').getContext('2d');
    const frame = screen.createImageData(128,128);
    screen.fillStyle = 'white';
    screen.font = '10px sans-serif';
    screen.textAlign = 'center';
    screen.fillText('Starting game…',64,66);
    const worker = new Worker('/worker.js', {type:'module'});
    const audio = new AudioContext();
    let active = true, next = 0, first = true;
    const fail = message => { worker.terminate(); audio.close(); prompt('abyss-webos-error',String(message)); };
    worker.onerror = e => fail(e.message);
    worker.onmessage = ({data}) => {
        if (data.frame) {
            const src = data.frame;
            for (let i=0; i<src.length; i++) {
                const c=src[i], p=i*4;
                frame.data[p]=(c>>11)*255/31;
                frame.data[p+1]=((c>>5)&63)*255/63;
                frame.data[p+2]=(c&31)*255/31;
                frame.data[p+3]=255;
            }
            screen.putImageData(frame,0,0);
            if (first) { first=false; prompt('color-frame',''); }
        } else if (data.pcm && active) {
            const samples = data.pcm;
            const buffer = audio.createBuffer(1,samples.length,22050);
            buffer.copyToChannel(samples,0);
            const out = audio.createBufferSource();
            out.buffer = buffer;
            out.connect(audio.destination);
            if (next < audio.currentTime || next > audio.currentTime+.2) next=audio.currentTime+.035;
            out.start(next);
            next += buffer.duration;
        } else if (data.save !== undefined) {
            try {
                const store = (cmd,v) => { if (prompt('color-save-'+cmd,v) !== 'ok') throw Error('Cannot save game progress'); };
                store('start','');
                // The snapshot is ASCII JSON to keep UTF-16 surrogate pairs out of chunk boundaries.
                const json = data.save.replace(/[^\x00-\x7f]/g, c=>'\\u'+c.charCodeAt(0).toString(16).padStart(4,'0'));
                for (let i=0;i<json.length;i+=32768) store('append',json.slice(i,i+32768));
                store('end','');
            } catch(e) { fail(e); }
        } else if (data.error) fail(data.error);
        else if (data.exit) prompt('color-exit','');
    };
    window.colorKey = (mask,down) => worker.postMessage({mask,down});
    document.addEventListener('mojo-deactivate', () => { active=false; worker.postMessage({active}); audio.suspend(); next=0; });
    document.addEventListener('mojo-activate', () => { active=true; worker.postMessage({active}); audio.resume(); });
    const previousSpeed = window.abyssWebSpeed;
    window.abyssWebSpeed = percent => { previousSpeed(percent); worker.postMessage({speed:percent/100}); };
    audio.resume();
})();
