/*
 * Copyright (c) 2008-2016 Allwinner Technology Co. Ltd.
 * All rights reserved.
 *
 * File : demojpeg.c
 * Description : demojpeg
 * History :
 *
 */

#include <stdio.h>

#include <log.h>
#include <AwMessageQueue.h>
#include <errno.h>

#define TEST_MESSAGE1 0x301
#define TEST_MESSAGE2 0x302
#define TEST_MESSAGE3 0x303

struct AwMessage {
    AWMESSAGE_COMMON_MEMBERS
    uintptr_t params[8];
};


int main(int argc, char** argv)
{
    AwMessageQueue* mq;
    mq = AwMessageQueueCreate(4, "demuxComp");

    AwMessage msg;
    memset(&msg, 0, sizeof(AwMessage));
    msg.messageId = TEST_MESSAGE1;
    AwMessageQueuePostMessage(mq, &msg);

    memset(&msg, 0, sizeof(AwMessage));
    msg.messageId = TEST_MESSAGE2;
    AwMessageQueuePostMessage(mq, &msg);

    memset(&msg, 0, sizeof(AwMessage));
    if(AwMessageQueueGetMessage(mq, &msg) < 0)
    {
        loge("get message fail.");
        return -1;
    }
    logd("========= msgid: %x", msg.messageId);

    return 0;
}

