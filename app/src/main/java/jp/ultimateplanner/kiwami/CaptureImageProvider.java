package jp.ultimateplanner.kiwami;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;

/** Grants the external camera a temporary URI for a full-resolution photo.
 * The only exposed file is a fixed JPEG in this application's private cache.
 * No camera or storage permission, and no additional AndroidX dependency, are needed.
 */
public final class CaptureImageProvider extends ContentProvider {
    private File checkedFile(Uri uri) throws FileNotFoundException {
        if(getContext()==null || uri==null
                || !(getContext().getPackageName()+".capture").equals(uri.getAuthority())
                || !"/image".equals(uri.getPath()))throw new FileNotFoundException("Invalid capture URI");
        return new File(getContext().getCacheDir(),"camera_capture.jpg");
    }
    @Override public boolean onCreate(){return true;}
    @Override public String getType(Uri uri){
        try{checkedFile(uri);return "image/jpeg";}
        catch(FileNotFoundException e){return null;}
    }
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode) throws FileNotFoundException {
        File file=checkedFile(uri);
        int flags;
        if("r".equals(mode))flags=ParcelFileDescriptor.MODE_READ_ONLY;
        else if("w".equals(mode)||"wt".equals(mode))flags=ParcelFileDescriptor.MODE_WRITE_ONLY|ParcelFileDescriptor.MODE_CREATE|ParcelFileDescriptor.MODE_TRUNCATE;
        else if("rw".equals(mode)||"rwt".equals(mode))flags=ParcelFileDescriptor.MODE_READ_WRITE|ParcelFileDescriptor.MODE_CREATE|("rwt".equals(mode)?ParcelFileDescriptor.MODE_TRUNCATE:0);
        else throw new FileNotFoundException("Unsupported mode");
        return ParcelFileDescriptor.open(file,flags);
    }
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] selectionArgs,String sortOrder){
        try{
            File file=checkedFile(uri);
            String[] columns=projection!=null?projection:new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE};
            MatrixCursor cursor=new MatrixCursor(columns);
            Object[] values=new Object[columns.length];
            for(int i=0;i<columns.length;i++){
                if(OpenableColumns.DISPLAY_NAME.equals(columns[i]))values[i]=file.getName();
                else if(OpenableColumns.SIZE.equals(columns[i]))values[i]=file.length();
            }
            cursor.addRow(values);return cursor;
        }catch(FileNotFoundException e){return null;}
    }
    @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}
    @Override public int delete(Uri uri,String selection,String[] selectionArgs){throw new UnsupportedOperationException();}
    @Override public int update(Uri uri,ContentValues values,String selection,String[] selectionArgs){throw new UnsupportedOperationException();}
}