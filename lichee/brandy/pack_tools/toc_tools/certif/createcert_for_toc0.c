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
#include "common.h"
#include "include.h"
#include "ctype.h"
#include "toc0_const_def.h"
#include "rsa.h"
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
*    note          :  ����һ�����У����ո�4+��4,�ϲ���Ϊһ���µ��ֽڣ�����
*                     0x41(A) 0x31(1)  �ϲ���Ϊ0xa1
*
************************************************************************************************************
*/
static int __sample_atoi(u8 ch, u8 *dst)
{
	u8 ret_c;

	if(isdigit(ch))
		ret_c = ch - '0';
	else if(isupper(ch))
		ret_c = ch - 'A' + 10;
	else if(islower(ch))
		ret_c = ch - 'a' + 10;
	else
	{
		printf("sample_atoi err: ch 0x%02x is not a digit or hex ch\n", ch);
		return -1;
	}
	*dst = ret_c;

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
*    note          :  ����һ�����У����ո�4+��4,�ϲ���Ϊһ���µ��ֽڣ�����
*                     0x41(A) 0x31(1)  �ϲ���Ϊ0xa1
*
************************************************************************************************************
*/
int __sunxi_bytes_merge(u8 *dst, u32 dst_len, u8 *src, uint src_len)
{
	int i=0, j;
	u8  c_h, c_l;

	if((src_len>>1) > dst_len)
	{
		printf("bytes merge failed, the dst buffer is too short\n");

		return -1;
	}
	if(src_len & 0x01)		//����
	{
		src_len --;
		if(__sample_atoi(src[i], &dst[0]))
		{
			return -1;
		}
		i++;
	}

	for(j=i;i<src_len;i+=2, j++)
	{
		c_h = src[i];
		c_l = src[i+1];

		if(__sample_atoi(src[i], &c_h))
		{
			return -1;
		}

		if(__sample_atoi(src[i+1], &c_l))
		{
			return -1;
		}
		dst[j] = (c_h << 4) | c_l;
	}

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
static uint __probe_fix_len_by_asn1_type(uint len)
{
	if(len <= 127)
	{
		return 2; //ͷ������(1�ֽ�) + ��������(1�ֽ�)
	}
	else
	{
		return 4; //ͷ������(1�ֽ�) + ��������(1�ֽ�) + ���ȱ���(2�ֽ�)
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
static uint __probe_fix_len_and_head_by_asn1_type(uint len, u8 *buf, u8 tag)
{
	buf[0] = tag;

	if(len <= 127)
	{
		buf[1] = len;

		return 2; //ͷ������(1�ֽ�) + ��������(1�ֽ�)

	}
	else
	{
		buf[1] = 0x82;
		buf[2] = (len>>8) & 0xff;
		buf[3] = (len>>0) & 0xff;

		return 4; //ͷ������(1�ֽ�) + ��������(1�ֽ�) + ���ȱ���(2�ֽ�)
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
uint __create_publickey_asn1_type(u8 *dst_buf, u8 *key_n, uint key_n_len, u8 *key_e, uint key_e_len)
{
	uint key_n_len_fix, key_e_len_fix;
	uint key_part_len, key_part_len_fix;
	uint all_sequence_len, all_len_fix;
	uint len;
	u8   *p_tmp = dst_buf;

	//���ձ��۸ĵ�ans1�ṹ
	key_n_len_fix = __probe_fix_len_by_asn1_type(key_n_len);
	key_e_len_fix = __probe_fix_len_by_asn1_type(key_e_len);
	//��key��n��e�ϲ����ǳ�Ϊһ��sequence���㳤��
	key_part_len     = key_n_len + key_n_len_fix + key_e_len + key_e_len_fix;
	key_part_len_fix = __probe_fix_len_by_asn1_type(key_part_len);
	//��key sequence��TOC0_CONST_SUBJECT_PUBLIC_ALGORITHN_OID�ϲ����ǳ�Ϊһ��sequence
	all_sequence_len     = key_part_len + key_part_len_fix + sizeof(TOC0_CONST_SUBJECT_PUBLIC_ALGORITHN_OID);
	//��ʼ������ݵ�ַ
	//�������sequence����Ϣ��������OID��key����
	all_len_fix = __probe_fix_len_and_head_by_asn1_type(all_sequence_len, p_tmp, 0x30);
	p_tmp += all_len_fix;
	//����OID����
	memcpy(p_tmp, TOC0_CONST_SUBJECT_PUBLIC_ALGORITHN_OID, sizeof(TOC0_CONST_SUBJECT_PUBLIC_ALGORITHN_OID));
	p_tmp += sizeof(TOC0_CONST_SUBJECT_PUBLIC_ALGORITHN_OID);
	//��ʼ׼��key��������sequence��Ϣ
	len = __probe_fix_len_and_head_by_asn1_type(key_part_len, p_tmp, 0x30);
	p_tmp += len;
	//��ʼ�������key��Ϣ���������n��Ϣ
	len = __probe_fix_len_and_head_by_asn1_type(key_n_len, p_tmp, 0x02);
	p_tmp += len;
	//Ȼ�����n��ֵ
	memcpy(p_tmp, key_n, key_n_len);
	p_tmp += key_n_len;
	//���e����Ϣ
	len = __probe_fix_len_and_head_by_asn1_type(key_e_len, p_tmp, 0x02);
	p_tmp += len;
	//Ȼ�����e��ֵ
	memcpy(p_tmp, key_e, key_e_len);

	return all_sequence_len + all_len_fix;
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
*    note          :  ����bin�ļ����ͻ�׼�����ļ�������һ��֤���ļ��������ļ�
*
*
************************************************************************************************************
*/
int u8_to_str(u8 *p_buff_u8, u32 u8_len, u8 *p_str, u32 str_buff_len)
{
	int i = 0;
	u8  temp_h = 0;
	u8  temp_l = 0;

	for(i = 0; i < u8_len; i++)
	{
		temp_h = ((p_buff_u8[i])>>4)&0xf;
		temp_l = ((p_buff_u8[i])>>0)&0xf;

		if(temp_h < 0xa)
		{
			p_str[(i<<1) + 0] = temp_h + '0';
		}
		else
		{
			p_str[(i<<1) + 0] = temp_h - 0x0a + 'a';
		}

		if(temp_l < 0xa)
		{
			p_str[(i<<1) + 1] = temp_l + '0';
		}
		else
		{
			p_str[(i<<1) + 1] = temp_l - 0x0a + 'a';
		}
	}

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
static int	__merge_certif_for_toc0(char *hash_value, u8 *key_n, u8 *key_e, u8 *key_d, char *cert_name)
{
	u8 *p_toc0_cert, *toc0_cert;
	u8 *p_key_n_tmp, *p_sign_start;
    u8  sign_value[256];
	u8  key_n_digital[RSA_BIT_WITDH/8];
	u8  key_e_digital[RSA_BIT_WITDH/8];
	u8  hash_of_cert_in_str[32 * 2 + 8];
	u8  hash_of_cert[32];

	u8 *p_cert_key_part;

	uint key_n_len, key_e_len, key_part_len;
	uint cert_main_part_len, cert_all_part_len;

	p_key_n_tmp = key_n;
	while(*p_key_n_tmp == '0')
	{
		p_key_n_tmp ++;
	}
	key_n_len = strlen((const char *)p_key_n_tmp);
	key_e_len = strlen((const char *)key_e);
	//ת���ַ����͵�keyΪ�������ͣ��ֱ���nֵ��eֵ
	memset(key_n_digital, 0, RSA_BIT_WITDH/8);
	memset(key_e_digital, 0, RSA_BIT_WITDH/8);
	if(__sunxi_bytes_merge(key_n_digital, RSA_BIT_WITDH/8, p_key_n_tmp, key_n_len))
	{
		printf("dragoncreatetoc0certif err in sunxi_bytes_merge for keyn value\n");

		return -1;
	}
	if(__sunxi_bytes_merge(key_e_digital, RSA_BIT_WITDH/8, key_e, key_e_len))
	{
		printf("dragoncreatetoc0certif err in sunxi_bytes_merge for keye value\n");

		return -1;
	}
	//��ʼ���д���֤�飬������openssl��׼����
	toc0_cert = (u8 *)malloc(1024 * 1024);
	if(toc0_cert == NULL)
	{
		printf("dragoncreatetoc0certif err: cant malloc memory to store toc0\n");

		return -1;
	}
	memset(toc0_cert, 0, 1024 * 1024);
	p_toc0_cert = toc0_cert + sizeof(TOC0_CONST_TOTAL_SEQUENCE) + sizeof(TOC0_CONST_MAIN_SEQUENCE);
	//����֤������ͷ���ṹ
//	memcpy(p_toc0_cert, TOC0_COMST_TOTAL_SEQUENCE, sizeof(TOC0_COMST_TOTAL_SEQUENCE));
//	p_toc0_cert += sizeof(TOC0_COMST_TOTAL_SEQUENCE);
//	//����֤��ͷ���ṹ
//	memcpy(p_toc0_cert, TOC0_COMST_MAIN_SEQUENCE, sizeof(TOC0_COMST_MAIN_SEQUENCE));
//	p_toc0_cert += sizeof(TOC0_COMST_MAIN_SEQUENCE);
	//����֤��汾��
	memcpy(p_toc0_cert, TOC0_CONST_VERSION, sizeof(TOC0_CONST_VERSION));
	p_toc0_cert += sizeof(TOC0_CONST_VERSION);
	//����֤�����к�
	memcpy(p_toc0_cert, TOC0_CONST_SERIAL, sizeof(TOC0_CONST_SERIAL));
	p_toc0_cert += sizeof(TOC0_CONST_SERIAL);
	//����֤���㷨OID
	memcpy(p_toc0_cert, TOC0_CONST_ALGORITHM_OID, sizeof(TOC0_CONST_ALGORITHM_OID));
	p_toc0_cert += sizeof(TOC0_CONST_ALGORITHM_OID);
	//����֤�鷢����
	memcpy(p_toc0_cert, TOC0_CONST_ISSUER_NAME, sizeof(TOC0_CONST_ISSUER_NAME));
	p_toc0_cert += sizeof(TOC0_CONST_ISSUER_NAME);
	//����֤����Ч��
	memcpy(p_toc0_cert, TOC0_CONST_VALIDE_PERIOD, sizeof(TOC0_CONST_VALIDE_PERIOD));
	p_toc0_cert += sizeof(TOC0_CONST_VALIDE_PERIOD);
	//����֤�������Ϣ
	memcpy(p_toc0_cert, TOC0_CONST_SUBJECT_NAME, sizeof(TOC0_CONST_SUBJECT_NAME));
	p_toc0_cert += sizeof(TOC0_CONST_SUBJECT_NAME);
	//����֤�鹫Կ(�����������)
	key_part_len = __create_publickey_asn1_type(p_toc0_cert, key_n_digital, (key_n_len + 1)/2, key_e_digital, (key_e_len + 1)/2);
	p_toc0_cert += key_part_len;
	//������չ��tocken
	memcpy(p_toc0_cert, TOC0_CONST_EXTENSION_TOCKEN, sizeof(TOC0_CONST_EXTENSION_TOCKEN));
	p_toc0_cert += sizeof(TOC0_CONST_EXTENSION_TOCKEN);
	//������չ��sequence
	memcpy(p_toc0_cert, TOC0_CONST_EXTENSION_SEQUENCE, sizeof(TOC0_CONST_EXTENSION_SEQUENCE));
	p_toc0_cert += sizeof(TOC0_CONST_EXTENSION_SEQUENCE);
	//����hash����
	memcpy(p_toc0_cert, TOC0_CONST_SBROMSW_HASH_TYPE, sizeof(TOC0_CONST_SBROMSW_HASH_TYPE));
	p_toc0_cert += sizeof(TOC0_CONST_SBROMSW_HASH_TYPE);
	//���hash����

	memcpy(p_toc0_cert, hash_value, 32);
	p_toc0_cert += 32;
	cert_main_part_len = p_toc0_cert - toc0_cert - sizeof(TOC0_CONST_TOTAL_SEQUENCE) - sizeof(TOC0_CONST_MAIN_SEQUENCE);
	//����ǩ��sequence
	memcpy(p_toc0_cert, TOC0_CONST_SIGNATURE_SEQUENCE, sizeof(TOC0_CONST_SIGNATURE_SEQUENCE));
	p_toc0_cert += sizeof(TOC0_CONST_SIGNATURE_SEQUENCE);
	//����ǩ��OID
	memcpy(p_toc0_cert, TOC0_CONST_SIGNATURE_OID, sizeof(TOC0_CONST_SIGNATURE_OID));
	p_toc0_cert += sizeof(TOC0_CONST_SIGNATURE_OID);
	//���ǩ��ͷ��
	memcpy(p_toc0_cert, TOC0_CONST_SIGNATURE_VALUE_HEAD, sizeof(TOC0_CONST_SIGNATURE_VALUE_HEAD));
	p_toc0_cert += sizeof(TOC0_CONST_SIGNATURE_VALUE_HEAD);
	//���ǩ������
	p_sign_start = p_toc0_cert;
	p_toc0_cert += sizeof(sign_value);

	cert_all_part_len = p_toc0_cert - toc0_cert - sizeof(TOC0_CONST_TOTAL_SEQUENCE);

	p_cert_key_part = toc0_cert + sizeof(TOC0_CONST_TOTAL_SEQUENCE);
	__probe_fix_len_and_head_by_asn1_type(cert_all_part_len, toc0_cert, 0x30);
	__probe_fix_len_and_head_by_asn1_type(cert_main_part_len, toc0_cert + sizeof(TOC0_CONST_TOTAL_SEQUENCE), 0x30);

	printf("cert_all_part_len=%d\n", cert_all_part_len);
	printf("cert_main_part_len=%d\n", cert_main_part_len);
	//����֤��key���ֵ�hash
	sha256(p_cert_key_part, cert_main_part_len, hash_of_cert);

//	sunxi_dump(hash_of_cert, 32);
	//�ַ�ת��Ϊ����
	memset(hash_of_cert_in_str, 0, sizeof(hash_of_cert_in_str));
	u8_to_str(hash_of_cert, 32, hash_of_cert_in_str, sizeof(hash_of_cert_in_str));

//	sunxi_dump(hash_of_cert_in_str, sizeof(hash_of_cert_in_str));

//	printf("key n:\n");
//	sunxi_dump(p_key_n_tmp, 560);
//	printf("key e:\n");
//	sunxi_dump(key_e, 56);
//	printf("key d:\n");
//	sunxi_dump(key_d, 560);
	//����hash��rsa
	rsa_sign_main(  (char *) p_key_n_tmp,
					(char *) key_d,
					(char *) key_e,
					(char *) hash_of_cert_in_str,

					p_sign_start,
					256,
					RSA_BIT_WITDH);

//	sunxi_dump(p_sign_start, 256);

	//memcpy((void *)p_sign_start, sign_u8, RSA_BIT_WITDH/8);

	printf("Make toc0 cert %s.der success.\n", cert_name);

	FILE *p_file;
	char file_name[MAX_PATH];

	memset(file_name, 0, MAX_PATH);
	sprintf(file_name, "%s.crtpt", cert_name);

	p_file = fopen(file_name, "wb+");
	if(p_file == NULL)
	{
		printf("dragoncreatetoc0certif err: cant create file %s to store toc0 cert\n", file_name);
		free(toc0_cert);

		return -1;
	}
	fwrite(toc0_cert, p_toc0_cert - toc0_cert, 1, p_file);
	fclose(p_file);
	free(toc0_cert);

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
int create_cert_for_toc0(char *lpCfg, toc_descriptor_t *toc0, char *keypath)
{
	char all_toc0[1024];
	char type_name[32], line_info[256];
	int  ret;
	char hash_value[128];
	char key_n[560], key_e[560], key_d[560];
	char current_path[MAX_PATH];
	char bin_name[64], key_name[64];

	memset(all_toc0, 0, 1024);
	if(GetPrivateProfileSection("toc0", all_toc0, 1024, lpCfg))
	{
		printf("dragoncreate_toc0_certif err in GetPrivateProfileSection\n");

		return -1;
	}
	if(all_toc0[0] == '\0')
	{
		printf("dragoncreate_toc0_certif err no content match toc0\n");

		return -1;
	}

	memset(type_name, 0, 32);
	memset(line_info, 0, 256);

	printf("all_toc0=%s\n", all_toc0);

	GetPrivateProfileLineInfo(all_toc0, type_name, line_info);

	printf("type_name=%s\n", type_name);
	printf("line_info=%s\n", line_info);

	memset(bin_name , 0, 64);
	memset(key_name , 0, 64);

	GetPrivateEachItem(line_info, toc0->item, bin_name, key_name);

	printf("item=%s\n", toc0->item);
	printf("bin_name=%s\n", bin_name);
	printf("key_name=%s\n", key_name);
	//����key����
	sprintf(toc0->key, "%s/%s", keypath, key_name);
	printf("key=%s\n", toc0->key);
	//����֤������
	memset(current_path , 0, MAX_PATH);
	GetFullPath(current_path, TOC0PATH_CONST);
	sprintf(toc0->cert, "%s/%s", current_path, toc0->item);

	GetFullPath(toc0->bin, bin_name);

	memset(hash_value, 0, 128);
	ret = calchash_in_hex(toc0->bin, hash_value);
	if(ret < 0)
	{
		printf("dragoncreate_toc0_certif err in calchash\n");

		return -1;
	}
	//��ȡ��Ӧ��key�Ĺ�Կ
	memset(key_n, 0, 560);
	memset(key_e, 0, 560);
	memset(key_d, 0, 560);
	memset(current_path, 0, MAX_PATH);
	sprintf(current_path, "%s.bin", toc0->key);
	printf("current_path=%s\n", current_path);
	ret = getallkey(current_path, key_n, key_e, key_d);
	if(ret < 0)
	{
		printf("dragoncreate_toc0_certif err in getpublickey bin\n");

		return -1;
	}
	printf("%s %d %s\n", __FILE__, __LINE__, __func__);
	//����֤��
	ret = __merge_certif_for_toc0(hash_value, (u8 *)key_n, (u8 *)key_e, (u8 *)key_d, toc0->cert);
	if(ret < 0)
	{
		printf("dragoncreate_toc0_certif err in dragoncreatetoc0certif rootkey\n");

		return -1;
	}

	return 0;
}

