package com.interlinkj.android.remotecamera;

import java.io.IOException;
import java.util.List;

import android.content.Context;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import static com.interlinkj.android.remotecamera.RemoteCamera.TAG;
import static com.interlinkj.android.remotecamera.RemoteCamera.MESSAGE_READ;

public class Preview extends SurfaceView implements SurfaceHolder.Callback {

	private Camera mCamera;
	private SurfaceHolder mHolder;
	private Context mContext;
	private Handler mHandler;
	
	public void setHandler(Handler aHandler) {
		mHandler = aHandler;
	}

	/**
	 * コンストラクタ
	 * 
	 * @param context
	 * @param attrs
	 */
	public Preview(Context context, AttributeSet attrs) {
		super(context, attrs);
		Log.i(TAG, "new Preview");

		mContext = context;

		mHolder = getHolder();
		mHolder.addCallback(this);
		mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		Log.i(TAG, "end");
	}

	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		Log.i(TAG, "Preview surfaceChanged");
		
		if (mCamera != null) {
			mCamera.stopPreview();
			Camera.Parameters parameters = mCamera.getParameters();
//			List<Integer>supportedFormats = parameters.getSupportedPreviewFormats();
//			if(supportedFormats != null) {
//				parameters.setPreviewFormat(supportedFormats.get(0));
//			} else {
//				parameters.setPreviewFormat(ImageFormat.NV21);
//			}
			// プレビューサイズ
			List<Size> supportedSizes = parameters.getSupportedPreviewSizes();
			if(supportedSizes != null) {
				Size previewSize = supportedSizes.get(0);
				parameters.setPreviewSize(previewSize.width, previewSize.height);
			} else {
				parameters.setPreviewSize(width, height);				
			}
			// 画像サイズ
			List<Size> supportedPictSizes = parameters.getSupportedPictureSizes();
			if(supportedPictSizes != null) {
				Size pictSize = supportedPictSizes.get(0);
				parameters.setPictureSize(pictSize.width, pictSize.height);
			}
			// Androidのカメラは横向き専用
			parameters.set("orientation", "landscape");
			// フォーカスモード
			parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_MACRO);
			try {
				mCamera.setParameters(parameters);
				mCamera.setPreviewDisplay(holder);
			} catch (IOException e) {
				Log.e(TAG, "IOException");
			} catch (RuntimeException e) {
				Log.e(TAG, "RuntimeException");
			}
			mCamera.startPreview();
			mCamera.autoFocus(null);
		}
	}

	public void surfaceCreated(SurfaceHolder holder) {
		Log.i(TAG, "Preview surfaceCreated");

		mCamera = Camera.open();
		((RemoteCamera)mContext).setCamera(mCamera);
		try {
			mCamera.setPreviewDisplay(holder);
		} catch (IOException e) {
			Log.e(TAG, "IOException");
			e.printStackTrace();
		}
	}

	public void surfaceDestroyed(SurfaceHolder holder) {
		Log.i(TAG, "Preview surfaceDestroyed");
		((RemoteCamera)mContext).closeCamera();
	}

	public boolean onTouchEvent(MotionEvent event) {
		mCamera.autoFocus(mAutofocusCallback);
		return true;	
	}
	
	
	private Camera.AutoFocusCallback mAutofocusCallback =
		new Camera.AutoFocusCallback() {
			public void onAutoFocus(boolean flag, Camera camera) {
				Message msg = mHandler.obtainMessage();
				msg.what = MESSAGE_READ;
				mHandler.sendMessage(msg);
			}
		};
}
