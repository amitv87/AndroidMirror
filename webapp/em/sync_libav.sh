set -e
set -x

VERSION=12.3

if [[ ! -d "libav" ]] ; then
  curl -L https://github.com/libav/libav/archive/v$VERSION.zip | jar xv && mv libav-$VERSION libav
fi

cd libav && chmod a+x configure version.sh && ./configure --cc="emcc" --cross-prefix="em" \
  --extra-cflags="-O3 -g0 -include ../strip_log.h" \
  --enable-cross-compile --target-os=none --arch=x86_32 --cpu=generic \
  --enable-gpl --enable-version3 \
  --disable-avdevice --disable-avformat --disable-swscale --disable-avresample \
  --disable-avfilter --disable-pthreads --disable-w32threads --disable-programs \
  --disable-logging --disable-everything --enable-decoder=h264 --enable-small \
  --disable-asm --disable-doc --disable-devices --disable-network --disable-hwaccels \
  --disable-safe-bitstream-reader --disable-parsers --disable-bsfs --disable-debug \
  --disable-protocols --disable-indevs --disable-outdevs --enable-lto --enable-hardcoded-tables
