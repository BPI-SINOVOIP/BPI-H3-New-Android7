/******************************************************************************
 *
 *  Copyright (C) 2009-2012 Realtek Corporation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at:
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

/******************************************************************************
 *
 *  Filename:      hardware.c
 *
 *  Description:   Contains controller-specific functions, like
 *                      firmware patch download
 *                      low power mode operations
 *
 ******************************************************************************/

#define LOG_TAG "bt_hwcfg"

#include <utils/Log.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <signal.h>
#include <time.h>
#include <errno.h>
#include <fcntl.h>
#include <dirent.h>
#include <ctype.h>
#include <cutils/properties.h>
#include <stdlib.h>
#include "bt_hci_bdroid.h"
#include "bt_vendor_rtk.h"
#include "userial.h"
#include "userial_vendor.h"
#include "upio.h"
#include <unistd.h>
#include <endian.h>
#include <byteswap.h>

#include "bt_vendor_lib.h"


/******************************************************************************
**  Constants &  Macros
******************************************************************************/
#define RTK_VERSION "4.0.0"

#ifndef BTHW_DBG
#define BTHW_DBG FALSE
#endif

#if (BTHW_DBG == TRUE)
#define BTHWDBG(param, ...) {ALOGD(param, ## __VA_ARGS__);}
#else
#define BTHWDBG(param, ...) {}
#endif

#ifndef USE_CONTROLLER_BDADDR
#define USE_CONTROLLER_BDADDR TRUE
#endif

#if __BYTE_ORDER == __LITTLE_ENDIAN
#define cpu_to_le16(d)  (d)
#define cpu_to_le32(d)  (d)
#define le16_to_cpu(d)  (d)
#define le32_to_cpu(d)  (d)
#elif __BYTE_ORDER == __BIG_ENDIAN
#define cpu_to_le16(d)  bswap_16(d)
#define cpu_to_le32(d)  bswap_32(d)
#define le16_to_cpu(d)  bswap_16(d)
#define le32_to_cpu(d)  bswap_32(d)
#else
#error "Unknown byte order"
#endif

#define FIRMWARE_DIRECTORY  "/system/etc/firmware/%s"
#define BT_CONFIG_DIRECTORY "/system/etc/firmware/%s"
#define PATCH_DATA_FIELD_MAX_SIZE       252
#define RTK_VENDOR_CONFIG_MAGIC         0x8723ab55
#define RTK_PATCH_LENGTH_MAX            24576   //24*1024

struct rtk_bt_vendor_config_entry{
    uint16_t offset;
    uint8_t entry_len;
    uint8_t entry_data[0];
} __attribute__ ((packed));

struct rtk_bt_vendor_config{
    uint32_t signature;
    uint16_t data_len;
    struct rtk_bt_vendor_config_entry entry[0];
} __attribute__ ((packed));

#define HCI_CMD_MAX_LEN             258

#define HCI_RESET                               0x0C03
#define HCI_READ_LMP_VERSION                    0x1001
#define HCI_VSC_H5_INIT                         0xFCEE
#define HCI_VSC_UPDATE_BAUDRATE                 0xFC17
#define HCI_VSC_DOWNLOAD_FW_PATCH               0xFC20
#define HCI_VSC_READ_ROM_VERSION                0xFC6D
#define HCI_VSC_READ_CHIP_TYPE                  0xFC61

#define HCI_VERSION_MASK_10     (1<<0)     //Bluetooth Core Spec 1.0b
#define HCI_VERSION_MASK_11     (1<<1)     //Bluetooth Core Spec 1.1
#define HCI_VERSION_MASK_12     (1<<2)     //Bluetooth Core Spec 1.2
#define HCI_VERSION_MASK_20     (1<<3)     //Bluetooth Core Spec 2.0+EDR
#define HCI_VERSION_MASK_21     (1<<4)     //Bluetooth Core Spec 2.1+EDR
#define HCI_VERSION_MASK_30     (1<<5)     //Bluetooth Core Spec 3.0+HS
#define HCI_VERSION_MASK_40     (1<<6)     //Bluetooth Core Spec 4.0
#define HCI_VERSION_MASK_41     (1<<7)     //Bluetooth Core Spec 4.1
#define HCI_VERSION_MASK_42     (1<<8)     //Bluetooth Core Spec 4.2
#define HCI_VERSION_MASK_ALL    (0xFFFFFFFF)

#define HCI_REVISION_MASK_ALL   (0xFFFFFFFF)

#define LMP_SUBVERSION_NONE     (0x0)
#define LMPSUBVERSION_8723a     (0x1200)

#define CHIPTYPE_NONE           (0x1F)      //Chip Type's range: 0x0 ~ 0xF
#define CHIP_TYPE_MASK_ALL      (0xFFFFFFFF)

#define PROJECT_ID_MASK_ALL     (0xFFFFFFFF)    // temp used for unknow project id for a new chip

#define PATCH_OPTIONAL_MATCH_FLAG_CHIPTYPE   (0x1)

#define CONFIG_MAC_OFFSET_GEN_1_2       (0x3C)      //MAC's OFFSET in config/efuse for realtek generation 1~2 bluetooth chip
#define CONFIG_MAC_OFFSET_GEN_3PLUS     (0x44)      //MAC's OFFSET in config/efuse for rtk generation 3+ bluetooth chip

#define HCI_EVT_CMD_CMPL_OPCODE_OFFSET          (3)     //opcode's offset in COMMAND Completed Event
#define HCI_EVT_CMD_CMPL_STATUS_OFFSET          (5)     //status's offset in COMMAND Completed Event

#define HCI_EVT_CMD_CMPL_OP1001_HCI_VERSION_OFFSET     (6)     //HCI_Version's offset in COMMAND Completed Event for OpCode 0x1001(Read Local Version Information Command)
#define HCI_EVT_CMD_CMPL_OP1001_HCI_REVISION_OFFSET     (7)     //HCI_Revision's offset in COMMAND Completed Event for OpCode 0x1001(Read Local Version Information Command)
#define HCI_EVT_CMD_CMPL_OP1001_LMP_SUBVERSION_OFFSET  (12)    //LMP Subversion's offset in COMMAND Completed Event for OpCode 0x1001(Read Local Version Information Command)
#define HCI_EVT_CMD_CMPL_OP0C14_LOCAL_NAME_OFFSET      (6)     //Local Name's offset in COMMAND Completed Event for OpCode 0x0C14(Read Local Name Command)
#define HCI_EVT_CMD_CMPL_OP1009_BDADDR_OFFSET    (6)     //BD_ADDR's offset in COMMAND Completed Event for OpCode 0x1009(Read BD_ADDR Command)
#define HCI_EVT_CMD_CMPL_OPFC6D_EVERSION_OFFSET        (6)  //eversion's offset in COMMAND Completed Event for OpCode 0xfc6d(Read eVERSION Vendor Command)
#define HCI_EVT_CMD_CMPL_OPFC61_CHIPTYPE_OFFSET        (6)  //chip type's offset in COMMAND Completed Event for OpCode 0xfc61(Read ChipType Vendor Command)

//#define UPDATE_BAUDRATE_CMD_PARAM_SIZE          (6)
#define HCI_CMD_PREAMBLE_SIZE                   (3)
#define HCI_CMD_READ_CHIP_TYPE_SIZE             (5)
//#define HCD_REC_PAYLOAD_LEN_BYTE                (2)
#define BD_ADDR_LEN                             (6)
//#define LOCAL_NAME_BUFFER_LEN                   (32)
//#define LOCAL_BDADDR_PATH_BUFFER_LEN            (256)

#define H5_SYNC_REQ_SIZE (2)
#define H5_SYNC_RESP_SIZE (2)
#define H5_CONF_REQ_SIZE (3)
#define H5_CONF_RESP_SIZE (2)

#define STREAM_TO_UINT16(u16, p) {u16 = ((uint16_t)(*(p)) + (((uint16_t)(*((p) + 1))) << 8)); (p) += 2;}
#define UINT16_TO_STREAM(p, u16) {*(p)++ = (uint8_t)(u16); *(p)++ = (uint8_t)((u16) >> 8);}
#define UINT32_TO_STREAM(p, u32) {*(p)++ = (uint8_t)(u32); *(p)++ = (uint8_t)((u32) >> 8); *(p)++ = (uint8_t)((u32) >> 16); *(p)++ = (uint8_t)((u32) >> 24);}
#define STREAM_TO_UINT32(u32, p) {u32 = (((uint32_t)(*(p))) + ((((uint32_t)(*((p) + 1)))) << 8) + ((((uint32_t)(*((p) + 2)))) << 16) + ((((uint32_t)(*((p) + 3)))) << 24)); (p) += 4;}
#define UINT8_TO_STREAM(p, u8)   {*(p)++ = (uint8_t)(u8);}
/******************************************************************************
**  Local type definitions
******************************************************************************/

