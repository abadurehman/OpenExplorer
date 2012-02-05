package org.brandroid.openmanager.adapters;

import org.brandroid.openmanager.R;
import org.brandroid.openmanager.util.BetterPopupWindow;
import org.brandroid.utils.Logger;
import org.brandroid.utils.MenuBuilderNew;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.*;
import android.content.DialogInterface.OnDismissListener;
import android.graphics.Rect;
import android.view.*;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.LinearLayout.LayoutParams;
import android.widget.ListView;

public class IconContextMenu
{
	
	public interface IconContextItemSelectedListener {
		void onIconContextItemSelected(MenuItem item, Object info, View view);
	}
	
	private GridView mGrid;
	//private Dialog dialog;
	protected final BetterPopupWindow popup;
	protected final ViewGroup root;
	private MenuBuilderNew menu;
	protected final View anchor;
	private int maxColumns = 2;
	
	private IconContextItemSelectedListener iconContextItemSelectedListener;
	private Object info;

    public IconContextMenu(Context context, int menuId, View from, View head, View foot) {
    	this(context, newMenu(context, menuId), from, head, foot);
    }
    
    public static MenuBuilderNew newMenu(Context context, int menuId) {
    	MenuBuilderNew menu = new MenuBuilderNew(context);
    	new MenuInflater(context).inflate(menuId, menu);
    	return menu;
    }

	public IconContextMenu(Context context, Menu menu, final View from, final View head, final View foot) {
		MenuBuilderNew newMenu = new MenuBuilderNew(context);
		for(int i = 0; i < menu.size(); i++)
		{
			MenuItem item = menu.getItem(i);
			if(item.isVisible())
				newMenu.add(item.getGroupId(), item.getItemId(), item.getOrder(), item.getTitle());
		}
		root = (ViewGroup) ((LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE)).inflate(R.layout.paste_layout, null);
		//root.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        //menu = newMenu;
        anchor = from;
        //this.dialog = new AlertDialog.Builder(context);
        popup = new BetterPopupWindow(context, anchor);
        mGrid = new GridView(context);
        mGrid.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        mGrid.setNumColumns(maxColumns);
        if(head != null)
        	root.addView(head);
		root.addView(mGrid);
		if(foot != null)
			root.addView(foot);
        popup.setContentView(root);
        setAdapter(context, new IconContextMenuAdapter(context, menu));
	}
	
	public void setAdapter(Context context, final IconContextMenuAdapter adapter)
	{
		mGrid.setAdapter(adapter);
		Logger.LogInfo("mGrid Adapter set");
		mGrid.setOnItemClickListener(new OnItemClickListener() {

			public void onItemClick(AdapterView<?> arg0, View v, int pos, long id) {
				if(iconContextItemSelectedListener != null)
				{
					iconContextItemSelectedListener.onIconContextItemSelected(
							adapter.getItem(pos), info, v);
				}
			}
			
		} );
		
		
		//root.
		//popup.setBackgroundDrawable(context.getResources().getDrawable(R.drawable.contextmenu_top_right));
		//popup.setContentView(mList);
		/*this.dialog = new AlertDialog.Builder(context)
	        .setAdapter(adapter, new DialogInterface.OnClickListener() {
		        //@Override
		        public void onClick(DialogInterface dialog, int which) {
		        	if (iconContextItemSelectedListener != null) {
		        		iconContextItemSelectedListener.onIconContextItemSelected(adapter.getItem(which), info);
		        	}
		        }
	        })
	        .setInverseBackgroundForced(true)
	        .create();
	        */
    }
	
	public void setNumColumns(int cols) {
		maxColumns = cols;
		if(mGrid != null)
			mGrid.setNumColumns(cols);
	}
	
	public void setInfo(Object info) {
		this.info = info;
	}

	public Object getInfo() {
		return info;
	}
	
	public MenuBuilderNew getMenu() {
		return menu;
	}
	
	public void setOnDismissListener(PopupWindow.OnDismissListener listener)
	{
		popup.setOnDismissListener(listener);
	}
    public void setOnIconContextItemSelectedListener(IconContextItemSelectedListener iconContextItemSelectedListener) {
        this.iconContextItemSelectedListener = iconContextItemSelectedListener;
    }
    
    public void setTitle(CharSequence title) {
    	popup.setTitle(title);
    }
	public void setTitle(int stringId) {
		popup.setTitle(stringId);
	}

    public void show()
    {
    	//popup.showLikeQuickAction();
    	popup.showLikePopDownMenu();
    }
    public void show(int left, int top)
    {
    	//popup.showLikeQuickAction();
    	popup.showLikePopDownMenu(left, top);
    }
    public void dismiss()
    {
    	popup.dismiss();
    }

    public void addView(View v)
    {
    	Logger.LogInfo("View added to IconContextMenu");
    	root.addView(v);
    }
	public void addView(View v, int index) {
		root.addView(v, index);
	}

    
    /*
    public void setOnCancelListener(DialogInterface.OnCancelListener onCancelListener) {
    	popup.setOnCancelListener(onCancelListener);
    }
    
    public void setOnDismissListener(DialogInterface.OnDismissListener onDismissListener) {
    	dialog.setOnDismissListener(onDismissListener);
    }
    
    public void show() {
    	dialog.show();
    }
    
    public void dismiss() {
    	dialog.dismiss();
    }
    
    public void cancel() {
    	dialog.cancel();
    }
    
    public Dialog getDialog() {
    	return dialog;
    }
    */
}