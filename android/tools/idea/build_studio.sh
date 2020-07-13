#!/bin/bash
# Expected arguments:
# $1 = out_dir
# $2 = dist_dir
# $3 = build_number

PROG_DIR=$(dirname "$0")

function die() {
  echo "$*" > /dev/stderr
  echo "Usage: $0 <out_dir> <dest_dir> <build_number>" > /dev/stderr
  exit 1
}

while [[ -n "$1" ]]; do
  if [[ -z "$OUT" ]]; then
    OUT="$1"
  elif [[ -z "$DIST" ]]; then
    DIST="$1"
  elif [[ -z "$BNUM" ]]; then
    BNUM="$1"
  else
    die "[$0] Unknown parameter: $1"
  fi
  shift
done

if [[ -z "$OUT" ]]; then die "## Error: Missing out folder"; fi
if [[ -z "$DIST" ]]; then die "## Error: Missing destination folder"; fi
if [[ -z "$BNUM" ]]; then die "## Error: Missing build number"; fi

cd "$PROG_DIR"

mkdir -p "$OUT"

ANT="java -jar lib/ant/lib/ant-launcher.jar -f build.xml"

echo "## Building android-studio ##"
echo "## Dest dir : $DIST"
echo "## Qualifier: $QUAL"
echo "## Build Num: $BNUM"
echo

$ANT "-Dout=$OUT" "-DbuildNumber=$BNUM"

echo "## Copying android-studio destination files"
cp -rfv "$OUT"/artifacts/android-studio* "$DIST"/
cp -rfv "$OUT"/updater-full.jar "$DIST"/android-studio-updater.jar
