#include "multi_ir.h"
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include <stdio.h>
#include <stdlib.h>
#include <errno.h>
#include <string.h>
#include <ctype.h>
#include <dirent.h>
#include <sys/types.h>
#include <sys/ioctl.h>
#include <utils/Log.h>
#include <linux/input.h>

static void keymap_init(struct keymap_t *map)
{
	int i;
	for (i=0; i<KEYCODE_CNT; i++, map++) {
		map->keycode = RESERVE_CODE;
		strcpy(map->name, RESERVE_NAME);
	}
}

#ifdef DEBUG
static void dump_keymap(struct keymap_t *map)
{
	int i;

	for (i=0; i<KEYCODE_CNT; i++, map++) {
		if (!strlen(map->name)) continue;
		printf("key\t%d\t%s\n", map->keycode, map->name);
	}
}

static void dump_mapping_table(struct mapping_table_t *table, struct keymap_t *def)
{
	int i;

	printf("+ identity: 0x%04x\n", table->identity);
	for (i=0; i<KEYCODE_CNT; i++) {
		printf("\t[%3d] --> { %3d, %-20s }\n", i, table->value[i],
					(def + table->value[i])->name);
	}
}
#endif

/**
 * @brief: create a keymap form keylayout file(*.kl)
 * @param: path - path to the target file
 * @param: map - store the result
 * @return:
 */
static int create_keymap_from_file(const char *path, struct keymap_t *map)
{
	int ret = 0;
	FILE *kl_fd = NULL;
	char buf[1024], lable[32], name[MAX_NAME_LEN];
	int keycode;
	char *p;
	struct keymap_t *des;

	kl_fd = fopen(path, "r");
	if (!kl_fd) {
		fprintf(stderr, "open '%s' fail, %s\n", path, strerror(errno));
		return -1;
	}

	while (fgets(buf, 1024, kl_fd)) {
		p = buf;
		while (*p==' ') p++;
		if (*p=='#') continue;

		if (sscanf(buf, "%s %d %s", lable, &keycode, name)!=3) continue;
		if (strcmp(lable, "key")!=0 || keycode < KEYCODE_MIN || keycode > KEYCODE_MAX) continue;

		des = (struct keymap_t *)(map + keycode);
		des->keycode = keycode;
		strcpy(des->name, name);
	}

	if (!feof(kl_fd)) {
		fprintf(stderr, "reading '%s' error, %s\n", path, strerror(errno));
		ret = -1;
	}

	fclose(kl_fd);
	return ret;
}

/**
 * @brief: make a mapping from src to des, the result is store at @table,
 *         the mapping will set to ir driver through ioctl syscall.
 * @param: src
 * @param: des
 * @param: table
 */
static void generate_mapping_table(struct keymap_t* src, struct keymap_t *des, struct mapping_table_t *table)
{
	int i, j;
	struct keymap_t *p;

	memset(table, 0, sizeof(struct mapping_table_t));
	for (i=0; i<KEYCODE_CNT; i++, src++) {
		if (src->keycode==RESERVE_CODE) continue;

		p = des;
		for (j=0; j<KEYCODE_CNT; j++, p++) {
			if (p->keycode!=-1 && strcmp(src->name, p->name)==0) {
				table->value[p->keycode] = src->keycode;

				if (table->powerkey==0 && strcmp(src->name, "POWER")==0) {
					table->powerkey = p->keycode;
				}

				p->keycode = -1;
				break;
			}
		}
	}
}

/**
 * @brief: filter out the valid kaylaout file.
 * @param: name
 * @return: -1 means something error, else the identity.
 */
static int kl_filename_verify(const char *name)
{
	char *p;
	int i, identity;

	/* filename prefix filter */
	if (strncmp(name, FILENAME_PREFIX, strlen(FILENAME_PREFIX)))
		return -1;

	/* filename extensiion filter */
	p = (char *)name + strlen(FILENAME_PREFIX) + 4;
	if (strcmp(p, FILENAME_EXTENSION))
		return -1;

	/* identity filter, 4 hexadecimal number */
	p = (char *)name + strlen(FILENAME_PREFIX);
	for (i=0; i<4; i++) {
		if (isxdigit(*(p+i))==0)
			return -1;
	}

	identity =  strtol(p, &p, 16);

	return identity;
}

int multi_ir_init(const char* defaultLayoutPath, const char* layoutsPath)
{
	int dev_fd, ret = 0;
	char kl_path[512] = {0};
	DIR *dir;
	struct dirent *dirent;
	int identity;
    struct mapping_table_t *mapping_table;
    int pos = 0;

    mapping_tables = (struct mapping_table_t **)calloc(32,sizeof(struct mapping_table_t *));
    if (mapping_tables == NULL) {
        return -1;
    }
    table_size = 32;

	/* create default key map */
	keymap_init(default_keys);
	if (create_keymap_from_file(defaultLayoutPath, default_keys)!=0) {
        return -1;
    }

	#ifdef DEBUG
	dump_keymap(default_keys);
	#endif

	dir = opendir(layoutsPath);
	if (!dir) {
		fprintf(stderr, "Open directory '%s' error, %s\n",
			layoutsPath, strerror(errno));
        return -1;
	}

	while ((dirent=readdir(dir))!=NULL) {
		identity = kl_filename_verify(dirent->d_name);
		if (identity!=-1) {

			sprintf(kl_path, "%s/%s", layoutsPath, dirent->d_name);
			memset(customer_keys, 0, sizeof(struct keymap_t)*(KEYCODE_CNT));
			create_keymap_from_file(kl_path, customer_keys);

            mapping_table = (struct mapping_table_t *)malloc(sizeof(struct mapping_table_t));
            if (mapping_table == NULL) {
                return -1;
            }
			generate_mapping_table(default_keys, customer_keys, mapping_table);
			mapping_table->identity = identity;
			#ifdef DEBUG
			dump_mapping_table(mapping_table, default_keys);
			#endif
            if (invalid_table_size == table_size) {
                mapping_tables = (struct mapping_table_t **)realloc(mapping_tables,sizeof(struct mapping_table_t *)*(table_size + 8));
                if (mapping_tables == NULL) {
                    return -1;
                }
                table_size += 8;
            }
            if (invalid_table_size < table_size) {
                mapping_tables[invalid_table_size] = mapping_table;
                invalid_table_size ++;
            }
		}
	}

	closedir(dir);
	return ret;
}

int convertKey(struct input_event* event) {

    int address = (event->value >> 8) & 0xffff;
    int keycode = event->value & 0xff;
    if (invalid_table_size == 0 || mapping_tables == NULL)  {
        return -1;
    }
    int i = 0;
    for (i = 0; i < invalid_table_size;i++) {
        int pos = (table_pos + i) % invalid_table_size;
        if (address == mapping_tables[pos]->identity ) {
            table_pos = pos;
           return mapping_tables[pos]->value[keycode];
        }
    }
    return -1;
}
