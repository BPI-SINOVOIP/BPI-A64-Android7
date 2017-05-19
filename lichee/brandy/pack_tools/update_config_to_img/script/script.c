// script.cpp : Defines the entry point for the console application.
//
#include <malloc.h>
#include <string.h>
#include "types.h"
#include "script.h"
#include <ctype.h>
#include <unistd.h>

//__asm__(".symver memcpy ,memcpy@GLIBC_2.2.5");

#define debug(fmt, args...)	//printf(fmt, ##args)

int parser_script(void *pbuf, int script_len, FILE *hfile);

int IsFullName(const char *FilePath)
{
	if ( FilePath[0] == '/')
	{
		return 1;
	}
	else
	{
		return 0;
	}
}

void GetFullPath(char *dName, const char *sName)
{
	char Buffer[MAX_PATH];

	if (IsFullName(sName))
	{
		strcpy(dName, sName);
		return ;
	}

	/* Get the current working directory: */
	if (getcwd(Buffer, MAX_PATH ) == NULL)
	{
		perror( "_getcwd error" );
		return ;
	}

	sprintf(dName, "%s/%s", Buffer, sName);
}


int _get_str_length(char *str)
{
	int length = 0;

	while (str[length])
	{
		length ++;
	}

	return length;
}

int script(char *src_file)
{
	char   src[MAX_PATH];
	char   dest[MAX_PATH];
	FILE  *dst_file = NULL;
	FILE  *script_file = NULL;
	int    ret = -1, src_length;
	char   *script_addr = NULL;
	int    script_len;
	{

		debug("input name %s\n", src_file);
		memset(src, 0, sizeof(MAX_PATH));
		memset(dest, 0, sizeof(MAX_PATH));
		GetFullPath(src, src_file);

		src_length = _get_str_length(src);
		memcpy(dest, src, src_length);
		dest[src_length - 0] = NULL;
		dest[src_length - 1] = 'n';
		dest[src_length - 2] = 'i';
		dest[src_length - 3] = 'b';

		debug("Script  source file Path=%s\n", src);
		debug("Script  bin file Path=%s\n", dest);

		script_file = fopen(src, "rb");

		if (!script_file)
		{
			printf("unable to open script file %s\n", src);

			goto _err_out;
		}

		dst_file = fopen(dest, "wb");

		if (!dst_file)
		{
			printf("unable to open dest file\n");

			goto _err_out;
		}

		fseek(script_file, 0, SEEK_END);
		script_len = ftell(script_file);
		fseek(script_file, 0, SEEK_SET);

		script_addr = (char *)malloc(script_len + 1);
		memset(script_addr, 0, script_len + 1);

		if (!script_addr)
		{
			printf("unable to malloc memory for script\n");

			goto _err_out;
		}

		fread(script_addr, 1, script_len, script_file);
		fclose(script_file);
		script_file = NULL;

		ret = parser_script(script_addr, script_len, dst_file);

		if (ret)
		{
			printf("error1\n");
			goto _err_out;
		}

		free(script_addr);
		script_addr = NULL;
		fclose(dst_file);
		dst_file = NULL;
		debug("parser  file ok\n");
	}

_err_out:

	if (script_addr)
	{
		free(script_addr);
	}

	if (script_file)
	{
		fclose(script_file);
		script_file = NULL;
	}

	if (dst_file)
	{
		fclose(dst_file);
		dst_file = NULL;
	}

	return ret;
}
#define  THIS_LINE_NULL        (0)
#define  THIS_LINE_MAINKEY     (1)
#define  THIS_LINE_SUBKEY      (2)
#define  THIS_LINE_ERROR       (-1)
//�˺������ص�ǰ�еĳ��ȣ���ָ�뷵�ص�ǰ������
/********************************************
* flag = 0      //��ǰ��ע���У������
*      = 1      //��ǰ���ֶ���
*      = 2      //��ǰ�������У������ֶε���һ��
*      = -1     //��ǰ�в����Ϲ淶������
*********************************************/
static  int  _get_line_status(char  *daddr,  int  *flag, int last_len)
{
	char  *src;
	int    len;
	char   ch;

	src = daddr;
	ch  = *src++;
	last_len --;

	switch (ch)
	{
		case ';':     //ע����
		case 0x0D:    //�س���
		{
			*flag = THIS_LINE_NULL;
		}
		break;

		case '[':    //������
		{
			*flag = THIS_LINE_MAINKEY;
			break;
		}

		default:     //�Ӽ���
		{
			*flag = THIS_LINE_SUBKEY;
			break;
		}
	}

	len = 1;
	ch = *src++;

	while ((ch != 0x0A) && (last_len >  len))    //ֻҪ���ǻ��з��ţ���������
	{
		ch = *src++;
		len ++;

		if (len >= 512)
		{
			*flag = THIS_LINE_ERROR;

			return 0;
		}
	}

	return len + 1;
}

