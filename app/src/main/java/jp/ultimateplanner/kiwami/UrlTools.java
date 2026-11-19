package jp.ultimateplanner.kiwami;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Browser-only URL helper. Never run embedded scripts or arbitrary URI schemes. */
public final class UrlTools {
    private static final Pattern LINK=Pattern.compile("(?i)https?://[^\\s<>()]+");
    private UrlTools(){}
    public static boolean valid(String candidate){
        try{Uri uri=Uri.parse(candidate);String scheme=uri.getScheme(),host=uri.getHost();
            return scheme!=null && (scheme.equalsIgnoreCase("https")||scheme.equalsIgnoreCase("http")) && host!=null && !host.trim().isEmpty();
        }catch(Exception ignored){return false;}
    }
    public static String find(String raw){
        if(raw==null)return "";
        return ImportParser.findUrl(raw);
    }
    public static void open(Context context,String value){
        if(!valid(value)){Toast.makeText(context,"URLの形式が正しくありません",Toast.LENGTH_SHORT).show();return;}
        try{Intent i=new Intent(Intent.ACTION_VIEW,Uri.parse(value));i.addCategory(Intent.CATEGORY_BROWSABLE);context.startActivity(i);}
        catch(Exception e){Toast.makeText(context,"URLを開けませんでした",Toast.LENGTH_SHORT).show();}
    }
}