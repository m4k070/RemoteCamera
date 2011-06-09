package com.interlinkj.android.remotecamera;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

import static com.interlinkj.android.remotecamera.RemoteShutter.TAG;
import static com.interlinkj.android.remotecamera.RemoteShutter.MESSAGE_PREVIEW_DATA;
import static com.interlinkj.android.remotecamera.RemoteShutter.MESSAGE_JPEG_DATA;

public class ReadThread extends Thread {
	private Handler mmHandler;

	public void setHandler(Handler aHandler) {
		mmHandler = aHandler;
	}

	public ReadThread() {
	}

	public void run() {
		final byte[] buffer = new byte[1024 * 1024 * 2]; // buffer store for the stream
		int bytes; // bytes returned from read()

		// 例外が発生するまで受信処理を続ける
		while(true) {
			// Read from the InputStream
			bytes = BluetoothConnection.getInstance().read(buffer);

			if(bytes > 0) {
				// Send the obtained bytes to the UI Activity
				Message msg = mmHandler.obtainMessage(MESSAGE_JPEG_DATA, bytes,
						-1, buffer);
				if(!mmHandler.sendMessage(msg)) {
					Log.e(TAG, "sendMessage Failed.");
				}
			}

		}
	}

	// Call this from the main Activity to shutdown the connection
	public void cancel() {
		BluetoothConnection.getInstance().close();
	}
}
