package com.interlinkj.android.remotecamera;

import java.util.Calendar;
import com.google.ads.*;
import com.google.ads.AdRequest.ErrorCode;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

/***
 * 起動機能選択画面Activity
 * 
 * @author Ito
 * 
 */
public class SelectModeActivity extends Activity {
	private AdView mAdView;

	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		setContentView(R.layout.select);

		Button cameraBtn = (Button)findViewById(R.id.button_camera);
		Button shutterBtn = (Button)findViewById(R.id.button_shutter);
		Button exitBtn = (Button)findViewById(R.id.button_exit);

		cameraBtn.setOnClickListener(mCameraClick);
		shutterBtn.setOnClickListener(mShutterClick);
		exitBtn.setOnClickListener(mExitClick);

		mAdView = (AdView)findViewById(R.id.ad);
		mAdView.setAdListener(mAdListener);
		SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.DAY_OF_YEAR, -30);
		long lastClicked = pref.getLong("last_clicked", cal.getTimeInMillis());
		if(lastClicked > cal.getTimeInMillis()) {
			mAdView.destroy();
		} else {
			mAdView.loadAd(new AdRequest());			
		}
	}

	private OnClickListener mCameraClick = new OnClickListener() {
		@Override
		public void onClick(View arg0) {
			Intent i = new Intent(SelectModeActivity.this, RemoteCamera.class);
			startActivity(i);
		}
	};

	private OnClickListener mShutterClick = new OnClickListener() {
		@Override
		public void onClick(View arg0) {
			Intent i = new Intent(SelectModeActivity.this, RemoteShutter.class);
			startActivity(i);
		}
	};

	private OnClickListener mExitClick = new OnClickListener() {
		@Override
		public void onClick(View view) {
			finish();
		}
	};

	private AdListener mAdListener = new AdListener() {

		@Override
		public void onReceiveAd(Ad ad) {
			// TODO Auto-generated method stub

		}

		@Override
		public void onFailedToReceiveAd(Ad ad, ErrorCode errorcode) {
			// TODO Auto-generated method stub

		}

		@Override
		public void onPresentScreen(Ad ad) {
			// TODO Auto-generated method stub

		}

		@Override
		public void onDismissScreen(Ad ad) {
			// 広告をクリックして、表示されたサイト等からアプリに戻った際に呼ばれる
			//Log.d("RemoteCamera", "onDismissScreen()");
			ad.stopLoading();
			mAdView.destroy();
		}

		@Override
		public void onLeaveApplication(Ad ad) {
			// 広告をクリックして、アプリから離れた際に呼ばれる
			//Log.d("RemoteCamera", "onLeaveApplication()");
			SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
			Editor editor = pref.edit();
			editor.putLong("last_clicked", System.currentTimeMillis());
			editor.commit();
		}

	};
}
