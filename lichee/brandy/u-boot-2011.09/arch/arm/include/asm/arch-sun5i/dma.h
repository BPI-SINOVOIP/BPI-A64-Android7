/*
 * (C) Copyright 2007-2013
 * Allwinner Technology Co., Ltd. <www.allwinnertech.com>
 * Jerry Wang <wangflord@allwinnertech.com>
 *
 * See file CREDITS for list of people who contributed to this
 * project.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.	 See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston,
 * MA 02111-1307 USA
 */

#ifndef	_DMA_H_
#define	_DMA_H_

#include <asm/arch/intc.h>
#include <asm/arch/cpu.h>

#define  DMAC_DMATYPE_NORMAL      0
#define  DMAC_DMATYPE_DEDICATED   1


#define CFG_SW_DMA_NORMAL_MAX       8
#define CFG_SW_DMA_DEDICATE_MAX     8

#define CFG_SW_DMA_NORMAL_BASE              (SUNXI_DMA_BASE + 0x100              )
#define CFS_SW_DMA_NORMAL0                  (CFG_SW_DMA_NORMAL_BASE + 0x20 * 0   )
#define CFS_SW_DMA_NORMAL1                  (CFG_SW_DMA_NORMAL_BASE + 0x20 * 1   )
#define CFS_SW_DMA_NORMAL2                  (CFG_SW_DMA_NORMAL_BASE + 0x20 * 2   )
#define CFS_SW_DMA_NORMAL3                  (CFG_SW_DMA_NORMAL_BASE + 0x20 * 3   )
#define CFS_SW_DMA_NORMAL4                  (CFG_SW_DMA_NORMAL_BASE + 0x20 * 4   )
#define CFS_SW_DMA_NORMAL5                  (CFG_SW_DMA_NORMAL_BASE + 0x20 * 5   )
#define CFS_SW_DMA_NORMAL6                  (CFG_SW_DMA_NORMAL_BASE + 0x20 * 6   )
#define CFS_SW_DMA_NORMAL7                  (CFG_SW_DMA_NORMAL_BASE + 0x20 * 7   )

#define CFG_SW_DMA_DEDICATE_BASE            (SUNXI_DMA_BASE + 0x300               )
#define CFG_SW_DMA_DEDICATE0                (CFG_SW_DMA_DEDICATE_BASE + 0x20 * 0 )
#define CFG_SW_DMA_DEDICATE1                (CFG_SW_DMA_DEDICATE_BASE + 0x20 * 1 )
#define CFG_SW_DMA_DEDICATE2                (CFG_SW_DMA_DEDICATE_BASE + 0x20 * 2 )
#define CFG_SW_DMA_DEDICATE3                (CFG_SW_DMA_DEDICATE_BASE + 0x20 * 3 )
#define CFG_SW_DMA_DEDICATE4                (CFG_SW_DMA_DEDICATE_BASE + 0x20 * 4 )
#define CFG_SW_DMA_DEDICATE5                (CFG_SW_DMA_DEDICATE_BASE + 0x20 * 5 )
#define CFG_SW_DMA_DEDICATE6                (CFG_SW_DMA_DEDICATE_BASE + 0x20 * 6 )
#define CFG_SW_DMA_DEDICATE7                (CFG_SW_DMA_DEDICATE_BASE + 0x20 * 7 )

