# set -e
# set -x

PKG=$(cat app/build.gradle | grep applicationId | awk '{print $2}' | sed -e 's/^"//' -e 's/"$//')
userId=$(adb shell dumpsys package $PKG | grep 'userId=' | awk -F '[/=]' '{print $2}')
# -u 2000
adb shell top -d 1 -u $userId $@

tput cnorm 
