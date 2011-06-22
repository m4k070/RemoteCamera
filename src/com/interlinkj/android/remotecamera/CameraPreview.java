package com.interlinkj.android.remotecamera;

import java.io.IOException;
import java.util.List;

import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import static com.interlinkj.android.remotecamera.RemoteCamera.TAG;
import static com.interlinkj.android.remotecamera.RemoteCamera.MESSAGE_SHUTTER;

/**
 * カメラ側プレビュー用SurfaceView
 * 
 * @author Ito
 * 
 */
public class CameraPreview extends SurfaceView implements
		SurfaceHolder.Callback {

	private Camera mCamera;
	private SurfaceHolder mHolder;
	private RemoteCamera mContext;
	private Handler mHandler;

	public void setHandler(Handler aHandler) {
		mHandler = aHandler;
	}

	public Handler getHandler() {
		return mHandler;
	}

	/**
	 * コンストラクタ
	 * 
	 * @param context
	 * @param attrs
	 */
	public CameraPreview(RemoteCamera context, AttributeSet attrs) {
		super(context, attrs);

		mContext = context;

		mHolder = getHolder();
		mHolder.addCallback(this);
		mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
	}

	public void surfaceChanged(SurfaceHolder holder, int format, int width,
			int height) {
//		Log.i(TAG, "Preview surfaceChanged");

		if(mCamera != null) {
			mCamera.stopPreview();
			Camera.Parameters parameters = mCamera.getParameters();
//			List<Integer>supportedFormats = parameters.getSupportedPreviewFormats();
//			if(supportedFormats != null) {
//				parameters.setPreviewFormat(supportedFormats.get(0));
//			} else {
//				parameters.setPreviewFormat(ImageFormat.NV21);
//			}
			// プレビューサイズ
			Size optimalSize = getOptimalPreviewSize(
					parameters.getSupportedPreviewSizes(), width, height);
			parameters.setPreviewSize(optimalSize.width, optimalSize.height);
			// 画像サイズ
			List<Size> supportedPictSizes = parameters
					.getSupportedPictureSizes();
			if(supportedPictSizes != null) {
				Size pictSize = supportedPictSizes.get(0);
				parameters.setPictureSize(pictSize.width, pictSize.height);
			}
			// Androidのカメラは横向き専用
			parameters.set("orientation", "landscape");
			try {
				mCamera.setParameters(parameters);
				mCamera.setPreviewDisplay(holder);
			} catch(IOException e) {
//				Log.e(TAG, "IOException");
			} catch(RuntimeException e) {
//				Log.e(TAG, "RuntimeException");
			}
			mCamera.startPreview();
			mCamera.autoFocus(null);
		}
	}

	public void surfaceCreated(SurfaceHolder holder) {
//		Log.i(TAG, "Preview surfaceCreated");

		mCamera = Camera.open();
		mContext.setCamera(mCamera);
		Camera.Parameters param = mCamera.getParameters();
		// フォーカスモード
		List<String> focusModes = param.getSupportedFocusModes();
		if(focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
			param.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
			mContext.setUseAutofocus(true);
		} else {
			param.setFocusMode(Camera.Parameters.FOCUS_MODE_INFINITY);
			mContext.setUseAutofocus(false);
		}
		try {
			mCamera.setPreviewDisplay(holder);
		} catch(IOException e) {
//			Log.e(TAG, "IOException");
			e.printStackTrace();
		}
	}

	public void surfaceDestroyed(SurfaceHolder holder) {
//		Log.i(TAG, "Preview surfaceDestroyed");
		mContext.closeCamera();
	}

	/*
	@Override
	public boolean onTouchEvent(MotionEvent e) {
		Message msg = mHandler.obtainMessage();
		msg.what = MESSAGE_SHUTTER;
		mHandler.sendMessage(msg);
		
		return true;
	}
	*/

	private Size getOptimalPreviewSize(List<Size> sizes, int w, int h) {
		final double ASPECT_TOLERANCE = 0.05;
		double targetRatio = (double)w / h;
		if(sizes == null)
			return null;

		Size optimalSize = null;
		double minDiff = Double.MAX_VALUE;

		int targetHeight = h;

		// Try to find an size match aspect ratio and size
		for(Size size : sizes) {
			double ratio = (double)size.width / size.height;
			if(Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE)
				continue;
			if(Math.abs(size.height - targetHeight) < minDiff) {
				optimalSize = size;
				minDiff = Math.abs(size.height - targetHeight);
			}
		}

		// Cannot find the one match the aspect ratio, ignore the requirement
		if(optimalSize == null) {
			minDiff = Double.MAX_VALUE;
			for(Size size : sizes) {
				if(Math.abs(size.height - targetHeight) < minDiff) {
					optimalSize = size;
					minDiff = Math.abs(size.height - targetHeight);
				}
			}
		}
		return optimalSize;
	}
}
