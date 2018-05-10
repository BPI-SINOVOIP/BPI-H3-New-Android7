/*
************************************************************************************************************************
*                                          Boot rom
*                                         Seucre Boot
*
*                             Copyright(C), 2006-2013, AllWinners Microelectronic Co., Ltd.
*											       All Rights Reserved
*
* File Name   : SBromSW_TOC1.c
*
* Author      : glhuang
*
* Version     : 0.0.1
*
* Date        : 2014.03.07
*
* Description :
*
* Others      : None at present.
*
*
* History     :
*
*  <Author>        <time>       <version>      <description>
*
* glhuang       2014.03.07       0.0.1        build the file
*
************************************************************************************************************************
*/
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "libboot.h"
#include "sunxi_boot_api.h"
#include "private_boot0.h"
#include "private_uboot.h"
#include "private_toc.h"
#include "sbrom_toc.h"
#include "spare_head.h"

#define DEBUG

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
static struct sbrom_toc1_head_info  *toc1_head = NULL;
static struct sbrom_toc1_item_info  *toc1_item = NULL;

int sunxi_buffer_read(u32 start_sector, u32 blkcnt, void *buff)
{
	memcpy(buff, (void *)((char *)toc1_head + 512 * start_sector), 512 * blkcnt);
	return blkcnt;
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
s32 toc1_init(BufferExtractCookie* cookie)
{
	toc1_head = (struct sbrom_toc1_head_info *)cookie->buffer;
	toc1_item = (struct sbrom_toc1_item_info *)(cookie->buffer + sizeof(struct sbrom_toc1_head_info));
 
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
int toc1_item_traverse(void)
{
	 int i;
	 struct sbrom_toc1_head_info  *p_head = toc1_head;
	 struct sbrom_toc1_item_info  *item_head = toc1_item;
#if defined(BOOT_DEBUG)
	for(i=0;i<p_head->items_nr;i++,item_head++)
	{
		printf("\n*******************TOC1 Item Message*************************\n");
		printf("Entry_name        = %s\n",   item_head->name);
		printf("Entry_data_offset = 0x%x\n", item_head->data_offset);
		printf("Entry_data_len    = 0x%x\n", item_head->data_len);

		printf("encrypt	          = 0x%x\n", item_head->encrypt);
		printf("Entry_type        = 0x%x\n", item_head->type);
		printf("run_addr          = 0x%x\n", item_head->run_addr);
		printf("index             = 0x%x\n", item_head->index);

		printf("Entry_call        = 0x%x\n", item_head->run_addr);
		printf("Entry_end         = 0x%x\n", item_head->end);
		printf("***************************************************************\n\n");
	}
#endif

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
uint toc1_item_read(struct sbrom_toc1_item_info *p_toc_item, void * p_dest, u32 buff_len)
{
	u32 to_read_blk_start = 0;
	u32 to_read_blk_sectors = 0;
	s32 ret = 0;

	if( buff_len  < p_toc_item->data_len )
	{
		printf("PANIC : Toc1_item_read() error --1--,buff error\n");

		return 0;
	}

	to_read_blk_start   = (p_toc_item->data_offset)>>9;
	to_read_blk_sectors = (p_toc_item->data_len + 0x1ff)>>9;

	ret = sunxi_buffer_read(to_read_blk_start,to_read_blk_sectors, p_dest);
	if(ret != to_read_blk_sectors)
	{
		printf("PANIC: toc1_item_read() error --2--, read error\n");

		return 0;
	}

	return ret * 512;
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
uint toc1_item_read_rootcertif(void * p_dest, u32 buff_len)
{
	u32 to_read_blk_start = 0;
	u32 to_read_blk_sectors = 0;
	s32 ret = 0;
	struct sbrom_toc1_item_info *p_toc_item = toc1_item;

	if( buff_len  < p_toc_item->data_len )
	{
		printf("PANIC : toc1_item_read_rootcertif() error --1--,buff error\n");

		return 0;
	}

	to_read_blk_start   = (p_toc_item->data_offset)>>9;
	to_read_blk_sectors = (p_toc_item->data_len + 0x1ff)>>9;

	ret = sunxi_buffer_read(to_read_blk_start, to_read_blk_sectors, p_dest);

	if(ret != to_read_blk_sectors)
	{
		printf("PANIC: toc1_item_read_rootcertif() error --2--, read error\n");

		return 0;
	}

	return ret*512;
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
*    return        :  成功：当前索引号>0   失败：找不到索引号，返回-1
*
*    note          :  自动获取下一个索引号对应的所有成员
*                     如果失败，通常是索引到了尽头
*
************************************************************************************************************
*/
int toc1_item_probe_next(sbrom_toc1_item_group *item_group)
{
	struct sbrom_toc1_head_info  *p_head = toc1_head;
	struct sbrom_toc1_item_info  *item_head;
	char  *item_name = NULL;
	int    i;

	for(i=1; i<p_head->items_nr; i++)
	{
		item_head = toc1_item + i;

		if(item_name != NULL)
		{
			if(!strcmp(item_name, item_head->name))
			{
				if(item_head->type == TOC_ITEM_ENTRY_TYPE_BIN_CERTIF)
				{
					if(item_group->bin_certif == NULL)
					{
						item_group->bin_certif = item_head;
					}
					else
					{
						printf("two bin certif with the same name %s\n", item_name);

						return -1;
					}
				}
				else if(item_head->type == TOC_ITEM_ENTRY_TYPE_BIN)
				{
					if(item_group->binfile == NULL)
					{
						item_group->binfile = item_head;
					}
					else
					{
						printf("two bin files with the same name %s\n", item_name);

						return -1;
					}
				}
				else
				{
					printf("unknown item type\n");

					return -1;
				}
				item_head->reserved[0] = 1;

				return 1;
			}
		}
		else if(!item_head->reserved[0])
		{
			if(item_head->type == TOC_ITEM_ENTRY_TYPE_BIN_CERTIF)
			{
				item_group->bin_certif = item_head;
			}
			else if(item_head->type == TOC_ITEM_ENTRY_TYPE_BIN)
			{
				item_group->binfile = item_head;
			}
			else if(item_head->type == TOC_ITEM_ENTRY_TYPE_LOGO)
			{
				item_head->reserved[0] = 1;
				item_name = item_head->name;
				item_group->binfile = item_head;

				return 1;
			}
			else
			{
				printf("unknown item type\n");

				return -1;
			}
			item_head->reserved[0] = 1;
			item_name = item_head->name;
		}
	}

	if(item_group->bin_certif == NULL)
	{
		for(i=1; i<p_head->items_nr; i++) //all item_head->reserved[0]=0,to ensure checksum is correct
		{
			item_head = toc1_item + i;
			item_head->reserved[0]=0;
		}
		return 0;
	}
	else
		return 1;
}
