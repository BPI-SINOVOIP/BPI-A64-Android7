package com.softwinner.shared;

import com.softwinner.update.R;

import java.io.File;
import java.text.DecimalFormat;

import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.app.Activity;
import android.content.Intent;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.content.res.Configuration;


public class LocalVerifyActivity extends Activity implements VerifyPackage.ProgressListener{
	
	private static final int VERIFY_SUCCESS = 0;
	private static final int VERIFY_FIALED = 1;
	
	private RelativeLayout backRL, startRL;
	private TextView titleTv, nameTv, sizeTv, startTv, verifyTv; 
	private ImageView startImg;
	private VerifyPackage mVerifyPackage;
	private String packagePath;
	private File packageFile;
	private VerifyTask mVerifyTask;
	
	private Handler mHandler = new Handler(){
		@Override
		public void handleMessage(Message msg) {
			switch(msg.what){
			case VERIFY_SUCCESS:
				startTv.setTextColor(getResources().getColor(R.color.start_btn_enabled_color));
				startRL.setClickable(true);
				//verifyTv.setText(R.string.package_verify_success);
				verifyTv.setText("");
				startImg.setBackgroundResource(R.drawable.ic_check_press);
				break;
			case VERIFY_FIALED:
				verifyTv.setText(R.string.package_verify_failed);
				break;
			}
		}
	};
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.local_verify_layout);
		backRL = (RelativeLayout) findViewById(R.id.back_btn);
		startRL = (RelativeLayout)findViewById(R.id.start_install_btn);
		titleTv = (TextView) findViewById(R.id.title_text);
		nameTv = (TextView) findViewById(R.id.package_name);
		sizeTv = (TextView) findViewById(R.id.package_size);
		startTv = (TextView) findViewById(R.id.start_install_txt);
		verifyTv = (TextView) findViewById(R.id.verifying_txt);
		startImg = (ImageView) findViewById(R.id.start_install_img);
		
		Intent intent = getIntent();
		packagePath = intent.getStringExtra("path");
		packageFile  = new File(packagePath);
		titleTv.setText(packageFile.getName());
		nameTv.setText(packageFile.getName());
		sizeTv.setText(sizeToMb(packageFile.length()));
		verifyTv.setText(R.string.verifying_local_txt);
		startTv.setTextColor(getResources().getColor(R.color.start_btn_disable_color));
		startImg.setBackgroundResource(R.drawable.ic_check_nor);
		
		backRL.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				mVerifyTask.cancel(false);
				LocalVerifyActivity.this.finish();
			}
		});
		
		startRL.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				InstallPowerDialog dialog = new InstallPowerDialog.Builder(
						LocalVerifyActivity.this).setClickListener(new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								if(which == 0){
									dialog.dismiss();
								}else if(which == 1){
									dialog.dismiss();
									Intent intent = new Intent(LocalVerifyActivity.this, LocalRebootActivity.class);
									intent.putExtra("path",packagePath);
									LocalVerifyActivity.this.startActivity(intent);
								}
							}
						}).create();
				dialog.setCanceledOnTouchOutside(false);
				dialog.show();
			}
		});
		startRL.setClickable(false);
		
		mVerifyTask = new VerifyTask(this);
		mVerifyTask.execute();
	}
	
	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
	}
	
	private String sizeToMb(long length){
		String str="";
		float size = (float)length /1024/1024;
		str = new DecimalFormat("#.000").format(size)+"Mb";
		return str;
	}
	
	@Override
	public void onProgress(int progress) {
		if(progress == 100){
			Message msg = new Message();
			msg.what = VERIFY_SUCCESS;
			mHandler.sendMessage(msg);
		}
	}

	@Override
	public void onVerifyFailed(int errorCode, Object object) {
		Message msg = new Message();
		msg.what = VERIFY_FIALED;
		mHandler.sendMessage(msg);
	}

	@Override
	public void onCopyProgress(int progress) {

	}

	@Override
	public void onCopyFailed(int errorCode, Object object) {
		
	}
	
	class VerifyTask extends AsyncTask<Void,Integer,Integer>{
        private Context context;
        VerifyTask(Context context) {
            this.context = context;
        }

        @Override
        protected void onPreExecute() {
            
        }
        
        @Override
        protected Integer doInBackground(Void... params) {
			mVerifyPackage = new VerifyPackage(LocalVerifyActivity.this);
			mVerifyPackage.verifyPackage(packageFile, LocalVerifyActivity.this);
			return 0;
        }

        @Override
        protected void onPostExecute(Integer integer) {

        }

        @Override
        protected void onProgressUpdate(Integer... values) {
           
        }
    }
}
