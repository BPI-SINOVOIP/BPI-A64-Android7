#!/bin/bash

echo "Build Uboot"
$echo
cd ./lichee/brandy
./bpi_uboot_build.sh
cd -

echo "Build Kernel"
echo
cd ./lichee
./build.sh 
cd ..
cd ./android
ls

echo "Setting CCACHE..."
export USE_CCACHE=1
export CCACHE_DIR=/media/dangku/myssd/m64_android7/git/ccache
prebuilts/misc/linux-x86/ccache/ccache -M 100G

source build/envsetup.sh
lunch bpi_m64_hdmi-userdebug
extract-bsp
make -j8
pack

cd ../lichee/tools/pack
ls -l
