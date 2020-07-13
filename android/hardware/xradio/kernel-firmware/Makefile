include Makefile.common

.PHONY: install
install:
	mkdir -p $(INSTALL_MOD_PATH)
	@for file in $(XR_KERNEL_FIRMWARE_FILES_BT) ; do \
		cp $$file $(INSTALL_MOD_PATH); \
	done
	@for file in $(XR_KERNEL_FIRMWARE_FILES_FM) ; do \
		cp $$file $(INSTALL_MOD_PATH); \
	done
	@for file in $(XR_KERNEL_FIRMWARE_FILES_WLAN) ; do \
		cp $$file $(INSTALL_MOD_PATH); \
	done
	@for file in $(XR_KERNEL_FIRMWARE_FILES_HDMI) ; do \
		cp $$file $(INSTALL_MOD_PATH); \
	done

.PHONY: clean
clean:
	@for file in $(XR_KERNEL_FIRMWARE_FILES_BT) ; do \
		rm -f $(INSTALL_MOD_PATH)$$file; \
	done
	@for file in $(XR_KERNEL_FIRMWARE_FILES_FM) ; do \
		rm -f $(INSTALL_MOD_PATH)$$file; \
	done
	@for file in $(XR_KERNEL_FIRMWARE_FILES_WLAN) ; do \
		rm -f $(INSTALL_MOD_PATH)$$file; \
	done
	@for file in $(XR_KERNEL_FIRMWARE_FILES_HDMI) ; do \
		rm -f $(INSTALL_MOD_PATH)$$file; \
	done

.PHONY: config build distclean

config:
build:
distclean:
