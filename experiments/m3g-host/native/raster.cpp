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

inline u32 textureBlend(u32 bg, u32 fg, int mode) {
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

template<bool Textured, bool Linear>
void render(u32* raster, int16_t* depth, const u32* texture, const float* f, const int* p) {
    const bool colors = p[6]&1, usesDepth = p[6]&4, depthWrite = p[6]&8;
    const bool perspective = p[6]&16, fog = p[6]&32, parallel = p[6]&64, dither = p[6]&128;
    float xL=f[0], xR=f[1], zL=f[2], pwL=f[3], sL=f[11], tL=f[12];
    int row=p[2], fogFactor=65535, stepFog=0;
    for (int y=p[0]; y<p[1]; ++y, xL+=f[4], xR+=f[5], zL+=f[6], pwL+=f[7], row+=p[3]) {
        int left=std::max(p[4], narrow(xL+0.999999f));
        int right=std::min(p[5], narrow(xR+0.999999f));
        if (left >= right) { if (Textured) { sL+=f[13]; tL+=f[14]; } continue; }
        u32 color[4] = {};
        if (colors) {
            float dx=left-f[17], dy=y-f[18];
            for (int c=0; c<4; ++c) {
                color[c]=narrow((float((u32(p[8]) >> (24-c*8)) & 255) + dx*f[19+c] + dy*f[23+c])*65536.0f);
            }
        }
        float diff=left-xL;
        float pw=perspective ? pwL+diff*f[9] : 0.0f;
        float inv=perspective ? reciprocal(pw) : 1.0f, stepInv=0.0f;
        float z=usesDepth ? zL+diff*f[8]+f[10] : -32768.0f;
        float s=0, t=0;
        if (Textured) { s=sL+diff*f[15]; t=tL+diff*f[16]; sL+=f[13]; tL+=f[14]; }
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
                    float eye=parallel ? f[29]+(x-f[17])*f[30]+(y-f[18])*f[31] : inv;
                    float end=parallel ? eye+f[30]*span : inv+stepInv*span;
                    float a=std::max(0.0f, std::min(255.0f, (f[27]-eye*f[28])*256.0f));
                    float b=std::max(0.0f, std::min(255.0f, (f[27]-end*f[28])*256.0f));
                    fogFactor=narrow(a*256.0f);
                    stepFog=narrow((b-fogFactor*0.00390625f)*((1.0f/span)*256.0f));
                }
                next+=span;
            }
            int16_t zi=static_cast<int16_t>(narrow(z));
            if (depth[index] < zi) {
                if (colors) for (int c=0;c<4;++c) color[c]+=p[9+c];
                if (Textured) { s+=f[15]; t+=f[16]; }
                continue;
            }
            u32 pixel=p[7];
            if (colors) {
                pixel=0;
                for (int c=0;c<4;++c) { pixel|=((color[c] >> 16)&255) << (24-c*8); color[c]+=p[9+c]; }
            }
            if (Textured) {
                pixel=textureBlend(pixel, sample<Linear>(texture, s*inv, t*inv, p[16], p[17], p[18]), p[20]);
                s+=f[15]; t+=f[16];
            }
            int alpha=((pixel >> 24)*p[14]) >> 8;
            if (alpha < p[13]) continue;
            if (alpha == 0) pixel|=ALPHA;
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
}

extern "C" JNIEXPORT jboolean JNICALL
Java_javax_microedition_m3g_NativeRasterizer_drawHalf(JNIEnv* env, jclass,
        jintArray pixels, jshortArray depth, jintArray texture, jfloatArray state, jintArray flags) {
    if (!pixels || !depth || !state || !flags || env->GetArrayLength(state)!=32
            || env->GetArrayLength(flags)!=25) return false;
    float f[32]; int p[25];
    env->GetFloatArrayRegion(state, 0, 32, f);
    env->GetIntArrayRegion(flags, 0, 25, p);
    if (env->ExceptionCheck()) return false;
    const int length=env->GetArrayLength(pixels);
    const bool textured=p[6]&2;
    if (env->GetArrayLength(depth)<length || p[0]<0 || p[1]<p[0] || p[3]<=0
            || p[4]<0 || p[5]<p[4] || p[5]-p[4]>p[3] || p[13]<0 || p[13]>255
            || p[14]<0 || p[14]>256 || p[20]<0 || p[20]>5 || p[21]<0 || p[21]>4
            || p[24]<0 || p[24]>255 || (p[24] & (p[24]+1))!=0) return false;
    if (p[0]==p[1] || p[4]==p[5]) return true;
    int64_t first=int64_t(p[2])+p[4];
    int64_t last=int64_t(p[2])+int64_t(p[1]-p[0]-1)*p[3]+p[5];
    if (first<0 || last>length) return false;
    if (textured && (!texture || p[16]<=0 || p[17]<=0 || p[16]>32768 || p[17]>32768
            || int64_t(p[16])*p[17]>env->GetArrayLength(texture)
            || env->IsSameObject(pixels, texture))) return false;
    // Reject non-finite setup before acquiring writable arrays or drawing anything.
    for (float v:f) if (!std::isfinite(v)) return false;
    auto* raster=static_cast<u32*>(env->GetPrimitiveArrayCritical(pixels, nullptr));
    if (!raster) return false;
    auto* z=static_cast<int16_t*>(env->GetPrimitiveArrayCritical(depth, nullptr));
    auto* tex=textured && z ? static_cast<u32*>(env->GetPrimitiveArrayCritical(texture, nullptr)) : nullptr;
    bool ready=z && (!textured || tex);
    if (ready) {
        if (!textured) render<false,false>(raster,z,nullptr,f,p);
        else if (p[19]) render<true,true>(raster,z,tex,f,p);
        else render<true,false>(raster,z,tex,f,p);
    }
    if (tex) env->ReleasePrimitiveArrayCritical(texture, tex, JNI_ABORT);
    if (z) env->ReleasePrimitiveArrayCritical(depth, z, 0);
    env->ReleasePrimitiveArrayCritical(pixels, raster, 0);
    return ready;
}
