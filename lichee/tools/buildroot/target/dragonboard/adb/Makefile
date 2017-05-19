TARGET          = adbd
INCLUDES        += -I. \
                   -I./libs/libcutils \
				   -I./libs/libmincrypt

SRCS := \
	adb.c \
	backup_service.c \
	fdevent.c \
	transport.c \
	transport_local.c \
	transport_usb.c \
	adb_auth_client.c \
	sockets.c \
	services.c \
	file_sync_service.c \
	jdwp_service.c \
	framebuffer_service.c \
	remount_service.c \
	usb_linux_client.c \
	log_service.c \
	utils.c

SUB_LIB := \
		libmincrypt.a \
		libcutils.a \
		-lpthread

OBJS    = $(SRCS:.c=.o)
CC := arm-linux-gnueabi-gcc
LOCAL_CFLAGS := -O2 -g -DADB_HOST=0 -Wall -Wno-unused-parameter -DALLOW_ADBD_ROOT=1
LOCAL_CFLAGS += -D_XOPEN_SOURCE -D_GNU_SOURCE
LOCAL_CFLAGS += -DHAVE_FORKEXEC=1
%.o: %.c
	$(CC) $(CFLAGS) $(LOCAL_CFLAGS) $(INCLUDES) -c -o $@ $<

$(TARGET): $(OBJS) libcutils libmincrypt
	echo "$(LDFLAGS)"
	echo "$(CC)"
	$(CC) $(LDFLAGS) $(OBJS) $(SUB_LIB) -o $@

libmincrypt:
	make -C libs/libmincrypt
	cp -f libs/libmincrypt/libmincrypt.a .

libcutils:
	make -C libs/libcutils
	cp -f libs/libcutils/libcutils.a .

all:$(TARGET)

#install:
#	install adbd $(TARGET_DIR)/bin
#	install scripts/adb_shell $(TARGET_DIR)/bin
#	install scripts/adbd.init $(TARGET_DIR)/etc/init.d
#	install scripts/adb_profile $(TARGET_DIR)/etc
install:
	install adbd ./../rootfs/bin
	install scripts/adb_shell ./../rootfs/bin
	install scripts/adbd.init ./../rootfs/etc/init.d
	install scripts/adb_profile ./../rootfs/etc
	install scripts/start_adb.sh ./../rootfs/bin

clean: libmincrypt_clean libcutils_clean
	rm -rf $(TARGET) *.o *.a *~

libmincrypt_clean:
	make -C libs/libmincrypt clean

libcutils_clean:
	make -C libs/libcutils clean

