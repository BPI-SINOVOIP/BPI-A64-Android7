#ifndef __DTBFAST_H_
#define __DTBFAST_H_

#include <include.h>


#define  DTBFAST_HEAD_MAX_DEPTH       (16)


struct dtbfast_header {
	uint32 magic;			 /* magic word FDT_MAGIC */
	uint32 totalsize;		 /* total file size */
	uint32 level0_count;	 /* the count of level0 head */
	uint32 off_head;    	 /* offset to head */
	uint32 head_count;		 /* total head */
	uint32 off_prop;		 /* offset to prop */
	uint32 prop_count;		 /* total prop */
	uint32 reserved[9];

};


struct head_node {
	uint32  name_sum;		//�������Ƶ�ÿ���ַ����ۼӺ�
	uint32  name_sum_short; //������@֮���ַ����ۼӺ�
	uint32  name_offset;	//�������Ƶ�ƫ��������dtb��Ѱ��
	uint32  name_bytes;		//�������Ƶĳ���
	uint32  name_bytes_short;//@֮ǰ���Ƶĳ���
	uint32  repeate_count;
	uint32  head_offset;	//ָ���һ��head��offset
	uint32  head_count;		//head�ܵĸ���
	uint32  data_offset;	//ָ���һ��prop��offset
	uint32  data_count;		//prop�ܵĸ���
	uint32  reserved[2];
};

struct prop_node {
	uint32  name_sum;		//���Ƶ�ÿ���ַ����ۼӺ�
	uint32  name_offset;	//�������Ƶ�ƫ������dtb��Ѱ��
	uint32  name_bytes;		//�������Ƶĳ���
	uint32  offset;			//����prop��ƫ������dtb��Ѱ��
};


#endif /* __DTBFAST_H_ */
