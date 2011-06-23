package com.interlinkj.android.remotecamera;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import wiiremotej.WiiRemote;
import wiiremotej.WiiRemoteJ;
import wiiremotej.event.WRButtonEvent;
import wiiremotej.event.WiiRemoteAdapter;

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
import android.content.pm.ApplicationInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.Toast;

import static com.interlinkj.android.remotecamera.CameraSetting.RECENT_DEVICE_PREF_KEY;
import static com.interlinkj.android.remotecamera.CameraSetting.SAVE_PATH_PREF_KEY;
import static com.interlinkj.android.remotecamera.CameraSetting.FILENAME_FMT_PREF_KEY;

public class RemoteCamera extends Activity {
	public static final String TAG = "RemoteCamera";
	public static final int MESSAGE_SHUTTER = 0;
	public static final int MESSAGE_DIALOG_SHOW = 1;
	public static final int MESSAGE_CONNECT_FAILED = 2;
	public static final int MESSAGE_CONNECT_SUCCESS = 3;
	public static final int REQUEST_ENABLE_BLUETOOTH = 1;
	public static final int CAMERA_PREFERENCE = 2;

	private static boolean mDebug = false;
	private static Handler mHandler;
	private static AlertDialog mDialog; // 接続デバイス選択ダイアログ
	private static ConnectedThread mConnectedThread;
	private Context mContext;
	private Camera mCamera = null;
	private Bitmap mBitmap;
	private CameraPreview mPreview;
	private BluetoothAdapter mAdapter;
	private BluetoothDevice mDevice;
	private boolean isConnect = false;
	private byte[] mPreviewBuffer;
	private Connection mConnection;
	private boolean mUseAutofocus;
	private GestureDetector mGestureDetector;

	static {
		System.loadLibrary("yuv420sp2rgb");
	}

	public native void yuv420sp2rgb(int[] rgb, byte[] yuv420sp, int width,
			int height, int type);

	public synchronized byte[] getPreviewBuffer() {
		return mPreviewBuffer;
	}

	public synchronized void setPreviewBuffer(byte[] aBuf) {
		mPreviewBuffer = aBuf;
	}

	public boolean isUseAutofocus() {
		return mUseAutofocus;
	}

	public void setUseAutofocus(boolean flag) {
		mUseAutofocus = flag;
	}

