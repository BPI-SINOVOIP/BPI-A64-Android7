
package com.softwinner.shared;

import com.softwinner.update.R;

import java.io.File;
import java.io.FilenameFilter;

import android.os.Bundle;
import android.os.Environment;
import android.app.Activity;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

public class FileSearch extends Activity implements OnItemClickListener{

	private ListView mListView;
	private RelativeLayout backLayout;
	private RelativeLayout selectBtn;
	private LayoutInflater mInflater;
	private FileAdapter mAdapter = new FileAdapter();
	private TextView scanBtn;
	private File mFiles[];
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.file_list);
		mInflater = LayoutInflater.from(this);
		mListView = (ListView) findViewById(R.id.file_list);
        mListView.setOnItemClickListener(this);
		backLayout = (RelativeLayout)findViewById(R.id.back_btn);
		backLayout.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				finish();
			}
        });
		
		selectBtn = (RelativeLayout)findViewById(R.id.select_btn);
		selectBtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				Intent intent = new Intent(FileSearch.this,FileSelector.class);
				FileSearch.this.startActivity(intent); 
			}
		});
		
		scanBtn = (TextView) findViewById(R.id.scan_btn);
		scanBtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				initList();
			}
		});
		
		initList();
	}
	
	private void initList(){
		File file = new File(Environment.getExternalStorageDirectory().getPath());
		mFiles = file.listFiles(new FileNameSelector("zip"));
		mListView.setAdapter(mAdapter);
		mAdapter.setCurrentList(mFiles);
	}

	@Override
	public void onItemClick(AdapterView<?> arg0, View view, int position, long id) {
		Intent intent = new Intent(FileSearch.this, LocalVerifyActivity.class);
		intent.putExtra("path", mFiles[position].getPath());
		FileSearch.this.startActivity(intent);
	}
	
	public class FileNameSelector implements FilenameFilter {
		String extension = ".";

		public FileNameSelector(String fileExtensionNoDot) {
			extension += fileExtensionNoDot;
		}

		public boolean accept(File dir, String name) {
			return name.endsWith(extension);
		}
	}
	
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

