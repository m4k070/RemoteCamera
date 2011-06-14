package com.interlinkj.android.remotecamera;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

import static com.interlinkj.android.remotecamera.RemoteCamera.MESSAGE_SHUTTER;
import static com.interlinkj.android.remotecamera.RemoteCamera.TAG;

public class ConnectedThread extends Thread {
	public boolean running = false;
	private Handler mHandler;

	public void setHandler(Handler aHandler) {
		mHandler = aHandler;
	}

	public ConnectedThread() {
	}

	public void run() {
		byte[] buffer = new byte[1024]; // buffer store for the stream
		int bytes; // bytes returned from read()
		
		// 例外が発生するまで受信処理を続ける
		Connection connection = BluetoothConnection.getInstance();
		while(running) {
			// Read from the InputStream
			bytes = connection.read(buffer);

			if(bytes > 0) {
				// Send the obtained bytes to the UI Activity
				Message msg = mHandler.obtainMessage(MESSAGE_SHUTTER, bytes,
						-1, buffer);
				if(!mHandler.sendMessage(msg)) {
					Log.e(TAG, "sendMessage Failed.");
				}
			}
		}
	}

	/* Call this from the main Activity to shutdown the connection */
	public void cancel() {
		running = false;
		BluetoothConnection.getInstance().close();
	}
}