/* Hardware Configuration State */
enum {
    HW_CFG_H5_INIT = 1,
    HW_CFG_READ_LOCAL_VER,
    HW_CFG_READ_ECO_VER,   //eco version
    HW_CFG_READ_CHIP_TYPE,
    HW_CFG_START,
    HW_CFG_SET_UART_BAUD_HOST,//change FW baudrate
    HW_CFG_SET_UART_BAUD_CONTROLLER,//change Host baudrate
    HW_CFG_SET_UART_HW_FLOW_CONTROL,
    HW_CFG_DL_FW_PATCH
};

/* h/w config control block */
typedef struct
{
    uint32_t    baudrate;
    uint16_t    lmp_subversion;
    uint8_t     state;          /* Hardware configuration state */
    uint8_t     eversion;
    uint32_t    project_id_mask;
    uint8_t     hci_version;
    uint8_t     hci_revision;
    uint8_t     chip_type;
    uint8_t     dl_fw_flag;
    int         fw_len;          /* FW patch file len */
    int         config_len;      /* Config patch file len */
    int         total_len;       /* FW & config extracted buf len */
    uint8_t     *fw_buf;         /* FW patch file buf */
    uint8_t     *config_buf;     /* Config patch file buf */
    uint8_t     *total_buf;      /* FW & config extracted buf */
    uint8_t     patch_frag_cnt;  /* Patch fragment count download */
    uint8_t     patch_frag_idx;  /* Current patch fragment index */
    uint8_t     patch_frag_len;  /* Patch fragment length */
    uint8_t     patch_frag_tail; /* Last patch fragment length */
    uint8_t     hw_flow_cntrl;   /* Uart flow control, bit7:set, bit0:enable */
} bt_hw_cfg_cb_t;

/* low power mode parameters */
typedef struct
{
    uint8_t sleep_mode;                     /* 0(disable),1(UART),9(H5) */
    uint8_t host_stack_idle_threshold;      /* Unit scale 300ms/25ms */
    uint8_t host_controller_idle_threshold; /* Unit scale 300ms/25ms */
    uint8_t bt_wake_polarity;               /* 0=Active Low, 1= Active High */
    uint8_t host_wake_polarity;             /* 0=Active Low, 1= Active High */
    uint8_t allow_host_sleep_during_sco;
    uint8_t combine_sleep_mode_and_lpm;
    uint8_t enable_uart_txd_tri_state;      /* UART_TXD Tri-State */
    uint8_t sleep_guard_time;               /* sleep guard time in 12.5ms */
    uint8_t wakeup_guard_time;              /* wakeup guard time in 12.5ms */
    uint8_t txd_config;                     /* TXD is high in sleep state */
    uint8_t pulsed_host_wake;               /* pulsed host wake if mode = 1 */
} bt_lpm_param_t;

/******************************************************************************
**  Externs
******************************************************************************/

void hw_config_cback(void *p_evt_buf);
extern uint8_t vnd_local_bd_addr[BD_ADDR_LEN];

/******************************************************************************
**  Static variables
******************************************************************************/
static bt_hw_cfg_cb_t hw_cfg_cb;

//#define BT_CHIP_PROBE_SIMULATION
#ifdef BT_CHIP_PROBE_SIMULATION
static bt_hw_cfg_cb_t hw_cfg_test;
struct bt_chip_char{
    uint16_t    lmp_subversion;
    uint8_t     hci_version;
    uint8_t     hci_revision;
    uint8_t     chip_type;
};
struct bt_chip_char bt_chip_chars[] = \
{
        {0x8723, 0x4, 0xb, CHIPTYPE_NONE},      //8703as
        {0x8723, 0x6, 0xb, CHIPTYPE_NONE},      //8723bs
        {0x8703, 0x4, 0xd, 0x7},                //8703bs
        {0x8703, 0x6, 0xb, 0x3},                //8723cs-cg
        {0x8703, 0x6, 0xb, 0x4},                //8723cs-vf
        {0x8703, 0x6, 0xb, 0x5},                //8723cs-xx
        {0x8723, 0x6, 0xd, CHIPTYPE_NONE},      //8723ds
};
#endif
static bt_lpm_param_t lpm_param =
{
    LPM_SLEEP_MODE,
    LPM_IDLE_THRESHOLD,
    LPM_HC_IDLE_THRESHOLD,
    LPM_BT_WAKE_POLARITY,
    LPM_HOST_WAKE_POLARITY,
    LPM_ALLOW_HOST_SLEEP_DURING_SCO,
    LPM_COMBINE_SLEEP_MODE_AND_LPM,
    LPM_ENABLE_UART_TXD_TRI_STATE,
    0,  /* not applicable */
    0,  /* not applicable */
    0,  /* not applicable */
    LPM_PULSED_HOST_WAKE
};

//signature: realtech
const uint8_t RTK_EPATCH_SIGNATURE[8]={0x52,0x65,0x61,0x6C,0x74,0x65,0x63,0x68};
//Extension Section IGNATURE:0x77FD0451
const uint8_t EXTENSION_SECTION_SIGNATURE[4]={0x51,0x04,0xFD,0x77};

typedef struct {
    uint16_t    lmp_subversion;
    uint32_t     hci_version_mask;
    uint32_t     hci_revision_mask;
    uint32_t     chip_type_mask;
    uint32_t     project_id_mask;
    char        *patch_name;
    char        *config_name;
    uint16_t     mac_offset;
} patch_info;

static patch_info patch_table[] = {
/*    lmp_subv                      hci_version_mask                    hci_revision_mask                chip_type_mask              project_id_mask                 fw name                                 config name                         mac offset       */
    {0x1200,            HCI_VERSION_MASK_ALL,    HCI_REVISION_MASK_ALL, CHIP_TYPE_MASK_ALL,  1<<0,                  "rtl8723as_fw",         "rtl8723as_config",     CONFIG_MAC_OFFSET_GEN_1_2},    //Rtl8723AS

    {0x8723,            ~(HCI_VERSION_MASK_21),  ~(1<<0xd),             CHIP_TYPE_MASK_ALL,  1<<1,                  "rtl8723bs_fw",         "rtl8723bs_config",     CONFIG_MAC_OFFSET_GEN_1_2},     //Rtl8723BS
//    {0x8723,            ~(HCI_VERSION_MASK_21),  ~(1<<0xd),             CHIP_TYPE_MASK_ALL,  1<<1,                  "rtl8723bs_VQ0_fw",     "rtl8723bs_VQ0_config", CONFIG_MAC_OFFSET_GEN_1_2}, //Rtl8723BS_VQ0
    {0x8821,            HCI_VERSION_MASK_ALL,    HCI_REVISION_MASK_ALL, CHIP_TYPE_MASK_ALL,  1<<2,                  "rtl8821as_fw",         "rtl8821as_config",     CONFIG_MAC_OFFSET_GEN_1_2},     //Rtl8821AS
    {0x8761,            HCI_VERSION_MASK_ALL,    HCI_REVISION_MASK_ALL, CHIP_TYPE_MASK_ALL,  1<<3,                  "rtl8761at_fw",         "rtl8761at_config",     CONFIG_MAC_OFFSET_GEN_1_2},     //Rtl8761AW
    {0x8723,            HCI_VERSION_MASK_21,     HCI_REVISION_MASK_ALL, CHIP_TYPE_MASK_ALL,  1<<4,                  "rtl8703as_fw",         "rtl8703as_config",     CONFIG_MAC_OFFSET_GEN_1_2},     //Rtl8703AS

    {0x8703,            HCI_VERSION_MASK_ALL,    HCI_REVISION_MASK_ALL, 1<<7,                1<<6,                  "rtl8703bs_fw",         "rtl8703bs_config",     CONFIG_MAC_OFFSET_GEN_3PLUS},     //Rtl8703BS
    {0x8703,            HCI_VERSION_MASK_ALL,    HCI_REVISION_MASK_ALL, 1<<5,                1<<7,                  "rtl8723cs_xx_fw",      "rtl8723cs_xx_config",  CONFIG_MAC_OFFSET_GEN_3PLUS},     //rtl8723cs_xx
    {0x8703,            HCI_VERSION_MASK_ALL,    HCI_REVISION_MASK_ALL, 1<<3,                1<<7,                  "rtl8723cs_cg_fw",      "rtl8723cs_cg_config",  CONFIG_MAC_OFFSET_GEN_3PLUS},     //rtl8723cs_cg
    {0x8703,            HCI_VERSION_MASK_ALL,    HCI_REVISION_MASK_ALL, 1<<4,                1<<7,                  "rtl8723cs_vf_fw",      "rtl8723cs_vf_config",  CONFIG_MAC_OFFSET_GEN_3PLUS},     //rtl8723cs_vf
    {0x8822,            HCI_VERSION_MASK_ALL,    HCI_REVISION_MASK_ALL, CHIP_TYPE_MASK_ALL,  1<<8,                  "rtl8822bs_fw",         "rtl8822bs_config",     CONFIG_MAC_OFFSET_GEN_3PLUS},     //Rtl8822BS

    {0x8723,            HCI_VERSION_MASK_ALL,    (1<<0xd),              CHIP_TYPE_MASK_ALL,  1<<9,                  "rtl8723ds_fw",         "rtl8723ds_config",     CONFIG_MAC_OFFSET_GEN_3PLUS}, //Rtl8723ds
/*  todo: RTL8821CS */
/*  todo: RTL8703CS */

    {LMP_SUBVERSION_NONE,HCI_VERSION_MASK_ALL,   HCI_REVISION_MASK_ALL, CHIP_TYPE_MASK_ALL, PROJECT_ID_MASK_ALL,    "rtl_none_fw",          "rtl_none_config",      CONFIG_MAC_OFFSET_GEN_1_2}
};