//���ҳ��������ַ�������
static  int _fill_line_mainkey(char *pbuf, script_item_t *item)
{
	char *src;
	char  ch, i;

	i = 0;
	src = pbuf + 1;        //���� ��
	ch  = *src++;

	while (']' != ch)
	{
		item->item_name[i] = ch;
		i++;
		ch = *src++;

		if (i >= ITEM_MAIN_NAME_MAX)
		{
			item->item_name[i - 1] = 0;
			break;
		}
	}

	return 0;

}

static  int _get_item_value(char *pbuf, char *name, char *value)
{
	char  *src, *dstname, *dstvalue;
	int   len;

	src = pbuf;
	dstname = name;
	dstvalue = value;

	len = 0;

	//���ȼ�������ַ��ĺϷ���
	while (1)
	{
		//���һ��ʼ�����ո����TAB�����ƶ�ָ�룬ֱ���ҵ�һ���Ϸ��ַ�Ϊֹ
		if ((*src == ' ') || (*src == 0x09))
		{
			src ++;
		}
		//���ʲô��û���ҵ�����ֱ�ӷ���
		else if ((*src == 0x0D) || (*src == 0x0A))
		{
			dstname[0] = '\0';
			dstvalue[0] = '\0';

			return 0;
		}
		else
		{
			break;
		}
	}

	//�Ѿ��ҵ�һ���Ϸ��ַ��������ң�ֱ��Ѱ�ҵ��Ⱥţ�ȥ��β���Ŀո����TAB
	while (*src != '=')
	{
		dstname[len ++] = *src;
		src ++;

		if (len >= 31)
		{
			dstname[len] = '\0';
			break;
		}
	}

	while (1)
	{
		len --;

		if ((dstname[len] == ' ') || (dstname[len] == 0x09))
		{
			dstname[len] = '\0';
		}
		else
		{
			dstname[len + 1] = '\0';
			break;
		}
	}

	while (*src != '=')
	{
		src ++;
	}

	src++;
	len = 0;

	//���ȼ�������ַ��ĺϷ���
	while (1)
	{
		//���һ��ʼ�����ո����TAB�����ƶ�ָ�룬ֱ���ҵ�һ���Ϸ��ַ�Ϊֹ
		if ((*src == ' ') || (*src == 0x09))
		{
			src ++;
		}
		//���ʲô��û���ҵ�����ֱ�ӷ���
		else if ((*src == 0x0D) || (*src == 0x0A))
		{
			dstvalue[0] = '\0';
			return 0;
		}
		else
		{
			break;
		}
	}

	//�Ѿ��ҵ�һ���Ϸ��ַ��������ң�ֱ��Ѱ�ҵ��Ⱥţ�ȥ��β���Ŀո����TAB
	while ((*src != 0x0D) && (*src != 0x0A))
	{
		dstvalue[len ++] = *src;
		src ++;

		if (len >= 127)
		{
			dstvalue[len] = '\0';
			break;
		}
	}

	while (1)
	{
		len --;

		if ((dstvalue[len] == ' ') || (dstvalue[len] == 0x09))
		{
			dstvalue[len] = '\0';
		}
		else
		{
			dstvalue[len + 1] = '\0';
			break;
		}
	}

	return 0;
}
//�˺���ת���ַ����ݳ�Ϊ�������ݣ�����10���ƺ�16����
//ת�����������value�У�����ֵ��־ת���ɹ�����ʧ��
static  int  _get_str2int(char *saddr, int value[] )
{
	char  *src;
	char   off, ch;
	unsigned int  tmp_value = 0;
	int    data_count, i;
	char   tmp_str[128];
	int    sign = 1;

	data_count = 2;
	src = saddr;
	off = 0;         //0����10���ƣ�1����16����

	if (!strncmp(src, "port:", 5))
	{
		if ((src[5] == 'P') || (src[5] == 'p'))
		{
			off = 3;                    //��ʾ�Ƕ˿���������
			src += 6;
		}
	}
	else if (!strncmp(src, "string:", 7))
	{
		off = 0;
		src += 7;
	}
	else if (src[0] == '"')
	{
		off = 5;
		src += 1;
	}
	else if ((src[0] == '0') && ((src[1] == 'x') || (src[1] == 'X')))     //ʮ������
	{
		src += 2;
		off  = 2;
	}
	else if ((src[0] >= '0') && (src[0] <= '9'))                    //ʮ����
	{
		off = 1;
	}
	else if (((src[1] >= '0') && (src[1] <= '9')) && (src[0] == '-'))
	{
		src ++;
		off = 1;
		sign = -1;
	}
	else if (src[0] == '\0')
	{
		src++;
		off = 4;
	}

	//��ʾ���ַ���
	if (off == 0)
	{
		data_count = 0;

		while (src[data_count] != '\0')
		{
			data_count ++;

			if (data_count > 127)
			{
				break;
			}
		}

		if (data_count & 0x03)      //Ҫ�����ֽڶ���
		{
			data_count = (data_count & (~0x03)) + 4;
		}
		else
		{
			data_count = data_count + 4;
		}

		value[0] = data_count >> 2;

		if (saddr != src)
		{
			value[1] = 0;
		}
		else
		{
			value[1] = 1;
		}

		return DATA_TYPE_STRING;
	}
	else if (off == 5)	//��ʾ���ַ���
	{
		data_count = 0;

		while (src[data_count] != '"')
		{
			data_count ++;

			if (data_count > 127)
			{
				break;
			}
		}

		src[data_count] = '\0';

		if (data_count & 0x03)      //Ҫ�����ֽڶ���
		{
			data_count = (data_count & (~0x03)) + 4;
		}
		else
		{
			data_count = data_count + 4;
		}

		value[0] = data_count >> 2;
		value[1] = 5;

		return DATA_TYPE_STRING;
	}
	else if (off == 1)
	{
		while (*src != '\0')
		{
			if ((*src >= '0') && (*src <= '9'))
			{
				tmp_value = tmp_value * 10 + (*src - '0');
				src ++;
			}
			else
			{
				return -1;
			}
		}

		value[0] = tmp_value * sign;

		return DATA_TYPE_SINGLE_WORD;
	}
	else if (off == 2)
	{
		while (*src != '\0')
		{
			if ((*src >= '0') && (*src <= '9'))
			{
				tmp_value = tmp_value * 16 + (*src - '0');
				src ++;
			}
			else if ((*src >= 'A') && (*src <= 'F'))
			{
				tmp_value = tmp_value * 16 + (*src - 'A' + 10);
				src ++;
			}
			else if ((*src >= 'a') && (*src <= 'f'))
			{
				tmp_value = tmp_value * 16 + (*src - 'a' + 10);
				src ++;
			}
			else
			{
				return -1;
			}
		}

		value[0] = tmp_value;

		return DATA_TYPE_SINGLE_WORD;
	}
	else if (off == 3)                             //��ʾ��GPIO��Ϣ�����밴�ձ�׼��ʽ���� �˿ڱ�ţ��˿����ű�ţ�mult-driving��pull��driving-level��data��6��word
	{
		//��ȡ�ַ�������Ϣ������
		int  tmp_flag = 0;

		ch = *src++;

		if ((ch == 'o') || (ch == 'O'))            //��1����A�飬2����B�飬�������ƣ���0xffff����POWER����
		{
			ch = src[0];

			if ((ch == 'w') || (ch == 'W'))
			{
				ch = src[1];

				if ((ch == 'e') || (ch == 'E'))
				{
					ch = src[2];

					if ((ch == 'r') || (ch == 'R'))
					{
						//ȷ������POWER����
						value[0] = 0xffff;
						src += 3;
						tmp_flag = 1;
					}
				}
			}
		}

		if (!tmp_flag)
		{
			if ((ch >= 'A') && (ch <= 'Z'))
			{
				value[0] = ch - 'A' + 1;
			}
			else if ((ch >= 'a') && (ch <= 'z'))
			{
				value[0] = ch - 'a' + 1;
			}
			else
			{
				return -1;
			}
		}

		//��ȡ�ַ�������Ϣ���������
		//��һ���汾����֧���Զ���ȡ����
		ch = *src++;
		tmp_value = 0;

		while (ch != '<')
		{
			if ((ch >= '0') && (ch <= '9'))
			{
				tmp_value = tmp_value * 10 + (ch - '0');
				ch = *src++;
			}
			else if (ch == 0)
			{
				src --;
				break;
			}
			else
			{
				return -1;
			}
		}

		value[1] = tmp_value;
		//��ʼ�������еļ�����
		ch = *src++;

		while (ch != '\0')
		{
			i = 0;
			memset(tmp_str, 0, sizeof(tmp_str));

			while (ch != '>')    //���������
			{
				if ((ch >= 'A') && (ch <= 'Z'))
				{
					ch += 'a' - 'A';
				}

				tmp_str[i++] = ch;
				ch = *src++;
			}

			tmp_str[i] = '\0';

			//�Ƚ��ַ���
			if (!strcmp(tmp_str, "default"))
			{
				value[data_count] = -1;
			}
			else if (!strcmp(tmp_str, "none"))
			{
				value[data_count] = -1;
			}
			else if (!strcmp(tmp_str, "null"))
			{
				value[data_count] = -1;
			}
			else if (!strcmp(tmp_str, "-1"))
			{
				value[data_count] = -1;
			}
			else
			{
				i = 0;
				ch = tmp_str[i++];
				tmp_value = 0;

				if (ch == '-')
				{
					sign = -1;
					ch = tmp_str[i++];
				}

				while (ch != '\0')
				{
					if ((ch >= '0') && (ch <= '9'))
					{
						tmp_value = tmp_value * 10 + (ch - '0');
					}
					else
					{
						return -1;
					}

					ch = tmp_str[i++];
				}

				value[data_count] = tmp_value * sign;
			}

			data_count ++;
			ch = *src++;

			if (ch == '<')
			{
				ch = *src++;
			}
			else if (ch == '\0')
			{
				;
			}
			else
			{
				return -1;
			}
		}

		switch (data_count)
		{
			case 3:
				value[3] = -1;        // ��������
			case 4:
				value[4] = -1;        // ��������
			case 5:
				value[5] = -1;       // ����˿�
			case 6:
				break;
			default:
				return -1;
		}

		return DATA_TYPE_GPIO;
	}
	else if (off == 4)
	{
		value[0] = 4 >> 2;
		return DATA_EMPTY;
	}
	else
	{
		return -1;
	}
}
u32 randto1k(u32 num)
{
	if (num % 1024)
	{
		debug(" num %d randto1k\n", num);
		return ((num + 1023) / 1024 * 1024);
	}
	else
	{
		return num;
	}
}

