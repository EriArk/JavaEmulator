/* SPDX-License-Identifier: GPL-3.0-or-later */
#include <jni.h>
#include <android/bitmap.h>
#include <cstdint>

extern "C" JNIEXPORT jboolean JNICALL
Java_org_recompile_mobile_BitmapTransfer_readOpaque(JNIEnv* env, jclass, jobject bitmap,
        jintArray pixels, jint stride, jint x, jint y, jint width, jint height) {
    AndroidBitmapInfo info{};
    if (!bitmap || !pixels || x<0 || y<0 || width<=0 || height<=0 || stride<=0
            || AndroidBitmap_getInfo(env,bitmap,&info)!=ANDROID_BITMAP_RESULT_SUCCESS
            || info.format!=ANDROID_BITMAP_FORMAT_RGBA_8888
            || (info.flags&ANDROID_BITMAP_FLAGS_IS_HARDWARE)
            || int64_t(x)+width>info.width || int64_t(y)+height>info.height
            || int64_t(x)+width>stride
            || (int64_t(y)+height-1)*stride+x+width>env->GetArrayLength(pixels)) return false;
    void* address=nullptr;
    if(AndroidBitmap_lockPixels(env,bitmap,&address)!=ANDROID_BITMAP_RESULT_SUCCESS) return false;
    auto* output=static_cast<uint32_t*>(env->GetPrimitiveArrayCritical(pixels,nullptr));
    bool opaque=output!=nullptr;
    for(int row=0;opaque && row<height;row++) {
        const auto* source=reinterpret_cast<const uint32_t*>(
                static_cast<const uint8_t*>(address)+(size_t(y)+row)*info.stride)+x;
        auto* destination=output+(size_t(y)+row)*stride+x;
        for(int col=0;col<width;col++) {
            uint32_t color=source[col];
            // Partly transparent pixels require Android's exact unpremultiplication.
            if((color>>24)!=255) { opaque=false; break; }
            destination[col]=(color&0xff00ff00u)|((color&0xffu)<<16)|((color>>16)&0xffu);
        }
    }
    if(output) env->ReleasePrimitiveArrayCritical(pixels,output,0);
    AndroidBitmap_unlockPixels(env,bitmap);
    return opaque;
}
