package com.softwinner.shared;

import com.softwinner.update.R;
import com.softwinner.update.OtaUpgradeUtils;
import android.os.Bundle;
import android.app.Activity;
import android.content.Intent;
import android.os.Handler;
import android.os.AsyncTask;
import android.content.Context;
import android.widget.ProgressBar;
import android.view.KeyEvent;
import android.content.res.Configuration;

public class CopyPackageActivity extends Activity implements OtaUpgradeUtils.ProgressListener{

	private OtaUpgradeUtils mUpdateUtils;
	private String packagePath;
	private UpdateTask mUpdateTask;
	private ProgressBar pb;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.reboot_layout);
		
		pb = (ProgressBar) findViewById(R.id.progressBar1);
		
		Intent intent = getIntent();
		packagePath = intent.getStringExtra("path");
		mUpdateTask = new UpdateTask(this);
		
		Runnable r = new Runnable() {
			@Override
			public void run() {
				mUpdateTask.execute();
			}
		};

		new Handler().postDelayed(r, 1000);
	}
	
	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		setContentView(R.layout.reboot_layout);
		super.onConfigurationChanged(newConfig);
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
	    if(keyCode == KeyEvent.KEYCODE_BACK) {
	        return false;
	    } 
	    return super.onKeyDown(keyCode, event);
	}

	@Override
	public void onProgress(int progress) {
		pb.setProgress(progress/2);
	}

	@Override
	public void onVerifyFailed(int errorCode, Object object) {
		
	}

	@Override
	public void onCopyProgress(int progress) {
		pb.setProgress(50+progress/2);
	}

	@Override
	public void onCopyFailed(int errorCode, Object object) {
		
	}
	
	
	class UpdateTask extends AsyncTask<Void,Integer,Integer>{
        private Context context;
        UpdateTask(Context context) {
            this.context = context;
        }

        @Override
        protected void onPreExecute() {
            
        }
        
        @Override
        protected Integer doInBackground(Void... params) {
			mUpdateUtils = new OtaUpgradeUtils(CopyPackageActivity.this);
			mUpdateUtils.upgradeFromOta(packagePath, CopyPackageActivity.this);
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
