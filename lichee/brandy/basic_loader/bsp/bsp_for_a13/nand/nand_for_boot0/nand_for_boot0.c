
/*
**********************************************************************************************************************
*											        eGon
*						           the Embedded GO-ON Bootloader System
*									       eGON arm boot sub-system
*
*						  Copyright(C), 2006-2010, SoftWinners Microelectronic Co., Ltd.
*                                           All Rights Reserved
*
* File    : nand_for_boot0.c
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
#include "bsp_nand.h"
//#include "nand_oal.h"
//#include "nand_osal.h"
//#include "nand_simple.h"
//#include "nand_physic.h"
//#include "nand_ndfc.h"

extern void NAND_Print( const char * str, ...);
extern BOOT_NandGetPara(boot_nand_para_t *nand_info);
/*
************************************************************************************************************************
*                       GET FLASH INFO
*
*Description: get some info about nand flash;
*
*Arguments  : *param     the stucture with info.
*
*Return     :   = SUCESS  get ok;
*               = FAIL    get fail.
************************************************************************************************************************
*/
__s32 NFB_GetFlashInfo(boot_flash_info_t *param)
{
    boot_nand_para_t nand_info;

    BOOT_NandGetPara(&nand_info);

	param->chip_cnt	 		= nand_info.ChipCnt;
	param->blk_cnt_per_chip = nand_info.BlkCntPerDie * nand_info.DieCntPerChip;
	param->blocksize 		= nand_info.SectorCntPerPage * nand_info.PageCntPerPhyBlk;
	param->pagesize 		= nand_info.SectorCntPerPage;
	param->pagewithbadflag  = 0 ;   // fix page 0 as bad flag page index

	return 0;
}
/*
************************************************************************************************************************
*                       INIT NAND FLASH
*
*Description: initial nand flash,request hardware resources;
*
*Arguments  : void.
*
*Return     :   = SUCESS  initial ok;
*               = FAIL    initial fail.
************************************************************************************************************************
*/
__s32 NFB_PhyInit(void)
{
	__s32 ret;

	ret = PHY_Init();
	if (ret)
	{
		NAND_Print("NB0 : nand phy init fail\n");
		return ret;
	}

	ret = BOOT_AnalyzeNandSystem();
	if (ret)
	{
		NAND_Print("NB0 : nand scan fail\n");
		return ret;
	}

	NAND_Print("NB0 : nand phy init ok\n");


	return(PHY_ChangeMode(1));
}

/*
************************************************************************************************************************
*                       RELEASE NAND FLASH
*
*Description: release  nand flash and free hardware resources;
*
*Arguments  : void.
*
*Return     :   = SUCESS  release ok;
*               = FAIL    release fail.
************************************************************************************************************************
*/
__s32 NFB_PhyExit(void)
{
	PHY_Exit();
	/* close nand flash bus clock gate */
	//NAND_CloseAHBClock();

	return 0;
}

/*
************************************************************************************************************************
*                       READ ONE SINGLE PAGE
*
*Description: read one page data from nand based on single plane;
*
*Arguments  : *readop - the structure with physical address in nand and data buffer
*
*Return     :   = SUCESS  read ok;
*               = FAIL    read fail.
************************************************************************************************************************
*/
__s32 NFB_PhyRead (struct boot_physical_param *readop)
{
	return(PHY_SimpleRead (readop));
}
