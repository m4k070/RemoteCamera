package com.interlinkj.android.remotecamera;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.Timer;

import com.interlinkj.android.remotecamera.R;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

public class RemoteCamera extends Activity {
	public static final String TAG = "RemoteCamera";
	public static final int MESSAGE_SHUTTER = 0;
	public static final int MESSAGE_DIALOG_SHOW = 1;
	public static final int REQUEST_ENABLE_BLUETOOTH = 1;
	private static final String RECENT_DEVICE = "recent_device";

	private Context mContext;
	private Handler mHandler;
	private Camera mCamera = null;
	private Bitmap mBitmap;
	private Preview mPreview;
	private BluetoothAdapter mAdapter;
	private BluetoothDevice mDevice;
	private static AlertDialog mDialog;
	
	static {
		System.loadLibrary("yuv420sp2rgb");
	}
	
	public native void yuv420sp2rgb(int[] rgb, byte[] yuv420sp, int width, int height, int type);
	
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		Window window = getWindow();
		window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.main);


	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.menu, menu);
		return true;
	}
	
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch(item.getItemId()) {
		case R.id.item_exit:	// 終了
			finish();
			break;
		case R.id.item_recent:	// Recent Device
			// 規定の接続デバイスのアドレスを読み込み
			final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
			String addr = prefs.getString(RECENT_DEVICE, null);
			
			if(null == addr) {	// 規定の接続デバイスが無い場合
				List<String> deviceList = new ArrayList<String>();
				for(BluetoothDevice device : mAdapter.getBondedDevices()) {
					deviceList.add(device.getName());
				}
				final String[] deviceNames = deviceList.toArray(new String[0]);
				// 接続デバイス選択ダイアログを作成
				mDialog = new AlertDialog.Builder(this)
					.setTitle(R.string.dialog_title)
					.setItems(deviceNames, new DialogInterface.OnClickListener() {
						// 選択されたデバイスのアドレスを取得し接続
						public void onClick(DialogInterface dialoginterface, int i) {
							String address = null;
							for(BluetoothDevice device : mAdapter.getBondedDevices()) {
								if(device.getName().equals(deviceNames[i])) {
									address = device.getAddress();
								}
							}
							// 規定の接続デバイスとして保存
							Editor editor = prefs.edit();
							editor.putString(RECENT_DEVICE, address);
							editor.commit();
							
							connectDevice(address);
						}
					})
					.create();
				
				// ダイアログ表示を実行させる
				Message msg = mHandler.obtainMessage();
				msg.what = MESSAGE_DIALOG_SHOW;
				mHandler.sendMessage(msg);
			} else {
				connectDevice(addr);
			}
			break;
		}
		
		return true;
	}
	
	@Override
	public void onResume() {
		super.onResume();
		
		mContext = getApplicationContext();
		ReceiveHandler rHandler = new ReceiveHandler();
		rHandler.setShutterCallback(mShutterListener);
		rHandler.setPictureCallback(mPictureCallback);
		rHandler.setJpegCallback(mJpegCallback);
		mHandler = rHandler;
		mPreview = (Preview)findViewById(R.id.surfaceView1);
		mPreview.setHandler(mHandler);
		
		if(!ensureBluetooth()) {
			ensureEnabled();
		}
	}
	
	@Override
	public void onPause() {
		super.onPause();
		
		closeCamera();
		mDialog = null;
		mHandler = null;
	}
	
	public void setCamera(Camera c) {
		mCamera = c;
		((ReceiveHandler)mHandler).setCamera(c);
//		mCamera.setPreviewCallback(mPreviewCallback);
	}
	
	public void closeCamera() {
		if(mCamera != null) {
			mCamera.stopPreview();
			mCamera.release();
			mCamera = null;
		}
	}
	
	private void connectDevice(String addr) {
		mDevice = mAdapter.getRemoteDevice(addr);
		ConnectThread connectThread = new ConnectThread(mDevice);
		connectThread.setHandler(mHandler);
		connectThread.start();
	}
	
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

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch(requestCode) {
		case REQUEST_ENABLE_BLUETOOTH:
			if(resultCode == Activity.RESULT_OK) {
				onBluetoothEnabled();
				return;
			}
			break;
		}
	}

	private void onBluetoothEnabled() {
		ensureBluetooth();
	}

	private Camera.ShutterCallback mShutterListener = new Camera.ShutterCallback() {
		public void onShutter() {
			Log.i(TAG, "onShutter");
		}
	};
	
	private Camera.PictureCallback mPictureCallback = new Camera.PictureCallback() {
		public void onPictureTaken(byte[] data, Camera camera) {
			Log.i(TAG, "onPictureTaken");
			
			if(null == data) {
				return;
			}
			Camera.Parameters params = camera.getParameters();
			Camera.Size size = params.getPictureSize();
			
			BitmapFactory.Options opts = new BitmapFactory.Options();
			int ScaleW = size.width / 720 + 1;
			int ScaleH = size.height / 480 + 1;
			opts.inSampleSize = Math.max(ScaleW, ScaleH);
			mBitmap = BitmapFactory.decodeByteArray(data, 0, data.length, opts);
			
			mBitmap = null;
		}
	};
	
	private Camera.PictureCallback mJpegCallback = new Camera.PictureCallback() {
		public void onPictureTaken(byte[] data, Camera camera) {
			SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
			String saveFilename = Environment.getExternalStorageDirectory() +
							"/Picture/" + fmt.format(new Date()) + ".jpg";
			try {
				FileOutputStream fos =
					new FileOutputStream(saveFilename);
					fos.write(data);
					fos.close();
					Toast.makeText(mContext,
							saveFilename + Character.LINE_SEPARATOR + R.string.picture_save_success,
							Toast.LENGTH_LONG);
			} catch(Exception e) {
				e.printStackTrace();
				Toast.makeText(mContext, R.string.picture_save_failed, Toast.LENGTH_LONG);
			}
			camera.startPreview();
		}
	};
	
	private Camera.PreviewCallback mPreviewCallback = new Camera.PreviewCallback() {
		public void onPreviewFrame(byte[] data, Camera camera) {
			Log.i(TAG, "onPreviewFrame");
			
			// プレビュー画像をYUV420からRGBに変換
			Camera.Size size = camera.getParameters().getPreviewSize();
			int[] rgb = new int[size.width * size.height];
//			decodeYUV420SP(rgb, data, size.width, size.height);
			yuv420sp2rgb(rgb, data, size.width, size.height, 1);
			mBitmap = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.RGB_565);
			mBitmap.setPixels(rgb, 0, size.width, 0, 0, size.width, size.height);
		}
	};
	
	public static class ReceiveHandler extends Handler {
		private Camera mmCamera;
		private Camera.ShutterCallback mmShutterListener;
		private Camera.PictureCallback mmPictureCallback;
		private Camera.PictureCallback mmJpegCallback;

		public void setCamera(Camera aCamera) {
			mmCamera = aCamera;
		}
		
		public void setShutterCallback(Camera.ShutterCallback aShutterCallback)  {
			mmShutterListener = aShutterCallback;
		}
		
		public void setPictureCallback(Camera.PictureCallback aPictureCallback) {
			mmPictureCallback = aPictureCallback;
		}
		
		public void setJpegCallback(Camera.PictureCallback aJpegCallback) {
			mmJpegCallback = aJpegCallback;
		}
		
		@Override
		public void handleMessage(Message msg) {
			switch(msg.what) {
			case MESSAGE_SHUTTER:
				mmCamera.autoFocus(null);
				mmCamera.takePicture(null, null, mmJpegCallback);
				break;
			case MESSAGE_DIALOG_SHOW:
				mDialog.show();
			}
		}
	}
	
	private View.OnTouchListener mTouchListener =
		new View.OnTouchListener() {
			public boolean onTouch(View view, MotionEvent motionevent) {
				Message msg = mHandler.obtainMessage();
				msg.what = MESSAGE_SHUTTER;
				mHandler.sendMessage(msg);
				return false;
			}
		};
	
	/**
	 *  YUV420SP to BMP
	 * @param rgb 出力結果が保存される配列
	 * @param yuv420sp 変換元データ
	 * @param width 画像の幅
	 * @param height 画像の高さ
	 */
	public static void decodeYUV420SP(int[] rgb, byte[] yuv420sp, int width,
			int height) {
		Log.i(TAG, "decodeYUV420SP");
		final int frameSize = width * height;

		try {
			for (int j = 0, yp = 0; j < height; j++) {
				int uvp = frameSize + (j >> 1) * width, u = 0, v = 0;
				for (int i = 0; i < width; i++, yp++) {
					int y = (0xff & ((int) yuv420sp[yp])) - 16;
					if (y < 0)
						y = 0;
					if ((i & 1) == 0) {
						v = (0xff & yuv420sp[uvp++]) - 128;
						u = (0xff & yuv420sp[uvp++]) - 128;
					}

					int y1192 = 1192 * y;
					int r = (y1192 + 1634 * v);
					int g = (y1192 - 833 * v - 400 * u);
					int b = (y1192 + 2066 * u);

					if (r < 0)
						r = 0;
					else if (r > 262143)
						r = 262143;
					if (g < 0)
						g = 0;
					else if (g > 262143)
						g = 262143;
					if (b < 0)
						b = 0;
					else if (b > 262143)
						b = 262143;

					rgb[yp] = 0xff000000 | ((r << 6) & 0xff0000)
							| ((g >> 2) & 0xff00) | ((b >> 10) & 0xff);
				}
			}
		} catch (Exception e) {
			Log.e(TAG, "Exception");
		}

		Log.i(TAG, "end decode");
	}
}