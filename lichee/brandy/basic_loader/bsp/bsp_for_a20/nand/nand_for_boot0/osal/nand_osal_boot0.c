/*
**********************************************************************************************************************
*											        eGon
*						           the Embedded GO-ON Bootloader System
*									       eGON arm boot sub-system
*
*						  Copyright(C), 2006-2010, SoftWinners Microelectronic Co., Ltd.
*                                           All Rights Reserved
*
* File    : nand_osal_boot0.c
*
* By      : Jerry
*
* Version : V2.00
*
* Date	  :
*
* Descript:
**********************************************************************************************************************
*/
#include "types.h"
#include "boot0_i.h"

#define PIOC_REGS_BASE 		0x01c20800
#define   CCMU_REGS_BASE    0x01c20000

 unsigned int  nand_hDMA;
struct lib_dma
{
    volatile unsigned int config;           /* DMA���ò���              */
    volatile unsigned int src_addr;         /* DMA����Դ��ַ            */
    volatile unsigned int dst_addr;         /* DMA����Ŀ�ĵ�ַ          */
    volatile unsigned int bytes;            /* DMA�����ֽ���            */
    volatile unsigned int page_size;        /* DMA����PAGE SIZE         */
    volatile unsigned int page_step;        /* DMA����PAGE STEP         */
    volatile unsigned int comity_counter;   /* DMA����comity counter    */
};

typedef volatile struct lib_dma * lib_dma_t;


lib_dma_t  DMAChannel_D = (lib_dma_t)( 0x01c02000 + 0x300);

/*
****************************************************************************************************
*
*             OSAL_DmaRequest
*
*  Description:
*       request dma
*
*  Parameters:
*		type	0: normal timer
*				1: special timer
*  Return value:
*		dma handler
*		if 0, fail
****************************************************************************************************
*/
int NAND_RequestDMA(void)
{
    return (unsigned int)DMAChannel_D;
}
/*
****************************************************************************************************
*
*             OSAL_DmaRelease
*
*  Description:
*       release dma
*
*  Parameters:
*       hDma	dma handler
*
*  Return value:
*		EPDK_OK/FAIL
****************************************************************************************************
*/
int NAND_ReleaseDMA(void)
{
    /* stop dma                 */
    DMAChannel_D->config = 0;   /* stop dma                                           */

    return 0;
}
/*
****************************************************************************************************
*
*             OSAL_DmaConfig
*
*  Description:
*       start interrupt
*
*  Parameters:
*       hTmr	timer handler
*		pArg    *(pArg + 0)         ctrl
*               *(pArg + 1)         page size
*               *(pArg + 2)         page step
*               *(pArg + 3)         comity & block count
*
*  Return value:
*		EPDK_OK/FAIL
*
**********************************************************************************************************************
*/
/* DMA ��������  */
#define  CSP_DMAC_DMATYPE_NORMAL         			0
#define  CSP_DMAC_DMATYPE_DEDICATED      			1

#define CSP_DMAC_CFG_CONTINUOUS_ENABLE              (0x01)	//(0x01<<29)
#define CSP_DMAC_CFG_CONTINUOUS_DISABLE             (0x00)	//(0x01<<29)

//* DMA �ȴ�ʱ�� */
#define	CSP_DMAC_CFG_WAIT_1_DMA_CLOCK				(0x00)	//(0x00<<26)
#define	CSP_DMAC_CFG_WAIT_2_DMA_CLOCK				(0x01)	//(0x01<<26)
#define	CSP_DMAC_CFG_WAIT_3_DMA_CLOCK				(0x02)	//(0x02<<26)
#define	CSP_DMAC_CFG_WAIT_4_DMA_CLOCK				(0x03)	//(0x03<<26)
#define	CSP_DMAC_CFG_WAIT_5_DMA_CLOCK				(0x04)	//(0x04<<26)
#define	CSP_DMAC_CFG_WAIT_6_DMA_CLOCK				(0x05)	//(0x05<<26)
#define	CSP_DMAC_CFG_WAIT_7_DMA_CLOCK				(0x06)	//(0x06<<26)
#define	CSP_DMAC_CFG_WAIT_8_DMA_CLOCK				(0x07)	//(0x07<<26)

/* DMA ����Դ�� ���� */
/* DMA Ŀ�Ķ� ������ */
#define	CSP_DMAC_CFG_DEST_DATA_WIDTH_8BIT			(0x00)	//(0x00<<24)
#define	CSP_DMAC_CFG_DEST_DATA_WIDTH_16BIT			(0x01)	//(0x01<<24)
#define	CSP_DMAC_CFG_DEST_DATA_WIDTH_32BIT			(0x02)	//(0x02<<24)