#define TIEM_MAIN_MAX       128

int parser_script(void *pbuf, int script_len, FILE *hfile)
{
	int   ret = -1;
	char  *src, *dest = NULL, *tmp_dest;
	int   *dest_data = NULL, *tmp_dest_data, dest_data_index;
	int   line_len, line_status;
	int   new_main_key_flag = 0, sub_value_index = 0;
	script_item_t   item_table[TIEM_MAIN_MAX];
	char  sub_name[128], sub_value[128];
	int   value[8];
	unsigned int i, main_key_index = 0;
	script_head_t   script_head;
	unsigned int original_len = 0;;
	char *align_buf;

	src = (char *)pbuf;
	dest = (char *)malloc(512 * 1024);

	if (!dest)
	{
		printf("fail to get memory for script storage key\n");

		goto _err;
	}

	memset(dest, 0, 512 * 1024);
	tmp_dest = dest;
	dest_data = (int *)malloc(512 * 1024);

	if (!dest_data)
	{
		printf("fail to get memory for script storage data\n");

		goto _err;
	}

	memset(dest_data, 0, 512 * 1024);
	dest_data_index = 0;
	tmp_dest_data = dest_data;

	memset(item_table, 0, TIEM_MAIN_MAX * sizeof(script_item_t));

	while (script_len)
	{
		line_len = _get_line_status(src, &line_status, script_len);
		script_len -= line_len;

		switch (line_status)
		{
			case THIS_LINE_NULL:
			{
				break;
			}

			case THIS_LINE_MAINKEY:
			{
				if (_fill_line_mainkey(src, &item_table[main_key_index]))
				{
					goto _err;
				}

				if (!new_main_key_flag)
				{
					new_main_key_flag = 1;
					item_table[main_key_index].item_offset = 0;
				}
				else
				{
					item_table[main_key_index].item_offset = item_table[main_key_index - 1].item_offset + item_table[main_key_index - 1].item_length * 10;
				}

				main_key_index ++;

				break;
			}

			case THIS_LINE_SUBKEY:
			{
				if (!new_main_key_flag)
				{
					break;
				}

				if (_get_item_value(src, sub_name, sub_value))
				{
					goto _err;
				}

				strcpy(tmp_dest, sub_name);
				tmp_dest += ITEM_MAIN_NAME_MAX;

				ret = _get_str2int(sub_value, value);

				if (ret == -1)
				{
					goto _err;
				}
				else if (ret == DATA_TYPE_SINGLE_WORD)
				{
					*tmp_dest_data = value[0];
					*((unsigned int *)tmp_dest) = dest_data_index;
					tmp_dest += 4;
					*((unsigned int *)tmp_dest) = (1 << 0) | (DATA_TYPE_SINGLE_WORD << 16);
					tmp_dest_data ++;
					dest_data_index ++;
				}
				else if (ret == DATA_EMPTY)
				{
					*((int *)tmp_dest) = dest_data_index;
					tmp_dest += 4;
					*((unsigned int *)tmp_dest) = (value[0] << 0) | (DATA_EMPTY << 16);
					tmp_dest_data += value[0];
					dest_data_index += value[0];
				}
				else if (ret == DATA_TYPE_STRING)
				{
					*((int *)tmp_dest) = dest_data_index;

					if (value[1] == 0)
					{
						strncpy((char *)tmp_dest_data, sub_value + sizeof("string:") - 1, value[0] << 2);
					}
					else if (value[1] == 1)
					{
						strncpy((char *)tmp_dest_data, sub_value, value[0] << 2);
					}
					else
					{
						strncpy((char *)tmp_dest_data, sub_value + 1, value[0] << 2);
					}

					tmp_dest += 4;
					*((unsigned int *)tmp_dest) = (value[0] << 0) | (DATA_TYPE_STRING << 16);
					tmp_dest_data += value[0];
					dest_data_index += value[0];
				}
				else if (ret == DATA_TYPE_GPIO)
				{
					for (i = 0; i < 6; i++)
					{
						*(tmp_dest_data ++) = value[i];
					}

					*((unsigned int *)tmp_dest) = dest_data_index;
					tmp_dest += 4;
					*((unsigned int *)tmp_dest) = (6 << 0) | (DATA_TYPE_GPIO << 16);
					dest_data_index += 6;
				}
				else if (ret == DATA_TYPE_MULTI_WORD)
				{
					;
				}

				sub_value_index ++;
				tmp_dest += 4;
				item_table[main_key_index - 1].item_length ++;

				break;
			}

			default:
			{
				goto _err;
			}
		}

		src += line_len;
	}

	if (!main_key_index)
	{
		goto _err;
	}

	for (i = 0; i < main_key_index; i++)
	{
		item_table[i].item_offset += ((sizeof(script_item_t) * main_key_index) >> 2) + (sizeof(script_head_t) >> 2);
	}

	{
		src = dest;
		i = 0;

		while (i < sub_value_index * 10 * sizeof(int))
		{
			src += ITEM_MAIN_NAME_MAX;
			*(unsigned int *)src += ((sizeof(script_item_t) * main_key_index) >> 2) + (sub_value_index * 10) + (sizeof(script_head_t) >> 2);
			i += 10 * sizeof(int);
			src += 8;
		}
	}

	script_head.item_num = main_key_index;
	original_len   = sizeof(script_head_t) +  sizeof(script_item_t) * main_key_index +
	                 sub_value_index * 10 * sizeof(int) + dest_data_index * sizeof(int);
	script_head.length =  randto1k(original_len);

	script_head.version[0] = 1;
	script_head.version[1] = 2;
	fwrite(&script_head, 1, sizeof(script_head_t), hfile);
	fwrite(item_table, 1, sizeof(script_item_t) * main_key_index, hfile);
	fwrite(dest, 1, sub_value_index * 10 * sizeof(int), hfile);
	fwrite(dest_data, 1, dest_data_index * sizeof(int), hfile);

	if (script_head.length - original_len)
	{
		align_buf = (char *)malloc(script_head.length - original_len);

		if (align_buf == NULL)
		{
			printf("malloc align_buf fail\n");
			goto _err;
		}

		memset(align_buf, 0, script_head.length - original_len);
		fwrite(align_buf, 1, script_head.length - original_len, hfile);
	}

_err:

	if (dest)
	{
		free(dest);
	}

	if (dest_data)
	{
		free(dest_data);
	}

	if (align_buf)
	{
		free(align_buf);
	}

	ret = ((ret >= 0) ? 0 : -1);

	return ret;
}


