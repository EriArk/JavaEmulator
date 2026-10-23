// Adapted from AMD FidelityFX FSR 1.0, a21ffb8f6c13233ba336352bdff293894c706575.
// Copyright (c) 2021 Advanced Micro Devices, Inc. MIT; see licenses/FSR-MIT.txt.
// GLES2 port: scalar texture fetches and precise division replace gather/approximations.
precision highp float;
uniform sampler2D sampler0;
uniform vec2 sourceSize;
uniform vec2 textureSize;
varying vec2 v_texcoord0;
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
    // The twelve taps share four columns and four rows, including viewport clamping.
    vec4 xs=(clamp(base.x+vec4(-1,0,1,2),0.0,sourceSize.x-1.0)+0.5)/textureSize.x;
    vec4 ys=(clamp(base.y+vec4(-1,0,1,2),0.0,sourceSize.y-1.0)+0.5)/textureSize.y;
    vec3 f=texture2D(sampler0,vec2(xs.y,ys.y)).rgb, g=texture2D(sampler0,vec2(xs.z,ys.y)).rgb;
    vec3 j=texture2D(sampler0,vec2(xs.y,ys.z)).rgb, k=texture2D(sampler0,vec2(xs.z,ys.z)).rgb;
    vec3 lo=min(min(f,g),min(j,k)), hi=max(max(f,g),max(j,k));
    // The final deringing clamp fixes the result when all four nearest RGB values agree.
    vec3 filtered=lo;
#ifdef EASU_FLAT_REGIONS
    if (any(notEqual(lo,hi)))
#endif
    {
        vec3 b=texture2D(sampler0,vec2(xs.y,ys.x)).rgb, c=texture2D(sampler0,vec2(xs.z,ys.x)).rgb;
        vec3 e=texture2D(sampler0,vec2(xs.x,ys.y)).rgb, h=texture2D(sampler0,vec2(xs.w,ys.y)).rgb;
        vec3 i=texture2D(sampler0,vec2(xs.x,ys.z)).rgb, l=texture2D(sampler0,vec2(xs.w,ys.z)).rgb;
        vec3 n=texture2D(sampler0,vec2(xs.y,ys.w)).rgb, o=texture2D(sampler0,vec2(xs.z,ys.w)).rgb;
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
        filtered=clamp(result/max(w,0.00001),lo,hi);
    }
    gl_FragColor=vec4(filtered,1);
}
