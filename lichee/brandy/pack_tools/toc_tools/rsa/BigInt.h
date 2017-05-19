/*
**********************************************************************************************************************
*											        eGon
*						           the Embedded GO-ON Bootloader System
*									       eGON arm boot sub-system
*
*						  Copyright(C), 2006-2014, Allwinner Technology Co., Ltd.
*                                           All Rights Reserved
*
* File    :
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

/*****************************************************************
���������ͷ�ļ���BigInt.h
*****************************************************************/
//��������1120λ�������ƣ����м���
#define BI_MAXLEN 130
#define DEC 10
#define HEX 16
#ifndef WIN_32
	#include <inttypes.h>
#else
	typedef unsinged __int64 uint64_t;
#endif

class CBigint;
class CBigInt
{
public:
//������0x100000000�����µĳ���
    unsigned m_nLength;
//�������¼������0x100000000������ÿһλ��ֵ
    unsigned int m_ulValue[BI_MAXLEN];

    CBigInt();
    ~CBigInt();

/*****************************************************************
��������������
Mov����ֵ���㣬�ɸ�ֵΪ��������ͨ������������Ϊ�������=��
Cmp���Ƚ����㣬������Ϊ�������==������!=������>=������<=����
Add���ӣ��������������������ͨ�����ĺͣ�������Ϊ�������+��
Sub�������������������������ͨ�����Ĳ������Ϊ�������-��
Mul���ˣ��������������������ͨ�����Ļ���������Ϊ�������*��
Div�������������������������ͨ�������̣�������Ϊ�������/��
Mod��ģ���������������������ͨ������ģ��������Ϊ�������%��
*****************************************************************/
    void Mov( uint64_t A);
    void Mov(const CBigInt& A);
    CBigInt Add(const CBigInt& A);
    CBigInt Sub(const CBigInt& A);
    CBigInt Mul(const CBigInt& A);
    CBigInt Div(CBigInt& A);
    CBigInt Mod(CBigInt& A);
    CBigInt Add(unsigned A);
    CBigInt Sub(unsigned A);
    CBigInt Mul(unsigned A);
    CBigInt Div(unsigned A);
    unsigned Mod(unsigned A);
    int Cmp(const CBigInt& A);
    void Random(int bits);
/*****************************************************************
�������
Get�����ַ�����10���ƻ�16���Ƹ�ʽ���뵽����
Put����������10���ƻ�16���Ƹ�ʽ������ַ���
*****************************************************************/
    void Get(char c[513], unsigned int system=HEX);
    void Put( unsigned int system=HEX);
	void ToFile(const char *f, unsigned int system=HEX);
	void ToFile_String_hgl(char *f, unsigned int system=HEX);
	void ToFile_buff_str(char *p_buff, unsigned int buff_len,unsigned int bit_width);
	void Resu(char *f, unsigned int system=HEX);



/*****************************************************************
RSA�������
Rab�����������㷨������������
Euc��ŷ������㷨���ͬ�෽��
RsaTrans������ƽ���㷨������ģ����
GetPrime������ָ�����ȵ����������
*****************************************************************/
    int Rab();
    CBigInt Euc(CBigInt& A);
	CBigInt RsaTrans_en_de(const CBigInt& A, CBigInt& B, int EN_DE, char *resu);
    CBigInt RsaTrans_x(const CBigInt& A, CBigInt& B);
    CBigInt PowerMode(const CBigInt& p,  CBigInt& m);
	CBigInt ModMul(CBigInt& A, CBigInt& B);
	CBigInt MonPro(CBigInt& A, CBigInt& B, unsigned n);
	CBigInt ModExp(CBigInt& A, CBigInt& B);
    void GetPrime(int bits);
};
//#endif
