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

#ifndef _BOOTANIMATION_AUDIOPLAYER_H
#define _BOOTANIMATION_AUDIOPLAYER_H

#include <utils/Thread.h>
#include <utils/SortedVector.h>
#include <utils/FileMap.h>
#include <utils/StrongPointer.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <tinyalsa/asoundlib.h>

#define CARD_NUM 32


struct active_pcm {
    int active;
    int periodsize;
    int periodcount;
    struct pcm *pcm;
    struct pcm *hub_pcm;
    int output_device;
};
namespace android {

class AudioPlayer : public Thread
{
public:
                AudioPlayer();
    virtual     ~AudioPlayer();
    bool        init(const char* config);

    void        playFile(FileMap* fileMap);

private:
    virtual bool        threadLoop();
	struct active_pcm   cards[CARD_NUM];
    bool closeHubRoute(struct mixer* mixer);

private:
    int                 mCard;      // ALSA card to use
    int                 mDevice;    // ALSA device to use
    int                 mPeriodSize;
    int                 mPeriodCount;
    struct mixer *      ahub_mixer;
    FileMap*            mCurrentFile;
};
class AudioMap
{
public:
    AudioMap();
    ~AudioMap();
    int init_audio_map();
    int reinit_card(struct active_pcm (&cards)[CARD_NUM], int periodsize, int periodcount);
};
} // namespace android

#endif // _BOOTANIMATION_AUDIOPLAYER_H
