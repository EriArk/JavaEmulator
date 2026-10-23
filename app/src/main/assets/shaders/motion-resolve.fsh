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
    vec2 p=floor(v_texcoord0*sourceSize);
    vec4 now=texture2D(sampler0,uv(p));
    if(phase>=1.0) { gl_FragColor=now; return; }
    if(texture2D(sceneState,vec2(0.5)).r>0.5) { gl_FragColor=now; return; }
    vec4 old=texture2D(previous,uv(p));
    vec4 flow=texture2D(motion,v_texcoord0);
    vec2 v=floor(flow.rg*8.0+0.5)-4.0;
    if(flow.b<0.5) { gl_FragColor=now; return; }
    bool unchanged=largest(abs(old.rgb-now.rgb))<0.5/255.0;
    if(unchanged) {
        // Only sparse, verified background can receive an object between its endpoints.
        // Static foreground (HUD/text) remains at its real coordinates.
        if(flow.b>0.9) { gl_FragColor=now; return; }
        vec2 grid=ceil(sourceSize/8.0), tile=floor(v_texcoord0*grid);
        vec2 corner=ceil(tile*sourceSize/grid-0.5);
        if(largest(abs(now.rgb-texture2D(sampler0,uv(corner)).rgb))>0.5/255.0) {
            gl_FragColor=now; return;
        }
    }
    vec2 oldPos=p+v, newPos=p-v;
    if(any(lessThan(min(oldPos,newPos),vec2(-0.001))) ||
       any(greaterThan(max(oldPos,newPos),sourceSize-1.0+0.001))) { gl_FragColor=now; return; }
    // A block's motion cannot explain newly appeared/disappeared pixels unless both endpoints agree.
    if(largest(abs(texture2D(previous,uv(oldPos)).rgb-now.rgb))>0.06 ||
       largest(abs(texture2D(sampler0,uv(newPos)).rgb-old.rgb))>0.06) { gl_FragColor=now; return; }
    // Snap once: independently sampling half-pixel boundaries can select different
    // source pixels on either side of the same match, especially for odd motion.
    vec2 aPos=p+floor(v*phase+0.5), bPos=aPos-v;
    if(any(lessThan(min(aPos,bPos),vec2(-0.001))) ||
       any(greaterThan(max(aPos,bPos),sourceSize-1.0+0.001))) { gl_FragColor=now; return; }
    vec4 a=texture2D(previous,uv(aPos));
    vec4 b=texture2D(sampler0,uv(bPos));
    if(unchanged && largest(abs(a.rgb-now.rgb))>0.5/255.0) {
        // A borrowed vector must not duplicate a stationary foreground detail.
        if(largest(abs(a.rgb-texture2D(sampler0,uv(aPos)).rgb))<0.5/255.0 ||
           largest(abs(b.rgb-texture2D(previous,uv(bPos)).rgb))<0.5/255.0) {
            gl_FragColor=now; return;
        }
    }
    // Occlusions and incorrect matches retain the current real pixel.
    if(largest(abs(a.rgb-b.rgb))>0.06) gl_FragColor=now;
    else gl_FragColor=mix(a,b,phase);
}
