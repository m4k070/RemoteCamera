package com.interlinkj.android.remotecamera;

import java.io.IOException;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.Message;

import static com.interlinkj.android.remotecamera.RemoteShutter.MESSAGE_DIALOG_SHOW;
import static com.interlinkj.android.remotecamera.RemoteShutter.MESSAGE_DIALOG_DISMISS;;

public class AcceptThread extends Thread {
	private final BluetoothServerSocket mServerSocket;
	private Handler mHandler;
	public final UUID SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
	
	public AcceptThread(BluetoothAdapter anAdapter) {
		BluetoothServerSocket tmp = null;
		try {
			tmp = anAdapter.listenUsingRfcommWithServiceRecord("RemoteShutter", SPP);
		} catch(IOException e) {
			
		}
		mServerSocket = tmp;
	}
	
	public void setHandler(Handler aHandler) {
		mHandler = aHandler;
	}
	
	@Override
	public void run() {
		Message msg = mHandler.obtainMessage();
		msg.what = MESSAGE_DIALOG_SHOW;
		mHandler.sendMessage(msg);
		
		BluetoothSocket socket = null;
		while(true) {
			try {
				socket = mServerSocket.accept();
			} catch(IOException e) {
				break;
			}
			
			if(socket != null) {
				BluetoothConnection.getInstance().setSocket(socket);
				try {
					mServerSocket.close();
				} catch(IOException e) { }
				break;
			}
		}
		
		msg = mHandler.obtainMessage();
		msg.what = MESSAGE_DIALOG_DISMISS;
		mHandler.sendMessage(msg);

	}
}
