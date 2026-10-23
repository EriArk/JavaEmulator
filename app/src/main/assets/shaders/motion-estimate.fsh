// Copyright 2026. GPL-3.0-only. Bounded backward block matching, not an AI model.
precision highp float;
uniform sampler2D sampler0;
uniform sampler2D previous;
uniform vec2 sourceSize;
uniform vec2 textureSize;
varying vec2 v_texcoord0;
vec3 center, right, left, down, up, topLeft, topRight, bottomLeft, bottomRight;
vec3 currentAt(vec2 p) { return texture2D(sampler0,(clamp(p,vec2(0),sourceSize-1.0)+0.5)/textureSize).rgb; }
vec3 oldAt(vec2 p) { return texture2D(previous,(clamp(p,vec2(0),sourceSize-1.0)+0.5)/textureSize).rgb; }
float errorAt(vec2 p, vec2 v) {
    vec3 e=abs(center-oldAt(p+v));
    e+=abs(right-oldAt(p+vec2(1,0)+v));
    e+=abs(left-oldAt(p-vec2(1,0)+v));
    e+=abs(down-oldAt(p+vec2(0,1)+v));
    e+=abs(up-oldAt(p-vec2(0,1)+v));
    e+=abs(topLeft-oldAt(p+vec2(-1,-1)+v));
    e+=abs(topRight-oldAt(p+vec2(1,-1)+v));
    e+=abs(bottomLeft-oldAt(p+vec2(-1,1)+v));
    e+=abs(bottomRight-oldAt(p+vec2(1,1)+v));
    return dot(e,vec3(1.0/27.0));
}
void main() {
    // Sample pixel centers, never nearest-filter boundaries that vary across GPU precision.
    vec2 p=floor(v_texcoord0*sourceSize-0.5), best=vec2(0);
    center=currentAt(p); right=currentAt(p+vec2(1,0)); left=currentAt(p-vec2(1,0));
    down=currentAt(p+vec2(0,1)); up=currentAt(p-vec2(0,1));
    // A 3x3 patch observes diagonal details at the same nine-sample cost as the old cross.
    topLeft=currentAt(p+vec2(-1,-1)); topRight=currentAt(p+vec2(1,-1));
    bottomLeft=currentAt(p+vec2(-1,1)); bottomRight=currentAt(p+vec2(1,1));
    float zero=errorAt(p,best), score=zero, residual=zero;
    // Static blocks cannot benefit from motion search. Do not re-fetch their template per candidate.
    if(zero<0.002) { gl_FragColor=vec4(0.5,0.5,0,0); return; }
    for(int y=-4;y<=4;y++) for(int x=-4;x<=4;x++) {
        vec2 v=vec2(float(x),float(y));
        // Do not manufacture a matching patch by repeating clamped border pixels.
        if(any(lessThan(min(p,p+v),vec2(1))) || any(greaterThan(max(p,p+v),sourceSize-2.0))) continue;
        float error=errorAt(p,v), e=error+0.0005*dot(v,v);
        if(e<score) { score=e; residual=error; best=v; }
    }
    float confidence=(score<0.04 && zero-score>0.01)?1.0:0.0;
    gl_FragColor=vec4((best+4.0)/8.0,confidence,residual);
}