#define CFG_SW_DMA_OTHER_BASE               (SUNXI_DMA_BASE + 0x300 + 0x10       )
#define CFG_SW_DMA_DEDICATE0_OTHER          (CFG_SW_DMA_OTHER_BASE + 0x20 * 0    )
#define CFG_SW_DMA_DEDICATE1_OTHER          (CFG_SW_DMA_OTHER_BASE + 0x20 * 1    )
#define CFG_SW_DMA_DEDICATE2_OTHER          (CFG_SW_DMA_OTHER_BASE + 0x20 * 2    )
#define CFG_SW_DMA_DEDICATE3_OTHER          (CFG_SW_DMA_OTHER_BASE + 0x20 * 3    )
#define CFG_SW_DMA_DEDICATE4_OTHER          (CFG_SW_DMA_OTHER_BASE + 0x20 * 4    )
#define CFG_SW_DMA_DEDICATE5_OTHER          (CFG_SW_DMA_OTHER_BASE + 0x20 * 5    )
#define CFG_SW_DMA_DEDICATE6_OTHER          (CFG_SW_DMA_OTHER_BASE + 0x20 * 6    )
#define CFG_SW_DMA_DEDICATE7_OTHER          (CFG_SW_DMA_OTHER_BASE + 0x20 * 7    )


/* DMA ��������  */
#define DMAC_CFG_CONTINUOUS_ENABLE              (0x01)
#define DMAC_CFG_CONTINUOUS_DISABLE             (0x00)

/* DMA ����Ŀ�Ķ� ���� */
/* DMA Ŀ�Ķ� ������ */
#define	DMAC_CFG_DEST_DATA_WIDTH_8BIT			(0x00)
#define	DMAC_CFG_DEST_DATA_WIDTH_16BIT			(0x01)
#define	DMAC_CFG_DEST_DATA_WIDTH_32BIT			(0x02)

/* DMA Ŀ�Ķ� ͻ������ģʽ */
#define	DMAC_CFG_DEST_1_BURST       			(0x00)
#define	DMAC_CFG_DEST_4_BURST		    		(0x01)
#define	DMAC_CFG_DEST_8_BURST					(0x02)

/* DMA Ŀ�Ķ� ��ַ�仯ģʽ */
#define	DMAC_CFG_DEST_ADDR_TYPE_LINEAR_MODE		(0x00)
#define	DMAC_CFG_DEST_ADDR_TYPE_IO_MODE 		(0x01)


/* DMA ����Դ�� ���� */
/* DMA Դ�� ������ */
#define	DMAC_CFG_SRC_DATA_WIDTH_8BIT			(0x00)
#define	DMAC_CFG_SRC_DATA_WIDTH_16BIT			(0x01)
#define	DMAC_CFG_SRC_DATA_WIDTH_32BIT			(0x02)

/* DMA Դ�� ͻ������ģʽ */
#define	DMAC_CFG_SRC_1_BURST       				(0x00)
#define	DMAC_CFG_SRC_4_BURST		    		(0x01)
#define	DMAC_CFG_SRC_8_BURST		    		(0x02)

/* DMA Դ�� ��ַ�仯ģʽ */
#define	DMAC_CFG_SRC_ADDR_TYPE_LINEAR_MODE		(0x00)
#define	DMAC_CFG_SRC_ADDR_TYPE_IO_MODE 			(0x01)

/* DMA ����Դ�� ���� */
#define	DMAC_CFG_TYPE_SRAM						(21)
#define	DMAC_CFG_TYPE_DRAM		    	   		(22)

#define	DMAC_CFG_TYPE_CODEC	    				(19)

#define	DMAC_CFG_TYPE_OTG_EP1	    			(27)
#define	DMAC_CFG_TYPE_OTG_EP2	    			(28)
#define	DMAC_CFG_TYPE_OTG_EP3	    			(29)
#define	DMAC_CFG_TYPE_OTG_EP4	    			(30)
#define	DMAC_CFG_TYPE_OTG_EP5	    			(31)

#define DMAC_CFG_TYPE_SPI0                      (24)

#define DMAC_CFG_TYPE_SRC_DRAM                  (0x1)
#define DMAC_CFG_TYPE_DST_DRAM                  (0x1)


