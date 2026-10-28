LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE := abyssaudiobridge
LOCAL_SRC_FILES := audio.cpp
LOCAL_C_INCLUDES := $(LOCAL_PATH)/include/ffmpeg $(LOCAL_PATH)/include/eas
LOCAL_LDLIBS := -ldl
LOCAL_CPPFLAGS := -std=c++17 -O2 -Wall -Wextra -fexceptions
include $(BUILD_SHARED_LIBRARY)
