package com.softwinner.dragonbox.utils;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.media.AudioManager;
import android.media.AudioManagerEx;
import android.widget.Toast;
public class AudioChannelUtil {
    private static ArrayList<String> lastAudioOutputChannels = null;
    private static AudioManagerEx am = null; 
	public AudioChannelUtil() {
		
	}
	/**
	 * set the output channel, when test output ,it always need set channel
	 * @param context
	 * @param channels may use AudioManager.AUDIO_NAME_***
     * @param setType  remeber to restore after set
	 * @return the ordinary channels.the values before changed.
	 */
	public static List<String> setOuputChannels(Context context, boolean setType,String... channels) {
        if(am == null){
            am = new AudioManagerEx(context);
        }
		if(lastAudioOutputChannels == null){
			lastAudioOutputChannels = am.getActiveAudioDevices(AudioManagerEx.AUDIO_OUTPUT_ACTIVE);	
		}
        if(setType){
		    ArrayList<String> newOutPutChannels = new ArrayList<String>();
		    for (String channel : channels) {
			    newOutPutChannels.add(channel);
		    }   
            am.setAudioDeviceActive(newOutPutChannels,AudioManagerEx.AUDIO_OUTPUT_ACTIVE);
		    return lastAudioOutputChannels;
        }else {
            am.setAudioDeviceActive(lastAudioOutputChannels,AudioManagerEx.AUDIO_OUTPUT_ACTIVE);
            return lastAudioOutputChannels;
        }
	}
}
