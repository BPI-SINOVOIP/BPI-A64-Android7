# BPI-A64-Android-7.1

**Prepare**

Get the docker image from [Sinovoip Docker Hub](https://hub.docker.com/r/sinovoip/bpi-build-android-7/) , Build the android source with this docker environment.

----------

**Build**

Build U-boot

    $ cd brandy 
    $ ./build.sh -p sun50iw1p1

Build Lichee 

    $ cd lichee
    $ ./build.sh config

    Welcome to mkscript setup progress
	All available chips:
	   0. sun50iw1p1
	   1. sun50iw2p1
	   2. sun50iw3p1
	   3. sun50iw6p1
	   ...
	Choice: 0
	All available platforms:
	   0. android
	   1. dragonboard
	   ...
	Choice: 0
	All available kernel:
	   0. linux-3.10
	   1. linux-3.4
	Choice: 0
	All available boards:
	   0. m64-hdmi
	   1. m64-lcd7
	   ...
	Choice: 0
     
     $ ./build.sh

Build Android

    $ cd ../android
    $ source build/envsetup.sh
    $ lunch
    $ extract-bsp
    $ make -j8
    $ pack

----------
**Flash**

The target image is packed at lichee/tools/pack/, flash it to your device by PhoenixSuit or LiveSuit.
