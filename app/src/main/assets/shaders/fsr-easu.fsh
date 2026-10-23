// Adapted from AMD FidelityFX FSR 1.0, a21ffb8f6c13233ba336352bdff293894c706575.
// Copyright (c) 2021 Advanced Micro Devices, Inc. MIT; see licenses/FSR-MIT.txt.
// GLES2 port: scalar texture fetches and precise division replace gather/approximations.
precision highp float;
uniform sampler2D sampler0;
uniform vec2 sourceSize;
uniform vec2 textureSize;
varying vec2 v_texcoord0;
vec3 fetch(vec2 p) { return texture2D(sampler0, (clamp(p, vec2(0.0), sourceSize-1.0)+0.5)/textureSize).rgb; }
float lum(vec3 c) { return c.g + 0.5*(c.r+c.b); }
void edge(inout vec2 dir, inout float len, float w, float a, float b, float c, float d, float e) {
    vec2 delta = vec2(d-b,e-a);
    dir += delta*w;
    vec2 strength = clamp(abs(delta)/max(vec2(max(abs(d-c),abs(c-b)), max(abs(e-c),abs(c-a))),vec2(0.00001)),0.0,1.0);
    len += dot(strength,strength)*w;
}
void tap(inout vec3 color, inout float weight, vec2 off, vec2 dir, vec2 len, float lob, float clp, vec3 c) {
    vec2 v = vec2(dot(off,dir), dot(off,vec2(-dir.y,dir.x)))*len;
    float d = min(dot(v,v),clp);
    float a = lob*d-1.0, b = 0.4*d-1.0;
    float w = (1.5625*b*b-0.5625)*a*a;
    color += c*w; weight += w;
}
void main() {
    vec2 pos = v_texcoord0*sourceSize-0.5, base = floor(pos), p = fract(pos);
    vec3 b=fetch(base+vec2(0,-1)), c=fetch(base+vec2(1,-1));
    vec3 e=fetch(base+vec2(-1,0)), f=fetch(base), g=fetch(base+vec2(1,0)), h=fetch(base+vec2(2,0));
    vec3 i=fetch(base+vec2(-1,1)), j=fetch(base+vec2(0,1)), k=fetch(base+vec2(1,1)), l=fetch(base+vec2(2,1));
    vec3 n=fetch(base+vec2(0,2)), o=fetch(base+vec2(1,2));
    vec2 dir=vec2(0); float len=0.0;
    edge(dir,len,(1.0-p.x)*(1.0-p.y),lum(b),lum(e),lum(f),lum(g),lum(j));
    edge(dir,len,p.x*(1.0-p.y),lum(c),lum(f),lum(g),lum(h),lum(k));
    edge(dir,len,(1.0-p.x)*p.y,lum(f),lum(i),lum(j),lum(k),lum(n));
    edge(dir,len,p.x*p.y,lum(g),lum(j),lum(k),lum(l),lum(o));
    float d=dot(dir,dir);
    dir=d<1.0/32768.0?vec2(1,0):dir*inversesqrt(d);
    len=0.25*len*len;
    float stretch=dot(dir,dir)/max(abs(dir.x),abs(dir.y));
    vec2 lens=vec2(1.0+(stretch-1.0)*len,1.0-0.5*len);
    float lob=0.5-0.29*len, clp=1.0/lob, w=0.0; vec3 result=vec3(0);
    tap(result,w,vec2(0,-1)-p,dir,lens,lob,clp,b);
    tap(result,w,vec2(1,-1)-p,dir,lens,lob,clp,c);
    tap(result,w,vec2(-1,0)-p,dir,lens,lob,clp,e);
    tap(result,w,-p,dir,lens,lob,clp,f);
    tap(result,w,vec2(1,0)-p,dir,lens,lob,clp,g);
    tap(result,w,vec2(2,0)-p,dir,lens,lob,clp,h);
    tap(result,w,vec2(-1,1)-p,dir,lens,lob,clp,i);
    tap(result,w,vec2(0,1)-p,dir,lens,lob,clp,j);
    tap(result,w,vec2(1,1)-p,dir,lens,lob,clp,k);
    tap(result,w,vec2(2,1)-p,dir,lens,lob,clp,l);
    tap(result,w,vec2(0,2)-p,dir,lens,lob,clp,n);
    tap(result,w,vec2(1,2)-p,dir,lens,lob,clp,o);
    gl_FragColor=vec4(clamp(result/max(w,0.00001),min(min(f,g),min(j,k)),max(max(f,g),max(j,k))),1);
}
