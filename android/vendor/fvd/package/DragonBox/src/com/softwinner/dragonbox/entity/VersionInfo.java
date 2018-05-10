package com.softwinner.dragonbox.entity;

public class VersionInfo {
	
	private String mFireware = "";
	private String mModel = "";
	private String mDispaly = "";
	private String mCpuFreq = "";
	
	public VersionInfo() {
		
	}
	
	public VersionInfo(String fireware, String model,
			String dispaly, String cpuFreq) {
		super();
		this.mFireware = fireware;
		this.mModel = model;
		this.mDispaly = dispaly;
		this.mCpuFreq = cpuFreq;
	}
	
	public String getFireware() {
		return mFireware;
	}
	public void setFireware(String fireware) {
		this.mFireware = fireware;
	}
	public String getModel() {
		return mModel;
	}
	public void setModel(String model) {
		this.mModel = model;
	}
	public String getDispaly() {
		return mDispaly;
	}
	public void setDispaly(String dispaly) {
		this.mDispaly = dispaly;
	}
	public String getCpuFreq() {
		return mCpuFreq;
	}
	public void setCpuFreq(String mCpuFreq) {
		this.mCpuFreq = mCpuFreq;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((mCpuFreq == null) ? 0 : mCpuFreq.hashCode());
		result = prime * result
				+ ((mDispaly == null) ? 0 : mDispaly.hashCode());
		result = prime * result
				+ ((mFireware == null) ? 0 : mFireware.hashCode());
		result = prime * result + ((mModel == null) ? 0 : mModel.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		/*if (getClass() != obj.getClass())
			return false;*/
		VersionInfo other = (VersionInfo) obj;
		if (mCpuFreq == null) {
			if (other.mCpuFreq != null)
				return false;
		} else if (!mCpuFreq.equals(other.mCpuFreq))
			return false;
		if (mDispaly == null) {
			if (other.mDispaly != null)
				return false;
		} else if (!mDispaly.equals(other.mDispaly))
			return false;
		if (mFireware == null) {
			if (other.mFireware != null)
				return false;
		} else if (!mFireware.equals(other.mFireware))
			return false;
		if (mModel == null) {
			if (other.mModel != null)
				return false;
		} else if (!mModel.equals(other.mModel))
			return false;
		return true;
	}
}
