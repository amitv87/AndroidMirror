while true
do
	adb shell ps -A | grep -i 'boggy';
	sleep 1;
done