	@Override
	public boolean dispatchTouchEvent(MotionEvent e) {
		mGestureDetector.onTouchEvent(e);
		return super.dispatchTouchEvent(e);
	}

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Window window = getWindow();
		window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.camera);

		if(!ensureBluetooth()) {
			Toast.makeText(this, "Device does not support Bluetooth",
					Toast.LENGTH_LONG).show();
		}
		if(!ensureEnabled()) {
			Toast.makeText(this, "Bluetooth is not enable", Toast.LENGTH_LONG)
					.show();
		}

		mContext = getApplicationContext();
		ReceiveHandler rHandler = new ReceiveHandler();
		rHandler.setShutterCallback(mShutterListener);
		rHandler.setPictureCallback(mPictureCallback);
		rHandler.setJpegCallback(mJpegCallback);
		rHandler.setContext(this);
		mHandler = rHandler;
		BluetoothConnection.getInstance().setHandler(rHandler);
		mPreview = new CameraPreview(this, null);
		mPreview.setHandler(mHandler);
		if(!mPreview.isClickable()) {
			mPreview.setClickable(true);
		}
		LinearLayout ll = (LinearLayout)findViewById(R.id.linearLayout1);
		ll.addView(mPreview, new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.FILL_PARENT,
				LinearLayout.LayoutParams.FILL_PARENT));

		mGestureDetector = new GestureDetector(this, mGestureListener);

		ApplicationInfo applicationInfo = getApplicationInfo();
		if((applicationInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
			mDebug = true;
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.camera, menu);
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch(item.getItemId()) {
		case R.id.item_camera_exit: // 終了
			finish();
			break;
		case R.id.item_pref: // 設定
			Intent i = new Intent(this, CameraSetting.class);
			startActivityForResult(i, CAMERA_PREFERENCE);
			break;
		case R.id.item_recent: // 接続
			if(isConnect || mAdapter == null) {
				break;
			}
			startConnect();
			break;
		case R.id.item_disconnect:
			BluetoothConnection.getInstance().close();
			break;
		}

		return true;
	}

	@Override
	public void onResume() {
		super.onResume();
	}

	@Override
	public void onPause() {
		super.onPause();

		mDialog = null;
	}

	@Override
	public void onStop() {
		super.onStop();

		if(null != mConnectedThread) {
			mConnectedThread.cancel();
		}
	}

	public void setConnect(boolean b) {
		isConnect = b;
	}

	public void setCamera(Camera c) {
		mCamera = c;
		((ReceiveHandler)mHandler).setCamera(c);
//		mCamera.setPreviewCallback(mPreviewCallback2);
	}

	public void closeCamera() {
		if(mCamera != null) {
			mCamera.stopPreview();
			mCamera.release();
			mCamera = null;
		}
	}

	private void startConnect() {
		// 規定の接続デバイスのアドレスを読み込み
		final SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(this);
		String addr = prefs.getString(RECENT_DEVICE_PREF_KEY, null);
		final Connection connection = BluetoothConnection.getInstance();

		if(null == addr) { // 規定の接続デバイスが無い場合
			// 配列へ変換
			final String[] deviceNames = connection.getDeviceNameSet().toArray(
					new String[0]);

			// 接続デバイス選択ダイアログを作成
			mDialog = new AlertDialog.Builder(this)
					.setTitle(R.string.dialog_title) // タイトル
					.setItems(deviceNames, // 選択項目:ペアリング済みデバイス名
							new DialogInterface.OnClickListener() {
								// 選択されたデバイスのアドレスを取得し接続
								public void onClick(DialogInterface di, int i) {
									String address = null;
									address = connection
											.getDeviceAddress(deviceNames[i]);
									// 規定の接続デバイスとして保存
									Editor editor = prefs.edit();
									editor.putString(RECENT_DEVICE_PREF_KEY, address);
									editor.commit();

									connection.connect(address);
								}
							}).create();

			// ダイアログ表示を実行させる
			Message msg = mHandler.obtainMessage();
			msg.what = MESSAGE_DIALOG_SHOW;
			mHandler.sendMessage(msg);
		} else {
			connection.connect(addr);
		}
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
			} else {
				finish();
			}
			break;
		}
	}

	/**
	 * データを外部ストレージに保存する
	 * 
	 * @param data
	 * @param ext
	 *            拡張子(「.」は含まない)
	 */
	private void saveToStorage(byte[] data, String ext) {
		Resources res = getResources();
		SharedPreferences pref = PreferenceManager
				.getDefaultSharedPreferences(mContext);
		String savePath = pref.getString(SAVE_PATH_PREF_KEY, null);
		String filenameFormatString = pref.getString(FILENAME_FMT_PREF_KEY, null);
		File picturesPath = null;

		if(savePath == null) {
			// 保存先の設定が無い場合
			File sdPath = Environment.getExternalStorageDirectory();
			savePath = sdPath.getAbsolutePath() + "/Pictures";
			Editor editor = pref.edit();
			editor.putString(SAVE_PATH_PREF_KEY, savePath);
			editor.commit();
		}
		picturesPath = new File(savePath);
		
		SimpleDateFormat fmt;
		if(filenameFormatString == null) {
			// 保存ファイル名の書式指定が無い場合
			filenameFormatString = "yyyy-MM-dd-HH-mm-ss";
			Editor editor = pref.edit();
			editor.putString(FILENAME_FMT_PREF_KEY, filenameFormatString);
			editor.commit();
		} 
		fmt = new SimpleDateFormat(filenameFormatString);

		try {
			if(!picturesPath.exists()) {
				picturesPath.mkdirs();
			}
			String saveFilename = picturesPath.getAbsolutePath() + "/" +
					fmt.format(new Date()) + "." + ext;
			FileOutputStream fos = new FileOutputStream(saveFilename);
			fos.write(data);
			fos.close();
			Toast.makeText(
					mContext,
					saveFilename + "\n" +
							res.getText(R.string.picture_save_success),
					Toast.LENGTH_LONG).show();
		} catch(Exception e) {
			e.printStackTrace();
			Toast.makeText(mContext, res.getText(R.string.picture_save_failed),
					Toast.LENGTH_LONG).show();
		}
	}

	private void onBluetoothEnabled() {
		ensureBluetooth();
	}

	private void connectWiiRemote() {
		try {
			WiiRemote wiiremote = WiiRemoteJ.findRemote();
		} catch(InterruptedException irex) {
		} catch(IOException ioex) {
		}
	}

	private class WiiRemoteButtonListener extends WiiRemoteAdapter {
		public void buttonInputReceived(WRButtonEvent evt) {
			try {
				if(evt.isPressed(WRButtonEvent.A)) {

				}
			} catch(Exception e) {

			}
		}
	}

	private Camera.ShutterCallback mShutterListener = new Camera.ShutterCallback() {
		public void onShutter() {
			if(mDebug) {
				Log.i(TAG, "onShutter");
			}
		}
	};

	// 写真が撮影された際に呼び出されるコールバック
	private Camera.PictureCallback mPictureCallback = new Camera.PictureCallback() {
		public void onPictureTaken(byte[] data, Camera camera) {

			if(mDebug) {
				Log.i(TAG, "onPictureTaken");
			}

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

	// 撮影した写真がJPEG形式に変換された後に呼び出されるコールバック
	private Camera.PictureCallback mJpegCallback = new Camera.PictureCallback() {
		public void onPictureTaken(byte[] data, Camera camera) {
			// JPEGデータをファイルに保存
			saveToStorage(data, "jpg");

			// 撮影時に停止するカメラのプレビューを再開
			camera.startPreview();

			/*
			 * // 撮影したデータをシャッター側に送信 Bitmap jpg =
			 * BitmapFactory.decodeByteArray(data, 0, data.length); Bitmap
			 * scaled = Bitmap.createScaledBitmap(jpg, jpg.getWidth() / 16,
			 * jpg.getHeight() / 16, false); if(scaled.getWidth() < 0 ||
			 * scaled.getHeight() < 0) { return; }
			 * 
			 * ByteArrayOutputStream baos = new ByteArrayOutputStream();
			 * if(!scaled.compress(Bitmap.CompressFormat.PNG, 100, baos)) {
			 * Toast.makeText(mContext, "Jpeg to ByteArray process was fail",
			 * Toast.LENGTH_LONG).show(); }
			 * 
			 * WriteThread writeThread = new WriteThread(baos.toByteArray());
			 * writeThread.start();
			 */
		}
	};

	private Camera.PreviewCallback mPreviewCallback = new Camera.PreviewCallback() {
		public void onPreviewFrame(byte[] data, Camera camera) {
			if(mDebug) {
				Log.i(TAG, "onPreviewFrame");
			}

			// プレビュー画像をYUV420からRGBに変換
			Camera.Size size = camera.getParameters().getPreviewSize();
			int[] rgb = new int[size.width * size.height];
//			decodeYUV420SP(rgb, data, size.width, size.height);
			yuv420sp2rgb(rgb, data, size.width, size.height, 1);
			mBitmap = Bitmap.createBitmap(size.width, size.height,
					Bitmap.Config.RGB_565);
			mBitmap.setPixels(rgb, 0, size.width, 0, 0, size.width, size.height);
		}
	};

	private Camera.PreviewCallback mPreviewCallback2 = new Camera.PreviewCallback() {
		public void onPreviewFrame(byte[] data, Camera camera) {
			Connection connect = BluetoothConnection.getInstance();
			if(!connect.isConnecting()) {
				return;
			}

			Camera.Size size = camera.getParameters().getPreviewSize();
			int[] rgb = new int[size.width * size.height];
			yuv420sp2rgb(rgb, data, size.width, size.height, 1);
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			DataOutputStream dos = new DataOutputStream(baos);
			try {
				dos.writeInt(size.width);
				dos.writeInt(size.height);
				for(int i : rgb) {
					dos.writeInt(i);
				}
			} catch(IOException e) {
			}
			if(mDebug) {
				Log.i(TAG, "onPreviewFrame2");
			}
			connect.write(baos.toByteArray());
		}
	};

	public static class ReceiveHandler extends Handler {
		private Camera mmCamera;
		private Camera.ShutterCallback mmShutterListener = null;
		private Camera.PictureCallback mmPictureCallback = null;
		private Camera.PictureCallback mmJpegCallback = null;
		private RemoteCamera mmContext;

		public void setCamera(Camera aCamera) {
			mmCamera = aCamera;
		}

		public void setShutterCallback(Camera.ShutterCallback aShutterCallback) {
			mmShutterListener = aShutterCallback;
		}

		public void setPictureCallback(Camera.PictureCallback aPictureCallback) {
			mmPictureCallback = aPictureCallback;
		}

		public void setJpegCallback(Camera.PictureCallback aJpegCallback) {
			mmJpegCallback = aJpegCallback;
		}

		public void setContext(RemoteCamera aContext) {
			mmContext = aContext;
		}

		@Override
		public void handleMessage(Message msg) {
			switch(msg.what) {
			case MESSAGE_SHUTTER:
				if(null != mmCamera) {
					if(mmContext.isUseAutofocus()) {
						mmCamera.cancelAutoFocus();
						mmCamera.autoFocus(mAutofocusCallback);
					} else {
						mmCamera.takePicture(mmShutterListener,
								mmPictureCallback, mmJpegCallback);
					}
				}
				break;
			case MESSAGE_DIALOG_SHOW:
				mDialog.show();
				break;
			case MESSAGE_CONNECT_FAILED:
				String fmtSucc = (String)mmContext.getResources().getText(
						R.string.bt_connect_failed);
				Toast.makeText(mmContext, String.format(fmtSucc, msg.obj),
						Toast.LENGTH_LONG).show();
				break;
			case MESSAGE_CONNECT_SUCCESS:
				mmContext.setConnect(true);
				String fmtFail = (String)mmContext.getResources().getText(
						R.string.bt_connect_success);
				Toast.makeText(mmContext, String.format(fmtFail, msg.obj),
						Toast.LENGTH_LONG).show();
				mConnectedThread = new ConnectedThread();
				mConnectedThread.running = true;
				mConnectedThread.setHandler(mHandler);
				mConnectedThread.start();
				break;
			}
		}

		private Camera.AutoFocusCallback mAutofocusCallback = new Camera.AutoFocusCallback() {
			@Override
			public void onAutoFocus(boolean success, Camera aCamera) {
				if(success) {
					aCamera.takePicture(mmShutterListener, mmPictureCallback,
							mmJpegCallback);
				}
			}
		};
	}

	public class ConnectedTask extends AsyncTask<Integer, Integer, Integer> {

		@Override
		protected Integer doInBackground(Integer... aLen) {
			BluetoothConnection connection = BluetoothConnection.getInstance();
			byte[] buf = new byte[aLen[0]];
			int count = 0;

			try {
				while(true) {
					count = connection.read(buf);
				}
			} catch(Exception e) {
			}

			return count;
		}

		@Override
		protected void onPostExecute(Integer result) {

		}
	}

	private View.OnTouchListener mTouchListener = new View.OnTouchListener() {
		public boolean onTouch(View view, MotionEvent motionevent) {
			Message msg = mHandler.obtainMessage();
			msg.what = MESSAGE_SHUTTER;
			mHandler.sendMessage(msg);
			return false;
		}
	};

	private SimpleOnGestureListener mGestureListener = new SimpleOnGestureListener() {
		@Override
		public boolean onSingleTapUp(MotionEvent motionevent) {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public void onLongPress(MotionEvent motionevent) {
			Message msg = mHandler.obtainMessage();
			msg.what = MESSAGE_SHUTTER;
			mHandler.sendMessage(msg);
		}
	};

	/**
	 * YUV420SP to BMP
	 * 
	 * @param rgb
	 *            出力結果が保存される配列
	 * @param yuv420sp
	 *            変換元データ
	 * @param width
	 *            画像の幅
	 * @param height
	 *            画像の高さ
	 */
	public static void decodeYUV420SP(int[] rgb, byte[] yuv420sp, int width,
			int height) {
		if(mDebug) {
			Log.i(TAG, "decodeYUV420SP");
		}
		final int frameSize = width * height;

		try {
			for(int j = 0, yp = 0; j < height; j++) {
				int uvp = frameSize + (j >> 1) * width, u = 0, v = 0;
				for(int i = 0; i < width; i++, yp++) {
					int y = (0xff & ((int)yuv420sp[yp])) - 16;
					if(y < 0)
						y = 0;
					if((i & 1) == 0) {
						v = (0xff & yuv420sp[uvp++]) - 128;
						u = (0xff & yuv420sp[uvp++]) - 128;
					}

					int y1192 = 1192 * y;
					int r = (y1192 + 1634 * v);
					int g = (y1192 - 833 * v - 400 * u);
					int b = (y1192 + 2066 * u);

					if(r < 0)
						r = 0;
					else if(r > 262143)
						r = 262143;
					if(g < 0)
						g = 0;
					else if(g > 262143)
						g = 262143;
					if(b < 0)
						b = 0;
					else if(b > 262143)
						b = 262143;

					rgb[yp] = 0xff000000 | ((r << 6) & 0xff0000) |
							((g >> 2) & 0xff00) | ((b >> 10) & 0xff);
				}
			}
		} catch(Exception e) {
			if(mDebug) {
				Log.e(TAG, "Exception");
			}
		}

		if(mDebug) {
			Log.i(TAG, "end decode");
		}
	}
}