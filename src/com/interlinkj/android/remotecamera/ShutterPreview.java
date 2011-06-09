package com.interlinkj.android.remotecamera;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/**
 * シャッター側に撮影した写真のプレビューを表示させるためのSurfaceView
 * @author Ito
 *
 */
public class ShutterPreview extends SurfaceView implements
		SurfaceHolder.Callback {
	public Bitmap bitmap = null;
	private SurfaceHolder mHolder;
	private PreviewThread mThread;

	// 描画用スレッド
	public class PreviewThread extends Thread {
		public boolean running;
		private float width;
		private float height;
		private RectF rect;

		@Override
		public void run() {
			while(running) {
				Canvas c = null;
				try {
					c = mHolder.lockCanvas(null);
					synchronized(mHolder) {
						doDraw(c);
					}
				} finally {
					if(c != null) {
						mHolder.unlockCanvasAndPost(c);
					}
				}
			}
		}

		private void doDraw(Canvas canvas) {
			if(null != bitmap) {
				rect = new RectF(0.0f, 0.0f, width, height);
				canvas.drawBitmap(bitmap, null, rect, null);
			}
		}
	}

	public ShutterPreview(Context context, AttributeSet attrs) {
		super(context, attrs);
		mHolder = getHolder();
		mHolder.addCallback(this);
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width,
			int height) {
		mThread.width = width;
		mThread.height = height;
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		mThread = new PreviewThread();
		mThread.running = true;
		mThread.start();
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		mThread.running = false;
		try {
			mThread.join();
		} catch(InterruptedException e) { }
	}
}
