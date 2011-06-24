package com.interlinkj.android.remotecamera;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;

/**
 * シャッター側Activity
 * @author Ito
 *
 */
public class RemoteShutter extends Activity {
	public static final String TAG = "ReomteShutter";

	public static final int MESSAGE_CONNECT_SUCCESS = 0;
	public static final int MESSAGE_CONNECT_FAILED	= 1;
	public static final int MESSAGE_PREVIEW_DATA	= 2;
	public static final int MESSAGE_JPEG_DATA 		= 3;
	public static final int MESSAGE_DIALOG_SHOW		= 4;
	public static final int MESSAGE_DIALOG_DISMISS	= 5;

	public static final int REQUEST_ENABLE_BLUETOOTH = 0;

	private BluetoothAdapter mAdapter;	
	private Handler mHandler;
	private ProgressDialog mDialog;
	private ShutterPreview mSurface;
	private AcceptThread mAcceptThread = null;
	private static boolean mDebug = false;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.shutter);

		Button shutterBtn = (Button)findViewById(R.id.button_shoot);
		shutterBtn.setOnClickListener(mOnClickListener);

		if(!ensureBluetooth()) {
			Toast.makeText(this, "Device does not support Bluetooth",
					Toast.LENGTH_LONG).show();
		}
		if(!ensureEnabled()) {
			Toast.makeText(this, "Bluetooth is not enable", Toast.LENGTH_LONG)
					.show();
		}

		mHandler = new Handler() {
			@Override
			public void handleMessage(Message msg) {
				switch(msg.what) {
				case MESSAGE_DIALOG_SHOW:
					mDialog.show();
					break;
				case MESSAGE_DIALOG_DISMISS:
					mDialog.dismiss();
					ReceivePreviewThread readThread = new ReceivePreviewThread();
					readThread.setHandler(mHandler);
					readThread.start();
					break;
				case MESSAGE_PREVIEW_DATA:
					byte[] data = (byte[])msg.obj;
					int[] buf = new int[1024 * 1024 * 3];
					int width = 1;
					int height = 1;
					ByteArrayInputStream bai = new ByteArrayInputStream(data);
					DataInputStream dis = new DataInputStream(bai);
					try {
						width = dis.readInt();
						height = dis.readInt();
						for(int i = 0; i < width * height * 3; i++) {
							buf[i] = dis.readInt();
						}
					} catch(IOException e) { }

					Bitmap bmp = Bitmap.createBitmap(width, height,
							Bitmap.Config.RGB_565);
					bmp.setPixels(buf, 0, width, 0, 0, width, height);
					mSurface.bitmap = bmp;
					break;
				case MESSAGE_JPEG_DATA:
					byte[] jpegData = (byte[])msg.obj;
					Bitmap jpg = BitmapFactory.decodeByteArray(jpegData, 0, jpegData.length);
					mSurface.bitmap = jpg;
					break;
				}
			}
		};

		mSurface = (ShutterPreview)findViewById(R.id.surfaceView1);
		
		ApplicationInfo appInfo = getApplicationInfo();
		if((appInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
			mDebug = true;
		}
		
		if(mDebug) { Log.d(TAG, "RemoteShutter#onCreate()"); }
	}

	// メニュー作成時に呼び出される
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.shutter, menu);
		return true;
	}

	// メニュー項目選択時に呼び出される
	public boolean onOptionsItemSelected(MenuItem item) {
		switch(item.getItemId()) {
		// 接続待機開始
		case R.id.item_accept:
			startServer();
			mAcceptThread = null;
			return true;
		// 終了
		case R.id.item_shutter_exit:
			finish();
			return true;
		}
		return false;
	}

	public void onResume() {
		super.onResume();

		mDialog = new ProgressDialog(this);
		mDialog.setTitle("Bluetooth");
		mDialog.setMessage(getResources().getText(R.string.accepting));
		mDialog.setIndeterminate(true);
		mDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
		mDialog.setCancelable(true);
		mDialog.setOnCancelListener(mCancelListener);
		
		if(mAcceptThread != null) {
			mAcceptThread.resume();
		}
	}

	public void onPause() {
		super.onPause();

		if(mDialog.isShowing()) {
			mDialog.dismiss();
			mDialog = null;
		}
		
		if(mAcceptThread != null) {
			mAcceptThread.suspend();
		}
	}

	// カメラからの接続受付を開始
	public void startServer() {
		mAcceptThread = new AcceptThread(mAdapter);
		mAcceptThread.setHandler(mHandler);
		mAcceptThread.start();
	}

	private OnClickListener mOnClickListener = new OnClickListener() {

		@Override
		public void onClick(View v) {
			WriteThread writeThread = new WriteThread(new byte[] { 30 });
			writeThread.start();
		}
	};
	
	private DialogInterface.OnCancelListener mCancelListener =
		new DialogInterface.OnCancelListener() {
			@Override
			public void onCancel(DialogInterface listener) {
				mAcceptThread.cancel();
			}
	};

	private boolean ensureBluetooth() {
		mAdapter = BluetoothAdapter.getDefaultAdapter();
		if(null == mAdapter) {
			return false;
		}
		return true;
	}

	private boolean ensureEnabled() {
		if(!mAdapter.isEnabled()) {
			Intent i = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(i, REQUEST_ENABLE_BLUETOOTH);
			return false;
		}
		return true;
	}

	protected void onActivityResult(int requestCode, int resultCode, Intent i) {
		switch(requestCode) {
		case REQUEST_ENABLE_BLUETOOTH:
			if(resultCode == RESULT_OK) {
				mAdapter = BluetoothAdapter.getDefaultAdapter();
			} else {
				finish();
			}
		}
	}
}