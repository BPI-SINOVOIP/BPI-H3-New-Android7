package com.android.tv.settings.device.display;

import android.app.Fragment;

import com.android.tv.settings.BaseSettingsFragment;
import com.android.tv.settings.TvSettingsActivity;
import com.android.tv.settings.device.sound.SoundFragment;
import com.android.tv.settings.device.sound.SoundActivity.SettingsFragment;

public class DisplayActivity extends TvSettingsActivity {
	@Override
	protected Fragment createSettingsFragment() {
		return SettingsFragment.newInstance();
	}

	public static class SettingsFragment extends BaseSettingsFragment {

		public static SettingsFragment newInstance() {
			return new SettingsFragment();
		}

		@Override
		public void onPreferenceStartInitialScreen() {
			final DisplayFragment fragment = DisplayFragment.newInstance();
			startPreferenceFragment(fragment);
		}
	}
}