/* DMA Ŀ�Ķ� ͻ������ģʽ */
#define	CSP_DMAC_CFG_DEST_1_BURST       			(0x00)	//(0x00<<23)
#define	CSP_DMAC_CFG_DEST_4_BURST		    		(0x01)	//(0x01<<23)

/* DMA Ŀ�Ķ� ��ַ�仯ģʽ */
#define	CSP_DMAC_CFG_DEST_ADDR_TYPE_LINEAR_MODE		(0x00)	//(0x00<<21)
#define	CSP_DMAC_CFG_DEST_ADDR_TYPE_IO_MODE 		(0x01)	//(0x01<<21)
#define	CSP_DMAC_CFG_DEST_ADDR_TYPE_HPAGE_MODE 		(0x02)	//(0x02<<21)
#define	CSP_DMAC_CFG_DEST_ADDR_TYPE_VPAGE_MODE 		(0x03)	//(0x03<<21)


/* DMA ����Դ�� ���� */
/* DMA Դ�� ������ */
#define	CSP_DMAC_CFG_SRC_DATA_WIDTH_8BIT			(0x00)	//(0x00<<8)
#define	CSP_DMAC_CFG_SRC_DATA_WIDTH_16BIT			(0x01)	//(0x01<<8)
#define	CSP_DMAC_CFG_SRC_DATA_WIDTH_32BIT			(0x02)	//(0x02<<8)

/* DMA Դ�� ͻ������ģʽ */
#define	CSP_DMAC_CFG_SRC_1_BURST       				(0x00)	//(0x00<<7)
#define	CSP_DMAC_CFG_SRC_4_BURST		    		(0x01)	//(0x01<<7)

/* DMA Դ�� ��ַ�仯ģʽ */
#define	CSP_DMAC_CFG_SRC_ADDR_TYPE_LINEAR_MODE		(0x00)	//(0x00<<5)
#define	CSP_DMAC_CFG_SRC_ADDR_TYPE_IO_MODE 			(0x01)	//(0x01<<5)
#define	CSP_DMAC_CFG_SRC_ADDR_TYPE_HPAGE_MODE 		(0x02)	//(0x02<<5)
#define	CSP_DMAC_CFG_SRC_ADDR_TYPE_VPAGE_MODE 		(0x03)	//(0x03<<5)

/* DMA ����Ŀ�Ķ� D��DMA Ŀ��ѡ�� */
#define	CSP_DMAC_CFG_DEST_TYPE_D_SRAM 				(0x00)	//(0x00<<16)
#define	CSP_DMAC_CFG_DEST_TYPE_D_SDRAM				(0x01)	//(0x01<<16)
#define	CSP_DMAC_CFG_DEST_TYPE_TCON0				(0x02)	//(0x02<<16)
#define	CSP_DMAC_CFG_DEST_TYPE_NFC  		    	(0x03)	//(0x03<<16)

/* DMA ����Դ�� D��DMA Ŀ��ѡ�� */
#define	CSP_DMAC_CFG_SRC_TYPE_D_SRAM 				(0x00)	//(0x00<<0)
#define	CSP_DMAC_CFG_SRC_TYPE_D_SDRAM				(0x01)	//(0x01<<0)
#define	CSP_DMAC_CFG_SRC_TYPE_TCON0				    (0x02)	//(0x02<<0)
#define	CSP_DMAC_CFG_SRC_TYPE_NFC  		    	   	(0x03)	//(0x03<<0)

