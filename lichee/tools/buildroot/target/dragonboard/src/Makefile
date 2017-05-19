
# define sources root directory before everything
SRC_ROOT = .

# change compiler and linker option before you include rule.mk
#
# link to libscript.a when you need to fetch configuration
# from test_script
#
#CFLAGS := $(CFLAGS) -g
LDFLAGS := -lscript -ldirectfb -lpthread

# include rule.mk
include $(SRC_ROOT)/rule.mk

ifeq ($(LICHEE_CHIP),sun50iw1p1)
	CFLAGS := $(CFLAGS) -I$(SYSROOT)/usr/include/directfb -D_SUN50IW1P
endif

ifeq ($(LICHEE_CHIP),sun9iw1p1)
	CFLAGS := $(CFLAGS) -I$(SYSROOT)/usr/include/directfb -D_SUN9IW1P
endif

ifeq ($(LICHEE_CHIP),sun8iw6p1)
	CFLAGS := $(CFLAGS) -I$(SYSROOT)/usr/include/directfb -D_SUN8IW6P
endif

ifeq ($(LICHEE_CHIP),sun8iw5p1)
	CFLAGS := $(CFLAGS) -I$(SYSROOT)/usr/include/directfb -D_SUN8IW5P
endif
# define objects collection
libscript_objs = core/script.o

ifeq ($(LICHEE_CHIP),sun50iw1p1)
core_objs = core/core.o core/script_parser.o \
			view/view.o view/df_view.o view/tp_track.o \
			view/camera_sun50iw1p1.o
endif

ifeq ($(LICHEE_CHIP),sun9iw1p1)
core_objs = core/core.o core/script_parser.o \
			view/view.o view/df_view.o view/tp_track.o
endif

ifeq ($(LICHEE_CHIP),sun8iw6p1)
core_objs = core/core.o core/script_parser.o \
			view/view.o view/df_view.o view/tp_track.o \
			view/camera_sun8iw6p1.o
endif

ifeq ($(LICHEE_CHIP),sun8iw5p1)
core_objs = core/core.o core/script_parser.o \
			view/view.o view/df_view.o view/tp_track.o \
			view/camera_sun8iw5p1.o
endif

launcher_objs = core/launcher.o

# add targets to all
.PHONY: all
all: libscript.a core launcher
	make -C testcases
	make -C view

libscript.a: $(libscript_objs)
	@echo "  AR\t$^ -> $@"
	$(AR) $(ARFLAGS) $(LIBDIR)/$@ $^

$(libscript_objs): %.o: %.c
	$(COMPILE_MSG)
	$(COMPILEX)

core: $(core_objs)
	$(LINK_MSG)
	$(LINKX)

$(core_objs): %.o: %.c
	$(COMPILE_MSG)
	$(COMPILEX)

launcher: $(launcher_objs)
	$(LINK_MSG)
	$(LINKX)

$(launcher_objs): %.o: %.c
	$(COMPILE_MSG)
	$(COMPILEX)

.PHONY: clean
clean:
	@rm -rf $(BINDIR)/*
	@echo "  CLEAN *.o"
	@find . -name *.o -exec rm -f {} \;
	@echo "  CLEAN *.d"
	@find . -name *.d -exec rm -f {} \;
	@echo "  CLEAN *.d.*"
	@find . -name *.d.* -exec rm -f {} \;


# generate dependence
SRCDIRS = core/ view/ $(shell find testcases/ -type d)
SOURCES = $(foreach dir, $(SRCDIRS), $(wildcard $(dir)/*.c))

-include $(SOURCES:.c=.d)

%.d: %.c
	@set -e; rm -f $@; \
	$(CC) -MM $(CPPFLAGS) $< > $@.$$$$; \
	sed 's,\($*\)\.o[ :]*,\1.o $@ : ,g' < $@.$$$$ > $@; \
	rm -f $@.$$$$
