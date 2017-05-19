
# define sources root directory before everything
SRC_ROOT := ..

# include rule.mk
include $(SRC_ROOT)/rule.mk

# add you test case here
.PHONY: all
all:
	cp script_parser.sh $(BINDIR)/
	cp send_cmd_pipe.sh $(BINDIR)/
	make -C gsensortester
	make -C hdmitester
	make -C irtester
	make -C keytester
	make -C memtester
	make -C audiotester
	make -C mmctester
	make -C nandtester
	make -C rtctester
	make -C udisktester
	make -C wifitester
	make -C host1tester
	make -C host2tester
	make -C otgtester
	make -C 2gtester
	make -C bluetooth
	make -C gyrotester
	make -C gyroiiotester
	make -C vrcmptester
	make -C vrlstester
	make -C vrpstester
	make -C compasstester
	make -C emmctester
	make -C rtk_hciattach
	make -C pmutester
	make -C brcm_patchram_plus
	make -C mictester
