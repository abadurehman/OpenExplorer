package org.brandroid.openmanager.data;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.ref.SoftReference;
import java.nio.channels.FileChannel;
import java.util.HashSet;
import java.util.Hashtable;

import org.brandroid.openmanager.adapters.OpenPathDbAdapter;
import org.brandroid.openmanager.util.DFInfo;
import org.brandroid.openmanager.util.FileManager.SortType;
import org.brandroid.openmanager.util.FileManager;
import org.brandroid.openmanager.util.FileUtils;
import org.brandroid.openmanager.util.RootManager;
import org.brandroid.openmanager.util.ThumbnailCreator;
import org.brandroid.utils.Logger;

import android.app.AlertDialog.Builder;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.text.format.Formatter;

public class OpenFile extends OpenPath
{
	private File mFile;
	private OpenFile[] mChildren = null;
	private boolean bGrandPeeked = false;
	//private String mRoot = null;
	private OutputStream output = null;
	
	public OpenFile setRoot() {
		/// TODO fix this
		return this;
	}
	
	public OpenFile(File f) { mFile = f; }
	public OpenFile(String path) { mFile = new File(path); }
	public OpenFile(String folder, String name) {
		mFile = new File(folder, name);
	}
	public OpenFile(OpenFile folder, String name) {
		mFile = new File(folder.getFile(), name);
	}
	
	public File getFile() { return mFile; }

	@Override
	public String getName() {
		return mFile.getName();
	}

	@Override
	public String getPath() {
		return mFile.getPath();
	}

	@Override
	public long length() {
		return mFile.length();
	}
	
	@Override
	public int getListLength() {
		if(mChildren != null)
			return mChildren.length;
		else return -1;
	}
	
	public long getFreeSpace() {
		if(getDepth() > 3)
		{
			if(getPath().indexOf("/mnt/") > -1)
			{
				OpenFile toCheck = this;
				for(int i=3; i < getDepth(); i++)
				{
					toCheck = toCheck.getParent();
					if(toCheck == null) break;
				}
				if(toCheck != null)
					return toCheck.getFreeSpace();
			}
		}
		try {
			StatFs stat = new StatFs(getPath());
			if(stat.getFreeBlocks() > 0)
				return (long)stat.getFreeBlocks() * (long)stat.getBlockSize();
		} catch(Exception e) {
			Logger.LogWarning("Couldn't get Total Space.", e);
		}
		if(DFInfo.LoadDF().containsKey(getPath()))
			return (long)DFInfo.LoadDF().get(getPath()).getFree();
		return Build.VERSION.SDK_INT > 8 ? mFile.getFreeSpace() * 1024 * 1024 : 0;
	}
	public long getUsableSpace() {
		try {
			StatFs stat = new StatFs(getPath());
			if(stat.getAvailableBlocks() > 0)
				return (long)stat.getAvailableBlocks() * (long)stat.getBlockSize();
		} catch(Exception e) {
			Logger.LogWarning("Couldn't get Total Space.", e);
		}
		if(DFInfo.LoadDF().containsKey(getPath()))
			return (long)(DFInfo.LoadDF().get(getPath()).getSize() - DFInfo.LoadDF().get(getPath()).getFree());
		return mFile.getUsableSpace();
	}
	public long getTotalSpace() {
		if(getDepth() > 3)
		{
			if(getPath().indexOf("/mnt/") > -1)
			{
				OpenFile toCheck = this;
				for(int i=3; i < getDepth(); i++)
				{
					toCheck = toCheck.getParent();
					if(toCheck == null) break;
				}
				if(toCheck != null)
					return toCheck.getTotalSpace();
			}
		}
		try {
			StatFs stat = new StatFs(getPath());
			if(stat.getBlockCount() > 0)
				return (long)stat.getBlockCount() * (long)stat.getBlockSize();
		} catch(Exception e) {
			Logger.LogWarning("Couldn't get Total Space for " + getPath(), e);
		}
		if(DFInfo.LoadDF().containsKey(getPath()))
			return (long)DFInfo.LoadDF().get(getPath()).getSize();
		return Build.VERSION.SDK_INT > 8 ? mFile.getTotalSpace() * 1024 * 1024 : length();
	}
	public long getUsedSpace() {
		long ret = length();
		if(isDirectory())
			for(OpenPath kid : list())
				ret += ((OpenFile)kid).getUsedSpace();
		return ret;
	}
	public int countAllFiles() {
		if(!isDirectory()) return 1;
		int ret = 0;
		for(OpenPath kid : list())
			ret += ((OpenFile)kid).countAllFiles();
		return ret;
	}
	public int countAllDirectories() {
		if(!isDirectory()) return 0;
		int ret = 1;
		for(OpenPath kid : list())
			ret += ((OpenFile)kid).countAllFiles();
		return ret;
	}

