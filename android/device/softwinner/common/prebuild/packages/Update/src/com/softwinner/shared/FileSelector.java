
package com.softwinner.shared;

import com.softwinner.update.R;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.view.View.OnClickListener;
import android.widget.RelativeLayout;
import android.widget.ImageView;

import java.io.File;

public class FileSelector extends Activity implements OnItemClickListener {

    public static final String FILE = "file";

    private File mCurrentDirectory;

    private LayoutInflater mInflater;

    private FileAdapter mAdapter = new FileAdapter();

    private ListView mListView;
	private RelativeLayout backLayout;
	
	private TextView titleTxt;


	private static final File[] roots = new File[]{new File("/sdcard"), new File("/mnt/extsd")};
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mInflater = LayoutInflater.from(this);
        setContentView(R.layout.select_file_list);
        mListView = (ListView) findViewById(R.id.file_list);
        mListView.setAdapter(mAdapter);
        mAdapter.setCurrentList(roots);
        mListView.setOnItemClickListener(this);
		backLayout = (RelativeLayout)findViewById(R.id.back_btn);
		titleTxt = (TextView)findViewById(R.id.title_text);
		backLayout.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				 if (mCurrentDirectory == null) {
					finish();
				} else if (mCurrentDirectory.getPath().equals("/sdcard") || mCurrentDirectory.getPath().equals("/mnt/extsd") ) {
					mCurrentDirectory = null;
					mAdapter.setCurrentList(roots);
					titleTxt.setText(R.string.select_file_title);
				} else {
					mCurrentDirectory = mCurrentDirectory.getParentFile();
					mAdapter.setCurrentList(mCurrentDirectory);
					titleTxt.setText(mCurrentDirectory.getPath());
				}
			}
        });
		
    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
        File selectFile = (File) adapterView.getItemAtPosition(position);
        if (selectFile.isDirectory()) {
            mCurrentDirectory = selectFile;
            FileAdapter adapter = (FileAdapter) adapterView.getAdapter();
            adapter.setCurrentList(selectFile);
			titleTxt.setText(selectFile.getPath());
        } else if (selectFile.isFile()) {
            //Intent intent = new Intent();
            //intent.putExtra(FILE, selectFile.getPath());
            //setResult(0, intent);
            //finish();
			Intent intent = new Intent(FileSelector.this, LocalVerifyActivity.class);
			intent.putExtra("path", selectFile.getPath());
			FileSelector.this.startActivity(intent);
        }
    }

    @Override
    public void onBackPressed() {
        if (mCurrentDirectory == null) {
            super.onBackPressed();
        } else if (mCurrentDirectory.getPath().equals("/sdcard") || mCurrentDirectory.getPath().equals("/mnt/extsd") ) {
			mCurrentDirectory = null;
			mAdapter.setCurrentList(roots);
			titleTxt.setText(R.string.select_file_title);
		} else {
            mCurrentDirectory = mCurrentDirectory.getParentFile();
            mAdapter.setCurrentList(mCurrentDirectory);
			titleTxt.setText(mCurrentDirectory.getPath());
        }
    }

//    @Override
//    public boolean onKeyDown(int keyCode, KeyEvent event) {
//        if (keyCode == KeyEvent.KEYCODE_BACK) {
//            if (mCurrentDirectory == null || mCurrentDirectory.getPath().equals("/sdcard")) {
//                return super.onKeyDown(keyCode, event);
//            } else {
//                mAdapter.setCurrentList(mCurrentDirectory.getParentFile());
//                return false;
//            }
//        }
//        return super.onKeyDown(keyCode, event)
//    }

    private class FileAdapter extends BaseAdapter {

		private String[] imgTypes = { "jpg", "png", "bmp" };
		private String[] videoTypes = { "mp4", "avi", "3gp", "mkv", "rmvb",
										"wmv" };
		private String musicType = "mp3";
		private String zipType = "zip";								
		
        private File mFiles[];

        public void setCurrentList(File directory) {
            mFiles = directory.listFiles();
            notifyDataSetChanged();
        }

        public void setCurrentList(File[] directories) {
            mFiles = directories;
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return mFiles == null ? 0 : mFiles.length;
        }

        @Override
        public File getItem(int position) {
            File file = mFiles == null ? null : mFiles[position];
            return file;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
				convertView = mInflater.inflate(R.layout.file_item, null);
            }
			final ImageView fileImg, nextImg;
			final TextView fileName;
			fileImg = (ImageView) convertView.findViewById(R.id.file_type_img);
			nextImg = (ImageView) convertView.findViewById(R.id.next_img);
			nextImg.setVisibility(View.INVISIBLE);
			fileName = (TextView) convertView.findViewById(R.id.file_name);
            File file = mFiles[position];
            String name = file.getName();
            fileName.setText(name);
			if (file.isDirectory()) {
				fileImg.setBackgroundResource(R.drawable.ic_folder);
			} else {
				boolean isMatch = false;
				int i = 0;
				for (i = 0; i < imgTypes.length && !isMatch; i++) {
					if (file.getName().endsWith(imgTypes[i])) {
						fileImg.setBackgroundResource(R.drawable.ic_picture);
						isMatch = true;
						break;
					}
				}
				for (i = 0; i < videoTypes.length && !isMatch; i++) {
					if (file.getName().endsWith(videoTypes[i])) {
						fileImg.setBackgroundResource(R.drawable.ic_viedo);
						isMatch = true;
						break;
					}
				}	
				if(!isMatch && file.getName().endsWith(musicType)){
					fileImg.setBackgroundResource(R.drawable.ic_music);
					isMatch = true;
				}
				if(!isMatch && file.getName().endsWith(zipType)){
					fileImg.setBackgroundResource(R.drawable.ic_compressed_file);
					isMatch = true;
					nextImg.setVisibility(View.VISIBLE);
				}
				if(!isMatch){
					fileImg.setBackgroundResource(R.drawable.ic_file);
				}
			}
            return convertView;
        }

    }
}
