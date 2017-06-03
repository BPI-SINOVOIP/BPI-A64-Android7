#!/bin/bash

cd $PACKAGE

chip=sun50iw1p1
platform=android
board=bpi-m64-lcd5
board1=bpi-m64-hso-lcd7
board2=bpi-m64-lg007
debug=uart0
sigmode=none
securemode=none
version=7.0

usage()
{
	printf "Usage: pack [-cCHIP] [-pPLATFORM] [-bBOARD] [-a] [-d] [-s] [-v] [-h]
	-c CHIP (default: $chip)
	-p PLATFORM (default: $platform)
	-b BOARD (default: $board)
	-a android version
	-d pack firmware with debug info output to card0
	-s pack firmware with signature
	-v pack firmware with secureboot
	-h print this help message
"
}

while getopts "c:p:b:adsvh" arg
do
	case $arg in
		c)
			chip=$OPTARG
			;;
		p)
			platform=$OPTARG
			;;
		b)
			board=$OPTARG
			;;
		a)
			version=$version
			;;
		d)
			debug=card0
			;;
		s)
			sigmode=sig
			;;
		v)
			securemode=secure
			;;
		h)
			usage
			exit 0
			;;
		?)
			exit 1
			;;
	esac
done

./pack -c $chip -p $platform -b $board -a $version -d $debug -s $sigmode -v $securemode
./pack -c $chip -p $platform -b $board1 -a $version -d $debug -s $sigmode -v $securemode
./pack -c $chip -p $platform -b $board2 -a $version -d $debug -s $sigmode -v $securemode
