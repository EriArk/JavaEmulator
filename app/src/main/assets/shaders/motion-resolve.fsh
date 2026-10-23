// Copyright 2026. GPL-3.0-only. Reject uncertain blocks instead of cross-fading cuts.
precision highp float;
uniform sampler2D sampler0;
uniform sampler2D previous;
uniform sampler2D motion;
uniform sampler2D sceneState;
uniform vec2 sourceSize;
uniform vec2 textureSize;
uniform float phase;
varying vec2 v_texcoord0;
vec2 uv(vec2 p) { return (clamp(p,vec2(0),sourceSize-1.0)+0.5)/textureSize; }
float largest(vec3 c) { return max(c.r,max(c.g,c.b)); }
void main() {
    vec2 p=v_texcoord0*sourceSize-0.5;
    vec4 now=texture2D(sampler0,uv(p));
    if(phase>=1.0) { gl_FragColor=now; return; }
    if(texture2D(sceneState,vec2(0.5)).r>0.5) { gl_FragColor=now; return; }
    // HUD/text can share a motion block with the world but must stay at their real coordinates.
    vec4 old=texture2D(previous,uv(p));
    if(largest(abs(old.rgb-now.rgb))<0.5/255.0) { gl_FragColor=now; return; }
    vec4 flow=texture2D(motion,v_texcoord0);
    vec2 v=floor(flow.rg*8.0+0.5)-4.0;
    if(flow.b<0.5) { gl_FragColor=now; return; }
    vec2 oldPos=p+v, newPos=p-v;
    if(any(lessThan(min(oldPos,newPos),vec2(-0.001))) ||
       any(greaterThan(max(oldPos,newPos),sourceSize-1.0+0.001))) { gl_FragColor=now; return; }
    // A block's motion cannot explain newly appeared/disappeared pixels unless both endpoints agree.
    if(largest(abs(texture2D(previous,uv(oldPos)).rgb-now.rgb))>0.06 ||
       largest(abs(texture2D(sampler0,uv(newPos)).rgb-old.rgb))>0.06) { gl_FragColor=now; return; }
    vec2 aPos=p+v*phase, bPos=p-v*(1.0-phase);
    if(any(lessThan(min(aPos,bPos),vec2(-0.001))) ||
       any(greaterThan(max(aPos,bPos),sourceSize-1.0+0.001))) { gl_FragColor=now; return; }
    vec4 a=texture2D(previous,uv(aPos));
    vec4 b=texture2D(sampler0,uv(bPos));
    // Occlusions and incorrect matches retain the current real pixel.
    if(largest(abs(a.rgb-b.rgb))>0.06) gl_FragColor=now;
    else gl_FragColor=mix(a,b,phase);
}
