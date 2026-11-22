// SPDX-License-Identifier: GPL-3.0-only
// LCD and sprite layers exposed to the LGE SDK VM.
const ctx = document.querySelector('canvas').getContext('2d', {alpha:false});
const frame = ctx.createImageData(128, 128);
const baseColors = [0x000000,0xede3c7,0xbe3746,0x7fb8b5,0x4a3e4f,0x6ea76c,0x273f68,0xdebb59,
                    0xb48d6c,0x42595a,0xc0624d,0x333333,0x777777,0x8fab62,0x3abfd1,0xbbbbbb];
const palette = [...baseColors, ...baseColors];
const pixels = new Uint8Array(128*128), sprites = new Uint8Array(pixels.length);
let left=0, topEdge=0, right=128, bottom=128, cpuLostCycle=0;
let keyboard=false, cursor=0, shifted=false;
const keyboardRows=['0123456789[]','qwertyuiop"/','asdfghjkl;=↵','zxcvbnm,.- ⌫'];
const shiftedRows=['!@#$%^&*(){}','QWERTYUIOP|?','ASDFGHJKL:+↵','ZXCVBNM<>_ ⌫'];
function signed(x) { return x>32767 ? x-65536 : x; }
function rgb565(c) { return (Math.round((c>>>11)*255/31)<<16) | (Math.round(((c>>5)&63)*255/63)<<8) | Math.round((c&31)*255/31); }
const display = {
    reset() {
        pixels.fill(0); sprites.fill(0);
        for(let i=0;i<32;i++) palette[i]=baseColors[i&15];
        this.setClip(0,0,128,128);
    },
    setClip(x,y,w,h) {
        x=signed(x); y=signed(y);
        left=Math.max(0,x); topEdge=Math.max(0,y);
        right=Math.min(128,x+w); bottom=Math.min(128,y+h);
    },
    clearScreen(color=0) { pixels.fill(color&15); },
    drawPixel(color,x,y) {
        cpuLostCycle+=0.1;
        if (x>=left && y>=topEdge && x<right && y<bottom) pixels[y*128+x]=color&15;
    },
    plot(color,x,y) { this.drawPixel(color,x,y); },
    largeplot(color,x,y,size) {
        for(let j=Math.max(topEdge,y);j<Math.min(bottom,y+size);j++)
            for(let i=Math.max(left,x);i<Math.min(right,x+size);i++) this.drawPixel(color,i,j);
    },
    getPixel(x,y) { return x>=0 && x<128 && y>=0 && y<128 ? pixels[y*128+x] : 0; },
    drawSpritePixel(color,x,y) { if(x>=0 && x<128 && y>=0 && y<128) sprites[y*128+x]=color&15; },
    char(chr,x,y,color,bg) {
        for(let i=0;i<5;i++) {
            const bits=font[chr.charCodeAt(0)*5+i];
            for(let j=0;j<8;j++) this.drawPixel((bits>>j)&1 ? color : bg,x+i,y+j);
        }
    },
    changePalette(n,c) { if(n>=0 && n<32) palette[n]=rgb565(c); },
    drawLed(c) { document.querySelector('canvas').style.boxShadow='inset 0 0 2px #'+rgb565(c).toString(16).padStart(6,'0'); },
    viewKeyboard() { keyboard=globalKey===0; },
    redraw() {
        for(let i=0;i<pixels.length;i++) {
            const c=palette[sprites[i] ? 16+sprites[i] : pixels[i]];
            frame.data[i*4]=c>>16; frame.data[i*4+1]=(c>>8)&255;
            frame.data[i*4+2]=c&255; frame.data[i*4+3]=255;
        }
        ctx.putImageData(frame,0,0);
        if(keyboard) {
            ctx.fillStyle='#181818'; ctx.fillRect(0,74,128,54); ctx.font='9px monospace';
            const rows=shifted ? shiftedRows : keyboardRows;
            for(let j=0;j<4;j++) for(let i=0;i<12;i++) {
                ctx.fillStyle=cursor===j*12+i ? '#ffbd42' : 'white';
                ctx.fillText(rows[j][i],4+i*10,87+j*12);
            }
        }
    }
};
