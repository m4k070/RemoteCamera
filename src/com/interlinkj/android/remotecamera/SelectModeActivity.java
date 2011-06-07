package com.interlinkj.android.remotecamera;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

public class SelectModeActivity extends Activity {
	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		setContentView(R.layout.select);
		
		Button cameraBtn = (Button)findViewById(R.id.button_camera);
		Button shutterBtn = (Button)findViewById(R.id.button_shutter);
		
		cameraBtn.setOnClickListener(mCameraClick);
		shutterBtn.setOnClickListener(mShutterClick);
	}
	
	private OnClickListener mCameraClick =
		new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				Intent i = new Intent(SelectModeActivity.this, RemoteCamera.class);
				startActivity(i);
			}
		};
		
	private OnClickListener mShutterClick =
		new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				Intent i = new Intent(SelectModeActivity.this, RemoteShutter.class);
				startActivity(i);
			}
		};
}
