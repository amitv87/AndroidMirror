set -e
# set -x

export ROOT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"

EXEC_FILE=$0

help(){
  echo "Usage:"
  echo "$EXEC_FILE <action> [arguments]"
  echo "  r      run android app"
  echo "  hs     run prod webapp"
  echo "  so     bundle.js source map explorer"
  echo "  rw     run dev webapp"
  echo "  bw     build prod webapp"
  echo "  bwp    build webapp for publishing"
  echo "  em     run webap/em/cmd.sh"
  echo "  cap    record mp4"
}

build_web_app(){
  [[ "$1" == "w" ]] && export USE_WASM=1;
  cd webapp;
  rm -rf dist;
  npm run build;
}



ACTION="$1";
[[ -z "$ACTION" ]] && help;
shift;

cd $ROOT_DIR;

if [[ $ACTION == "r" ]] ; then
  ./run.sh $@; shift;
elif [[ $ACTION == "hs" ]] ; then
  ws -d webapp/dist -z;
elif [[ $ACTION == "rw" ]] ; then
  [[ "$1" == "w" ]] && export USE_WASM=1;
  cd webapp; npm run dev;
elif [[ $ACTION == "bw" ]] ; then
  build_web_app $@;
  ls -la dist;
elif [[ $ACTION == "so" ]] ; then
  cd webapp/dist;
  source-map-explorer bundle.js --html > sme.html && open sme.html;
elif [[ $ACTION == "bwp" ]] ; then
  export PUBLISH=1;
  build_web_app $@;
  cd dist && rm -rf css && find . -type f ! -name 'index.html' ! -name 'openh264.*' ! -name 'libav.*' -exec rm -f {} +;
  ls -la;
elif [[ $ACTION == "em" ]] ; then
  ./webapp/em/cmd.sh $@
elif [[ $ACTION == "cap" ]] ; then
  cd rec && node -r esm rec.js;
else
  echo "Invalid action: $ACTION"
  help
fi
