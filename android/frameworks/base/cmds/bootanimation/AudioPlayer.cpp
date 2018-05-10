/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#define LOG_NDEBUG 0
#define LOG_TAG "BootAnim_AudioPlayer"

#include "AudioPlayer.h"

#include <androidfw/ZipFileRO.h>

#include <utils/Log.h>
#include <utils/String8.h>

#define ID_RIFF 0x46464952
#define ID_WAVE 0x45564157
#define ID_FMT  0x20746d66
#define ID_DATA 0x61746164

// Maximum line length for audio_conf.txt
// We only accept lines less than this length to avoid overflows using sscanf()
#define MAX_LINE_LENGTH 1024
#define AUDIO_MAP_CNT 16
#define NAME_LEN 20
#define PATH_LEN 30
#define WRITE_BUF 4096

//for ahub
#define HUB "sndahub"
int output_card = -1;

typedef struct name_map_t
{
    char name[NAME_LEN];
    int card_num;
    int active;
    int output_device;
}name_map;

static name_map audio_name_map[AUDIO_MAP_CNT] =
{
    {"audiocodec",           -1,        1,  1},
    {"sndhdmi",              -1,        1,  0},
    {"sndspdif",             -1,        0, -1},
};

struct riff_wave_header {
    uint32_t riff_id;
    uint32_t riff_sz;
    uint32_t wave_id;
};

struct chunk_header {
    uint32_t id;
    uint32_t sz;
};

struct chunk_fmt {
    uint16_t audio_format;
    uint16_t num_channels;
    uint32_t sample_rate;
    uint32_t byte_rate;
    uint16_t block_align;
    uint16_t bits_per_sample;
};


