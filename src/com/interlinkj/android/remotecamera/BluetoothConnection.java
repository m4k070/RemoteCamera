package com.interlinkj.android.remotecamera;

import java.io.*;

import android.bluetooth.BluetoothSocket;

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
		int count = 0;
		
		try {
			count = mInStream.read(aBuf);
		} catch(Exception e) {
			e.printStackTrace();
		}
		
		return count;
	}
	
	public void write(byte[] aBuf) {
		try {
			mOutStream.write(aBuf);
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	public void close() {
		try {
			mSocket.close();
			mConnectFlag = false;
		} catch(IOException e) {
			e.printStackTrace();
		}
	}
}
