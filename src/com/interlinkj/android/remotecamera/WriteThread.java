package com.interlinkj.android.remotecamera;

/***
 * Bluetooth送信用スレッド
 * @author Ito
 *
 */
public class WriteThread extends Thread {
	private byte[] mData;
	
	public WriteThread(byte[] aData) {
		setData(aData);
	}
	
	public void setData(byte[] aData) {
		mData = aData;
	}
	
	public void run() {
		BluetoothConnection.getInstance().write(mData);
	}
}
