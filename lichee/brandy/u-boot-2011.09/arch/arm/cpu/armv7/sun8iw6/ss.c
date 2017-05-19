/*
**********************************************************************************************************************
*
*						           the Embedded Secure Bootloader System
*
*
*						       Copyright(C), 2006-2014, Allwinnertech Co., Ltd.
*                                           All Rights Reserved
*
* File    :
*
* By      :
*
* Version : V2.00
*
* Date	  :
*
* Descript:
**********************************************************************************************************************
*/
#include <common.h>
#include <asm/io.h>
#include <asm/arch/ccmu.h>
#include <asm/arch/ss.h>
#include <smc.h>
/*
************************************************************************************************************
*
*                                             function
*
*    name          :
*
*    parmeters     :
*
*    return        :
*
*    note          :
*
*
************************************************************************************************************
*/
static u32 __aw_endian4(u32 data)
{
	u32 d1, d2, d3, d4;
	d1= (data&0xff)<<24;
	d2= (data&0xff00)<<8;
	d3= (data&0xff0000)>>8;
	d4= (data&0xff000000)>>24;

	return (d1|d2|d3|d4);
}
/*
************************************************************************************************************
*
*                                             function
*
*    name          :
*
*    parmeters     :
*
*    return        :
*
*    note          :
*
*
************************************************************************************************************
*/
static u32 __sha_padding(u32 data_size, u8* text, u32 hash_mode)
{
	u32 i;
	u32 k, q;
	u32 size;
	u32 padding_buf[16];
    u8 *ptext;

	k = data_size/64;
	q = data_size%64;

	ptext = (u8*)padding_buf;
	memset(padding_buf, 0, 16 * sizeof(u32));
	if(q==0){
		padding_buf[0] = 0x00000080;

		if(hash_mode)
		{
			padding_buf[14] = data_size>>29;
			padding_buf[15] = data_size<<3;
			padding_buf[14] = __aw_endian4(padding_buf[14]);
			padding_buf[15] = __aw_endian4(padding_buf[15]);
		}
		else
		{
			padding_buf[14] = data_size<<3;
			padding_buf[15] = data_size>>29;
		}

		for(i=0; i<64; i++){
			text[k*64 + i] = ptext[i];
		}
		size = (k + 1)*64;
	}else if(q<56)
	{
		for(i=0; i<q; i++){
			ptext[i] = text[k*64 + i];
		}
		ptext[q] = 0x80;

		if(hash_mode)
		{
			padding_buf[14] = data_size>>29;
			padding_buf[15] = data_size<<3;
			padding_buf[14] = __aw_endian4(padding_buf[14]);
			padding_buf[15] = __aw_endian4(padding_buf[15]);
		}
		else
		{
			padding_buf[14] = data_size<<3;
			padding_buf[15] = data_size>>29;
		}

		for(i=0; i<64; i++){
			text[k*64 + i] = ptext[i];
		}
		size = (k + 1)*64;
	}else{
		for(i=0; i<q; i++){
			ptext[i] = text[k*64 + i];
		}
		ptext[q] = 0x80;
		for(i=0; i<64; i++){
			text[k*64 + i] = ptext[i];
		}

		//send last 512-bits text to SHA1/MD5
		for(i=0; i<16; i++){
			padding_buf[i] = 0x0;
		}

		if(hash_mode)
		{
			padding_buf[14] = data_size>>29;
			padding_buf[15] = data_size<<3;
			padding_buf[14] = __aw_endian4(padding_buf[14]);
			padding_buf[15] = __aw_endian4(padding_buf[15]);
		}
		else
		{
			padding_buf[14] = data_size<<3;
			padding_buf[15] = data_size>>29;
		}

		for(i=0; i<64; i++){
			text[(k + 1)*64 + i] = ptext[i];
		}
		size = (k + 2)*64;
	}

	return size;
}

