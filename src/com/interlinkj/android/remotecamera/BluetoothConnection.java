package com.interlinkj.android.remotecamera;

import android.bluetooth.*;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.interlinkj.android.remotecamera.RemoteCamera.MESSAGE_CONNECT_FAILED;
import static com.interlinkj.android.remotecamera.RemoteCamera.MESSAGE_CONNECT_SUCCESS;

/**
 * Bluetooth接続を管理するクラス
 * 
 * @author Ito
 * 
 */
public class BluetoothConnection extends Connection {
	private static final UUID SERIAL_PORT_PROFILE = UUID
			.fromString("00001101-0000-1000-8000-00805F9B34FB");
	private static final BluetoothConnection mInstance = new BluetoothConnection();

	public static BluetoothConnection getInstance() {
		return mInstance;
	}

	/**
	 * コンストラクタ
	 */
	private BluetoothConnection() {
		mAdapter = BluetoothAdapter.getDefaultAdapter();
		Set<BluetoothDevice> mBondedDevices = mAdapter.getBondedDevices();
		mDeviceTable = new HashMap<String, String>();
		for(BluetoothDevice device : mBondedDevices) {
			mDeviceTable.put(device.getName(), device.getAddress());
		}
	}

	private BluetoothAdapter mAdapter;
	private BluetoothSocket mSocket = null;
	private OutputStream mOutStream = null;
	private InputStream mInStream = null;
	private boolean mConnectFlag = false;
	private Handler mHandler = null;
	private Map<String, String> mDeviceTable;

	public synchronized void setSocket(BluetoothSocket aSocket) {
		mSocket = aSocket;

		try {
			setOutputStream(mSocket.getOutputStream());
			setInputStream(mSocket.getInputStream());
			mConnectFlag = true;
		} catch(Exception ignored) {

		}
		notifyAll();
	}

	public BluetoothSocket getSocket() {
		return mSocket;
	}

	public synchronized void setHandler(Handler aHandler) {
		mHandler = aHandler;
		notifyAll();
	}

	@Override
	public boolean isConnecting() {
		return mConnectFlag;
	}

	private class ConnectTask extends
			AsyncTask<BluetoothDevice, Integer, BluetoothSocket> {
		Message mMsg;

		@Override
		protected void onPreExecute() {
			mMsg = mHandler.obtainMessage();
		}

		@Override
		protected BluetoothSocket doInBackground(BluetoothDevice... aDevice) {
			try {
				mSocket = aDevice[0]
						.createRfcommSocketToServiceRecord(SERIAL_PORT_PROFILE);
			} catch(IOException ignored) {

			}

			try {
				mSocket.connect();
				mMsg.what = MESSAGE_CONNECT_SUCCESS;
			} catch(IOException e) {
				try {
					mSocket.close();
				} catch(IOException ignored) {

				}
				mMsg.what = MESSAGE_CONNECT_FAILED;
			}

			return mSocket;
		}

		@Override
		protected void onPostExecute(BluetoothSocket aSocket) {
			mHandler.sendMessage(mMsg);
		}
	}

	public class ConnectThread extends Thread {
		private BluetoothSocket mmSocket = null;
		private BluetoothDevice mmDevice = null;

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
			} catch(IOException ignored) {
			}
			mmSocket = tmp;
		}

		public void run() {
			// 通信開始前にデバイスの探索を中止させる
			if(mAdapter.isDiscovering()) {
				mAdapter.cancelDiscovery();
			}

			Message msg = mHandler.obtainMessage();
			msg.obj = mmDevice.getName();
			try {
				// ソケットを利用して通信を開始する
				mmSocket.connect();

				msg.what = MESSAGE_CONNECT_SUCCESS;
				BluetoothConnection.getInstance().setSocket(mmSocket);
				mConnectFlag = true;
			} catch(IOException connectException) {
				// 例外が発生した場合はソケットを閉じ処理を抜ける
				try {
					mmSocket.close();
				} catch(IOException ignored) {
				}
				msg.what = MESSAGE_CONNECT_FAILED;
			} finally {
				mHandler.sendMessage(msg);
			}
		}

		/** Will cancel an in-progress connection, and close the socket */
		public void cancel() {
			try {
				mmSocket.close();
			} catch(IOException ignored) {
			}
		}
	}

	@Override
	public synchronized void connect(String address) {
		while(mHandler == null) {
			try {
				wait();
			} catch(InterruptedException ignored) {
			}
		}

		BluetoothDevice device = mAdapter.getRemoteDevice(address);
		ConnectThread th = new ConnectThread(device);
		th.start();
	}

	@Override
	public Set<String> getDeviceNameSet() {
		return mDeviceTable.keySet();
	}

	@Override
	public String getDeviceAddress(String name) {
		return mDeviceTable.get(name);
	}

	@Override
	public int read(byte[] aBuf) {
		int count = 0;

		try {
			count = mInStream.read(aBuf);
		} catch(IOException ignored) {
		}

		return count;
	}

	@Override
	public void write(byte[] aBuf) {
		try {
			mOutStream.write(aBuf);
		} catch(IOException ignored) {
		}
	}

	@Override
	public synchronized void close() {
		try {
			mSocket.close();
			mInStream = null;
			mOutStream = null;
			mSocket = null;
			mConnectFlag = false;
		} catch(IOException ignored) {
		}
	}

	public synchronized void setInputStream(InputStream is) {
		mInStream = is;
		notifyAll();
	}

	public synchronized void setOutputStream(OutputStream os) {
		mOutStream = os;
		notifyAll();
	}
}
