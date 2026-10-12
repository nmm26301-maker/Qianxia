package com.qianxia.backup;
import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;
public final class ShareProvider extends ContentProvider {
    @Override public boolean onCreate(){return true;}
    public static Uri uri(Context c,File f)throws IOException{String root=c.getFilesDir().getCanonicalPath()+"/",path=f.getCanonicalPath();if(!path.startsWith(root))throw new IOException("无法分享该文件");return new Uri.Builder().scheme("content").authority(c.getPackageName()+".files").appendPath(path.substring(root.length())).build();}
    private File file(Uri u)throws FileNotFoundException{try{String path=u.getPathSegments().get(0);File f=new File(getContext().getFilesDir(),path);String canonical=f.getCanonicalPath(),root=getContext().getFilesDir().getCanonicalPath()+"/";if(!canonical.startsWith(root)||!(path.startsWith("backups/")&&path.endsWith(".qxb")||path.startsWith("restores/")&&path.matches("restores/[a-zA-Z0-9-]+/exports/[0-9]+\\.data"))||!f.isFile())throw new IOException();return f;}catch(Exception e){throw new FileNotFoundException("无法访问该文件");}}
    @Override public ParcelFileDescriptor openFile(Uri u,String mode)throws FileNotFoundException{if(!"r".equals(mode))throw new FileNotFoundException("只读文件");return ParcelFileDescriptor.open(file(u),ParcelFileDescriptor.MODE_READ_ONLY);}
    @Override public Cursor query(Uri u,String[]projection,String selection,String[]args,String order){try{File f=file(u);String[]cols=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;MatrixCursor cur=new MatrixCursor(cols);Object[]v=new Object[cols.length];for(int i=0;i<cols.length;i++)v[i]=OpenableColumns.DISPLAY_NAME.equals(cols[i])?f.getName():OpenableColumns.SIZE.equals(cols[i])?f.length():null;cur.addRow(v);return cur;}catch(Exception e){return null;}}
    @Override public String getType(Uri u){return "application/octet-stream";}
    @Override public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
    @Override public int delete(Uri u,String s,String[]a){throw new UnsupportedOperationException();}
    @Override public int update(Uri u,ContentValues v,String s,String[]a){throw new UnsupportedOperationException();}
}