	@Override
	public OpenFile getParent() {
		String parent = mFile.getParent();
		if(parent == null) return null;
		return new OpenFile(parent);
	}
	
	@Override
	public OpenFile[] listFiles() { return listFiles(false); }

	@Override
	public boolean listFromDb(SortType sort)
	{
		Cursor c = mDb.fetchItemsFromFolder(getPath().replace("/" + getName(), ""), sort);
		if(c == null) {
			Logger.LogWarning("Null found in DB");
			return false;
		}
		mChildren = new OpenFile[c.getCount()];
		c.moveToFirst();
		while(!c.isAfterLast())
		{
			String folder = c.getString(OpenPathDbAdapter.getKeyIndex(OpenPathDbAdapter.KEY_FOLDER));
			String name = c.getString(OpenPathDbAdapter.getKeyIndex(OpenPathDbAdapter.KEY_NAME));
			int size = c.getInt(OpenPathDbAdapter.getKeyIndex(OpenPathDbAdapter.KEY_SIZE));
			int modified = c.getInt(OpenPathDbAdapter.getKeyIndex(OpenPathDbAdapter.KEY_MTIME));
			mChildren[c.getPosition()] = new OpenFile(folder + "/" + name);
			c.moveToNext();
		}
		Logger.LogInfo("listFromDb found " + mChildren.length + " children");
		c.close();
		return true;
	}
	
	/*
	@Override
	public int getChildCount() throws IOException {
		Logger.LogDebug("Trying to get child count for " + mFile.getPath() + " view NoRoot");
		StringBuilder lines = RootManager.Default.executeNoRoot("ls " + mFile.getPath() + " | wc -l"); 
		if(lines == null || lines.length() == 0) return super.getChildCount();
		Logger.LogDebug("Return value: " + lines.toString());
		try {
			return Integer.parseInt(lines.toString());
		} catch(Exception e) { return super.getChildCount(); }
	}
	*/
	
	private OpenFile[] listFilesNative(File file)
	{
		Logger.LogDebug("Trying to list " + mFile.getPath() + " via NoRoot");
		StringBuilder sbfiles = RootManager.Default.executeNoRoot("ls " + mFile.getPath() + "/");
		if(sbfiles != null)
		{
			String[] files = sbfiles.toString().split("\n");
			OpenFile[] mChildren = new OpenFile[files.length];
			int i = 0;
			for(String s : files)
			{
				mChildren[i++] = new OpenFile(getPath() + "/" + s);
			}
			return mChildren;
		}
		return getOpenPaths(file.listFiles());
	}
	
	public static OpenFile getExternalMemoryDrive(boolean fallbackToInternal) // sd
	{
		for(OpenFile kid : getInternalMemoryDrive().getParent().listFiles())
			if(kid.getName().toLowerCase().indexOf("ext") > -1 && kid.canRead() && kid.list().length > 0)
				return kid;
		if(new File("/Removable").exists())
			for(File kid : new File("/Removable").listFiles())
				if(kid.getName().toLowerCase().indexOf("ext") > -1 && kid.canRead() && kid.list().length > 0)
					return new OpenFile(kid);
		if(!fallbackToInternal)
			return null;
		else
			return getInternalMemoryDrive();
	}
	public static OpenFile getInternalMemoryDrive() // internal
	{
		return new OpenFile(Environment.getExternalStorageDirectory());
	}
	

	private OpenFile[] getOpenPaths(File[] files)
	{
		if(files == null) return new OpenFile[0];
		OpenFile[] ret = new OpenFile[files.length];
		for(int i=0; i < files.length; i++)
			ret[i] = new OpenFile(files[i]);
		return ret;
	}
	private OpenFile[] getOpenPaths(String[] files, String base)
	{
		if(files == null) return new OpenFile[0];
		OpenFile[] ret = new OpenFile[files.length];
		for(int i=0; i < files.length; i++)
			ret[i] = new OpenFile(files[i]);
		return ret;
	}
	