struct rtk_epatch_entry{
    uint16_t chip_id;
    uint16_t patch_length;
    uint32_t patch_offset;
    uint32_t svn_version;
    uint32_t coex_version;
} __attribute__ ((packed));

struct rtk_epatch{
    uint8_t signature[8];
    uint32_t fw_version;
    uint16_t number_of_patch;
    struct rtk_epatch_entry entry[0];
} __attribute__ ((packed));


int check_match_state(bt_hw_cfg_cb_t *cfg,uint32_t mask)
{
    patch_info  *patch_entry;
    int res = 0;

    for(patch_entry = patch_table; patch_entry->lmp_subversion != LMP_SUBVERSION_NONE; patch_entry++) {
        if(patch_entry->lmp_subversion != cfg->lmp_subversion)
            continue;
        if((patch_entry->hci_version_mask!=HCI_VERSION_MASK_ALL)&&((patch_entry->hci_version_mask&(1<<cfg->hci_version))==0))
             continue;
        if((patch_entry->hci_revision_mask!=HCI_REVISION_MASK_ALL)&&((patch_entry->hci_revision_mask&(1<<cfg->hci_revision))==0))
             continue;
        if((mask&PATCH_OPTIONAL_MATCH_FLAG_CHIPTYPE)&&(patch_entry->chip_type_mask != CHIP_TYPE_MASK_ALL)&&((patch_entry->chip_type_mask&(1<<cfg->chip_type))==0))
             continue;
        res++;
    }
    ALOGI( "check_match_state return %d(cfg->lmp_subversion:0x%x cfg->hci_vesion:0x%x cfg->hci_revision:0x%x cfg->chip_type:0x%x mask:%08x)\n",
            res, cfg->lmp_subversion, cfg->hci_version, cfg->hci_revision, cfg->chip_type, mask);
    return res;
}
patch_info* get_patch_entry(bt_hw_cfg_cb_t *cfg)
{
    patch_info  *patch_entry;

    ALOGI("get_patch_entry(lmp_subversion:0x%x hci_vesion:0x%x cfg->hci_revision:0x%x chip_type:0x%x)\n",
            cfg->lmp_subversion, cfg->hci_version, cfg->hci_revision, cfg->chip_type);
    for(patch_entry = patch_table; patch_entry->lmp_subversion != LMP_SUBVERSION_NONE; patch_entry++) {
        if(patch_entry->lmp_subversion != cfg->lmp_subversion)
            continue;
        if((patch_entry->hci_version_mask != HCI_VERSION_MASK_ALL)&&((patch_entry->hci_version_mask&(1<<cfg->hci_version)) == 0))
             continue;
        if((patch_entry->hci_revision_mask != HCI_REVISION_MASK_ALL)&&((patch_entry->hci_revision_mask&(1<<cfg->hci_revision)) == 0))
             continue;
        if((patch_entry->chip_type_mask != CHIP_TYPE_MASK_ALL)&&((patch_entry->chip_type_mask&(1<<cfg->chip_type)) == 0))
             continue;
        break;
    }
    ALOGI("get_patch_entry return(patch_name:%s config_name:%s mac_offset:0x%x)\n",
            patch_entry->patch_name, patch_entry->config_name, patch_entry->mac_offset);
    return patch_entry;
}

/*******************************************************************************
**
** Function        ms_delay
**
** Description     sleep unconditionally for timeout milliseconds
**
** Returns         None
**
*******************************************************************************/
void ms_delay (uint32_t timeout)
{
    struct timespec delay;
    int err;

    if (timeout == 0)
        return;

    delay.tv_sec = timeout / 1000;
    delay.tv_nsec = 1000 * 1000 * (timeout%1000);

    /* [u]sleep can't be used because it uses SIGALRM */
    do {
        err = nanosleep(&delay, &delay);
    } while (err < 0 && errno ==EINTR);
}

/*******************************************************************************
**
** Function        line_speed_to_userial_baud
**
** Description     helper function converts line speed number into USERIAL baud
**                 rate symbol
**
** Returns         unit8_t (USERIAL baud symbol)
**
*******************************************************************************/
uint8_t line_speed_to_userial_baud(uint32_t line_speed)
{
    uint8_t baud;

    if (line_speed == 4000000)
        baud = USERIAL_BAUD_4M;
    else if (line_speed == 3000000)
        baud = USERIAL_BAUD_3M;
    else if (line_speed == 2000000)
        baud = USERIAL_BAUD_2M;
    else if (line_speed == 1500000)
        baud = USERIAL_BAUD_1_5M;
    else if (line_speed == 1000000)
        baud = USERIAL_BAUD_1M;
    else if (line_speed == 921600)
        baud = USERIAL_BAUD_921600;
    else if (line_speed == 460800)
        baud = USERIAL_BAUD_460800;
    else if (line_speed == 230400)
        baud = USERIAL_BAUD_230400;
    else if (line_speed == 115200)
        baud = USERIAL_BAUD_115200;
    else if (line_speed == 57600)
        baud = USERIAL_BAUD_57600;
    else if (line_speed == 19200)
        baud = USERIAL_BAUD_19200;
    else if (line_speed == 9600)
        baud = USERIAL_BAUD_9600;
    else if (line_speed == 1200)
        baud = USERIAL_BAUD_1200;
    else if (line_speed == 600)
        baud = USERIAL_BAUD_600;
    else
    {
        ALOGE( "userial vendor: unsupported baud speed %d", line_speed);
        baud = USERIAL_BAUD_115200;
    }

    return baud;
}

typedef struct _baudrate_ex
{
    uint32_t rtk_speed;
    uint32_t uart_speed;
}baudrate_ex;

baudrate_ex baudrates[] =
{
    {0x00006004, 921600},
    {0x05F75004, 921600},//RTL8723BS
    {0x00004003, 1500000},
    {0x04928002, 1500000},//RTL8723BS
    {0x00005002, 2000000},//same as RTL8723AS
    {0x00008001, 3000000},
    {0x04928001, 3000000},//RTL8723BS
    {0x06B58001, 3000000},//add RTL8703as
    {0x00007001, 3500000},
    {0x052A6001, 3500000},//RTL8723BS
    {0x00005001, 4000000},//same as RTL8723AS
    {0x0000701d, 115200},
    {0x0252C014, 115200}//RTL8723BS
};

