package com.interlinkj.android.remotecamera;

import java.io.*;

import android.bluetooth.BluetoothSocket;

/***
 * Bluetooth接続を管理するクラス
 * @author Ito
 *
 */
public class BluetoothConnection {
	private static final BluetoothConnection mInstance = new BluetoothConnection();

	public static BluetoothConnection getInstance() {
		return mInstance;
	}

	private BluetoothConnection() { }

	private BluetoothSocket mSocket;
	private OutputStream mOutStream;
	private InputStream mInStream;
	private boolean mConnectFlag = false;
	private boolean mBlockFlag = false;

	public void setSocket(BluetoothSocket aSocket) {
		mSocket = aSocket;

		try {
			mOutStream = mSocket.getOutputStream();
			mInStream = mSocket.getInputStream();
			mConnectFlag = true;
		} catch(Exception e) {

		}
	}

	public BluetoothSocket getSocket() {
		return mSocket;
	}
	
	public boolean isConnecting() {
		return mConnectFlag;
	}
	
	public int read(byte[] aBuf) {
		mBlockFlag = true;
		int count = 0;
		
		try {
			count = mInStream.read(aBuf);
		} catch(Exception e) {
			e.printStackTrace();
		}
		
		mBlockFlag = false;
		return count;
	}
	
	public void write(byte[] aBuf) {
		mBlockFlag = true;
		try {
			mOutStream.write(aBuf);
		} catch(Exception e) {
			e.printStackTrace();
		}
		mBlockFlag = false;
	}
	
	public void close() {
		try {
			while(true) {
				if(!mBlockFlag) {
					mSocket.close();
					mConnectFlag = false;
					return;
				}
			}
		} catch(IOException e) {
			e.printStackTrace();
		}
	}
}
