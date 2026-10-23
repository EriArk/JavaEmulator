// Adapted from AMD FidelityFX FSR 1.0, a21ffb8f6c13233ba336352bdff293894c706575.
// Copyright (c) 2021 Advanced Micro Devices, Inc. MIT; see licenses/FSR-MIT.txt.
precision highp float;
uniform sampler2D sampler0;
uniform vec2 sourceSize;
uniform float sharpness;
varying vec2 v_texcoord0;
vec3 fetch(vec2 d) { return texture2D(sampler0,clamp(v_texcoord0+d/sourceSize,0.5/sourceSize,1.0-0.5/sourceSize)).rgb; }
void main() {
    vec3 b=fetch(vec2(0,-1)), d=fetch(vec2(-1,0)), e=fetch(vec2(0)), f=fetch(vec2(1,0)), h=fetch(vec2(0,1));
    vec3 mn=min(min(b,d),min(f,h)), mx=max(max(b,d),max(f,h));
    vec3 hitMin=min(mn,e)/max(4.0*mx,vec3(0.00001));
    vec3 hitMax=(1.0-max(mx,e))/min(4.0*mn-4.0,vec3(-0.00001));
    vec3 lobes=max(-hitMin,hitMax);
    float lobe=max(-0.1875,min(max(max(lobes.r,lobes.g),lobes.b),0.0))*sharpness;
    gl_FragColor=vec4(clamp((lobe*(b+d+f+h)+e)/(4.0*lobe+1.0),0.0,1.0),1);
}