/**
* Change realtek Bluetooth speed to uart speed. It is matching in the struct baudrates:
*
* @code
* baudrate_ex baudrates[] =
* {
*   {0x7001, 3500000},
*   {0x6004, 921600},
*   {0x4003, 1500000},
*   {0x5001, 4000000},
*   {0x5002, 2000000},
*   {0x8001, 3000000},
*   {0x701d, 115200}
* };
* @endcode
*
* If there is no match in baudrates, uart speed will be set as #115200.
*
* @param rtk_speed realtek Bluetooth speed
* @param uart_speed uart speed
*
*/
static void rtk_speed_to_uart_speed(uint32_t rtk_speed, uint32_t* uart_speed)
{
    *uart_speed = 115200;

    uint8_t i;
    for (i=0; i< sizeof(baudrates)/sizeof(baudrate_ex); i++)
    {
        if (baudrates[i].rtk_speed == rtk_speed)
        {
            *uart_speed = baudrates[i].uart_speed;
            return;
        }
    }
    return;
}

/**
* Change uart speed to realtek Bluetooth speed. It is matching in the struct baudrates:
*
* @code
* baudrate_ex baudrates[] =
* {
*   {0x7001, 3500000},
*   {0x6004, 921600},
*   {0x4003, 1500000},
*   {0x5001, 4000000},
*   {0x5002, 2000000},
*   {0x8001, 3000000},
*   {0x701d, 115200}
* };
* @endcode
*
* If there is no match in baudrates, realtek Bluetooth speed will be set as #0x701D.
*
* @param uart_speed uart speed
* @param rtk_speed realtek Bluetooth speed
*
*/
static inline void uart_speed_to_rtk_speed(uint32_t uart_speed, uint32_t* rtk_speed)
{
    *rtk_speed = 0x701D;

    unsigned int i;
    for (i=0; i< sizeof(baudrates)/sizeof(baudrate_ex); i++)
    {
      if (baudrates[i].uart_speed == uart_speed)
      {
          *rtk_speed = baudrates[i].rtk_speed;
          return;
      }
    }
    return;
}


/*******************************************************************************
**
** Function         hw_config_set_bdaddr
**
** Description      Program controller's Bluetooth Device Address
**
** Returns          TRUE, if valid address is sent
**                  FALSE, otherwise
**
*******************************************************************************/
static uint8_t hw_config_set_controller_baudrate(HC_BT_HDR *p_buf, uint32_t baudrate)
{
    uint8_t retval = FALSE;
    uint8_t *p = (uint8_t *) (p_buf + 1);

    UINT16_TO_STREAM(p, HCI_VSC_UPDATE_BAUDRATE);
    *p++ = 4; /* parameter length */
    UINT32_TO_STREAM(p, baudrate);

    p_buf->len = HCI_CMD_PREAMBLE_SIZE + 4;

    retval = bt_vendor_cbacks->xmit_cb(HCI_VSC_UPDATE_BAUDRATE, p_buf, \
                                 hw_config_cback);

    return (retval);
}

uint32_t rtk_parse_config_file(unsigned char** config_buf, size_t* filelen, uint8_t bt_addr[6], uint16_t mac_offset)
{
    struct rtk_bt_vendor_config* config = (struct rtk_bt_vendor_config*) *config_buf;
    uint16_t config_len = le16_to_cpu(config->data_len), temp = 0;
    struct rtk_bt_vendor_config_entry* entry = config->entry;
    unsigned int i = 0;
    uint32_t baudrate = 0;
    uint32_t config_has_bdaddr = 0;
    uint8_t *p;

    if (le32_to_cpu(config->signature) != RTK_VENDOR_CONFIG_MAGIC)
    {
        ALOGE("config signature magic number(0x%x) is not set to RTK_VENDOR_CONFIG_MAGIC", config->signature);
        return 0;
    }

    if (config_len != *filelen - sizeof(struct rtk_bt_vendor_config))
    {
        ALOGE("config len(0x%x) is not right(0x%x)", config_len, *filelen-sizeof(struct rtk_bt_vendor_config));
        return 0;
    }

    for (i=0; i<config_len;)
    {
        switch(le16_to_cpu(entry->offset))
        {
            case 0xc:
            {
                p = (uint8_t *)entry->entry_data;
                STREAM_TO_UINT32(baudrate, p);
                if (entry->entry_len >= 12)
                {
                    hw_cfg_cb.hw_flow_cntrl |= 0x80; /* bit7 set hw flow control */
                    if (entry->entry_data[12] & 0x04) /* offset 0x18, bit2 */
                        hw_cfg_cb.hw_flow_cntrl |= 1; /* bit0 enable hw flow control */
                }

                ALOGI("config baud rate to :0x%08x, hwflowcontrol:0x%x, 0x%x", baudrate, entry->entry_data[12], hw_cfg_cb.hw_flow_cntrl);
                break;
            }
            default:
#if (USE_CONTROLLER_BDADDR == FALSE)
                if(le16_to_cpu(entry->offset) == mac_offset)
                {
                    config_has_bdaddr = 1;
                    int j=0;
                    for (j=0; j<entry->entry_len; j++)
                        entry->entry_data[j] = bt_addr[entry->entry_len - 1- j];
                    ALOGI("rtk_parse_config_file: DO NOT USE_CONTROLLER_BDADDR, config has bdaddr");
                    break;
                }
#endif
                ALOGI("config offset(0x%x),length(0x%x)", entry->offset, entry->entry_len);
                break;
        }
        temp = entry->entry_len + sizeof(struct rtk_bt_vendor_config_entry);
        i += temp;
        entry = (struct rtk_bt_vendor_config_entry*)((uint8_t*)entry + temp);
    }
#if(USE_CONTROLLER_BDADDR == FALSE)
    if(!config_has_bdaddr)
    {
        ALOGI("rtk_parse_config_file: DO NOT USE_CONTROLLER_BDADDR, config has no bdaddr");
        ALOGI("rtk_parse_config_file : CONFIG_ADDR is: %02X:%02X:%02X:%02X:%02X:%02X BT_MAC_OFFSET:%d",
        bt_addr[0], bt_addr[1],
        bt_addr[2], bt_addr[3],
        bt_addr[4], bt_addr[5],
        mac_offset);
        *config_buf = realloc(*config_buf, *filelen+9);
        ((struct rtk_bt_vendor_config*)*config_buf)->data_len += 9;

        *((char*)*config_buf + *filelen) = mac_offset;
        *((char*)*config_buf + *filelen + 1) = 0x00;
        *((char*)*config_buf + *filelen + 2) = 0x06;
        *((char*)*config_buf + *filelen + 3) = bt_addr[5];
        *((char*)*config_buf + *filelen + 4) = bt_addr[4];
        *((char*)*config_buf + *filelen + 5) = bt_addr[3];
        *((char*)*config_buf + *filelen + 6) = bt_addr[2];
        *((char*)*config_buf + *filelen + 7) = bt_addr[1];
        *((char*)*config_buf + *filelen + 8) = bt_addr[0];
        *filelen += 9;
    }
#endif

    return baudrate;
}

uint32_t rtk_get_bt_config(unsigned char** config_buf,
        uint32_t* config_baud_rate, char * config_file_short_name, uint16_t mac_offset)
{
    char bt_config_file_name[PATH_MAX] = {0};
    struct stat st;
    size_t filelen;
    int fd;
    FILE* file = NULL;

    sprintf(bt_config_file_name, BT_CONFIG_DIRECTORY, config_file_short_name);
    ALOGI("BT config file: %s", bt_config_file_name);

    if (stat(bt_config_file_name, &st) < 0)
    {
        ALOGE("can't access bt config file:%s, errno:%d\n", bt_config_file_name, errno);
        return -1;
    }

    filelen = st.st_size;

    if ((fd = open(bt_config_file_name, O_RDONLY)) < 0)
    {
        ALOGE("Can't open bt config file");
        return -1;
    }

    if ((*config_buf = malloc(filelen)) == NULL)
    {
        ALOGE("malloc buffer for config file fail(0x%x)\n", filelen);
        close(fd);
        return -1;
    }

    if (read(fd, *config_buf, filelen) < (ssize_t)filelen)
    {
        ALOGE("Can't load bt config file");
        free(*config_buf);
        close(fd);
        return -1;
    }

    *config_baud_rate = rtk_parse_config_file(config_buf, &filelen, vnd_local_bd_addr, mac_offset);
    ALOGI("Get config baud rate from config file:0x%x", *config_baud_rate);

    close(fd);
    return filelen;
}

