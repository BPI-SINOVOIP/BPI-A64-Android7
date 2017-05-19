#!/bin/bash

show_help()
{
    printf "
    ./build.sh [-c] [-h]
    OPTIONS
        -c      Copy rootfs.ext4 to ../../../out/dragonboard
        -h      Display help message
    "
}

script_fetch()
{
    filp="${LICHEE_TOOLS_DIR}/pack/chips/${LICHEE_CHIP}/configs/${LICHEE_BOARD}/test_config.fex"
    section=$1
    key=$2
	
    item=`awk -F '=' '/\['"$section"'\]/{a=1}a==1&&$1~/'"$key"'/{gsub(/[[:blank:]]*/,"",$0); print $0; exit}' $filp`
    value=${item#*=}
    start=${value:0:7}
    if [ "$start" = "string:" ]; then
        retval=${value#*string:}
    else
        start=${value:0:1}
        if [ "$start" = "\"" ]; then
            retval=${value#*\"}
            retval=${retval%\"*}
        else
            retval=$value
        fi
    fi
    echo $retval
}

mkdir -p ${LICHEE_BR_DIR}/target/${LICHEE_PLATFORM}/rootfs/sensors_cache

OUT_PATH=""
BR_ROOT=`(cd ../..; pwd)`
export PATH=$PATH:$BR_ROOT/target/tools/host/usr/bin
export PATH=$PATH:$BR_ROOT/output/external-toolchain/bin

while getopts hc OPTION
do
    case $OPTION in
        h)
            show_help
            exit 0
            ;;
        c)
            OUT_PATH="../../../out/dragonboard"
            ;;
    esac
done

# sysroot exist?
if [ ! -d "./sysroot" ]; then
    echo "extract sysroot.tar.gz"
    tar zxf sysroot.tar.gz
fi

if [ ! -d "./output/bin" ]; then
    mkdir -p ./output/bin
fi

cd src
make clean
make
if [ $? -ne 0 ]; then
    exit 1
fi
cd ..

cd adb
make clean
make
make install
if [ $? -ne 0 ]; then
    exit 1
fi
cd ..

if [ ! -d "rootfs/dragonboard" ]; then
    mkdir -p rootfs/dragonboard
fi

cp -rf extra/* rootfs/
rm -rf rootfs/dragonboard/*
cp -rf output/* rootfs/dragonboard/

echo "generating rootfs..."

NR_SIZE=`du -sm rootfs | awk '{print $1}'`
NEW_NR_SIZE=$(((($NR_SIZE+32)/16)*16))
#NEW_NR_SIZE=360
TARGET_IMAGE=rootfs.ext4

echo "blocks: $NR_SIZE"M" -> $NEW_NR_SIZE"M""
make_ext4fs -l $NEW_NR_SIZE"M" $TARGET_IMAGE rootfs/
fsck.ext4 -y $TARGET_IMAGE > /dev/null
echo "success in generating rootfs"

if [ -n "$OUT_PATH" ]; then
    cp -v rootfs.ext4 $OUT_PATH/
fi

echo "Build at: `date`"
