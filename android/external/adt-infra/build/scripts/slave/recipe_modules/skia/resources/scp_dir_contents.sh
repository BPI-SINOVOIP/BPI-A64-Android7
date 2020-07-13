#!/bin/bash

SRC_DIR=$1
DEST_DIR=$2

mkdir -p $DEST_DIR
scp -r $SRC_DIR/* $DEST_DIR
