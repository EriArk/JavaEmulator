// Copyright 2026. GPL-3.0-only. One-tile sparse motion reach, never recursive dilation.
precision highp float;
uniform sampler2D sampler0;
uniform vec2 sourceSize;
varying vec2 v_texcoord0;
void main() {
    vec2 p=floor(v_texcoord0*sourceSize);
    vec4 own=texture2D(sampler0,(p+0.5)/sourceSize);
    if(own.b<0.2 || own.b>0.3) { gl_FragColor=own; return; }
    vec2 selected=vec2(0);
    bool found=false, conflict=false;
    for(int y=-1;y<=1;y++) for(int x=-1;x<=1;x++) {
        vec2 q=p+vec2(float(x),float(y));
        if(any(lessThan(q,vec2(0))) || any(greaterThanEqual(q,sourceSize))) continue;
        vec4 candidate=texture2D(sampler0,(q+0.5)/sourceSize);
        if(candidate.b<0.7 || candidate.b>0.8) continue;
        vec2 v=floor(candidate.rg*8.0+0.5)-4.0;
        if(found && any(notEqual(v,selected))) conflict=true;
        selected=v; found=true;
    }
    gl_FragColor=found && !conflict?vec4((selected+4.0)/8.0,0.625,own.a):own;
}
