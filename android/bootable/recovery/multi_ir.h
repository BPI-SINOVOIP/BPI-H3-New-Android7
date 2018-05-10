#ifndef IR_KEY_CODE_H
#define IR_KEY_CODE_H

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


#define MAX_NAME_LEN	    (32)
#define KEYCODE_MIN			(0)
#define KEYCODE_MAX			(255)
#define KEYCODE_CNT			(KEYCODE_MAX-KEYCODE_MIN+1)

/*
 A mapping table is mapping from customer ir key layout file(*.kl)
 to default layout file.

 For example:
	in coustomer kl file  -->  key 28  POWER
	in default kl file    -->  key 57  POWER

	then, the mapping will be like that: mapping_table.value[57] = 28
*/
struct mapping_table_t {
	int identity;				/* means ir address */
	int powerkey;				/* powerky to wakeup system */
	int value[KEYCODE_CNT];		/* convert from coustomer keycode to
                               default keycode */
};



#ifdef LOG_TAG
#undef LOG_TAG
#endif
#define LOG_TAG "multi_ir"

#define RESERVE_CODE	0
#define RESERVE_NAME	"!RESERVE"

#undef DEBUG
#define IR_DEV_PATH				"/dev/sunxi-multi-ir"

/*
 * A valid customer ir key layout file *MUST* namd by 'customer_ir_xxxx.kl',
 * which 'xxxx' is the identity(ir address code, in hex),
 * eg. customer_ir_9f00.kl
*/
#define FILENAME_PREFIX			"customer_ir_"
#define FILENAME_EXTENSION		".kl"

#define UI_WAIT_KEY_TIMEOUT_SEC 120
#define KEY_LAYOUT_PATH         "res/keylayout"
#define DEFAULT_KEYLAYOUT_FILE  "res/keylayout/sunxi-ir.kl"

struct keymap_t {
	int keycode;
	char name[MAX_NAME_LEN];
};

static struct keymap_t default_keys[KEYCODE_CNT];
static struct keymap_t customer_keys[KEYCODE_CNT];

static struct mapping_table_t **mapping_tables = NULL;
static int table_size = 0;
static int invalid_table_size = 0;
static int table_pos = 0;

static void keymap_init(struct keymap_t *map);
#ifdef DEBUG
static void dump_keymap(struct keymap_t *map);
#endif

static int create_keymap_from_file(const char *path, struct keymap_t *map);
static void generate_mapping_table(struct keymap_t* src, struct keymap_t *des, struct mapping_table_t *table);
static int kl_filename_verify(const char *name);
int multi_ir_init(const char* defaultLayoutPath, const char* layoutsPath);
int convertKey(struct input_event* event);

#endif
