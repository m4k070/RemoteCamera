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
		final byte[] buffer = new byte[1024]; // buffer store for the stream
		int bytes; // bytes returned from read()
		
		// 例外が発生するまで受信処理を続ける
		while(running) {
			// Read from the InputStream
			bytes = BluetoothConnection.getInstance().read(buffer);

			if(bytes > 0) {
				// Send the obtained bytes to the UI Activity
				Message msg = mHandler.obtainMessage(MESSAGE_SHUTTER, bytes,
						-1, buffer);
				if(!mHandler.sendMessage(msg)) {
					Log.e(TAG, "sendMessage Failed.");
				}

//				mHandler.post(new Runnable() {
//					public void run() {
//						StringBuilder sb = new StringBuilder();
//						sb.append(mTextView);
//						sb.append("\r\n");
//						sb.append(new String(buffer));
//						mTextView.setText(sb.toString());
//					}
//				});
			}

		}
	}

	/* Call this from the main Activity to send data to the remote device */
	public void write(byte[] bytes) {
		BluetoothConnection.getInstance().write(bytes);
	}

	/* Call this from the main Activity to shutdown the connection */
	public void cancel() {
		running = false;
		BluetoothConnection.getInstance().close();
	}
}
