#!/bin/sh
rm -rf output/bin/hawkview
cd src
make clean
rm -rf lib/libscript.a
cd ..

rm -rf rootfs
rm -rf rootfs.ext4
rm -rf sysroot