typedef struct __ndma_config_set
{
    unsigned int      src_drq_type     : 5;            //Դ��ַ�洢���ͣ���DRAM, SPI,NAND�ȣ��μ�  __ndma_drq_type_t
    unsigned int      src_addr_type    : 1;            //ԭ��ַ���ͣ�����������߲���  0:����ģʽ  1:���ֲ���
    unsigned int      src_secure       : 1;            //source secure  0:secure  1:not secure
    unsigned int      src_burst_length : 2;            //����һ��burst��� 0:1   1:4   2:8
    unsigned int      src_data_width   : 2;            //���ݴ����ȣ�0:һ�δ���8bit��1:һ�δ���16bit��2:һ�δ���32bit��3:����
    unsigned int      reserved0        : 5;
    unsigned int      dst_drq_type     : 5;            //Ŀ�ĵ�ַ�洢���ͣ���DRAM, SPI,NAND��
    unsigned int      dst_addr_type    : 1;            //Ŀ�ĵ�ַ���ͣ�����������߲���  0:����ģʽ  1:���ֲ���
    unsigned int      dst_secure       : 1;            //dest secure  0:secure  1:not secure
    unsigned int      dst_burst_length : 2;            //����һ��burst��� ��0��Ӧ��1����1��Ӧ��4,
    unsigned int      dst_data_width   : 2;            //���ݴ����ȣ�0:һ�δ���8bit��1:һ�δ���16bit��2:һ�δ���32bit��3:����
    unsigned int      wait_state       : 3;            //�ȴ�ʱ�Ӹ��� ѡ��Χ��0-7
    unsigned int      continuous_mode  : 1;            //ѡ����������ģʽ 0:����һ�μ����� 1:�������䣬��һ��DMA������������¿�ʼ����
    unsigned int      reserved1        : 1;
}
__ndma_config_t;

typedef struct __ddma_config_set
{
    unsigned int      src_drq_type     : 5;            //Դ��ַ�洢���ͣ���DRAM, SPI,NAND�ȣ��μ�  __ddma_src_type_t
    unsigned int      src_addr_type    : 2;            //ԭ��ַ���ͣ�����������߲���  0:����ģʽ  1:���ֲ���  2:Hģʽ  3:Vģʽ
    unsigned int      src_burst_length : 2;            //����һ��burst��� ��0��Ӧ��1����1��Ӧ��4,
    unsigned int      src_data_width   : 2;            //���ݴ����ȣ�0:һ�δ���8bit��1:һ�δ���16bit��2:һ�δ���32bit��3:����
    unsigned int      reserved0        : 5;
    unsigned int      dst_drq_type     : 5;            //Ŀ�ĵ�ַ�洢���ͣ���DRAM, SPI,NAND��, �μ�  __ddma_dst_type_t
    unsigned int      dst_addr_type    : 2;            //Ŀ�ĵ�ַ���ͣ�����������߲��� 0:����ģʽ  1:���ֲ���  2:Hģʽ  3:Vģʽ
    unsigned int      dst_burst_length : 2;            //����һ��burst��� ��0��Ӧ��1����1��Ӧ��4,
    unsigned int      dst_data_width   : 2;            //���ݴ����ȣ�0:һ�δ���8bit��1:һ�δ���16bit��2:һ�δ���32bit��3:����
    unsigned int      reserved1        : 3;
    unsigned int      continuous_mode  : 1;            //ѡ����������ģʽ 0:����һ�μ����� 1:�������䣬��һ��DMA������������¿�ʼ����
    unsigned int      reserved2        : 1;
}
__ddma_config_t;

struct dma_irq_handler
{
	void                *m_data;
	void (*m_func)( void * data);
};


struct sw_dma
{
    volatile unsigned int config;           /* DMA���ò���              */
    volatile unsigned int src_addr;         /* DMA����Դ��ַ            */
    volatile unsigned int dst_addr;         /* DMA����Ŀ�ĵ�ַ          */
    volatile unsigned int bytes;            /* DMA�����ֽ���            */
};

typedef volatile struct sw_dma *sw_dma_t;

struct sw_dma_other
{
    volatile unsigned int page_size;        /* DMA����PAGE SIZE         */
    volatile unsigned int page_step;        /* DMA����PAGE STEP         */
    volatile unsigned int comity_counter;   /* DMA����comity counter    */
};

