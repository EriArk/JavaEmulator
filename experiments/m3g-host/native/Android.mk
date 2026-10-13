LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := micro3d
LOCAL_SRC_FILES := ../../../app/src/main/cpp/micro3d/src/utils.cpp
LOCAL_C_INCLUDES := $(LOCAL_PATH)/../../../app/src/main/cpp/micro3d/inc
LOCAL_LDLIBS := -llog -lGLESv2 -ljnigraphics
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := abyssm3graster
LOCAL_SRC_FILES := raster.cpp bitmap.cpp
LOCAL_LDLIBS := -ljnigraphics
LOCAL_CPPFLAGS := -std=c++17 -O3 -fwrapv -fno-fast-math -ffp-contract=off -Wall -Wextra
include $(BUILD_SHARED_LIBRARY)
