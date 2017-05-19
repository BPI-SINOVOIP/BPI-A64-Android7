;*****************************************************************************************************************
;                                                         eGON
;                                         the Embedded GO-ON Bootloader System
;
;                             Copyright(C), 2006-2008, SoftWinners Microelectronic Co., Ltd.
;											       All Rights Reserved
;
; File Name : func_in_asm.s
;
; Author : Gary.Wang
;
; Version : 1.1.0
;
; Date : 2009.09.14
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
; Gary.Wang       2009.09.14      1.1.0        build the file
;
;******************************************************************************************************************

		EXPORT    jump_to


		AREA  func_in_asm, CODE, READONLY
		CODE32

begin		


;*******************************************************************************
;��������: jump_to
;����ԭ��: void jump_to( __u32 entry_addr )
;��������: ��ת��entry_addr��ִ��
;��ڲ���: entry_addr(r0)       ��ڵ�ַ����Ŀ���ַ
;�� �� ֵ: void
;��    ע:
;*******************************************************************************
jump_to
	mov pc, r0



	END   ; end of func_in_asm.s