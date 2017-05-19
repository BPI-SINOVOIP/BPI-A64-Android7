#ifndef __KEY_H__
#define __KEY_H__

#include "efuse.h"

typedef struct EFUSE_KEY
{
	char name[32];							// key����
	int key_index;							// ��ַ����
	int store_max_bit;					// ������¼�����bit
	int show_bit_offset;				// key�Ƿ������
	int burned_bit_offset;			// key�Ƿ��Ѿ���¼��
	int reserve[4];
}
efuse_key_map_t;





#endif