int rtk_get_bt_firmware(uint8_t** fw_buf, char* fw_short_name)
{
    char filename[PATH_MAX] = {0};
    struct stat st;
    int fd = -1;
    size_t fwsize = 0;
    size_t buf_size = 0;

    sprintf(filename, FIRMWARE_DIRECTORY, fw_short_name);
    ALOGI("BT fw file: %s", filename);

    if (stat(filename, &st) < 0)
    {
        ALOGE("Can't access firmware, errno:%d", errno);
        return -1;
    }

    fwsize = st.st_size;
    buf_size = fwsize;

    if ((fd = open(filename, O_RDONLY)) < 0)
    {
        ALOGE("Can't open firmware, errno:%d", errno);
        return -1;
    }

    if (!(*fw_buf = malloc(buf_size)))
    {
        ALOGE("Can't alloc memory for fw&config, errno:%d", errno);
        if (fd >= 0)
        close(fd);
        return -1;
    }

    if (read(fd, *fw_buf, fwsize) < (ssize_t) fwsize)
    {
        free(*fw_buf);
        *fw_buf = NULL;
        if (fd >= 0)
        close(fd);
        return -1;
    }

    if (fd >= 0)
        close(fd);

    ALOGI("Load FW OK");
    return buf_size;
}

uint8_t rtk_get_fw_project_id(uint8_t *p_buf)
{
    uint8_t opcode;
    uint8_t len;
    uint8_t data = 0;

    do {
        opcode = *p_buf;
        len = *(p_buf - 1);
        if (opcode == 0x00)
        {
            if (len == 1)
            {
                data = *(p_buf - 2);
                ALOGI("bt_hw_parse_project_id: opcode %d, len %d, data %d", opcode, len, data);
                break;
            }
            else
            {
                ALOGW("bt_hw_parse_project_id: invalid len %d", len);
            }
        }
        p_buf -= len + 2;
    } while (*p_buf != 0xFF);

    return data;
}

struct rtk_epatch_entry *rtk_get_patch_entry(bt_hw_cfg_cb_t *cfg_cb)
{
    uint16_t i;
    struct rtk_epatch *patch;
    struct rtk_epatch_entry *entry;
    uint8_t *p;
    uint16_t chip_id;

    patch = (struct rtk_epatch *)cfg_cb->fw_buf;
    entry = (struct rtk_epatch_entry *)malloc(sizeof(*entry));
    if(!entry)
    {
        ALOGE("rtk_get_patch_entry: failed to allocate mem for patch entry");
        return NULL;
    }

    patch->number_of_patch = le16_to_cpu(patch->number_of_patch);

    ALOGI("rtk_get_patch_entry: fw_ver 0x%08x, patch_num %d",
                le32_to_cpu(patch->fw_version), patch->number_of_patch);

    for (i = 0; i < patch->number_of_patch; i++)
    {
        p = cfg_cb->fw_buf + 14 + 2*i;
        STREAM_TO_UINT16(chip_id, p);
        if (chip_id == cfg_cb->eversion + 1)
        {
            entry->chip_id = chip_id;
            p = cfg_cb->fw_buf + 14 + 2*patch->number_of_patch + 2*i;
            STREAM_TO_UINT16(entry->patch_length, p);
            p = cfg_cb->fw_buf + 14 + 4*patch->number_of_patch + 4*i;
            STREAM_TO_UINT32(entry->patch_offset, p);
            ALOGI("rtk_get_patch_entry: chip_id %d, patch_len 0x%x, patch_offset 0x%x",
                    entry->chip_id, entry->patch_length, entry->patch_offset);
            break;
        }
    }

    if (i == patch->number_of_patch)
    {
        ALOGE("rtk_get_patch_entry: failed to get etnry");
        free(entry);
        entry = NULL;
    }

    return entry;
}

void rtk_get_bt_final_patch(bt_hw_cfg_cb_t* cfg_cb)
{
    uint8_t proj_id = 0;
    struct rtk_epatch_entry* entry = NULL;
    struct rtk_epatch *patch = (struct rtk_epatch *)cfg_cb->fw_buf;
    int iBtCalLen = 0;

    if(cfg_cb->lmp_subversion == LMPSUBVERSION_8723a)
    {
        if(memcmp(cfg_cb->fw_buf, RTK_EPATCH_SIGNATURE, 8) == 0)
        {
            ALOGE("8723as check signature error!");
            cfg_cb->dl_fw_flag = 0;
            goto free_buf;
        }
        else
        {
            cfg_cb->total_len = cfg_cb->fw_len + cfg_cb->config_len;
            if (!(cfg_cb->total_buf = malloc(cfg_cb->total_len)))
            {
                ALOGE("can't alloc memory for fw&config, errno:%d", errno);
                cfg_cb->dl_fw_flag = 0;
                goto free_buf;
            }
            else
            {
                ALOGI("8723as, fw copy direct");
                memcpy(cfg_cb->total_buf, cfg_cb->fw_buf, cfg_cb->fw_len);
                memcpy(cfg_cb->total_buf+cfg_cb->fw_len, cfg_cb->config_buf, cfg_cb->config_len);
                cfg_cb->dl_fw_flag = 1;
                goto free_buf;
            }
        }
    }

    if (memcmp(cfg_cb->fw_buf, RTK_EPATCH_SIGNATURE, 8))
    {
        ALOGE("check signature error");
        cfg_cb->dl_fw_flag = 0;
        goto free_buf;
    }

    /* check the extension section signature */
    if (memcmp(cfg_cb->fw_buf + cfg_cb->fw_len - 4, EXTENSION_SECTION_SIGNATURE, 4))
    {
        ALOGE("check extension section signature error");
        cfg_cb->dl_fw_flag = 0;
        goto free_buf;
    }

    proj_id = rtk_get_fw_project_id(cfg_cb->fw_buf + cfg_cb->fw_len - 5);

    if((hw_cfg_cb.project_id_mask != PROJECT_ID_MASK_ALL)&& ((hw_cfg_cb.project_id_mask&(1<<proj_id)) ==0))
    {
        ALOGE("hw_cfg_cb.project_id_mask is 0x%08x, fw project_id is %d, does not match!!!",
                        hw_cfg_cb.project_id_mask, proj_id);
        cfg_cb->dl_fw_flag = 0;
        goto free_buf;
    }

    entry = rtk_get_patch_entry(cfg_cb);
    if (entry)
    {
        cfg_cb->total_len = entry->patch_length + cfg_cb->config_len;
    }
    else
    {
        cfg_cb->dl_fw_flag = 0;
        goto free_buf;
    }

    ALOGI("total_len = 0x%x", cfg_cb->total_len);

    if (!(cfg_cb->total_buf = malloc(cfg_cb->total_len)))
    {
        ALOGE("Can't alloc memory for multi fw&config, errno:%d", errno);
        cfg_cb->dl_fw_flag = 0;
        goto free_buf;
    }
    else
    {
        memcpy(cfg_cb->total_buf, cfg_cb->fw_buf + entry->patch_offset, entry->patch_length);
        memcpy(cfg_cb->total_buf + entry->patch_length - 4, &patch->fw_version, 4);
        memcpy(&entry->svn_version, cfg_cb->total_buf + entry->patch_length - 8, 4);
        memcpy(&entry->coex_version, cfg_cb->total_buf + entry->patch_length - 12, 4);
        ALOGI("BTCOEX:20%06d-%04x svn_version:%d lmp_subversion:0x%x hci_version:0x%x hci_revision:0x%x chip_type:%d Cut:%d libbt-vendor_uart version:%s\n",
        ((entry->coex_version >> 16) & 0x7ff) + ((entry->coex_version >> 27) * 10000),
        (entry->coex_version & 0xffff), entry->svn_version, cfg_cb->lmp_subversion, cfg_cb->hci_version, cfg_cb->hci_revision, cfg_cb->chip_type, cfg_cb->eversion+1, RTK_VERSION);
    }

    if (cfg_cb->config_len)
    {
        memcpy(cfg_cb->total_buf+entry->patch_length, cfg_cb->config_buf, cfg_cb->config_len);
    }

    cfg_cb->dl_fw_flag = 1;
    ALOGI("Fw:%s exists, config file:%s exists", (cfg_cb->fw_len>0)?"":"not", (cfg_cb->config_len>0)?"":"not");

free_buf:
    if (cfg_cb->fw_len > 0)
    {
        free(cfg_cb->fw_buf);
        cfg_cb->fw_len = 0;
    }

    if (cfg_cb->config_len > 0)
    {
        free(cfg_cb->config_buf);
        cfg_cb->config_len = 0;
    }

    if(entry)
    {
        free(entry);
    }
}

