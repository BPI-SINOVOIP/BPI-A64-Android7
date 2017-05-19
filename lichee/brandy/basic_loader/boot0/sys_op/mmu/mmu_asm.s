;*****************************************************************************************************************
;                                                         eGON
;                                         the Embedded GO-ON Bootloader System
;
;                             Copyright(C), 2006-2008, SoftWinners Microelectronic Co., Ltd.
;											       All Rights Reserved
;
; File Name : mmu_asm.s
;
; Author : Gary.Wang
;
; Version : 1.1.0
;
; Date : 2009.03.20
;
; Description :
;
; Others : None at present.
;
;
; History :
;
;  <Author>        <time>       <version>      <description>
;
; Gary.Wang       2009.03.20      1.1.0        build the file
;
;******************************************************************************************************************

		EXPORT  mmu_set_ttb
		EXPORT  mmu_set_domain_access
		EXPORT  mmu_flush_TLB
		EXPORT  mmu_set_smp

		AREA  mmu_asm, CODE, READONLY
		CODE32

begin





;*******************************************************************************
;��������: mmu_set_ttb
;����ԭ��: void mmu_set_ttb( uint32 ttb_base_addr )
;��������: set TTB
;��ڲ���: ttb base address
;�� �� ֵ: void
;��    ע:
;*******************************************************************************
mmu_set_ttb
	MCR p15, 0, r0, c2, c0, 0 ; Write TTB register
	MOV pc, lr


;*******************************************************************************
;��������: mmu_change_domain_access
;����ԭ��: s32   mmu_change_domain_access( uint32 value, uint32 mask )
;��������: Change Domain Access Register Values
;��ڲ���: value
;          mask
;�� �� ֵ: void
;��    ע:
;*******************************************************************************
mmu_set_domain_access
	MRC     p15, 0, r0, c3, c0, 0     ; Read Domain Access Control Register
	LDR     r0, =0x55555555           ; Initialize every domain entry to b01 (client)
	MCR     p15, 0, r0, c3, c0, 0     ; Write Domain Access Control Register
    MOV     pc, lr



;*******************************************************************************
;��������: mmu_flush_TLB
;����ԭ��: void   mmu_flush_TLB( void )
;��������: clear TLB
;��ڲ���: void
;�� �� ֵ: void
;��    ע:
;*******************************************************************************
mmu_flush_TLB
    MOV r0, #0
    MCR p15, 0, r0, c8, c7, 0 ; flush both the TLBs
    MOV pc, lr

;*******************************************************************************
;��������: mmu_flush_TLB
;����ԭ��: void   mmu_flush_TLB( void )
;��������: clear TLB
;��ڲ���: void
;�� �� ֵ: void
;��    ע:
;*******************************************************************************
mmu_set_smp
    mrc  p15, 0, r0, c1, c0, 1
    orr  r0, r0, #(1 << 6)
    mcr  p15, 0, r0, c1, c0, 1

	END   ; end of mmu_asm.s
