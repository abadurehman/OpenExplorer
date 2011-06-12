/*
    Open Manager For Tablets, an open source file manager for the Android system
    Copyright (C) 2011  Joe Berria <nexesdevelopment@gmail.com>

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package com.nexes.manager.tablet;

import com.nexes.manager.tablet.DirContentActivity.OnBookMarkAddListener;

import android.os.Bundle;
import android.os.Environment;
import android.content.Context;
import android.content.DialogInterface;
import android.preference.PreferenceManager;
import android.app.ListFragment;
import android.app.AlertDialog;
import android.view.ContextMenu;
import android.view.View;
import android.view.ViewGroup;
import android.view.LayoutInflater;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.ListView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ArrayAdapter;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.util.Log;

import java.util.ArrayList;

public class DirListActivity extends ListFragment implements OnBookMarkAddListener {
	private static final String PREF_LIST_KEY =	"pref_dirlist";
	private static final int BOOKMARK_POS = 6;
	
	private static OnChangeLocationListener mChangeLocList;
	private ArrayList<String> mDirList;
	private Context mContext;
	private ImageView mLastIndicater = null;
	private DirListAdapter mDelegate;
	private String mDirListString;
	

	public interface OnChangeLocationListener {
		void onChangeLocation(String name);
	}
	
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		String storage = "/" + Environment.getExternalStorageDirectory().getName();
		mContext = getActivity();
		mDirList = new ArrayList<String>();
		mDirListString = (PreferenceManager.getDefaultSharedPreferences(mContext))
												.getString(PREF_LIST_KEY, "");
		
		if(mDirListString.length() > 0) {		
			String[] l = mDirListString.split(":");
			
			for(String string : l)
				mDirList.add(string);
		
		} else {
			mDirList.add("/");
			mDirList.add(storage);
			mDirList.add(storage + "/" + "Download");
			mDirList.add(storage + "/" + "Music");
			mDirList.add(storage + "/" + "Movies");
			mDirList.add(storage + "/" + "Pictures");
			mDirList.add("Bookmarks");			
		}
	}
	
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		getView().setBackgroundResource(R.color.gray);
		
		ListView lv = getListView();		
		lv.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
		lv.setCacheColorHint(0x00000000);
		lv.setDrawSelectorOnTop(true);
		lv.setBackgroundResource(R.drawable.listgradback);
		
		mDelegate = new DirListAdapter(mContext, R.layout.dir_list_layout, mDirList);
		registerForContextMenu(lv);
		setListAdapter(mDelegate);
		
		DirContentActivity.setOnBookMarkAddListener(this);
	}
	
	@Override
	public void onListItemClick(ListView list, View view, int pos, long id) {
		ImageView v;
		
		if(pos == BOOKMARK_POS)
			return;
		
		if(mLastIndicater != null)
			mLastIndicater.setVisibility(View.GONE);
			
		v = (ImageView)view.findViewById(R.id.list_arrow);//maybe replace this?
		v.setVisibility(View.VISIBLE);
		mLastIndicater = v;
		
		if(mChangeLocList != null)
			mChangeLocList.onChangeLocation(mDirList.get(pos));
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		final int pos = ((AdapterContextMenuInfo)menuInfo).position;
		
		if(pos <= BOOKMARK_POS)
			return;

		new AlertDialog.Builder(mContext)
			.setTitle("Remove Bookmark " + mDirList.get(pos))
			.setMessage("Are you sure you want to remove this as a bookmark?")
			.setPositiveButton("Remove", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					mDirList.remove(pos);
					buildDirString();
					mDelegate.notifyDataSetChanged();
				}
			})
			.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					dialog.dismiss();
				}
				
			}).create().show();		
	}
	
	@Override
	public void onBookMarkAdd(String path) {
		mDirList.add(path);
		buildDirString();
		mDelegate.notifyDataSetChanged();
	}
	
	public static void setOnChangeLocationListener(OnChangeLocationListener l) {
		mChangeLocList = l;
	}
	
	public void setDirListString(String list) {
		mDirListString = list;
	}
	
	public String getDirListString() {
		return mDirListString;
	}
	
	/*
	 * Builds a string from mDirList to easily save and recall
	 * from preferences. 
	 */
	private void buildDirString() {
		
		if(mDirListString != null && mDirListString.length() > 0)
			mDirListString = "";
		
		for(String l : mDirList)
			mDirListString += l + ":";
	}
	
	
	
	/*
	 * 
	 */
	private class DirListAdapter extends ArrayAdapter<String> {
		private DataViewHolder mHolder;
		
		DirListAdapter(Context context, int layout, ArrayList<String> data) {
			super(context, layout, data);		
		}
		
		@Override
		public View getView(int position, View view, ViewGroup parent) {
			String name = mDirList.get(position);
			
			if(view == null) {
				LayoutInflater in = (LayoutInflater)mContext.
									getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				view = in.inflate(R.layout.dir_list_layout, parent, false);
				mHolder = new DataViewHolder();
				
				mHolder.mIcon = (ImageView)view.findViewById(R.id.list_icon);
				mHolder.mMainText = (TextView)view.findViewById(R.id.list_name);
				mHolder.mIndicate = (ImageView)view.findViewById(R.id.list_arrow);
				
				view.setTag(mHolder);
				
			} else {
				mHolder = (DataViewHolder)view.getTag();
			}
			
			if(mLastIndicater == null) {
				if(position == 1) {
					mHolder.mIndicate.setVisibility(View.VISIBLE);
					mLastIndicater = mHolder.mIndicate;
				}
			}
			
			if(position == 0)
				mHolder.mMainText.setText(name);
			else
				mHolder.mMainText.setText(name.substring(name.lastIndexOf("/") + 1, 
										  name.length()));

			switch(position) {
			case 0:
				mHolder.mIcon.setImageResource(R.drawable.drive);
				break;
			case 1:
				mHolder.mIcon.setImageResource(R.drawable.sdcard);
				break;
			case 2:
				mHolder.mIcon.setImageResource(R.drawable.download_md);
				break;
			case 3:
				mHolder.mIcon.setImageResource(R.drawable.music_md);
				break;
			case 4:
				mHolder.mIcon.setImageResource(R.drawable.movie_md);
				break;
			case 5:
				mHolder.mIcon.setImageResource(R.drawable.photo_md);
				break;
			case 6:
				mHolder.mIcon.setImageResource(R.drawable.favorites);
				view.setBackgroundColor(R.color.black);
				break;
				
			default:
				mHolder.mIcon.setImageResource(R.drawable.folder_md);
				break;
			}
			
			return view;
		}
	}	
}
