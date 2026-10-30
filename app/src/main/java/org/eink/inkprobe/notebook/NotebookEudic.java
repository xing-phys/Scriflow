package org.eink.inkprobe.notebook;

import android.content.*;
import android.net.Uri;

/** Eudic's documented Android floating-window endpoint. Query stays within the chosen app. */
final class NotebookEudic {
    static Intent lookup(Context context,String word){
        Uri uri=new Uri.Builder().scheme("eudic").authority("peek").appendPath(word).build();
        for(String name:new String[]{"com.eusoft.eudic","com.qianyan.eudic"}){
            Intent intent=new Intent(Intent.ACTION_VIEW,uri).setPackage(name);
            if(intent.resolveActivity(context.getPackageManager())!=null)return intent;
        }
        Intent intent=new Intent(Intent.ACTION_VIEW,uri);
        // Only use the documented scheme when another installed Eudic edition provides it.
        return intent.resolveActivity(context.getPackageManager())!=null?intent:null;
    }
    private NotebookEudic(){}
}
