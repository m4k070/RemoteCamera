package com.interlinkj.android.remotecamera;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

import static com.interlinkj.android.remotecamera.RemoteShutter.TAG;
import static com.interlinkj.android.remotecamera.RemoteShutter.MESSAGE_PREVIEW_DATA;
import static com.interlinkj.android.remotecamera.RemoteShutter.MESSAGE_JPEG_DATA;

public class ReceivePreviewThread extends Thread {
	private Handler mHandler;

	public void setHandler(Handler aHandler) {
		mHandler = aHandler;
	}

	public ReceivePreviewThread() { }

	public void run() {
		final byte[] buffer = new byte[1024 * 1024 * 2];	// buffer store for the stream
		int bytes; 											// bytes returned from read()

		// 例外が発生するまで受信処理を続ける
		while(true) {
			// Read from the InputStream
			bytes = BluetoothConnection.getInstance().read(buffer);

			if(bytes > 0) {
				// カメラ側から送られてきたプレビューのbyte配列を処理
				Message msg = mHandler.obtainMessage(MESSAGE_JPEG_DATA, bytes,
						-1, buffer);
				if(!mHandler.sendMessage(msg)) {
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