static int hci_download_patch_h4(HC_BT_HDR *p_buf, int index, uint8_t *data, int len)
{
    uint8_t retval = FALSE;
    uint8_t *p = (uint8_t *) (p_buf + 1);

    UINT16_TO_STREAM(p, HCI_VSC_DOWNLOAD_FW_PATCH);
    *p++ = 1 + len;  /* parameter length */
    *p++ = index;
    memcpy(p, data, len);
    p_buf->len = HCI_CMD_PREAMBLE_SIZE + 1+len;

    hw_cfg_cb.state = HW_CFG_DL_FW_PATCH;

    retval = bt_vendor_cbacks->xmit_cb(HCI_VSC_DOWNLOAD_FW_PATCH, p_buf, hw_config_cback);
    return retval;
}

/*******************************************************************************
**
** Function         hw_config_cback
**
** Description      Callback function for controller configuration
**
** Returns          None
**
*******************************************************************************/
void hw_config_cback(void *p_mem)
{
    HC_BT_HDR   *p_evt_buf = NULL;
    uint8_t     *p = NULL, *pp=NULL;
    uint8_t     status = 0;
    uint16_t    opcode = 0;
    HC_BT_HDR   *p_buf = NULL;
    uint8_t     is_proceeding = FALSE;
    int         i = 0;
    uint8_t     iIndexRx = 0;
    patch_info* prtk_patch_file_info = NULL;
    uint32_t    host_baudrate = 0;

#if (USE_CONTROLLER_BDADDR == TRUE)
    const uint8_t null_bdaddr[BD_ADDR_LEN] = {0,0,0,0,0,0};
#endif

    if(p_mem != NULL)
    {
        p_evt_buf = (HC_BT_HDR *) p_mem;
        status = *((uint8_t *)(p_evt_buf + 1) + HCI_EVT_CMD_CMPL_STATUS_OFFSET);
        p = (uint8_t *)(p_evt_buf + 1) + HCI_EVT_CMD_CMPL_OPCODE_OFFSET;
        STREAM_TO_UINT16(opcode,p);
    }

    /* Ask a new buffer big enough to hold any HCI commands sent in here */
    /*a cut fc6d status==1*/
    if (((status == 0) ||(opcode == HCI_VSC_READ_ROM_VERSION)) && bt_vendor_cbacks)
        p_buf = (HC_BT_HDR *)bt_vendor_cbacks->alloc(BT_HC_HDR_SIZE + HCI_CMD_MAX_LEN);

    if (p_buf != NULL)
    {
        p_buf->event = MSG_STACK_TO_HC_HCI_CMD;
        p_buf->offset = 0;
        p_buf->len = 0;
        p_buf->layer_specific = 0;

        ALOGI("hw_cfg_cb.state = %i", hw_cfg_cb.state);
        switch (hw_cfg_cb.state)
        {
            case HW_CFG_H5_INIT:
            {
                p = (uint8_t *)(p_buf + 1);
                UINT16_TO_STREAM(p, HCI_READ_LMP_VERSION);
                *p++ = 0;
                p_buf->len = HCI_CMD_PREAMBLE_SIZE;

                hw_cfg_cb.state = HW_CFG_READ_LOCAL_VER;
                is_proceeding = bt_vendor_cbacks->xmit_cb(HCI_READ_LMP_VERSION, p_buf, hw_config_cback);
                break;
            }
            case HW_CFG_READ_LOCAL_VER:
            {
                if (status == 0)
                {
                    p = ((uint8_t *)(p_evt_buf + 1) + HCI_EVT_CMD_CMPL_OP1001_HCI_VERSION_OFFSET);
                    STREAM_TO_UINT16(hw_cfg_cb.hci_version, p);
                    p = ((uint8_t *)(p_evt_buf + 1) + HCI_EVT_CMD_CMPL_OP1001_HCI_REVISION_OFFSET);
                    STREAM_TO_UINT16(hw_cfg_cb.hci_revision, p);
                    p = (uint8_t *)(p_evt_buf + 1) + HCI_EVT_CMD_CMPL_OP1001_LMP_SUBVERSION_OFFSET;
                    STREAM_TO_UINT16(hw_cfg_cb.lmp_subversion, p);
                    ALOGI("lmp_subversion = 0x%x hw_cfg_cb.hci_version = 0x%x hw_cfg_cb.hci_revision = 0x%x", hw_cfg_cb.lmp_subversion, hw_cfg_cb.hci_version, hw_cfg_cb.hci_revision);
                    if(hw_cfg_cb.lmp_subversion == LMPSUBVERSION_8723a)
                    {
                        hw_cfg_cb.state = HW_CFG_START;
                        goto CFG_START;
                    }
                    else
                    {
                        hw_cfg_cb.state = HW_CFG_READ_ECO_VER;
                        p = (uint8_t *) (p_buf + 1);
                        UINT16_TO_STREAM(p, HCI_VSC_READ_ROM_VERSION);
                        *p++ = 0;
                        p_buf->len = HCI_CMD_PREAMBLE_SIZE;
                        is_proceeding = bt_vendor_cbacks->xmit_cb(HCI_VSC_READ_ROM_VERSION, p_buf, hw_config_cback);
                    }
                }
                break;
            }
            case HW_CFG_READ_ECO_VER:
            {
                if(status == 0)
                {
                    hw_cfg_cb.eversion = *((uint8_t *)(p_evt_buf + 1) + HCI_EVT_CMD_CMPL_OPFC6D_EVERSION_OFFSET);
                    ALOGI("hw_config_cback chip_id of the IC:%d", hw_cfg_cb.eversion+1);
                }
                else if(1 == status)
                {
                    hw_cfg_cb.eversion = 0;
                }
                else
                {
                    is_proceeding = FALSE;
                    break;
                }

                if(check_match_state(&hw_cfg_cb, 0) > 1)    // check if have multiple matched patch_entry by lmp_subversion,hci_version, hci_revision
                {
                    hw_cfg_cb.state = HW_CFG_READ_CHIP_TYPE;
                     p = (uint8_t *) (p_buf + 1);
                    UINT16_TO_STREAM(p, HCI_VSC_READ_CHIP_TYPE);
                    *p++ = 5;
                    UINT8_TO_STREAM(p, 0x00);
                    UINT32_TO_STREAM(p, 0xB000A094);
                    p_buf->len = HCI_CMD_PREAMBLE_SIZE + HCI_CMD_READ_CHIP_TYPE_SIZE;

                    pp = (uint8_t *) (p_buf + 1);
                    for (i = 0; i < p_buf->len; i++)
                        ALOGI("get chip type command data[%d]= 0x%x", i, *(pp+i));

                    is_proceeding = bt_vendor_cbacks->xmit_cb(HCI_VSC_READ_CHIP_TYPE, p_buf, hw_config_cback);
                    break;
                }
                else
                {
                    hw_cfg_cb.state = HW_CFG_START;
                    goto CFG_START;
                }
            }
            case HW_CFG_READ_CHIP_TYPE:
            {
                ALOGI("READ_CHIP_TYPE status = %d", status);
                ALOGI("READ_CHIP_TYPE length = %d", p_evt_buf->len);
                p = (uint8_t *)(p_evt_buf + 1) ;
                for (i = 0; i < p_evt_buf->len; i++)
                    ALOGI("READ_CHIP_TYPE event data[%d]= 0x%x", i, *(p+i));
                if(status == 0)
                {
                    hw_cfg_cb.chip_type = ((*((uint8_t *)(p_evt_buf + 1) + HCI_EVT_CMD_CMPL_OPFC61_CHIPTYPE_OFFSET))&0x0F);
                    ALOGI("READ_CHIP_TYPE hw_cfg_cb.lmp_subversion = 0x%x", hw_cfg_cb.lmp_subversion);
                    ALOGI("READ_CHIP_TYPE hw_cfg_cb.hci_version = 0x%x", hw_cfg_cb.hci_version);
                    ALOGI("READ_CHIP_TYPE hw_cfg_cb.hci_revision = 0x%x", hw_cfg_cb.hci_revision);
                    ALOGI("READ_CHIP_TYPE hw_cfg_cb.chip_type = 0x%x", hw_cfg_cb.chip_type);
                }
                else
                {
                    is_proceeding = FALSE;
                    break;
                }
                if(check_match_state(&hw_cfg_cb, PATCH_OPTIONAL_MATCH_FLAG_CHIPTYPE) > 1)  // check if have multiple matched patch_entry by lmp_subversion,hci_version, hci_revision and chiptype
                {
                    ALOGE("check_match_state(lmp_subversion:0x%04x, hci_version:%d, hci_revision:%d chip_type:%d): Multi Matched Patch\n", hw_cfg_cb.lmp_subversion, hw_cfg_cb.hci_version, hw_cfg_cb.hci_revision, hw_cfg_cb.chip_type);
                    is_proceeding = FALSE;
                    break;
                }
                hw_cfg_cb.state = HW_CFG_START;
            }
CFG_START:
            case HW_CFG_START:
            {
#ifdef BT_CHIP_PROBE_SIMULATION
                {
                    int ii;
                    memcpy(&hw_cfg_test, &hw_cfg_cb, sizeof(hw_cfg_test));
                    for(i=0;i<sizeof(bt_chip_chars)/sizeof(bt_chip_chars[0]);i++)
                    {
                        ALOGE("BT_CHIP_PROBE_SIMULATION loop:%d $$$ BEGIN $$$\n", i);
                        hw_cfg_test.lmp_subversion = bt_chip_chars[i].lmp_subversion;
                        hw_cfg_test.hci_version = bt_chip_chars[i].hci_version;
                        hw_cfg_test.hci_revision = bt_chip_chars[i].hci_revision;
                        hw_cfg_test.chip_type = CHIPTYPE_NONE;
                        if(check_match_state(&hw_cfg_test, 0) > 1){
                            ALOGE("check_match_state hw_cfg_test(lmp_subversion:0x%04x, hci_version:%d, hci_revision:%d chip_type:%d): Multi Matched Patch\n", hw_cfg_test.lmp_subversion, hw_cfg_test.hci_version, hw_cfg_test.hci_revision, hw_cfg_test.chip_type);
                            if(bt_chip_chars[i].chip_type != CHIPTYPE_NONE){
                                ALOGE("BT_CHIP_PROBE_SIMULATION loop:%d *** Include ChipType ***\n", i);
                                hw_cfg_test.chip_type = bt_chip_chars[i].chip_type;
                                if(check_match_state(&hw_cfg_test, PATCH_OPTIONAL_MATCH_FLAG_CHIPTYPE) > 1){
                                    ALOGE("check_match_state hw_cfg_test(lmp_subversion:0x%04x, hci_version:%d, hci_revision:%d chip_type:%d): Multi Matched Patch\n", hw_cfg_test.lmp_subversion, hw_cfg_test.hci_version, hw_cfg_test.hci_revision, hw_cfg_test.chip_type);
                                }else{
                                    prtk_patch_file_info = get_patch_entry(&hw_cfg_test);
                                }
                            }
                        }else{
                            prtk_patch_file_info = get_patch_entry(&hw_cfg_test);
                        }
                        ALOGE("BT_CHIP_PROBE_SIMULATION loop:%d $$$ END $$$\n", i);
                    }
                }
#endif
                //get efuse config file and patch code file
                prtk_patch_file_info = get_patch_entry(&hw_cfg_cb);


                if((prtk_patch_file_info == NULL) || (prtk_patch_file_info->lmp_subversion == 0))
                {
                    ALOGE("get patch entry error");
                    is_proceeding = FALSE;
                    break;
                }

                hw_cfg_cb.config_len = rtk_get_bt_config(&hw_cfg_cb.config_buf, &hw_cfg_cb.baudrate, prtk_patch_file_info->config_name, prtk_patch_file_info->mac_offset);
                if (hw_cfg_cb.config_len < 0)
                {
                    ALOGE("Get Config file fail, just use efuse settings");
                    hw_cfg_cb.config_len = 0;
                }

                hw_cfg_cb.fw_len = rtk_get_bt_firmware(&hw_cfg_cb.fw_buf, prtk_patch_file_info->patch_name);
                if (hw_cfg_cb.fw_len < 0)
                {
                    ALOGE("Get BT firmware fail");
                    hw_cfg_cb.fw_len = 0;
                }
                else{
                    hw_cfg_cb.project_id_mask = prtk_patch_file_info->project_id_mask;
                    rtk_get_bt_final_patch(&hw_cfg_cb);
                }
                if (hw_cfg_cb.total_len > RTK_PATCH_LENGTH_MAX)
                {
                    ALOGE("total length of fw&config larger than allowed");
                    is_proceeding = FALSE;
                    break;
                }

                if ((hw_cfg_cb.total_len > 0) && hw_cfg_cb.dl_fw_flag)
                {
                    hw_cfg_cb.patch_frag_cnt = hw_cfg_cb.total_len / PATCH_DATA_FIELD_MAX_SIZE;
                    hw_cfg_cb.patch_frag_tail = hw_cfg_cb.total_len % PATCH_DATA_FIELD_MAX_SIZE;
                    if (hw_cfg_cb.patch_frag_tail)
                        hw_cfg_cb.patch_frag_cnt += 1;
                    else
                        hw_cfg_cb.patch_frag_tail = PATCH_DATA_FIELD_MAX_SIZE;
                    ALOGI("patch fragment count %d, tail len %d", hw_cfg_cb.patch_frag_cnt, hw_cfg_cb.patch_frag_tail);
                }
                else
                {
                    is_proceeding = FALSE;
                    break;
                }

                if ((hw_cfg_cb.baudrate == 0) && ((hw_cfg_cb.hw_flow_cntrl & 0x80) == 0))
                {
                    ALOGI("no baudrate to set and no need to set hw flow control");
                    goto DOWNLOAD_FW;
                }

                if ((hw_cfg_cb.baudrate == 0) && (hw_cfg_cb.hw_flow_cntrl & 0x80))
                {
                    ALOGI("no baudrate to set but set hw flow control is needed");
                    goto SET_HW_FLCNTRL;
                }
            }
            /* fall through intentionally */
            case HW_CFG_SET_UART_BAUD_CONTROLLER:
                ALOGI("bt vendor lib: set CONTROLLER UART baud 0x%x", hw_cfg_cb.baudrate);
                hw_cfg_cb.state = HW_CFG_SET_UART_BAUD_HOST;
                is_proceeding = hw_config_set_controller_baudrate(p_buf, hw_cfg_cb.baudrate);
                break;

            case HW_CFG_SET_UART_BAUD_HOST:
                /* update baud rate of host's UART port */
                rtk_speed_to_uart_speed(hw_cfg_cb.baudrate, &host_baudrate);
                ALOGI("bt vendor lib: set HOST UART baud %i", host_baudrate);
                userial_vendor_set_baud(line_speed_to_userial_baud(host_baudrate));

                if((hw_cfg_cb.hw_flow_cntrl & 0x80) == 0)
                    goto DOWNLOAD_FW;

SET_HW_FLCNTRL:
            case HW_CFG_SET_UART_HW_FLOW_CONTROL:
                ALOGI("Change HW flowcontrol setting");
                if(hw_cfg_cb.hw_flow_cntrl & 0x01)
                {
                    userial_vendor_set_hw_fctrl(1);
                }
                else
                {
                    userial_vendor_set_hw_fctrl(0);
                }
                ms_delay(100);
                hw_cfg_cb.state = HW_CFG_DL_FW_PATCH;

DOWNLOAD_FW:
            case HW_CFG_DL_FW_PATCH:
                ALOGI("bt vendor lib: HW_CFG_DL_FW_PATCH status:%i, opcode:0x%x", status, opcode);

                //recv command complete event for patch code download command
                if(opcode == HCI_VSC_DOWNLOAD_FW_PATCH)
                {
                    iIndexRx = *((uint8_t *)(p_evt_buf + 1) + HCI_EVT_CMD_CMPL_STATUS_OFFSET + 1);
                    ALOGI("bt vendor lib: HW_CFG_DL_FW_PATCH status:%i, iIndexRx:%i", status, iIndexRx);
                    hw_cfg_cb.patch_frag_idx++;

                    if(iIndexRx&0x80)
                    {
                        ALOGI("vendor lib fwcfg completed");
                        free(hw_cfg_cb.total_buf);
                        hw_cfg_cb.total_len = 0;

                        bt_vendor_cbacks->dealloc(p_buf);
                        bt_vendor_cbacks->fwcfg_cb(BT_VND_OP_RESULT_SUCCESS);

                        hw_cfg_cb.state = 0;
                        is_proceeding = TRUE;
                        break;
                    }
                }

                if (hw_cfg_cb.patch_frag_idx < hw_cfg_cb.patch_frag_cnt)
                {
                    if (hw_cfg_cb.patch_frag_idx == hw_cfg_cb.patch_frag_cnt - 1)
                    {
                        ALOGI("HW_CFG_DL_FW_PATCH: send last fw fragment");
                        hw_cfg_cb.patch_frag_idx |= 0x80;
                        hw_cfg_cb.patch_frag_len = hw_cfg_cb.patch_frag_tail;
                    }
                    else
                    {
                        hw_cfg_cb.patch_frag_idx &= 0x7F;
                        hw_cfg_cb.patch_frag_len = PATCH_DATA_FIELD_MAX_SIZE;
                    }
                }

                is_proceeding = hci_download_patch_h4(p_buf, hw_cfg_cb.patch_frag_idx,
                                    hw_cfg_cb.total_buf+(hw_cfg_cb.patch_frag_idx&0x7F)*PATCH_DATA_FIELD_MAX_SIZE,
                                    hw_cfg_cb.patch_frag_len);
                break;

            default:
                break;
        } // switch(hw_cfg_cb.state)
    } // if (p_buf != NULL)

    /* Free the RX event buffer */
    if ((bt_vendor_cbacks) && (p_evt_buf != NULL))
        bt_vendor_cbacks->dealloc(p_evt_buf);

    if (is_proceeding == FALSE)
    {
        ALOGE("vendor lib fwcfg aborted!!!");
        if (bt_vendor_cbacks)
        {
            if (p_buf != NULL)
                bt_vendor_cbacks->dealloc(p_buf);

            bt_vendor_cbacks->fwcfg_cb(BT_VND_OP_RESULT_FAIL);
        }

        if(hw_cfg_cb.config_len)
        {
            free(hw_cfg_cb.config_buf);
            hw_cfg_cb.config_len = 0;
        }

        if(hw_cfg_cb.fw_len)
        {
            free(hw_cfg_cb.fw_buf);
            hw_cfg_cb.fw_len= 0;
        }

        if(hw_cfg_cb.total_len)
        {
            free(hw_cfg_cb.total_buf);
            hw_cfg_cb.total_len = 0;
        }
        hw_cfg_cb.state = 0;
    }
}

