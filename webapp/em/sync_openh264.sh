set -e
set -x

VERSION=1.8.0

if [[ ! -d "openh264" ]] ; then
  curl -L https://github.com/cisco/openh264/archive/v$VERSION.zip | jar xv && mv openh264-$VERSION openh264
fi
