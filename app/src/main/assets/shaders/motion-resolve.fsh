// Copyright 2026. GPL-3.0-only. Reject uncertain blocks instead of cross-fading cuts.
precision highp float;
uniform sampler2D sampler0;
uniform sampler2D previous;
uniform sampler2D motion;
uniform vec2 sourceSize;
uniform vec2 textureSize;
uniform float phase;
varying vec2 v_texcoord0;
vec2 uv(vec2 p) { return (clamp(p,vec2(0),sourceSize-1.0)+0.5)/textureSize; }
void main() {
    vec2 p=v_texcoord0*sourceSize-0.5;
    vec4 flow=texture2D(motion,v_texcoord0);
    vec2 v=floor(flow.rg*8.0+0.5)-4.0;
    vec4 now=texture2D(sampler0,uv(p));
    if(flow.b<0.5 || phase>=1.0) { gl_FragColor=now; return; }
    vec4 a=texture2D(previous,uv(p+v*phase));
    vec4 b=texture2D(sampler0,uv(p-v*(1.0-phase)));
    // Occlusions and incorrect matches retain the current real pixel.
    if(dot(abs(a.rgb-b.rgb),vec3(0.333333))>0.12) gl_FragColor=now;
    else gl_FragColor=mix(a,b,phase);
}
