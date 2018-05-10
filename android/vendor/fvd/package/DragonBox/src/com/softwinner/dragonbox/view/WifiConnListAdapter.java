package com.softwinner.dragonbox.view;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.softwinner.dragonbox.R;
import com.softwinner.dragonbox.entity.WifiConnInfo;
import com.softwinner.dragonbox.utils.WifiUtil;

public class WifiConnListAdapter extends BaseAdapter{

		
		List<WifiInfo> mWifiInfos = new ArrayList<WifiInfo>();
		private LayoutInflater mInflater;
		public WifiConnListAdapter(Context context) {
			mInflater = LayoutInflater.from(context);
		}
		public void setWifiConnInfos(List<WifiInfo> wifiInfos) {
			mWifiInfos = wifiInfos;
			notifyDataSetChanged();
		}

		@Override
		public int getCount() {
			return mWifiInfos.size();
		}

		@Override
		public WifiInfo getItem(int position) {
			return mWifiInfos.get(position);
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public View getView(int position, View convert, ViewGroup root) {
			if (convert == null) {
				convert = mInflater.inflate(R.layout.case_wifi_item_conn, null);
				ViewHolder holder = new ViewHolder();
				holder.connIp = (TextView) convert.findViewById(R.id.case_wifi_conn_ip);
				holder.strength = (TextView) convert
						.findViewById(R.id.case_wifi_conn_strength);
				convert.setTag(holder);
			}
			ViewHolder holder = (ViewHolder) convert.getTag();
			holder.connIp.setText(WifiUtil.intToIp(getItem(position).getIpAddress()));

			/*int level = getItem(position).rssi != Integer.MIN_VALUE ? WifiManager
					.calculateSignalLevel(getItem(position).rssi, 16) : 0;
			level = getItem(position).rssi != Integer.MIN_VALUE ? WifiManager
					.calculateSignalLevel(getItem(position).rssi, 4) : 0;*/

			/*String strength = getItem(position).rssi == Integer.MIN_VALUE ? "不在范围"
					: "" + getItem(position).rssi + " DB ";*/
			holder.strength.setText(getItem(position).getRssi() + "DB");

			return convert;
		}
		
		private class ViewHolder {
			TextView connIp;
			TextView strength;
		}

}
