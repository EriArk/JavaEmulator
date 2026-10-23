// Copyright 2026. GPL-3.0-only. Bounded backward block matching, not an AI model.
precision highp float;
uniform sampler2D sampler0;
uniform sampler2D previous;
uniform vec2 sourceSize;
uniform vec2 textureSize;
varying vec2 v_texcoord0;
vec3 currentAt(vec2 p) { return texture2D(sampler0,(clamp(p,vec2(0),sourceSize-1.0)+0.5)/textureSize).rgb; }
vec3 oldAt(vec2 p) { return texture2D(previous,(clamp(p,vec2(0),sourceSize-1.0)+0.5)/textureSize).rgb; }
float errorAt(vec2 p, vec2 v) {
    vec3 e=abs(currentAt(p)-oldAt(p+v));
    e+=abs(currentAt(p+vec2(2,0))-oldAt(p+vec2(2,0)+v));
    e+=abs(currentAt(p-vec2(2,0))-oldAt(p-vec2(2,0)+v));
    e+=abs(currentAt(p+vec2(0,2))-oldAt(p+vec2(0,2)+v));
    e+=abs(currentAt(p-vec2(0,2))-oldAt(p-vec2(0,2)+v));
    return dot(e,vec3(1.0/15.0));
}
void main() {
    vec2 p=v_texcoord0*sourceSize-0.5, best=vec2(0);
    float zero=errorAt(p,best), score=zero;
    for(int y=-4;y<=4;y++) for(int x=-4;x<=4;x++) {
        vec2 v=vec2(float(x),float(y));
        float e=errorAt(p,v)+0.0005*dot(v,v);
        if(e<score) { score=e; best=v; }
    }
    float confidence=(score<0.04 && zero-score>0.01)?1.0:0.0;
    gl_FragColor=vec4((best+4.0)/8.0,confidence,1);
}
