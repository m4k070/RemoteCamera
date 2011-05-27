package com.interlinkj.android.remotecamera;

import java.io.IOException;
import java.util.UUID;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.Message;

import static com.interlinkj.android.remotecamera.RemoteCamera.MESSAGE_CONNECT_FAILED;
import static com.interlinkj.android.remotecamera.RemoteCamera.MESSAGE_CONNECT_SUCCESS;

;

public class ConnectThread extends Thread {
	private static final UUID SERIAL_PORT_PROFILE = UUID
			.fromString("00001101-0000-1000-8000-00805F9B34FB");
	private final BluetoothSocket mSocket;
	private final BluetoothDevice mDevice;
	private Handler mHandler;
	private static ConnectedThread mConnectedThread;

	public void setHandler(Handler aHandler) {
		mHandler = aHandler;
	}

	public ConnectThread(BluetoothDevice device) {
		// Use a temporary object that is later assigned to mmSocket,
		// because mmSocket is final
		BluetoothSocket tmp = null;
		mDevice = device;

		// BluetoothSocketの作成
		try {
			// UUIDを指定してrfcommのソケットを作成
			tmp = device.createRfcommSocketToServiceRecord(SERIAL_PORT_PROFILE);
		} catch(IOException e) {
		}
		mSocket = tmp;
	}

	public void run() {
		// 通信開始前にデバイスの探索を中止させる
//		mAdapter.cancelDiscovery();

		Message msg = mHandler.obtainMessage();
		msg.obj = mDevice.getName();
		try {
			// ソケットを利用して通信を開始する
			mSocket.connect();

			msg.what = MESSAGE_CONNECT_SUCCESS;
			// Do work to manage the connection (in a separate thread)
			manageConnectedSocket(mSocket);
		} catch(IOException connectException) {
			// 例外が発生した場合はソケットを閉じ処理を抜ける
			try {
				mSocket.close();
			} catch(IOException closeException) {
			}
			msg.what = MESSAGE_CONNECT_FAILED;
		} finally {
			mHandler.sendMessage(msg);
		}
	}

	/** Will cancel an in-progress connection, and close the socket */
	public void cancel() {
		try {
			mSocket.close();
		} catch(IOException e) {
		}
	}

	public void manageConnectedSocket(BluetoothSocket aSocket) {
		BluetoothConnection.getInstance().setSocket(aSocket);
		mConnectedThread = new ConnectedThread();
		mConnectedThread.setHandler(mHandler);
		mConnectedThread.start();
	}
}
