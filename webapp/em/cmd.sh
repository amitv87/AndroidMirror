set -e
# set -x

export WASM_OUT_DIR=wasm
export NUM_CPU=$(getconf _NPROCESSORS_ONLN)
export ROOT_DIR=$(cd $(dirname ${BASH_SOURCE[0]}) > /dev/null 2>&1 && pwd)

EXEC_FILE=$0

help(){
  echo "Usage:"
  echo "$EXEC_FILE <action> [arguments]"
  echo "  c      clean [target]"
  echo "  b      build [target]"
  echo "  ba     build libav"
  echo "  bf     build ffmpeg"
  echo "  bo     build openh264"
  echo "  sf     sync ffmpeg"
  echo "  so     sync openh264"
}

build(){
  emmake make -s -j$NUM_CPU $@
  ls -la $WASM_OUT_DIR;
}

ACTION="$1";
[[ -z "$ACTION" ]] && help;
shift;

cd $ROOT_DIR;

if [[ $ACTION == "c" ]] ; then
  build clean;
elif [[ $ACTION == "b" ]] ; then
  build $@;
elif [[ $ACTION == "ba" ]] ; then
  build $WASM_OUT_DIR/libav.js;
elif [[ $ACTION == "bf" ]] ; then
  build $WASM_OUT_DIR/ffmpeg.js;
elif [[ $ACTION == "bo" ]] ; then
  build $WASM_OUT_DIR/openh264.js;
elif [[ $ACTION == "sf" ]] ; then
  ./sync_libav.sh
elif [[ $ACTION == "so" ]] ; then
  ./sync_openh264.sh
else
  echo "Invalid action: $ACTION"
  help
fi
