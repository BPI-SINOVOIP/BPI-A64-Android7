#!/bin/bash
##############################################################################
# \version     1.0.0
# \date        2012年05月31日
# \author      James Deng <csjamesdeng@allwinnertech.com>
# \Descriptions:
#			create the inital version

# \version     1.1.0
# \date        2012年09月26日
# \author      Martin <zhengjiewen@allwinnertech.com>
# \Descriptions:
#			add some new features:
#			1.wifi hotpoint ssid and single strongth san
#			2.sort the hotpoint by single strongth quickly
##############################################################################
source send_cmd_pipe.sh
source script_parser.sh
module_path=`script_fetch "bluetooth" "module_path"`
loop_time=`script_fetch "bluetooth" "test_time"`
destination_bt=`script_fetch "bluetooth" "dst_bt"`
device_node=`script_fetch "bluetooth" "device_node"`
baud_rate=`script_fetch "bluetooth" "baud_rate"`

echo "module_path   : "$module_path
echo "loop_time     : "$loop_time
echo "destination_bt: "$destination_bt
echo "device_node   : "$device_node
echo "baud_rate     : "$baud_rate

for file in `ls /dragonboard/bin/*.hcd /dragonboard/bin/*.bin /dragonboard/bin/*.txt`; do
	filename=`echo $file | awk -F "/" '{print $NF}'`
	if [ ! -f /system/vendor/modules/$filename ]; then
		ln -s /dragonboard/bin/$filename /system/vendor/modules/$filename
	fi
done

echo 0 > /sys/class/rfkill/rfkill0/state
sleep 1
echo 1 > /sys/class/rfkill/rfkill0/state
sleep 1

if [ -z $baud_rate ] || [ $baud_rate -eq 115200 ]; then
	brcm_patchram_plus  --tosleep=50000 --no2bytes --enable_hci --scopcm=0,2,0,0,0,0,0,0,0,0  --baudrate ${baud_rate} --use_baudrate_for_download --patchram ${module_path}  $device_node &
else
	brcm_patchram_plus  --tosleep=50000 --no2bytes --enable_hci --scopcm=0,2,0,0,0,0,0,0,0,0  --baudrate ${baud_rate} --patchram ${module_path}  $device_node &
fi

sleep 5

for((i=1;i<=100;i++)); do
	rfkillpath="/sys/class/rfkill/rfkill"${i}
	if [ -d "$rfkillpath" ]; then
		if cat $rfkillpath"/type" | grep bluetooth; then
			statepath=${rfkillpath}"/state"
			echo "Bluetooth state file: $statepath"
			echo 1 > $statepath
			sleep 1
			break
		fi
	fi
done

if [ -z $statepath ]; then
	SEND_CMD_PIPE_FAIL_EX $3 "Bluetooth can't bootup."
	echo "Bluetooth can't  bootup."
	exit 1
fi

cciconfig hci0 up
sleep 1

for((i=1;i<=${loop_time} ;i++));  do
	devlist=`hcitool scan hci0 | grep "${destination_bt}"`
	if [ ! -z "$devlist" ]; then
		echo -e "Bluetooth found devices list:\n\n$devlist"
		devlist=`echo "$devlist" | awk '{print $2}'`
		devlist=`echo $devlist`
		SEND_CMD_PIPE_OK_EX $3 "List: $devlist"
		cciconfig hci0 down
		exit 1
	fi
	sleep 1
done

SEND_CMD_PIPE_FAIL_EX $3 "Bluetooth OK, but no device found."
echo "Bluetooth OK, but no device found."
