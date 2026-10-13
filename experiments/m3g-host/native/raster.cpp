/* SPDX-License-Identifier: GPL-3.0-or-later
 * Adapted from FreeJ2ME-Plus Graphics3D/Graphics3DPipelines (GPL-3.0-or-later).
 * Keeps their scanline coverage, interpolation and integer blending arithmetic.
 * No code from the legacy EPL M3G implementation is included.
 */
#include <jni.h>
#include <algorithm>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <climits>
#include <array>

namespace {
using u32 = uint32_t;
constexpr u32 RB = 0x00ff00ff, RGB = 0x00ffffff, ALPHA = 0xff000000;

// Java narrows floating point through a saturating int, including NaN -> zero.
inline int narrow(float f) {
    if (f >= 2147483648.0f) return INT_MAX;
    if (f <= -2147483648.0f) return INT_MIN;
    return std::isnan(f) ? 0 : static_cast<int>(f);
}
inline float reciprocal(float x) {
    u32 bits;
    std::memcpy(&bits, &x, 4);
    bits = 0x7ef127ea - bits;
    float y;
    std::memcpy(&y, &bits, 4);
    float e = 1.0f - x * y;
    return y * (1.0f + e) * (1.0f + e * e);
}
inline float fastExp(float x) {
    int bits = narrow(12102203.0f * x + float(1064866805));
    float value;
    std::memcpy(&value, &bits, 4);
    return value;
}
inline int wrap(int x, int size, bool repeat) {
    if (!repeat) return std::max(0, std::min(size - 1, x));
    if ((size & (size - 1)) == 0) return x & (size - 1);
    int r = x % size;
    return r < 0 ? r + size : r;
}

template<bool Linear>
inline u32 sample(const u32* image, float s, float t, int w, int h, int repeat) {
    if (!Linear) {
        int x = wrap(narrow(s + 32768.0f) - 32768, w, repeat & 1);
        int y = wrap(narrow(t + 32768.0f) - 32768, h, repeat & 2);
        return image[y * w + x];
    }
    int sf = narrow(s * 256.0f) - 128, tf = narrow(t * 256.0f) - 128;
    if (!(repeat & 1)) sf = std::max(0, std::min((w - 1) << 8, sf));
    if (!(repeat & 2)) tf = std::max(0, std::min((h - 1) << 8, tf));
    int x0 = wrap(sf >> 8, w, repeat & 1), y0 = wrap(tf >> 8, h, repeat & 2);
    int x1 = x0 + 1 < w ? x0 + 1 : ((repeat & 1) ? 0 : x0);
    int y1 = y0 + 1 < h ? y0 + 1 : ((repeat & 2) ? 0 : y0);
    u32 c00 = image[y0*w+x0], c10 = image[y0*w+x1];
    u32 c01 = image[y1*w+x0], c11 = image[y1*w+x1];
    u32 fx = sf & 255, fy = tf & 255, ix = 256-fx, iy = 256-fy;
    u32 rt = (((c00 & RB)*ix + (c10 & RB)*fx) >> 8) & RB;
    u32 at = ((((c00 >> 8) & RB)*ix + ((c10 >> 8) & RB)*fx) >> 8) & RB;
    u32 rb = (((c01 & RB)*ix + (c11 & RB)*fx) >> 8) & RB;
    u32 ab = ((((c01 >> 8) & RB)*ix + ((c11 >> 8) & RB)*fx) >> 8) & RB;
    return ((((at*iy + ab*fy) >> 8) & RB) << 8) | (((rt*iy + rb*fy) >> 8) & RB);
}

template<bool Extended>
inline u32 textureBlend(u32 bg, u32 fg, int mode, u32 blendColor) {
    if constexpr (Extended) switch (mode) {
        case 6: case 7: {
            u32 rb = (bg & RB) + (fg & RB), g = (bg & 0xff00) + (fg & 0xff00);
            u32 overflow = rb & 0x01000100;
            rb = (rb | (overflow - (overflow >> 8))) & RB;
            overflow = g & 0x10000;
            g = (g | (overflow - (overflow >> 8))) & 0xff00;
            u32 a = mode == 7 ? (((bg >> 24)*(fg >> 24)+128) >> 8) : bg >> 24;
            return (a << 24) | rb | g;
        }
        case 8: case 9: {
            u32 out = mode == 9 ? ((((bg >> 24)*(fg >> 24)+128) >> 8) << 24) : bg & ALPHA;
            for (int shift=0; shift<24; shift+=8) {
                int b=(bg >> shift)&255, c=(blendColor >> shift)&255, t=(fg >> shift)&255;
                out |= u32(b + (((c-b)*t+128) >> 8)) << shift;
            }
            return out;
        }
        case 10: {
            u32 a=fg >> 24, bRB=bg & RB, bAG=(bg >> 8)&RB;
            u32 rb=(bRB + ((u32(int32_t(((fg & RB)-bRB)*a) >> 8)) & RB)) & RB;
            u32 ag=(bAG + ((u32(int32_t((((fg >> 8)&RB)-bAG)*a) >> 8)) & RB)) & RB;
            return (bg & ALPHA) | ((rb | (ag << 8)) & RGB);
        }
        case 11: return bg;
        default: break;
    }
    switch (mode) {
        case 0: return fg;
        case 1: return (bg & ALPHA) | (fg & RGB);
        case 4: return ((((bg >> 24)*(fg >> 24) + 128) >> 8) << 24) | (bg & RGB);
        case 5: return (fg & ALPHA) | (bg & RGB);
        default: {
            u32 round = mode == 3 ? 128 : 0;
            u32 r = (((bg >> 16) & 255)*((fg >> 16) & 255) + round) >> 8;
            u32 g = (((bg >> 8) & 255)*((fg >> 8) & 255) + round) >> 8;
            u32 b = ((bg & 255)*(fg & 255) + round) >> 8;
            u32 a = mode == 3 ? ((bg >> 24)*(fg >> 24) + (bg >> 24)) >> 8 : bg >> 24;
            return (a << 24) | (r << 16) | (g << 8) | b;
        }
    }
}

inline u32 composite(u32 bg, u32 fg, int alpha, int mode) {
    if (mode == 0) return fg;
    if (mode == 1) {
        if (alpha <= 0) return bg;
        if (alpha >= 255) return fg;
        u32 inv = 255-alpha;
        u32 rb = (((fg & RB)*alpha + (bg & RB)*inv) >> 8) & RB;
        u32 g = ((((fg >> 8) & 255)*alpha + ((bg >> 8) & 255)*inv) >> 8) & 255;
        u32 a = alpha + (((bg >> 24)*inv) >> 8);
        return (a << 24) | (g << 8) | rb;
    }
    if (mode == 2) {
        if (alpha == 0) return bg;
        u32 rb = (bg & RB) + (((fg & RB)*alpha >> 8) & RB);
        u32 g = (bg & 0xff00) + (((fg & 0xff00)*alpha >> 8) & 0xff00);
        u32 a = (bg >> 24) + ((alpha*(255-(bg >> 24))) >> 8);
        u32 overflow = rb & 0x01000100;
        rb = (rb | (overflow - (overflow >> 8))) & RB;
        overflow = g & 0x10000;
        g = (g | (overflow - (overflow >> 8))) & 0xff00;
        return ((a | (0u-(a >> 8))) << 24) | rb | g;
    }
    u32 out = 0;
    for (int shift = 0; shift < 32; shift += 8) {
        u32 c = (((bg >> shift) & 255)*((fg >> shift) & 255)) >> (mode == 4 ? 7 : 8);
        out |= std::min(255u, c) << shift;
    }
    return out;
}

struct MipLevel {
    const u32* pixels = nullptr;
    int width, height;
    float scaleS, scaleT;
};

struct Texture {
    const u32* pixels = nullptr;
    int width, height, repeat, linear, blend;
    u32 blendColor;
    float sL, tL, dsL, dtL, stepS, stepT;
    MipLevel levels[16];
    int count=1, mip=0;
    float sx=0, tx=0, sy=0, ty=0, wx=0, wy=0;
};

const std::array<int,256> logFraction=[] {
    std::array<int,256> values{};
    for (int i=0; i<256; ++i) values[i]=int(std::log(1.0+i/256.0)*184.6649652337873);
    return values;
}();

inline u32 sampleLevel(const Texture& tex, int level, float s, float t) {
    const MipLevel& image=tex.levels[level];
    s*=image.scaleS; t*=image.scaleT;
    return tex.linear ? sample<true>(image.pixels,s,t,image.width,image.height,tex.repeat)
                      : sample<false>(image.pixels,s,t,image.width,image.height,tex.repeat);
}

inline u32 mipSample(const Texture& tex, float s, float t, float inv) {
    int lod=0;
    if (tex.mip) {
        float sx=(tex.sx-s*tex.wx)*inv, tx=(tex.tx-t*tex.wx)*inv;
        float sy=(tex.sy-s*tex.wy)*inv, ty=(tex.ty-t*tex.wy)*inv;
        float a=sx*sx+tx*tx, b=sy*sy+ty*ty, rho=a>b ? a : b;
        if (rho>1) {
            u32 bits;
            std::memcpy(&bits,&rho,4);
            lod=(int((bits>>23)&255)-127)*128+logFraction[(bits>>15)&255];
        }
        lod=std::min(lod,(tex.count-1)<<8);
    }
    int level=tex.mip==2 ? lod>>8 : (lod+127)>>8, weight=lod&255;
    u32 color=sampleLevel(tex,level,s,t);
    if (tex.mip!=2 || weight==0 || level==tex.count-1) return color;
    u32 other=sampleLevel(tex,level+1,s,t), invWeight=256-weight;
    u32 rb=(((color&RB)*invWeight+(other&RB)*weight)>>8)&RB;
    u32 ag=((((color>>8)&RB)*invWeight+((other>>8)&RB)*weight)>>8)&RB;
    return (ag<<8)|rb;
}

// Units and the single-texture filter specialize the common inner loops.
template<int Units, int Filter = -1, bool Extended = true, bool Mip = false>
void render(u32* __restrict raster, int16_t* __restrict depth,
            Texture* __restrict textures, const float* __restrict f, const int* __restrict p) {
    const bool colors = p[6]&1, usesDepth = p[6]&4, depthWrite = p[6]&8;
    const bool perspective = p[6]&16, fog = p[6]&32, parallel = p[6]&64, dither = p[6]&128;
    float xL=f[0], xR=f[1], zL=f[2], pwL=f[3];
    int row=p[2], fogFactor=65535, stepFog=0;
    for (int y=p[0]; y<p[1]; ++y, xL+=f[4], xR+=f[5], zL+=f[6], pwL+=f[7], row+=p[3]) {
        int left=std::max(p[4], narrow(std::ceil(xL-0.5f)));
        int right=std::min(p[5], narrow(std::ceil(xR-0.5f)));
        if (left >= right) {
            for (int i=0; i<Units; ++i) { textures[i].sL+=textures[i].dsL; textures[i].tL+=textures[i].dtL; }
            continue;
        }
        u32 color[4] = {};
        if (colors) {
            float dx=left+0.5f-f[17], dy=y+0.5f-f[18];
            for (int c=0; c<4; ++c) {
                color[c]=narrow((float((u32(p[8]) >> (24-c*8)) & 255) + dx*f[19+c] + dy*f[23+c])*65536.0f);
            }
        }
        float diff=left+0.5f-xL;
        float pw=perspective ? pwL+diff*f[9] : 0.0f;
        float inv=perspective ? reciprocal(pw) : 1.0f, stepInv=0.0f;
        float z=usesDepth ? zL+diff*f[8]+f[10] : -32768.0f;
        float s[4], t[4];
        for (int i=0; i<Units; ++i) {
            Texture& tex=textures[i];
            s[i]=tex.sL+diff*tex.stepS; t[i]=tex.tL+diff*tex.stepT;
            tex.sL+=tex.dsL; tex.tL+=tex.dtL;
        }
        int next=perspective ? left : INT_MAX, index=row+left;
        for (int x=left; x<right; ++x, ++index, z+=f[8], pw+=f[9], inv+=stepInv, fogFactor+=stepFog) {
            if (x == next) {
                int span=std::min(right-x, (p[24]+1)-(x&p[24]));
                float denom=pw*(pw+f[9]*span);
                u32 bits;
                std::memcpy(&bits, &denom, 4);
                bits=0x7ef127ea-bits;
                float invDenom;
                std::memcpy(&invDenom, &bits, 4);
                invDenom=invDenom*(2.0f-denom*invDenom);
                stepInv=-f[9]*invDenom;
                if (fog) {
                    float eye=parallel ? f[29]+(x+0.5f-f[17])*f[30]+(y+0.5f-f[18])*f[31] : inv;
                    float end=parallel ? eye+f[30]*span : inv+stepInv*span;
                    float a=(p[6]&256 ? fastExp(-f[32]*eye) : f[27]-eye*f[28])*256.0f;
                    float b=(p[6]&256 ? fastExp(-f[32]*end) : f[27]-end*f[28])*256.0f;
                    a=a<0 ? 0 : a>255 ? 255 : a;
                    b=b<0 ? 0 : b>255 ? 255 : b;
                    fogFactor=narrow(a*256.0f);
                    stepFog=narrow((b-fogFactor*0.00390625f)*(1.0f/span)*256.0f);
                }
                next+=span;
            }
            int16_t zi=static_cast<int16_t>(narrow(z));
            if (depth[index] < zi) {
                if (colors) for (int c=0;c<4;++c) color[c]+=p[9+c];
                for (int i=0; i<Units; ++i) { s[i]+=textures[i].stepS; t[i]+=textures[i].stepT; }
                continue;
            }
            u32 pixel=p[7];
            if (colors) {
                pixel=0;
                for (int c=0;c<4;++c) { pixel|=((color[c] >> 16)&255) << (24-c*8); color[c]+=p[9+c]; }
            }
            for (int i=0; i<Units; ++i) {
                const Texture& tex=textures[i];
                u32 color;
                if constexpr (Mip) color=mipSample(tex,s[i]*inv,t[i]*inv,inv);
                else color=(Filter==1 || (Filter<0 && tex.linear))
                        ? sample<true>(tex.pixels,s[i]*inv,t[i]*inv,tex.width,tex.height,tex.repeat)
                        : sample<false>(tex.pixels,s[i]*inv,t[i]*inv,tex.width,tex.height,tex.repeat);
                pixel=textureBlend<Extended>(pixel,color,tex.blend,tex.blendColor);
                s[i]+=tex.stepS; t[i]+=tex.stepT;
            }
            int alpha=((pixel >> 24)*p[14]) >> 8;
            if (alpha < p[13]) continue;
            pixel=(pixel&RGB)|(u32(alpha)<<24);
            if (depthWrite) depth[index]=zi;
            if (p[15] == 0) continue;
            if (fog) {
                int amount=fogFactor >> 8;
                u32 rb=(p[22]+(int32_t(((pixel & RB)-p[22])*amount) >> 8)) & RB;
                int g=p[23]+((int((pixel >> 8)&255)-p[23])*amount >> 8);
                pixel=(pixel & ALPHA) | rb | (u32(g) << 8);
            }
            if (dither) {
                int value=(((x^y)&1)<<3) | ((y&1)<<2) | (((x^y)&2)<<1) | ((y&2)>>1);
                u32 out=pixel & ALPHA;
                for (int shift=0;shift<24;shift+=8) out|=std::min(255u, ((pixel >> shift)&255)+value) << shift;
                pixel=out;
            }
            u32 bg=raster[index], mask=p[15];
            raster[index]=(composite(bg, pixel, alpha, p[21]) & mask) | (bg & ~mask);
        }
    }
}

// Resolve/validate all Java references before entering any pinned-array region.
struct TextureRefs {
    JNIEnv* env;
    jintArray arrays[64] = {};
    bool owned[64] = {};
    explicit TextureRefs(JNIEnv* e) : env(e) {}
    ~TextureRefs() { for (int i=0; i<64; ++i) if (owned[i] && arrays[i]) env->DeleteLocalRef(arrays[i]); }
};

bool draw(JNIEnv* env, jintArray pixels, jshortArray depth, jobjectArray images,
          jintArray singleImage, bool single, jfloatArray state, jintArray flags,
          jfloatArray mipState=nullptr, jintArray mipFlags=nullptr) {
    if (!pixels || !depth || !state || !flags || env->GetArrayLength(state)!=51
            || env->GetArrayLength(flags)!=45) return false;
    float f[51]; int p[45];
    env->GetFloatArrayRegion(state, 0, 51, f);
    env->GetIntArrayRegion(flags, 0, 45, p);
    if (env->ExceptionCheck()) return false;
    const int length=env->GetArrayLength(pixels);
    const int units=p[25];
    const bool mip=mipState || mipFlags;
    float mf[18] = {}; int mp[8] = {};
    if (mip) {
        if (!mipState || !mipFlags || env->GetArrayLength(mipState)!=18
                || env->GetArrayLength(mipFlags)!=8 || single) return false;
        env->GetFloatArrayRegion(mipState,0,18,mf);
        env->GetIntArrayRegion(mipFlags,0,8,mp);
        if (env->ExceptionCheck()) return false;
        for (float value : mf) if (!std::isfinite(value)) return false;
    }
    if (env->GetArrayLength(depth)<length || p[0]<0 || p[1]<p[0] || p[3]<=0
            || p[4]<0 || p[5]<p[4] || p[5]-p[4]>p[3] || p[13]<0 || p[13]>255
            || p[14]<0 || p[14]>256 || p[21]<0 || p[21]>4
            || units<0 || units>4 || bool(p[6]&2)!=(units>0)
            || (single && units>1)
            || p[24]<0 || p[24]>255 || (p[24] & (p[24]+1))!=0) return false;
    if (p[0]==p[1] || p[4]==p[5]) return true;
    int64_t first=int64_t(p[2])+p[4];
    int64_t last=int64_t(p[2])+int64_t(p[1]-p[0]-1)*p[3]+p[5];
    if (first<0 || last>length) return false;
    // Reject non-finite setup before acquiring writable arrays or drawing anything.
    for (int i=0; i<33; ++i) if ((i<11 || i>16) && !std::isfinite(f[i])) return false;
    TextureRefs refs(env);
    Texture textures[4];
    int alias[64], unitBase[4], total=0;
    const u32* pinned[64] = {};
    if (units && !single && (!images || env->GetArrayLength(images)<units)) return false;
    for (int i=0; i<units; ++i) {
        int fi=i==0 ? 11 : 33+(i-1)*6, pi=i==0 ? 16 : 27+(i-1)*6;
        if (p[pi]<=0 || p[pi+1]<=0 || p[pi]>32768 || p[pi+1]>32768
                || p[pi+2]<0 || p[pi+2]>3 || p[pi+3]<0 || p[pi+3]>1 || p[pi+4]<0 || p[pi+4]>11
                ) return false;
        for (int j=0; j<6; ++j) if (!std::isfinite(f[fi+j])) return false;
        Texture& t=textures[i];
        t.width=p[pi]; t.height=p[pi+1]; t.repeat=p[pi+2]; t.linear=p[pi+3];
        t.blend=p[pi+4]; t.blendColor=p[i==0 ? 26 : pi+5];
        t.sL=f[fi]; t.tL=f[fi+1]; t.dsL=f[fi+2]; t.dtL=f[fi+3]; t.stepS=f[fi+4]; t.stepT=f[fi+5];
        if (mip) {
            t.count=mp[i*2]; t.mip=mp[i*2+1];
            int expected=1, size=std::max(t.width,t.height);
            if (t.mip) while ((size >>= 1)>0) ++expected;
            if (t.count!=expected || t.count>16 || t.mip<0 || t.mip>2) return false;
            t.sx=mf[i*4]; t.tx=mf[i*4+1]; t.sy=mf[i*4+2]; t.ty=mf[i*4+3];
            t.wx=mf[16]; t.wy=mf[17];
        }
        unitBase[i]=total;
        if (!single && env->GetArrayLength(images)<total+t.count) return false;
        for (int level=0; level<t.count; ++level,++total) {
            auto array=single ? singleImage : static_cast<jintArray>(env->GetObjectArrayElement(images,total));
            refs.arrays[total]=array; refs.owned[total]=!single;
            int w=std::max(1,t.width >> level), h=std::max(1,t.height >> level);
            t.levels[level].width=w; t.levels[level].height=h;
            t.levels[level].scaleS=float(w)/t.width; t.levels[level].scaleT=float(h)/t.height;
            if (!array || env->ExceptionCheck() || int64_t(w)*h>env->GetArrayLength(array)
                    || env->IsSameObject(pixels,array)) return false;
            alias[total]=total;
            for (int j=0; j<total; ++j) if (env->IsSameObject(array,refs.arrays[j])) { alias[total]=alias[j]; break; }
        }
    }
    auto* raster=static_cast<u32*>(env->GetPrimitiveArrayCritical(pixels, nullptr));
    if (!raster) return false;
    auto* z=static_cast<int16_t*>(env->GetPrimitiveArrayCritical(depth, nullptr));
    bool ready=z;
    for (int i=0; ready && i<total; ++i) {
        pinned[i]=alias[i]<i ? pinned[alias[i]]
            : static_cast<u32*>(env->GetPrimitiveArrayCritical(refs.arrays[i],nullptr));
        ready=pinned[i];
    }
    if (ready) {
        for (int i=0; i<units; ++i) {
            textures[i].pixels=pinned[unitBase[i]];
            for (int l=0; l<textures[i].count; ++l) textures[i].levels[l].pixels=pinned[unitBase[i]+l];
        }
        if (mip) switch (units) {
            case 1: render<1,-1,true,true>(raster,z,textures,f,p); break;
            case 2: render<2,-1,true,true>(raster,z,textures,f,p); break;
            case 3: render<3,-1,true,true>(raster,z,textures,f,p); break;
            case 4: render<4,-1,true,true>(raster,z,textures,f,p); break;
            default: ready=false;
        }
        else switch (units) {
            case 0: render<0>(raster,z,textures,f,p); break;
            case 1:
                if (textures[0].blend>5) render<1>(raster,z,textures,f,p);
                else if (textures[0].linear) render<1,1,false>(raster,z,textures,f,p);
                else render<1,0,false>(raster,z,textures,f,p);
                break;
            case 2: render<2>(raster,z,textures,f,p); break;
            case 3: render<3>(raster,z,textures,f,p); break;
            case 4: render<4>(raster,z,textures,f,p); break;
        }
    }
    for (int i=total-1; i>=0; --i) if (pinned[i] && alias[i]==i)
        env->ReleasePrimitiveArrayCritical(refs.arrays[i],const_cast<u32*>(pinned[i]),JNI_ABORT);
    if (z) env->ReleasePrimitiveArrayCritical(depth, z, 0);
    env->ReleasePrimitiveArrayCritical(pixels, raster, 0);
    return ready;
}
}

