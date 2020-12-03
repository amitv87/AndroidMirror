set -e
# set -x

key="$1"

POSITIONAL=()
while [[ $# -gt 0 ]]
do
key="$1"

case $key in
  -c|--clean)
    CLEAN="$1"
    shift # past argument
  ;;
  -p|--pkg)
    PKG="$2"
    shift # past argument
    shift # past value
  ;;
  *)    # unknown option
    POSITIONAL+=("$1") # save it in an array for later
    shift # past argument
  ;;
esac
done

if [[ ! -z "$CLEAN" ]] ; then
  adb logcat -c
fi

if [[ -z "$PKG" ]] ; then
  PKG=$(cat app/build.gradle | grep applicationId | awk '{print $2}' | sed -e 's/^"//' -e 's/"$//')
else
  sleep 1
fi

# adb shell ps -o USER,UID,PID,NAME
# adb logcat -v color --pid=$(adb shell pidof $PKG)

PSLIST=$(adb shell ps -A -o PID,NAME | grep $PKG)
echo "$PSLIST"

PIDS=$(echo $(echo $"$PSLIST" | awk '{print $1}'))
echo PIDS: $PIDS

# GREP_ARGS=$(echo ${PIDS// /\\|})
# adb logcat -v color | grep $GREP_ARGS

logcat_pids=
PID_ARR=(${PIDS})
for i in "${PID_ARR[@]}"
do
  adb logcat -v color --pid="$i" &
  logcat_pids+=" $!"
done

_term() {
  kill -TERM $logcat_pids
}

trap _term SIGINT
trap _term SIGTERM

wait $logcat_pids