int NAND_DMAConfigStart(int rw, unsigned int buff_addr, int len)
{

    __u32  dma_cfg;

	if(rw)  //write
	{
		dma_cfg = (CSP_DMAC_CFG_SRC_TYPE_D_SDRAM     << 0)     |     \
	              (CSP_DMAC_CFG_SRC_ADDR_TYPE_LINEAR_MODE    << 5)     |     \
	              (CSP_DMAC_CFG_SRC_4_BURST << 7)     |     \
	              (CSP_DMAC_CFG_SRC_DATA_WIDTH_32BIT   << 9)     |     \
	              (CSP_DMAC_CFG_DEST_TYPE_NFC    << 16)    |     \
	              (CSP_DMAC_CFG_DEST_ADDR_TYPE_IO_MODE    << 21)    |     \
	              (CSP_DMAC_CFG_DEST_4_BURST << 23)    |     \
	              (CSP_DMAC_CFG_DEST_DATA_WIDTH_32BIT   << 25)    |     \
	              (CSP_DMAC_CFG_CONTINUOUS_DISABLE  << 29);

	}
	else //read
	{

		dma_cfg = (CSP_DMAC_CFG_SRC_TYPE_NFC    << 0)     |     \
			      (CSP_DMAC_CFG_SRC_ADDR_TYPE_IO_MODE   << 5)     |     \
			      (CSP_DMAC_CFG_SRC_4_BURST << 7)     |     \
			      (CSP_DMAC_CFG_SRC_DATA_WIDTH_32BIT  << 9)     |     \
			      (CSP_DMAC_CFG_DEST_TYPE_D_SDRAM    << 16)    |     \
			      (CSP_DMAC_CFG_DEST_ADDR_TYPE_LINEAR_MODE    << 21)    |     \
			      (CSP_DMAC_CFG_DEST_4_BURST << 23)    |     \
			      (CSP_DMAC_CFG_DEST_DATA_WIDTH_32BIT   << 25)    |     \
			      (CSP_DMAC_CFG_CONTINUOUS_DISABLE  << 29);
	}

    DMAChannel_D->config         = dma_cfg;
    DMAChannel_D->comity_counter = 0x7f077f07;

    if(!rw)
    {
        //���Ƕ�����������ʱ��ˢ��Ŀ�ĵ�ַ��ԭ�����ˢ��DRAM(SRAM)
    	//wlibc_CleanFlushDCacheRegion((void *)buff_addr, len);
        DMAChannel_D->src_addr = 0x01c03030;
        DMAChannel_D->dst_addr = buff_addr;

    }
    else
    {
        //����д������д��ʱ��ˢ��Դ��ַ��ԭ�����ˢ��DRAM(SRAM)
    	//wlibc_CleanFlushDCacheRegion((void *)buff_addr, len);
        DMAChannel_D->src_addr = buff_addr;
        DMAChannel_D->dst_addr = 0x01c03030;

    }

    DMAChannel_D->bytes    = len;
    DMAChannel_D->config   = DMAChannel_D->config | 0x80000000;   /* start dma                                               */

    return 0;
}

/*
**********************************************************************************************************************
*                                       OSAL_DmaQueryStatus
*
* Description: This function is used to query interrupt pending and clear pending bits if some pending is be set
*
* Arguments  : hDma         dma handle
*
* Returns    : bit31~24     main interrupt no
*              bit23~16     sub interrupt no
*              bit1         dma end interrupt flag
*              bit0         dma half end interrupt flag
**********************************************************************************************************************
*/
unsigned int  	NAND_QueryDmaStat(void)
{
	return ((DMAChannel_D->config) & (0x1 << 30));
}


int NAND_WaitDmaFinish(void)
{
    return 0;
}
void NAND_AHBEnable(void)
{
    *(volatile unsigned int *)(CCMU_REGS_BASE + 0x60) |= 1 << 13;
}

void NAND_AHBDisable(void)
{
    *(volatile unsigned int *)(CCMU_REGS_BASE + 0x60) &= ~(1 << 13);
}

void NAND_ClkRequest(void)
{
    return ;
}

void NAND_ClkRelease(void)
{
    return ;
}

void NAND_ClkEnable(void)
{
	*(volatile unsigned int *)(CCMU_REGS_BASE + 0x80) |= 1U << 31;

    return ;
}

void NAND_ClkDisable(void)
{
	*(volatile unsigned int *)(CCMU_REGS_BASE + 0x80) &= ~(1U << 31);

    return ;
}