	public OpenFile[] listFiles(boolean grandPeek) {
		if(mChildren == null)
		{
			if(!grandPeek)
			{
				mChildren = getOpenPaths(mFile.listFiles());
				//Logger.LogDebug(mFile.getPath() + " has " + mChildren.length + " children");
			} else
				mChildren = listFilesNative(mFile);
			if((mChildren == null || mChildren.length == 0) && isDirectory())
			{
				if(RootManager.Default.isRoot() && (mFile.getName().equalsIgnoreCase("data") || mFile.getPath().indexOf("/data") > -1 || mFile.getPath().indexOf("/system") > -1))
				{
					Logger.LogDebug("Trying to list " + mFile.getPath() + " via Su");
					HashSet<String> files = RootManager.Default.execute("ls " + mFile.getPath());
					if(files != null)
					{
						mChildren = new OpenFile[files.size()];
						int i = 0;
						for(String s : files)
						{
							mChildren[i++] = new OpenFile(getPath() + "/" + s);
							if(grandPeek && mChildren[i].isDirectory())
								mChildren[i++].listFiles();
						}
						return mChildren;
					}
				}
			}
			if((mChildren == null || mChildren.length == 0) && !isDirectory() && mFile.getParentFile() != null)
				mChildren = getParent().listFiles(grandPeek);
			
			if(mChildren == null)
				return new OpenFile[0];
		}

		if(grandPeek && !bGrandPeeked && mChildren != null && mChildren.length > 0)
		{
			for(int i=0; i < mChildren.length; i++)
			{
				try {
					if(!mChildren[i].isDirectory()) continue;
					mChildren[i].listFiles();
				} catch(ArrayIndexOutOfBoundsException e) {
					Logger.LogWarning("Grandchild lost!", e);
				}
			}
			bGrandPeeked = true;
		}

		return mChildren;
	}

	@Override
	public Boolean isDirectory() {
		return mFile.isDirectory();
	}

	@Override
	public Uri getUri() {
		return Uri.fromFile(mFile);
	}

	@Override
	public Long lastModified() {
		return mFile.lastModified();
	}

	@Override
	public Boolean canRead() {
		return mFile.canRead();
	}

	@Override
	public Boolean canWrite() {
		return mFile.canWrite();
	}

	@Override
	public Boolean canExecute() {
		if(Build.VERSION.SDK_INT > 9)
			return mFile.canExecute();
		else return false;
	}

	@Override
	public Boolean exists() {
		return mFile.exists();
	}
	@Override
	public OpenPath[] list() {
		return listFiles();
	}
	
	@Override
	public Boolean requiresThread() {
		return false;
	}
	@Override
	public String getAbsolutePath() {
		return mFile.getAbsolutePath();
	}
	@Override
	public OpenFile getChild(String name)
	{
		File base = getFile();
		if(!base.isDirectory())
			base = base.getParentFile();
		OpenFile ret = new OpenFile(new File(base, name));
		return ret;
	}
	@Override
	public Boolean isFile() {
		return mFile.isFile();
	}
	@Override
	public Boolean delete() {
		return mFile.delete();
	}
	@Override
	public Boolean mkdir() {
		return mFile.mkdirs();
	}
	@Override
	public InputStream getInputStream() throws IOException {
		return new FileInputStream(mFile);
	}
	public FileChannel getInputChannel() throws IOException {
		return ((FileInputStream)getInputStream()).getChannel();
	}
	@Override
	public OutputStream getOutputStream() throws IOException {
		if(output == null)
			output = new FileOutputStream(mFile);
		return output;
	}
	public FileChannel getOutputChannel() throws IOException
	{
		return ((FileOutputStream)getOutputStream()).getChannel();
	}
	public boolean copyFrom(OpenFile sourceFile)
	{
		try {
			if(!exists())
				getFile().createNewFile();
		} catch(IOException e) { }
		boolean ret = false;
		FileChannel source = null;
		FileChannel dest = null;
		try {
			source = sourceFile.getInputChannel();
			dest = getOutputChannel();
			dest.transferFrom(source, 0, source.size());
			ret = true;
		} catch(IOException e) {
			Logger.LogError("Couldn't CopyFrom (" + sourceFile.getPath() + " -> " + getPath() + ")", e);
		} finally {
			if(source != null)
				try {
					source.close();
				} catch (IOException e) { }
			if(dest != null)
				try {
					dest.close();
				} catch (IOException e) { }
		}
		return ret;
	}
	@Override
	public Boolean isHidden() {
		return mFile.isHidden() || mFile.getName().startsWith(".");
	}
	
	@Override
	public void setPath(String path) {
		mFile = new File(path); 
	}

	public boolean create() throws IOException
	{
		return mFile.createNewFile();
	}

	public String readAscii() {
		StringBuilder sb = new StringBuilder((int)length());
		try {
			BufferedReader br = new BufferedReader(new InputStreamReader(getInputStream()));
			String line;
			while((line = br.readLine()) != null)
				sb.append(line + "\n");
		} catch (Exception e) {
			Logger.LogError("Couldn't read data from OpenFile(" + getPath() + ")", e);
		}
		return sb.toString();
	}
	public void write(String data) {
		try {
			OutputStream s = getOutputStream();
			s.write(data.getBytes());
			s.flush();
			s.close();
		} catch(Exception e) {
			Logger.LogError("Couldn't write to OpenFile (" + getPath() + ")", e);
		}
	}	
}
