#!/bin/bash
# Expected arguments:
# $1 = out_dir
# $2 = qualifier (ignored, optional)
# $3 = build_number or preview-<build_number>
# $4 = --target ...

PROG_DIR=$(dirname "$0")

DEST=""
QUAL=""
BNUM=""
TARGET=""

function die() {
  echo "$*" > /dev/stderr
  echo "Usage: $0 [--keep] dest_dir [date] build_number [--target build]" > /dev/stderr
  exit 1
}

while [[ -n "$1" ]]; do
  if [[ "$1" == "--target" ]]; then
    shift
    TARGET="$1"
  elif [[ "${1:0:2}" == "--" ]]; then
    die "[$0] Unknown parameter: $1"
  elif [[ -z "$DEST" ]]; then
    DEST="$1"
  elif [[ -z "$QUAL" ]]; then
    QUAL="$1"
  elif [[ -z "$BNUM" ]]; then
    BNUM="$1"
  else
    die "[$0] Unknown parameter: $1"
  fi
  shift
done

if [[ -z $BNUM && -n $QUAL ]]; then
  BNUM="$QUAL"
  QUAL=""
fi
BNUM="${BNUM/preview-/}"

if [[ -z "$DEST" ]]; then die "## Error: Missing dest_dir"; fi
if [[ -z "$BNUM" ]]; then die "## Error: Missing build_number"; fi

cd $PROG_DIR

OUT="../../out/host/android-studio"
mkdir -p "$OUT"

ANT="java -jar lib/ant/lib/ant-launcher.jar -f build.xml"

echo "## Building android-studio ##"
echo "## Dest dir : $DEST"
echo "## Qualifier: $QUAL"
echo "## Build Num: $BNUM"
echo "## Target   : $TARGET"
echo

$ANT "-Dout=$OUT" "-DbuildNumber=$BNUM" $TARGET

echo "## Copying android-studio destination files"
cp -rfv $OUT/artifacts/android-studio* $DEST/
cp -rfv $OUT/updater-full.jar $DEST/android-studio-updater.jar
