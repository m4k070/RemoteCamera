package com.interlinkj.android.remotecamera;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import static com.interlinkj.android.remotecamera.RemoteCamera.MESSAGE_READ;
import static com.interlinkj.android.remotecamera.RemoteCamera.TAG;

public class ConnectedThread extends Thread {
	private final BluetoothSocket mmSocket;
	private final InputStream mmInStream;
	private final OutputStream mmOutStream;
	private Handler mmHandler;

	public void setHandler(Handler aHandler) {
		mmHandler = aHandler;
	}

	public ConnectedThread(BluetoothSocket socket) {
		mmSocket = socket;
		InputStream tmpIn = null;
		OutputStream tmpOut = null;

		// ソケットからInputStreamとOutputStreamを取得する
		try {
			tmpIn = socket.getInputStream();
			tmpOut = socket.getOutputStream();
		} catch(IOException e) {
		}

		mmInStream = tmpIn;
		mmOutStream = tmpOut;
	}

	public void run() {
		final byte[] buffer = new byte[1024]; // buffer store for the stream
		int bytes; // bytes returned from read()

		// 例外が発生するまで受信処理を続ける
		while(true) {
			try {
				// Read from the InputStream
				bytes = mmInStream.read(buffer);

				// Send the obtained bytes to the UI Activity
				Message msg = mmHandler.obtainMessage(MESSAGE_READ, bytes,
						-1, buffer);
				if(!mmHandler.sendMessage(msg)) {
					Log.e(TAG, "sendMessage Failed.");
				}

				/*
				 * mHandler.post(new Runnable() { public void run() {
				 * StringBuilder sb = new StringBuilder();
				 * sb.append(mTextView); sb.append("\r\n"); sb.append(new
				 * String(buffer)); mTextView.setText(sb.toString()); } });
				 */
			} catch(IOException e) {
				break;
			}
		}
	}

	/* Call this from the main Activity to send data to the remote device */
	public void write(byte[] bytes) {
		try {
			mmOutStream.write(bytes);
		} catch(IOException e) {
		}
	}

	/* Call this from the main Activity to shutdown the connection */
	public void cancel() {
		try {
			mmSocket.close();
		} catch(IOException e) {
		}
	}
}