/******************************************************************************
**   LPM Static Functions
******************************************************************************/

/*******************************************************************************
**
** Function         hw_lpm_ctrl_cback
**
** Description      Callback function for lpm enable/disable rquest
**
** Returns          None
**
*******************************************************************************/
void hw_lpm_ctrl_cback(void *p_mem)
{
    HC_BT_HDR *p_evt_buf = (HC_BT_HDR *) p_mem;
    bt_vendor_op_result_t status = BT_VND_OP_RESULT_FAIL;

    if (*((uint8_t *)(p_evt_buf + 1) + HCI_EVT_CMD_CMPL_STATUS_OFFSET) == 0)
    {
        status = BT_VND_OP_RESULT_SUCCESS;
    }

    if (bt_vendor_cbacks)
    {
        bt_vendor_cbacks->lpm_cb(status);
        bt_vendor_cbacks->dealloc(p_evt_buf);
    }
}


/*****************************************************************************
**   Hardware Configuration Interface Functions
*****************************************************************************/


/*******************************************************************************
**
** Function        hw_config_start
**
** Description     Kick off controller initialization process
**
** Returns         None
**
*******************************************************************************/
void hw_config_start(void)
{
    HC_BT_HDR *p_buf = NULL;
    uint8_t     *p = NULL;
    memset(&hw_cfg_cb, 0, sizeof(bt_hw_cfg_cb_t));
    hw_cfg_cb.dl_fw_flag = 1;
    hw_cfg_cb.chip_type = CHIPTYPE_NONE;
    ALOGI("\nRealtek libbt-vendor_uart Version %s \n",RTK_VERSION);
    ALOGI("hw_config_start\n");
    /* Start from sending H5 SYNC */
    if (bt_vendor_cbacks)
    {
        hw_cfg_cb.state = HW_CFG_H5_INIT;
        p_buf = (HC_BT_HDR *)bt_vendor_cbacks->alloc(BT_HC_HDR_SIZE + HCI_CMD_MAX_LEN);

        if (p_buf != NULL)
        {
            p_buf->event = MSG_STACK_TO_HC_HCI_CMD;
            p_buf->offset = 0;
            p_buf->len = 0;
            p_buf->layer_specific = 0;

            ALOGI("hw_cfg_cb.state = %i", hw_cfg_cb.state);
            p = (uint8_t *)(p_buf + 1);
            UINT16_TO_STREAM(p, HCI_VSC_H5_INIT);
            *p++ = 0;
            p_buf->len = HCI_CMD_PREAMBLE_SIZE;
            bt_vendor_cbacks->xmit_cb(HCI_VSC_H5_INIT, (void *)p_buf, hw_config_cback);
        }

    }
}