namespace android {

AudioPlayer::AudioPlayer()
    :   mCard(-1),
        mDevice(-1),
        mPeriodSize(0),
        mPeriodCount(0),
        ahub_mixer(NULL),
        mCurrentFile(NULL)
{
}

AudioPlayer::~AudioPlayer() {
    for(int index = 0; index < CARD_NUM; index++)
    {
        if(cards[index].pcm)
        {
            pcm_close(cards[index].pcm);
            cards[index].pcm = NULL;
        }
        if(cards[index].hub_pcm)
        {
            pcm_close(cards[index].hub_pcm);
            cards[index].hub_pcm = NULL;
        }
    }
}

//for h6 ahub
/*static bool setHubRoute(struct mixer* mixer)
{
    struct mixer_ctl *ctl;
    //i2s1->APBIF_TXDIF0
    ctl = mixer_get_ctl_by_name(mixer, "I2S1 Src Select");
    mixer_ctl_set_value(ctl, 0, 1);
    //enble i2s1
    ctl = mixer_get_ctl_by_name(mixer, "I2S1OUT Switch");
    mixer_ctl_set_value(ctl, 0, 1);

    //i2s3->APBIF_TXDIF0
    ctl = mixer_get_ctl_by_name(mixer, "I2S3 Src Select");
    mixer_ctl_set_value(ctl, 0, 2);
    //enable i2s3
    ctl = mixer_get_ctl_by_name(mixer, "I2S3OUT Switch");
    mixer_ctl_set_value(ctl, 0, 1);
    return 0;
}
*/

static bool setMixerValue(struct mixer* mixer, const char* name, const char* values)
{
    if (!mixer) {
        ALOGE("no mixer in setMixerValue");
        return false;
    }
    struct mixer_ctl *ctl = mixer_get_ctl_by_name(mixer, name);
    if (!ctl) {
        ALOGE("mixer_get_ctl_by_name failed for %s", name);
        return false;
    }

    enum mixer_ctl_type type = mixer_ctl_get_type(ctl);
    int numValues = mixer_ctl_get_num_values(ctl);
    int intValue;
    char stringValue[MAX_LINE_LENGTH];

    for (int i = 0; i < numValues && values; i++) {
        // strip leading space
        while (*values == ' ') values++;
        if (*values == 0) break;

        switch (type) {
            case MIXER_CTL_TYPE_BOOL:
            case MIXER_CTL_TYPE_INT:
                if (sscanf(values, "%d", &intValue) == 1) {
                    if (mixer_ctl_set_value(ctl, i, intValue) != 0) {
                        ALOGE("mixer_ctl_set_value failed for %s %d", name, intValue);
                    }
                } else {
                    ALOGE("Could not parse %s as int for %s", values, name);
                }
                break;
            case MIXER_CTL_TYPE_ENUM:
                if (sscanf(values, "%s", stringValue) == 1) {
                    if (mixer_ctl_set_enum_by_string(ctl, stringValue) != 0) {
                        ALOGE("mixer_ctl_set_enum_by_string failed for %s %s", name, stringValue);
                    }
                } else {
                    ALOGE("Could not parse %s as enum for %s", values, name);
                }
                break;
            default:
                ALOGE("unsupported mixer type %d for %s", type, name);
                break;
        }
        values = strchr(values, ' ');
    }

    return true;
}


/*
 * Parse the audio configuration file.
 * The file is named audio_conf.txt and must begin with the following header:
 *
 * card=<ALSA card number>
 * device=<ALSA device number>
 * period_size=<period size>
 * period_count=<period count>
 *
 * This header is followed by zero or more mixer settings, each with the format:
 * mixer "<name>" = <value list>
 * Since mixer names can contain spaces, the name must be enclosed in double quotes.
 * The values in the value list can be integers, booleans (represented by 0 or 1)
 * or strings for enum values.
 */
bool AudioPlayer::init(const char* config)
{
    int tempInt;
    memset(cards, 0, sizeof(cards));
    struct mixer* mixer = NULL;
    char    name[MAX_LINE_LENGTH];
    AudioMap  *audiomap = new AudioMap();
    audiomap->init_audio_map();

    for (;;) {
        const char* endl = strstr(config, "\n");
        if (!endl) break;
        String8 line(config, endl - config);
        if (line.length() >= MAX_LINE_LENGTH) {
            ALOGE("Line too long in audio_conf.txt");
            return false;
        }
        const char* l = line.string();

        if (sscanf(l, "card=%d", &tempInt) == 1) {
            ALOGD("card=%d", tempInt);
            mCard = tempInt;

            mixer = mixer_open(mCard);
            if (!mixer) {
                ALOGE("could not open mixer for card %d", mCard);
                return false;
            }
        } else if (sscanf(l, "device=%d", &tempInt) == 1) {
            ALOGD("device=%d", tempInt);
            mDevice = tempInt;
        } else if (sscanf(l, "period_size=%d", &tempInt) == 1) {
            ALOGD("period_size=%d", tempInt);
            mPeriodSize = tempInt;
        } else if (sscanf(l, "period_count=%d", &tempInt) == 1) {
            ALOGD("period_count=%d", tempInt);
            mPeriodCount = tempInt;
        } else if (sscanf(l, "mixer \"%[0-9a-zA-Z _]s\"", name) == 1) {
            const char* values = strchr(l, '=');
            if (values) {
                values++;   // skip '='
                ALOGD("name: \"%s\" = %s", name, values);
                setMixerValue(mixer, name, values);
            } else {
                ALOGE("values missing for name: \"%s\"", name);
            }
        }
        config = ++endl;
    }

    mixer_close(mixer);
    audiomap->reinit_card(cards, mPeriodSize, mPeriodCount);
    delete(audiomap);
   
    return true;
}

void AudioPlayer::playFile(FileMap* fileMap) {
    // stop any currently playing sound
    requestExitAndWait();

    mCurrentFile = fileMap;
    run("bootanim audio", PRIORITY_URGENT_AUDIO);
}

bool AudioPlayer::threadLoop()
{
    struct pcm_config config;
    bool moreChunks = true;
    const struct chunk_fmt* chunkFmt = NULL;
    int bufferSize;
    const uint8_t* wavData;
    size_t wavLength;
    const struct riff_wave_header* wavHeader;

    if (mCurrentFile == NULL) {
        ALOGE("mCurrentFile is NULL");
        return false;
    }

    wavData = (const uint8_t *)mCurrentFile->getDataPtr();
    if (!wavData) {
        ALOGE("Could not access WAV file data");
        goto exit;
    }
    wavLength = mCurrentFile->getDataLength();

    wavHeader = (const struct riff_wave_header *)wavData;
    if (wavLength < sizeof(*wavHeader) || (wavHeader->riff_id != ID_RIFF) ||
        (wavHeader->wave_id != ID_WAVE)) {
        ALOGE("Error: audio file is not a riff/wave file\n");
        goto exit;
    }
    wavData += sizeof(*wavHeader);
    wavLength -= sizeof(*wavHeader);

    do {
        const struct chunk_header* chunkHeader = (const struct chunk_header*)wavData;
        if (wavLength < sizeof(*chunkHeader)) {
            ALOGE("EOF reading chunk headers");
            goto exit;
        }

        wavData += sizeof(*chunkHeader);
        wavLength -=  sizeof(*chunkHeader);

        switch (chunkHeader->id) {
            case ID_FMT:
                chunkFmt = (const struct chunk_fmt *)wavData;
                wavData += chunkHeader->sz;
                wavLength -= chunkHeader->sz;
                break;
            case ID_DATA:
                /* Stop looking for chunks */
                moreChunks = 0;
                break;
            default:
                /* Unknown chunk, skip bytes */
                wavData += chunkHeader->sz;
                wavLength -= chunkHeader->sz;
        }
    } while (moreChunks);

    if (!chunkFmt) {
        ALOGE("format not found in WAV file");
        goto exit;
    }

    memset(&config, 0, sizeof(config));
    config.channels = chunkFmt->num_channels;
    config.rate = chunkFmt->sample_rate;
    config.period_size = mPeriodSize;
    config.period_count = mPeriodCount;
    config.start_threshold = mPeriodSize / 4;
    config.stop_threshold = INT_MAX;
    config.avail_min = config.start_threshold;
    if (chunkFmt->bits_per_sample != 16) {
        ALOGE("only 16 bit WAV files are supported");
        goto exit;
    }
    config.format = PCM_FORMAT_S16_LE;
    //for h6 ahub
    //open ahub(output_card) mixer
    /*ahub_mixer = mixer_open(output_card);
    //set route
    setHubRoute(ahub_mixer);
    //open ahub
    for(int index = 0; index < CARD_NUM; index++)
    {
        if(1 == cards[index].active)
        {
            //if 0(hdmi) 4(cvbs) is active then card[0], card[4] is active
            cards[index].hub_pcm = pcm_open(output_card, cards[index].output_device, PCM_OUT, &config);
            if (!cards[index].hub_pcm || !pcm_is_ready(cards[index].hub_pcm))
            {
                ALOGE("Unable to open PCM device (%s)\n", pcm_get_error(cards[index].hub_pcm));
                goto exit;
            }
            bufferSize = pcm_frames_to_bytes(cards[index].hub_pcm, pcm_get_buffer_size(cards[index].hub_pcm));
        }
    }*/

    //open ordinay cards
    for(int index = 0; index < CARD_NUM; index++)
    {
        if(1 == cards[index].active)
        {
            //if 0(hdmi) 4(cvbs) is active then card[0], card[4] is active
            cards[index].pcm = pcm_open(index, mDevice, PCM_OUT, &config);
            if (!cards[index].pcm || !pcm_is_ready(cards[index].pcm))
            {
                ALOGE("Unable to open PCM device (%s)\n", pcm_get_error(cards[index].pcm));
                goto exit;
            }
            pcm_prepare(cards[index].pcm);
            bufferSize = pcm_frames_to_bytes(cards[index].pcm, pcm_get_buffer_size(cards[index].pcm));
        }
    }
    #if 0
    cards[0].hub_pcm = pcm_open(0, 0, PCM_OUT, &config);
    if(cards[0].hub_pcm == NULL)
    else
    cards[0].pcm = pcm_open(1, 0, PCM_OUT, &config);
    //pcm_prepare(cards[0].pcm);
    #endif

    while (wavLength > 0) {
        if (exitPending()) goto exit;
        size_t count = WRITE_BUF;
        if (count > wavLength)
            count = wavLength;

        /* write card */
        for(int index = 0; index < CARD_NUM; index++)
        {
            if(1 == cards[index].active)
            {
                if(pcm_write(cards[index].pcm, wavData, count))
                {
                    ALOGE("pcm_write failed (%s)", pcm_get_error(cards[index].pcm));
                    goto exit;
                }
            }
        }
        wavData += count;
        wavLength -= count;
    }

exit:
    /* close card */
    for(int index = 0; index < CARD_NUM; index++)
    {
        if(cards[index].pcm)
        {
            pcm_close(cards[index].pcm);
            cards[index].pcm = NULL;
        }
        if(cards[index].hub_pcm)
        {
            pcm_close(cards[index].hub_pcm);
            cards[index].hub_pcm = NULL;
        }
    }
    delete mCurrentFile;
    mCurrentFile = NULL;
    return false;
}


AudioMap::AudioMap()
{}
AudioMap::~AudioMap()
{}

int AudioMap::init_audio_map()
{
    char path[PATH_LEN] = {0};
    char name[NAME_LEN] = {0};
    int index = 0;
    int ret = -1;
    int fd = -1;
    for(; index < AUDIO_MAP_CNT; index++)
    {
        memset(path, 0, PATH_LEN);
        memset(name, 0, NAME_LEN);
        sprintf(path, "/proc/asound/card%d/id", index);

        fd = open(path, O_RDONLY, 0);
        if(fd < 0)
        {return -1;}

        ret = read(fd, name, NAME_LEN);
        if(ret < 0)
        {return -2;};

        int innerindex = 0;
        for(; innerindex < AUDIO_MAP_CNT; innerindex++)
        {
            //search for hub card num
            if(strncmp(HUB, name, strlen(HUB)) == 0)
            {
                output_card = index;
            }
            //search for ordinary card num
            if((strlen(audio_name_map[innerindex].name) != 0)
                    && (strncmp(audio_name_map[innerindex].name, name, strlen(audio_name_map[innerindex].name)) == 0))
            {
                audio_name_map[innerindex].card_num = index;
            }
            else
            {}
        }
        close(fd);
    }
    return 1;
}
int AudioMap::reinit_card(struct active_pcm (&cards)[32], int periodsize, int periodcount)
{
    int index = 0;
    for(; index < AUDIO_MAP_CNT; index++    )
    {
        if(audio_name_map[index].active == 1)
        {
            int card_num = audio_name_map[index].card_num;
            cards[card_num].active = 1;
            cards[card_num].periodsize = periodsize;
            cards[card_num].periodcount = periodcount;
            cards[card_num].output_device= audio_name_map[index].output_device;
        }
    }
    return 1;
}
} // namespace android