extern "C" JNIEXPORT jboolean JNICALL
Java_javax_microedition_m3g_NativeRasterizer_drawHalf(JNIEnv* env, jclass,
        jintArray pixels, jshortArray depth, jobjectArray images, jfloatArray state, jintArray flags) {
    return draw(env,pixels,depth,images,nullptr,false,state,flags);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_javax_microedition_m3g_NativeRasterizer_drawSingle(JNIEnv* env, jclass,
        jintArray pixels, jshortArray depth, jintArray image, jfloatArray state, jintArray flags) {
    return draw(env,pixels,depth,nullptr,image,true,state,flags);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_javax_microedition_m3g_NativeRasterizer_drawMip(JNIEnv* env, jclass,
        jintArray pixels, jshortArray depth, jobjectArray images, jfloatArray state, jintArray flags,
        jfloatArray mipState, jintArray mipFlags) {
    if (!mipState || !mipFlags) return false;
    return draw(env,pixels,depth,images,nullptr,false,state,flags,mipState,mipFlags);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_javax_microedition_m3g_NativeRasterizer_drawSprite(JNIEnv* env, jclass,
        jintArray pixels, jshortArray depth, jintArray image, jfloatArray state, jintArray flags) {
    if (!pixels || !image || !state || !flags || env->GetArrayLength(state)!=4
            || env->GetArrayLength(flags)!=20 || env->IsSameObject(pixels,image)) return false;
    float f[4]; int p[20];
    env->GetFloatArrayRegion(state,0,4,f); env->GetIntArrayRegion(flags,0,20,p);
    if (env->ExceptionCheck()) return false;
    for (float value : f) if (!std::isfinite(value)) return false;
    int length=env->GetArrayLength(pixels);
    bool useDepth=p[12]&1, writeDepth=p[12]&2;
    if (p[0]<0 || p[1]<p[0] || p[2]<0 || p[3]<p[2] || p[5]<=0
            || int64_t(p[1])-p[0]>p[5] || p[6]<=0 || p[7]<=0
            || int64_t(p[6])*p[7]>env->GetArrayLength(image)
            || p[8]<0 || p[9]<0 || p[10]<=0 || p[11]<=0
            || int64_t(p[8])+p[10]>p[6] || int64_t(p[9])+p[11]>p[7]
            || p[12]<0 || p[12]>15 || (writeDepth && !useDepth)
            || p[13]<SHRT_MIN || p[13]>SHRT_MAX || p[14]<0 || p[14]>256
            || p[15]<0 || p[15]>255 || p[17]<0 || p[17]>4 || p[19]<0 || p[19]>255
            || (useDepth && (!depth || env->GetArrayLength(depth)<length))) return false;
    if (p[0]==p[1] || p[2]==p[3]) return true;
    int64_t first=int64_t(p[4])+p[0];
    int64_t last=int64_t(p[4])+int64_t(p[3]-p[2]-1)*p[5]+p[1];
    if (first<0 || last>length) return false;
    auto* raster=static_cast<u32*>(env->GetPrimitiveArrayCritical(pixels,nullptr));
    if (!raster) return false;
    auto* z=useDepth ? static_cast<int16_t*>(env->GetPrimitiveArrayCritical(depth,nullptr)) : nullptr;
    auto* src=(!useDepth || z) ? static_cast<u32*>(env->GetPrimitiveArrayCritical(image,nullptr)) : nullptr;
    if (src) {
        int row=p[4];
        for (int y=p[2]; y<p[3]; ++y,row+=p[5]) {
            float v=(y+0.5f-f[2])*f[3];
            int ty=p[9]+narrow(((p[12]&4) ? 1.0f-v : v)*p[11]);
            ty=std::max(p[9],std::min(p[9]+p[11]-1,ty))*p[6];
            float u=f[0];
            for (int x=p[0],idx=row+x; x<p[1]; ++x,++idx,u+=f[1]) {
                if (useDepth && z[idx]<p[13]) continue;
                int tx=p[8]+narrow(u);
                tx=std::max(p[8],std::min(p[8]+p[10]-1,tx));
                u32 color=src[ty+tx];
                int alpha=((color >> 24)*p[14]) >> 8;
                if (!alpha || alpha<p[15]) continue;
                color=(color&RGB)|(u32(alpha)<<24);
                if (p[12]&8) {
                    u32 rb=p[18]&RB;
                    rb=(rb+(int32_t(((color&RB)-rb)*p[19]) >> 8))&RB;
                    int g=(p[18] >> 8)&255;
                    g+=((int((color >> 8)&255)-g)*p[19]) >> 8;
                    color=(color&ALPHA)|rb|(u32(g)<<8);
                }
                u32 bg=raster[idx], mask=p[16];
                raster[idx]=(composite(bg,color,alpha,p[17])&mask)|(bg&~mask);
                if (writeDepth) z[idx]=p[13];
            }
        }
        env->ReleasePrimitiveArrayCritical(image,src,JNI_ABORT);
    }
    if (z) env->ReleasePrimitiveArrayCritical(depth,z,0);
    env->ReleasePrimitiveArrayCritical(pixels,raster,0);
    return src!=nullptr;
}
