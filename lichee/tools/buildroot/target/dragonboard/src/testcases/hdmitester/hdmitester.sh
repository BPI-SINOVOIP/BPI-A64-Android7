#!/bin/sh

source send_cmd_pipe.sh
source script_parser.sh

module_count=`script_fetch "hdmi" "module_count"`
if [ $module_count -gt 0 ]; then
    for i in $(seq $module_count); do
        key_name="module"$i"_path"
        module_path=`script_fetch "hdmi" "$key_name"`
        if [ -n "$module_path" ]; then
            echo "insmod $module_path"
            insmod "$module_path"
            if [ $? -ne 0 ]; then
                SEND_CMD_PIPE_FAIL $3
                exit 1
            fi
        fi
    done
fi

sleep 3

device_name=`script_fetch "hdmi" "device_name"`
	hdmitester $* "$device_name" &
	exit 0
SEND_CMD_PIPE_FAIL $3
