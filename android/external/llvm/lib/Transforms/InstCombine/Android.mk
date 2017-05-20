LOCAL_PATH:= $(call my-dir)

transforms_inst_combine_SRC_FILES := \
  InstCombineAddSub.cpp \
  InstCombineAndOrXor.cpp \
  InstCombineCalls.cpp \
  InstCombineCasts.cpp \
  InstCombineCompares.cpp \
  InstCombineLoadStoreAlloca.cpp \
  InstCombineMulDivRem.cpp \
  InstCombinePHI.cpp \
  InstCombineSelect.cpp \
  InstCombineShifts.cpp \
  InstCombineSimplifyDemanded.cpp \
  InstCombineVectorOps.cpp \
  InstructionCombining.cpp

# For the host
# =====================================================
include $(CLEAR_VARS)

LOCAL_SRC_FILES := $(transforms_inst_combine_SRC_FILES)
LOCAL_MODULE:= libLLVMInstCombine

LOCAL_MODULE_HOST_OS := darwin linux windows

include $(LLVM_HOST_BUILD_MK)
include $(LLVM_GEN_ATTRIBUTES_MK)
include $(LLVM_GEN_INTRINSICS_MK)
include $(BUILD_HOST_STATIC_LIBRARY)

# For the device
# =====================================================
ifneq (true,$(DISABLE_LLVM_DEVICE_BUILDS))
include $(CLEAR_VARS)

LOCAL_SRC_FILES := $(transforms_inst_combine_SRC_FILES)
LOCAL_MODULE:= libLLVMInstCombine

include $(LLVM_DEVICE_BUILD_MK)
include $(LLVM_GEN_ATTRIBUTES_MK)
include $(LLVM_GEN_INTRINSICS_MK)
include $(BUILD_STATIC_LIBRARY)
endif
