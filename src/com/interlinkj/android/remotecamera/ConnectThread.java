package com.interlinkj.android.remotecamera;

import java.io.IOException;
import java.util.UUID;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;

public class ConnectThread extends Thread {
	private static final UUID SERIAL_PORT_PROFILE = UUID
		.fromString("00001101-0000-1000-8000-00805F9B34FB");
	private final BluetoothSocket mmSocket;
	private final BluetoothDevice mmDevice;

	public ConnectThread(BluetoothDevice device) {
		// Use a temporary object that is later assigned to mmSocket,
		// because mmSocket is final
		BluetoothSocket tmp = null;
		mmDevice = device;

		// BluetoothSocketの作成
		try {
			// UUIDを指定してrfcommのソケットを作成
			tmp = device
					.createRfcommSocketToServiceRecord(SERIAL_PORT_PROFILE);
		} catch(IOException e) {
		}
		mmSocket = tmp;
	}

	public void run() {
		// 通信開始前にデバイスの探索を中止させる
//		mAdapter.cancelDiscovery();

		try {
			// ソケットを利用して通信を開始する
			mmSocket.connect();
		} catch(IOException connectException) {
			// 例外が発生した場合はソケットを閉じ処理を抜ける
			try {
				mmSocket.close();
			} catch(IOException closeException) {
			}
			return;
		}

		// Do work to manage the connection (in a separate thread)
		manageConnectedSocket(mmSocket);
	}

	/** Will cancel an in-progress connection, and close the socket */
	public void cancel() {
		try {
			mmSocket.close();
		} catch(IOException e) {
		}
	}
	
	public void manageConnectedSocket(BluetoothSocket mmSocket) {
		ConnectedThread mConnectedThread = new ConnectedThread(mmSocket);
		mConnectedThread.start();
	}
}
