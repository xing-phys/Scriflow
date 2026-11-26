package org.eink.inkprobe.notebook;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

/** Debug-only disposable notebook for exercising the real reading UI in an emulator. */
public final class NotebookReadingFixtureActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        String name=getIntent().getStringExtra("name");int remove=getIntent().getIntExtra("remove",0);
        if(name==null||!name.matches("ReadingOverlap-[a-f0-9]{8}")){finish();return;}
        NotebookSession session=NotebookSession.get(this);
        session.list((page,error)->{
            if(error!=null){report(0,error.toString());return;}
            if(remove>0){
                NotebookCatalog.Entry found=null;for(NotebookCatalog.Entry entry:session.entries())if(entry.id==remove&&name.equals(entry.name))found=entry;
                if(found==null){report(0,"Fixture not found");return;}
                if(found.status==NotebookCatalog.TRASH){
                    session.permanentlyDeleteNotebook(remove,(deleted,deleteError)->{report(remove,deleteError==null?"":deleteError.toString());finish();});
                }else session.trashNotebook(remove,(ignored,trashError)->{
                    if(trashError!=null){report(0,trashError.toString());return;}
                    session.permanentlyDeleteNotebook(remove,(deleted,deleteError)->{report(remove,deleteError==null?"":deleteError.toString());finish();});
                });
                return;
            }
            session.createNotebook(name,(created,createError)->{
                if(createError!=null){report(0,createError.toString());return;}
                int id=session.notebookId();
                // A reused disposable notebook slot can retain earlier view preferences.
                session.saveReadingSettings(id,NotebookReadingSettings.DEFAULT,settingsError->{
                    if(settingsError!=null){report(id,settingsError.toString());return;}
                    session.addPage(1,(second,error2)->{
                        if(error2!=null){report(id,error2.toString());return;}
                        session.addPage(2,(third,error3)->{
                            if(error3!=null){report(id,error3.toString());return;}
                            session.selectPage(1,(first,error4)->{
                                report(id,error4==null?"":error4.toString());
                                if(error4==null)startActivity(new Intent(this,NotebookReadingSelfTestActivity.class));
                                finish();
                            });
                        });
                    });
                });
            });
        });
    }
    private void report(int id,String error) {
        try(FileOutputStream out=new FileOutputStream(new File(getCacheDir(),"reading-fixture.json"))){
            out.write(new JSONObject().put("id",id).put("error",error).toString().getBytes(StandardCharsets.UTF_8));
        }catch(Exception ignored){}
    }
}
