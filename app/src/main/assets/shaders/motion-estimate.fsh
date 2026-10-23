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
float largest(vec3 c) { return max(c.r,max(c.g,c.b)); }
// 0: ordinary tile, 1: sparse background without a moving anchor, 2: moving anchor.
float sparseAnchor(inout vec2 p) {
    vec2 grid=ceil(sourceSize/8.0), tile=floor(v_texcoord0*grid);
    vec2 lo=ceil(tile*sourceSize/grid-0.5);
    vec2 hi=ceil((tile+1.0)*sourceSize/grid-0.5)-1.0;
    vec3 background=currentAt(lo);
    if(largest(abs(oldAt(lo)-background))>0.5/255.0) return 0.0;
    for(int i=1;i<4;i++) {
        vec2 corner=vec2(i==1?lo.x:hi.x,i==2?lo.y:hi.y);
        if(largest(abs(currentAt(corner)-background))>0.5/255.0 ||
           largest(abs(oldAt(corner)-background))>0.5/255.0) return 0.0;
    }
    float oldCount=0.0, newCount=0.0, contrast=0.0;
    vec2 anchor=p;
    for(int y=0;y<8;y++) for(int x=0;x<8;x++) {
        vec2 q=lo+vec2(float(x),float(y));
        if(any(greaterThan(q,hi))) continue;
        vec3 a=oldAt(q), b=currentAt(q);
        float difference=largest(abs(b-background));
        if(largest(abs(a-background))>0.5/255.0) oldCount+=1.0;
        if(difference>0.5/255.0) newCount+=1.0;
        if(max(oldCount,newCount)>16.0) return 0.0;
        if(difference>contrast && largest(abs(a-b))>0.5/255.0) {
            contrast=difference; anchor=q;
        }
    }
    if(contrast<=0.5/255.0) return 1.0;
    p=anchor; return 2.0;
}
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
    float sparse=sparseAnchor(p);
    center=currentAt(p); right=currentAt(p+vec2(1,0)); left=currentAt(p-vec2(1,0));
    down=currentAt(p+vec2(0,1)); up=currentAt(p-vec2(0,1));
    // A 3x3 patch observes diagonal details at the same nine-sample cost as the old cross.
    topLeft=currentAt(p+vec2(-1,-1)); topRight=currentAt(p+vec2(1,-1));
    bottomLeft=currentAt(p+vec2(-1,1)); bottomRight=currentAt(p+vec2(1,1));
    float zero=errorAt(p,best), score=zero, residual=zero;
    vec4 directionalError=vec4(1.0); // -x, +x, -y, +y; reuses the existing search samples.
    // Static blocks cannot benefit from motion search. Do not re-fetch their template per candidate.
    if(zero<0.002 || sparse==1.0) {
        gl_FragColor=vec4(0.5,0.5,sparse==1.0?0.25:0.0,zero); return;
    }
    for(int y=-4;y<=4;y++) for(int x=-4;x<=4;x++) {
        vec2 v=vec2(float(x),float(y));
        // Do not manufacture a matching patch by repeating clamped border pixels.
        if(any(lessThan(min(p,p+v),vec2(1))) || any(greaterThan(max(p,p+v),sourceSize-2.0))) continue;
        float error=errorAt(p,v), e=error+0.0005*dot(v,v);
        if(x<0) directionalError.x=min(directionalError.x,error);
        if(x>0) directionalError.y=min(directionalError.y,error);
        if(y<0) directionalError.z=min(directionalError.z,error);
        if(y>0) directionalError.w=min(directionalError.w,error);
        if(e<score) { score=e; residual=error; best=v; }
    }
    // Repeated patterns can match opposite directions equally well. Distance bias
    // and search order are not evidence of motion; prefer the real frame there.
    float similar=residual+0.002;
    bool ambiguous=(best.x<0.0 && directionalError.y<=similar)
        || (best.x>0.0 && directionalError.x<=similar)
        || (best.y<0.0 && directionalError.w<=similar)
        || (best.y>0.0 && directionalError.z<=similar);
    float confidence=(!ambiguous && score<0.04 && zero-score>0.01)?1.0:0.0;
    // B encodes eligibility as well as confidence: .25 sparse background, .75 sparse match, 1 dense match.
    if(sparse>0.0) confidence=confidence>0.5?0.75:0.0;
    gl_FragColor=vec4((best+4.0)/8.0,confidence,residual);
}
