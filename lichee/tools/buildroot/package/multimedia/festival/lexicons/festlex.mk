#############################################################
#
# festival lexicons
#
#############################################################
LEX1 = festlex_CMU.tar.gz
LEX2 = festlex_OALD.tar.gz
LEX3 = festlex_POSLEX.tar.gz
FESTLEX_STATUS_DIR = $(BUILD_DIR)/festival_lexicons
FESTLEX_INSTALL_DIR = $(TARGET_DIR)/usr/share

$(FESTLEX_STATUS_DIR)/.downloaded:
	mkdir -p $(FESTLEX_STATUS_DIR)
ifeq ($(BR2_PACKAGE_FESTLEX_CMU),y)
	$(call DOWNLOAD,$(FESTIVAL_SITE),$(LEX1))
endif
ifeq ($(BR2_PACKAGE_FESTLEX_OALD),y)
	$(call DOWNLOAD,$(FESTIVAL_SITE),$(LEX2))
endif
ifeq ($(BR2_PACKAGE_FESTLEX_POS),y)
	$(call DOWNLOAD,$(FESTIVAL_SITE),$(LEX3))
endif
	touch $@

$(FESTLEX_STATUS_DIR)/.installed: $(FESTLEX_STATUS_DIR)/.downloaded
ifeq ($(BR2_PACKAGE_FESTLEX_CMU),y)
	tar -xvf $(DL_DIR)/$(LEX1) --directory $(FESTLEX_INSTALL_DIR)
endif
ifeq ($(BR2_PACKAGE_FESTLEX_OALD),y)
	tar -xvf $(DL_DIR)/$(LEX2) --directory $(FESTLEX_INSTALL_DIR)
endif
ifeq ($(BR2_PACKAGE_FESTLEX_POS),y)
	tar -xvf $(DL_DIR)/$(LEX3) --directory $(FESTLEX_INSTALL_DIR)
endif
	touch $@

festlex: $(FESTLEX_STATUS_DIR)/.installed

#############################################################
#
# Toplevel Makefile options
#
#############################################################
ifeq ($(BR2_PACKAGE_FESTIVAL),y)
TARGETS+=festlex
endif
