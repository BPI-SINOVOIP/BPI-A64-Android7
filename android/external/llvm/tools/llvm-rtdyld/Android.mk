LOCAL_PATH := $(call my-dir)

LLVM_ROOT_PATH := $(LOCAL_PATH)/../..


#===---------------------------------------------------------------===
# llvm-rtdyld command line tool
#===---------------------------------------------------------------===

llvm_rtdyld_SRC_FILES := \
  llvm-rtdyld.cpp

llvm_rtdyld_STATIC_LIBRARIES := \
  libLLVMARMCodeGen \
  libLLVMARMInfo \
  libLLVMARMDesc \
  libLLVMARMAsmPrinter \
  libLLVMARMAsmParser \
  libLLVMARMDisassembler \
  libLLVMAArch64CodeGen \
  libLLVMAArch64Info \
  libLLVMAArch64AsmParser \
  libLLVMAArch64Desc \
  libLLVMAArch64AsmPrinter \
  libLLVMAArch64Utils \
  libLLVMAArch64Disassembler \
  libLLVMMipsCodeGen \
  libLLVMMipsInfo \
  libLLVMMipsDesc \
  libLLVMMipsAsmPrinter \
  libLLVMMipsAsmParser \
  libLLVMMipsDisassembler \
  libLLVMX86CodeGen \
  libLLVMX86Info \
  libLLVMX86Desc \
  libLLVMX86AsmPrinter \
  libLLVMX86AsmParser \
  libLLVMX86Utils \
  libLLVMX86Disassembler \
  libLLVMDebugInfoDWARF \
  libLLVMDebugInfoPDB \
  libLLVMExecutionEngine    \
  libLLVMCodeGen \
  libLLVMObject             \
  libLLVMMC                 \
  libLLVMMCParser           \
  libLLVMRuntimeDyld        \
  libLLVMBitReader          \
  libLLVMCore               \
  libLLVMSupport            \
  libLLVMMCDisassembler \

include $(CLEAR_VARS)

LOCAL_MODULE := llvm-rtdyld
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_CLASS := EXECUTABLES
LOCAL_IS_HOST_MODULE := true

LOCAL_SRC_FILES := $(llvm_rtdyld_SRC_FILES)

LOCAL_STATIC_LIBRARIES := $(llvm_rtdyld_STATIC_LIBRARIES)

LOCAL_LDLIBS += -lpthread -lm -ldl

include $(LLVM_ROOT_PATH)/llvm.mk
include $(LLVM_HOST_BUILD_MK)
include $(LLVM_GEN_INTRINSICS_MK)
include $(BUILD_HOST_EXECUTABLE)