//align & padding
/*
************************************************************************************************************
*
*                                             function
*
*    name          :
*
*    parmeters     :
*
*    return        :
*
*    note          :
*
*
************************************************************************************************************
*/
static void __rsa_padding(u8 *dst_buf, u8 *src_buf, u32 data_len, u32 group_len)
{
	int i = 0;

	memset(dst_buf, 0, group_len);
	for(i = group_len - data_len; i < group_len; i++)
	{
		dst_buf[i] = src_buf[group_len - 1 - i];
	}
}
/*
************************************************************************************************************
*
*                                             function
*
*    name          :
*
*    parmeters     :
*
*    return        :
*
*    note          :
*
*
************************************************************************************************************
*/
void sunxi_ss_open(void)
{
	u32  reg_val;

	//enable SS working clock
	reg_val = smc_readl(CCM_SS_SCLK_CTRL); //SS CLOCK
	reg_val &= ~(0x3<<24);
	reg_val |= 0x1<<24;
	reg_val &= ~(0x3<<16);
	reg_val |= 0x0<<16;			// /1
	reg_val &= ~(0xf);
	reg_val |= (5 -1);			// /5
	reg_val |= 0x1U<<31;
	smc_writel(reg_val,CCM_SS_SCLK_CTRL);	//clock = 960/5=192
	//enable SS AHB clock
	reg_val = smc_readl(CCMU_BUS_CLK_GATING_REG0);
	reg_val |= 0x1<<5;		//SS AHB clock on
	smc_writel(reg_val,CCMU_BUS_CLK_GATING_REG0);
	//del-assert SS reset
	reg_val = smc_readl(CCMU_BUS_SOFT_RST_REG0);
	reg_val |= 0x1<<5;		//SS AHB clock reset
	smc_writel(reg_val,CCMU_BUS_SOFT_RST_REG0);
}
/*
************************************************************************************************************
*
*                                             function
*
*    name          :
*
*    parmeters     :
*
*    return        :
*
*    note          :
*
*
************************************************************************************************************
*/
void sunxi_ss_close(void)
{
	u32  reg_val;

	//assert SS reset
	reg_val = smc_readl(CCMU_BUS_SOFT_RST_REG0);
	reg_val &= ~(0x1<<5);		//SS AHB clock reset
	smc_writel(reg_val,CCMU_BUS_SOFT_RST_REG0);
	//disable SS AHB clock
	reg_val = smc_readl(CCMU_BUS_CLK_GATING_REG0);
	reg_val &= ~(0x1<<5);		//SS AHB clock on
	smc_writel(reg_val,CCMU_BUS_CLK_GATING_REG0);
	//disable SS working clock
	smc_writel(0, CCM_SS_SCLK_CTRL);
}
//src_addr		//32B ����
/*
************************************************************************************************************
*
*                                             function
*
*    name          :
*
*    parmeters     :
*
*    return        :
*
*    note          :
*
*
************************************************************************************************************
*/
int  sunxi_sha_calc(u8 *dst_addr, u32 dst_len,
					u8 *src_addr, u32 src_len)
{
	u32 reg_val = 0;
	u32 total_len = 0;
	u32 md_size = 0;
	s32 i = 0;

	ALLOC_CACHE_ALIGN_BUFFER(u8,p_sign,CACHE_LINE_SIZE);
	ALLOC_CACHE_ALIGN_BUFFER(u8,p_iv,CACHE_LINE_SIZE);

	memset(p_iv, 0, CACHE_LINE_SIZE);

	//set mode
	reg_val = readl(SS_CTL);
	reg_val &= ~(0xf<<2);
	reg_val &= ~(0x1<<17);			//IV steady of its constants
	reg_val |= 0x1<<30;        		// flow0

	reg_val |= 0x8<<2;	      	//SHA256
	md_size = 32;

	writel(reg_val, SS_CTL);
	//set src addr
	writel((u32)src_addr	, SS_DATA_SRC_LOW_ADR);
	writel(0		        , SS_DATA_SRC_HIGH_ADR);
	//set dest addr
	writel((u32)p_sign, SS_DATA_DST_LOW_ADR);
	writel(0	      , SS_DATA_DST_HIGH_ADR);
	//set src len
	//while((*(volatile int *)0)!=1);
	total_len = __sha_padding(src_len,(u8 *)src_addr, 1);

	flush_cache((u32)src_addr, total_len);
	flush_cache((u32)p_sign, CACHE_LINE_SIZE);
	flush_cache((u32)p_iv, CACHE_LINE_SIZE);

	writel(total_len/4,SS_DATA_LEN);
	//set IV
	writel((u32)p_iv, SS_PM_LOW_ADR);
	writel(0	    , SS_PM_HIGH_ADR);
	//enable INT
	reg_val = readl(SS_INT_CTRL);
	reg_val &= ~0x3;
	reg_val |= 1;
	writel(reg_val , SS_INT_CTRL);

	//start SS
	reg_val = readl(SS_CTL);
	reg_val &= ~(0x1U<<31);
	reg_val &= ~(0x1<<30);
	reg_val |= 0x1;
	writel(reg_val,SS_CTL);
	//wait end
	while((readl(SS_INT_STATUS)&0x01)==0);

	invalidate_dcache_range((ulong)p_sign,(ulong)p_sign+CACHE_LINE_SIZE);
	for(i=0; i< md_size; i++)
	{
	    dst_addr[i] = p_sign[i];
	}
	//clear SS end interrupt
	reg_val = readl(SS_INT_STATUS);
	if((reg_val&0x1)==1)
	{
		reg_val &= ~(0x3);
		reg_val |= 0x1;
	}
	writel(reg_val,SS_INT_STATUS);
	//stop SS
	reg_val = readl(SS_CTL);
	reg_val &= ~0x1;
	writel(reg_val,SS_CTL);

	return 0;
}
/*
************************************************************************************************************
*
*                                             function
*
*    name          :
*
*    parmeters     :
*
*    return        :
*
*    note          :
*
*
************************************************************************************************************
*/
s32 sunxi_rsa_calc(u8 * n_addr,   u32 n_len,
				   u8 * e_addr,   u32 e_len,
				   u8 * dst_addr, u32 dst_len,
				   u8 * src_addr, u32 src_len)
{
#define	TEMP_BUFF_LEN	((2048>>3) + CACHE_LINE_SIZE)

	u32 reg_val = 0;
	u32 mod_bit_size = 2048;
	u32 mod_size_len_inbytes = mod_bit_size/8;

	ALLOC_CACHE_ALIGN_BUFFER(u8,p_n,TEMP_BUFF_LEN);
	ALLOC_CACHE_ALIGN_BUFFER(u8,p_e,TEMP_BUFF_LEN);
	ALLOC_CACHE_ALIGN_BUFFER(u8,p_src,TEMP_BUFF_LEN);
	ALLOC_CACHE_ALIGN_BUFFER(u8,p_dst,TEMP_BUFF_LEN);

	__rsa_padding(p_src, src_addr, src_len, mod_size_len_inbytes);
	__rsa_padding(p_n, n_addr, n_len, mod_size_len_inbytes);

	memset(p_e, 0, mod_size_len_inbytes);
	memcpy(p_e, e_addr, e_len);
	//setting
	reg_val = readl(SS_CTL);
	reg_val &= ~(0xf<<2);
	reg_val |= 9<<2;
	reg_val &= ~(0x1<<6);		//RSA encrypt
	reg_val &= ~(0x3<<9);
	reg_val |= 0x2<<9;			//RSA 2048
	reg_val &= ~(0x3U<<30);
	reg_val |= 0x1<<30;
	writel(reg_val, SS_CTL);
	//data len
	writel(mod_bit_size/32, SS_DATA_LEN);
	//src
	writel((u32)p_src, SS_DATA_SRC_LOW_ADR);
	writel(0		 , SS_DATA_SRC_HIGH_ADR);
	//key addr
	writel((u32)p_e  , SS_KEY_LOW_ADR);
	writel(0		 , SS_KEY_HIGH_ADR);
	//dest
	writel((u32)p_dst, SS_DATA_DST_LOW_ADR);
	writel(0		 , SS_DATA_DST_HIGH_ADR);
	//mod
	writel((u32)p_n  , SS_PM_LOW_ADR);
	writel(0		 , SS_PM_HIGH_ADR);

	flush_cache((u32)p_n, mod_size_len_inbytes);
	flush_cache((u32)p_e, mod_size_len_inbytes);
	flush_cache((u32)p_src, mod_size_len_inbytes);
	flush_cache((u32)p_dst, mod_size_len_inbytes);

	//enable INT
	reg_val = readl(SS_INT_CTRL);
	reg_val &= ~0x3;
	reg_val |= 1;
	writel(reg_val, SS_INT_CTRL);
	//start SS
	reg_val = readl(SS_CTL);
	reg_val &= ~(0x1U<<31);
	reg_val &= ~(0x1<<30);
	reg_val |= 0x1;
	writel(reg_val,SS_CTL);
	//wait end
	while((readl(SS_INT_STATUS)&0x01)==0);

	invalidate_dcache_range((ulong)p_dst,(ulong)p_dst+mod_bit_size);
	//read dst data
	__rsa_padding(dst_addr, p_dst, mod_bit_size/64, mod_bit_size/64);
	//clear SS end interrupt
	reg_val = readl(SS_INT_STATUS);
	if((reg_val&0x1)==1)
	{
		reg_val &= ~(0x3);
		reg_val |= 0x1;
	}
	writel(reg_val,SS_INT_STATUS);
	//stop SS
	reg_val = readl(SS_CTL);
	reg_val &= ~0x1;
	writel(reg_val,SS_CTL);

	return 0;
}