#if (HW_END_WITH_HCI_RESET == TRUE)
/******************************************************************************
*
**
** Function         hw_epilog_cback
**
** Description      Callback function for Command Complete Events from HCI
**                  commands sent in epilog process.
**
** Returns          None
**
*******************************************************************************/
void hw_epilog_cback(void *p_mem)
{
    HC_BT_HDR   *p_evt_buf = (HC_BT_HDR *) p_mem;
    uint8_t     *p, status;
    uint16_t    opcode;

    status = *((uint8_t *)(p_evt_buf + 1) + HCI_EVT_CMD_CMPL_STATUS_OFFSET);
    p = (uint8_t *)(p_evt_buf + 1) + HCI_EVT_CMD_CMPL_OPCODE_OFFSET;
    STREAM_TO_UINT16(opcode,p);

    BTHWDBG("%s Opcode:0x%04X Status: %d", __FUNCTION__, opcode, status);

    if (bt_vendor_cbacks)
    {
        /* Must free the RX event buffer */
        bt_vendor_cbacks->dealloc(p_evt_buf);

        /* Once epilog process is done, must call epilog_cb callback
           to notify caller */
        bt_vendor_cbacks->epilog_cb(BT_VND_OP_RESULT_SUCCESS);
    }
}

/******************************************************************************
*
**
** Function         hw_epilog_process
**
** Description      Sample implementation of epilog process
**
** Returns          None
**
*******************************************************************************/
void hw_epilog_process(void)
{
    HC_BT_HDR  *p_buf = NULL;
    uint8_t     *p;

    BTHWDBG("hw_epilog_process");

    /* Sending a HCI_RESET */
    if (bt_vendor_cbacks)
    {
        /* Must allocate command buffer via HC's alloc API */
        p_buf = (HC_BT_HDR *) bt_vendor_cbacks->alloc(BT_HC_HDR_SIZE + \
                                                       HCI_CMD_PREAMBLE_SIZE);
    }

    if (p_buf)
    {
        p_buf->event = MSG_STACK_TO_HC_HCI_CMD;
        p_buf->offset = 0;
        p_buf->layer_specific = 0;
        p_buf->len = HCI_CMD_PREAMBLE_SIZE;

        p = (uint8_t *) (p_buf + 1);
        UINT16_TO_STREAM(p, HCI_RESET);
        *p = 0; /* parameter length */

        /* Send command via HC's xmit_cb API */
        bt_vendor_cbacks->xmit_cb(HCI_RESET, p_buf, hw_epilog_cback);
    }
    else
    {
        if (bt_vendor_cbacks)
        {
            ALOGE("vendor lib epilog process aborted [no buffer]");
            bt_vendor_cbacks->epilog_cb(BT_VND_OP_RESULT_FAIL);
        }
    }
}
#endif // (HW_END_WITH_HCI_RESET == TRUE)