/*
**********************************************************************************************************************
*
*             NAND_GetCmuClk
*
*  Description:
*
*
*  Parameters:
*
*
*  Return value:
*
*
**********************************************************************************************************************
*/
unsigned int NAND_GetCmuClk(void)
{
	unsigned int reg_val;
	unsigned int factor_n;
	unsigned int factor_k;
	unsigned int clock;

	reg_val  = *(volatile unsigned int *)(0x01c20000 + 0x28);//PLL6
	factor_n = (reg_val >> 8) & 0x1f;
	factor_k = ((reg_val >> 4) & 0x3) + 1;

	clock = 24 * factor_n * factor_k/2;

	return clock;
}
/*
**********************************************************************************************************************
*
*             NAND_GetCmuClk
*
*  Description:
*
*
*  Parameters:
*
*
*  Return value:
*
*
**********************************************************************************************************************
*/
void NAND_SetClk(__u32 nand_max_clock)
{
	unsigned int edo_clk, cmu_clk;
	unsigned int cfg;
	unsigned int nand_clk_divid_ratio;

	/*set nand clock*/
	//edo_clk = (nand_max_clock > 20)?(nand_max_clock-10):nand_max_clock;
	edo_clk = nand_max_clock * 2;

    cmu_clk = NAND_GetCmuClk( );
	nand_clk_divid_ratio = cmu_clk / edo_clk;
	if (cmu_clk % edo_clk)
			nand_clk_divid_ratio++;
	if (nand_clk_divid_ratio){
		if (nand_clk_divid_ratio > 16)
			nand_clk_divid_ratio = 15;
		else
			nand_clk_divid_ratio--;
	}
	/*set nand clock gate on*/
	cfg = *(volatile unsigned int *)(CCMU_REGS_BASE + 0x80);

	/*gate on nand clock*/
	cfg |= (1U << 31);
	/*take cmu pll6 as nand src block*/
	cfg &= ~(0x3 << 24);
	cfg |=  (0x1 << 24);
	//set divn = 0
	cfg &= ~(0x03 << 16);

	/*set ratio*/
	cfg &= ~(0x0f << 0);
	cfg |= (nand_clk_divid_ratio & 0xf) << 0;

	*(volatile unsigned int *)(CCMU_REGS_BASE + 0x80) = cfg;
}

int NAND_GetClk(void)
{
	unsigned int cmu_clk;
	unsigned int cfg;
	unsigned int nand_max_clock, nand_clk_divid_ratio;

	/*set nand clock*/
    cmu_clk = NAND_GetCmuClk( );

    /*set nand clock gate on*/
	cfg = *(volatile unsigned int *)(CCMU_REGS_BASE + 0x80);
    nand_clk_divid_ratio = ((cfg)&0xf) +1;
    nand_max_clock = cmu_clk/(2*nand_clk_divid_ratio);

    return nand_max_clock;


}

/*
**********************************************************************************************************************
*
*             NAND_GetCmuClk
*
*  Description:
*
*
*  Parameters:
*
*
*  Return value:
*
*
**********************************************************************************************************************
*/


void NAND_PIORequest(void)
{
//	boot_set_gpio((void *)BOOT_STORAGE_CODE1, 0, 1);
*(volatile unsigned int *)(0x01c20800 + 0x48) = 0x22222222;
*(volatile unsigned int *)(0x01c20800 + 0x4C) = 0x22222222;
*(volatile unsigned int *)(0x01c20800 + 0x50) = 0x2222222;
*(volatile unsigned int *)(0x01c20800 + 0x54) = 0x2;

}
/*
**********************************************************************************************************************
*
*             NAND_ReleasePin
*
*  Description:
*
*
*  Parameters:
*
*
*  Return value:
*
*
**********************************************************************************************************************
*/
void NAND_PIORelease(void)
{
	return ;
}
void NAND_EnRbInt(void)
{
	return ;
}


void NAND_ClearRbInt(void)
{
	return ;
}

int NAND_WaitRbReady(void)
{
	return 0;
}

void NAND_RbInterrupt(void)
{
	return ;
}
/*
************************************************************************************************************
*
*                                             OSAL_malloc
*
*    �������ƣ�
*
*    �����б�
*
*    ����ֵ  ��
*
*    ˵��    �� ����һ����ٵ�malloc������Ŀ��ֻ���ṩ����һ��������������벻ͨ��
*               �����ṩ�κεĺ�������
*
************************************************************************************************************
*/
void* NAND_Malloc(unsigned int Size)
{
	return (void *)0x40000000;
}
/*
************************************************************************************************************
*
*                                             OSAL_free
*
*    �������ƣ�
*
*    �����б�
*
*    ����ֵ  ��
*
*    ˵��    �� ����һ����ٵ�free������Ŀ��ֻ���ṩ����һ��������������벻ͨ��
*               �����ṩ�κεĺ�������
*
************************************************************************************************************
*/
void NAND_Free(void *pAddr, unsigned int Size)
{
    return ;
}

void *NAND_IORemap(unsigned int base_addr, unsigned int size)
{
    return (void *)base_addr;
}
/*
**********************************************************************************************************************
*
*             OSAL_printf
*
*  Description:  �û����������趨�Ƿ���Ҫ��ӡ
*
*
*  Parameters:
*
*
*  Return value:
*
*
**********************************************************************************************************************
*/
int NAND_Print(const char * str, ...)
{
	//UART_printf2(str);
    return 0;
}

