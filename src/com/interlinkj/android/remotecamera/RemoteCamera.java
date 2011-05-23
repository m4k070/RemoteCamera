package com.interlinkj.android.remotecamera;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import com.interlinkj.android.remotecamera.R;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

public class RemoteCamera extends Activity {
	public static final String TAG = "RemoteCamera";
	public static final int MESSAGE_READ = 0;

	private Context mContext;
	private Handler mHandler;
	private Camera mCamera = null;
	private Bitmap mBitmap;
	private Preview mPreview;
	
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

		mContext = getApplicationContext();
		mHandler = new ReceiveHandler();
		((ReceiveHandler)mHandler).setShutterCallback(mShutterListener);
		((ReceiveHandler)mHandler).setPictureCallback(mPictureCallback);
		mPreview = (Preview)findViewById(R.id.surfaceView1);
		mPreview.setHandler(mHandler);
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
		case R.id.item1:
			finish();
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
		
		closeCamera();
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
			try {
				FileOutputStream fos =
					new FileOutputStream(Environment.getExternalStorageDirectory() + "/test.jpg");
					fos.write(data);
					fos.close();
			} catch(FileNotFoundException e) {
				e.printStackTrace();
			} catch(IOException e) {
				e.printStackTrace();
			}
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
			if(MESSAGE_READ == msg.what) {
				mmCamera.takePicture(mmShutterListener, null, mmJpegCallback);
			}
		}
	}
	
	private View.OnTouchListener mTouchListener =
		new View.OnTouchListener() {
			public boolean onTouch(View view, MotionEvent motionevent) {
				Message msg = mHandler.obtainMessage();
				msg.what = MESSAGE_READ;
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