typedef volatile struct sw_dma_other *sw_dma_other_t;

typedef struct sw_dma_channal_set
{
    unsigned int            used;           /* DMA�Ƿ�ʹ��            */
      signed int            channalNo;      /* DMAͨ�����              */
    sw_dma_t                channal;        /* DMAͨ��                  */
    sw_dma_other_t          other;          /* DMA��������              */
	struct dma_irq_handler  dma_func;
}
sw_dma_channal_set_t;


typedef struct  __dma_config_set
{
    unsigned int      src_drq_type     ; //Դ��ַ�洢���ͣ���DRAM, SPI,NAND�ȣ�����ѡ��NDMA����DDMA, ѡ�� __ndma_drq_type_t���� __ddma_src_type_t
    unsigned int      src_addr_mode    ; //ԭ��ַ���� NDMA�� 0:����ģʽ  1:���ֲ���  DDMA�� 0:����ģʽ  1:���ֲ���  2:Hģʽ  3:Vģʽ
    unsigned int      src_burst_length ; //����һ��burst��� ��0��Ӧ��1����1��Ӧ��4,
    unsigned int      src_data_width   ; //���ݴ����ȣ�0:һ�δ���8bit��1:һ�δ���16bit��2:һ�δ���32bit��3:����
    unsigned int      dst_drq_type     ; //Դ��ַ�洢���ͣ���DRAM, SPI,NAND�ȣ�����ѡ��NDMA����DDMA, ѡ�� __ndma_drq_type_t���� __ddma_dst_type_t
    unsigned int      dst_addr_mode    ; //ԭ��ַ���� NDMA�� 0:����ģʽ  1:���ֲ���  DDMA�� 0:����ģʽ  1:���ֲ���  2:Hģʽ  3:Vģʽ
    unsigned int      dst_burst_length ; //����һ��burst��� ��0��Ӧ��1����1��Ӧ��4,
    unsigned int      dst_data_width   ; //���ݴ����ȣ�0:һ�δ���8bit��1:һ�δ���16bit��2:һ�δ���32bit��3:����
    unsigned int      wait_state       ; //�ȴ�ʱ�Ӹ��� ѡ��Χ��0-7��ֻ��NDMA��Ч
    unsigned int      continuous_mode  ; //ѡ����������ģʽ 0:����һ�μ����� 1:�������䣬��һ��DMA������������¿�ʼ����
}
__dma_config_t;

typedef struct 	__dma_setting_set
{
    __dma_config_t         cfg;	    	    //DMA���ò���
    unsigned int           pgsz;            //DEʹ�ò������鿽��ʹ��
    unsigned int           pgstp;           //DEʹ�ò������鿽��ʹ��
    unsigned int           cmt_blk_cnt;     //DEʹ�ò������鿽��ʹ��
}sunxi_dma_setting_t;

extern    void          sunxi_dma_init(void);
extern    void          sunxi_dma_exit(void);
extern    unsigned int 	sunxi_dma_request			(unsigned int dmatype);
extern    int 			sunxi_dma_release			(unsigned int hdma);
extern    int 			sunxi_dma_setting			(unsigned int hdma, void *cfg);
extern    int 			sunxi_dma_start			    (unsigned int hdma, unsigned int saddr, unsigned int daddr, unsigned int bytes);
extern    int 			sunxi_dma_stop			    (unsigned int hdma);
extern    int 			sunxi_dma_querystatus		(unsigned int hdma);

extern    int 			sunxi_dma_install_int(uint hdma, interrupt_handler_t dma_int_func, void *p);

extern    int 			sunxi_dma_disable_int(uint hdma);
extern    int 			sunxi_dma_enable_int(uint hdma);
extern    int 			sunxi_dma_free_int(uint hdma);

#endif	//_DMA_H_

/* end of _DMA_H_ */

