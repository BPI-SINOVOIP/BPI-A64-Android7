#############################################################
#
# valgrind
#
#############################################################

VALGRIND_VERSION=3.2.3
VALGRIND_SITE:=http://valgrind.org/downloads/
VALGRIND_DIR:=$(BUILD_DIR)/valgrind-$(VALGRIND_VERSION)
VALGRIND_SOURCE:=valgrind-$(VALGRIND_VERSION).tar.bz2
VALGRIND_CAT:=$(BZCAT)

$(DL_DIR)/$(VALGRIND_SOURCE):
	$(call DOWNLOAD,$(VALGRIND_SITE),$(VALGRIND_SOURCE))

$(VALGRIND_DIR)/.unpacked: $(DL_DIR)/$(VALGRIND_SOURCE)
	$(VALGRIND_CAT) $(DL_DIR)/$(VALGRIND_SOURCE) | tar -C $(BUILD_DIR) $(TAR_OPTIONS) -
	touch $(VALGRIND_DIR)/.unpacked

$(VALGRIND_DIR)/.patched: $(VALGRIND_DIR)/.unpacked
	toolchain/patch-kernel.sh $(VALGRIND_DIR) package/valgrind/ valgrind\*.patch
	touch $(VALGRIND_DIR)/.patched

$(VALGRIND_DIR)/.configured: $(VALGRIND_DIR)/.patched
	(cd $(VALGRIND_DIR); rm -rf config.cache; \
		$(TARGET_CONFIGURE_OPTS) \
		$(TARGET_CONFIGURE_ARGS) \
		CC="$(TARGET_CC) $(TARGET_CFLAGS) -I$(LINUX_HEADERS_DIR)/include" \
		./configure $(QUIET) \
		--target=$(GNU_TARGET_NAME) \
		--host=$(GNU_TARGET_NAME) \
		--build=$(GNU_HOST_NAME) \
		--prefix=/usr \
		$(DISABLE_NLS) \
		--without-uiout --disable-valgrindmi \
		--disable-tui --disable-valgrindtk \
		--without-x --without-included-gettext \
		--disable-tls \
	)
	touch $(VALGRIND_DIR)/.configured

$(VALGRIND_DIR)/coregrind/valgrind: $(VALGRIND_DIR)/.configured
	$(MAKE) -C $(VALGRIND_DIR)
	touch -c $@

$(TARGET_DIR)/usr/bin/valgrind: $(VALGRIND_DIR)/coregrind/valgrind
	$(MAKE) \
	    prefix=$(TARGET_DIR)/usr \
	    exec_prefix=$(TARGET_DIR)/usr \
	    bindir=$(TARGET_DIR)/usr/bin \
	    sbindir=$(TARGET_DIR)/usr/sbin \
	    libexecdir=$(TARGET_DIR)/usr/lib \
	    datadir=$(TARGET_DIR)/usr/share \
	    sysconfdir=$(TARGET_DIR)/etc \
	    sharedstatedir=$(TARGET_DIR)/usr/com \
	    localstatedir=$(TARGET_DIR)/var \
	    libdir=$(TARGET_DIR)/usr/lib \
	    infodir=$(TARGET_DIR)/usr/info \
	    mandir=$(TARGET_DIR)/usr/man \
	    includedir=$(TARGET_DIR)/usr/include \
	    -C $(VALGRIND_DIR) install
	mv $(TARGET_DIR)/usr/bin/valgrind $(TARGET_DIR)/usr/bin/valgrind.bin
	cp package/valgrind/uclibc.supp $(TARGET_DIR)/usr/lib/valgrind/
	cp package/valgrind/valgrind.sh $(TARGET_DIR)/usr/bin/valgrind
	chmod a+x $(TARGET_DIR)/usr/bin/valgrind
	rm -rf $(TARGET_DIR)/usr/share/doc/valgrind
	touch -c $@

valgrind: $(TARGET_DIR)/usr/bin/valgrind

valgrind-source: $(DL_DIR)/$(VALGRIND_SOURCE)

valgrind-clean:
	-$(MAKE) -C $(VALGRIND_DIR) clean
	-rm -f $(TARGET_DIR)/usr/bin/valgrind*
	rm -rf $(TARGET_DIR)/usr/lib/valgrind

valgrind-dirclean:
	rm -rf $(VALGRIND_DIR)

#############################################################
#
# Toplevel Makefile options
#
#############################################################
ifeq ($(BR2_PACKAGE_VALGRIND),y)
TARGETS+=valgrind
endif
