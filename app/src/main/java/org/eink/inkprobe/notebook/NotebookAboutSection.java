package org.eink.inkprobe.notebook;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.provider.Settings;
import android.text.format.Formatter;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** Version, explicit release checks, and update preferences in the existing Settings screen. */
final class NotebookAboutSection {
    private final Activity activity;private final NotebookUpdates updates;
    private final TextView status;private final NotebookButton check,download,install,cancel,remove;
    private final CheckBox previews;
    private final Runnable listener=this::render;private boolean active;

    NotebookAboutSection(Activity activity,LinearLayout body){
        this.activity=activity;updates=NotebookUpdates.get(activity);
        TextView title=label("About",16);title.setTypeface(null,Typeface.BOLD);
        LinearLayout.LayoutParams heading=new LinearLayout.LayoutParams(-1,dp(32));heading.setMargins(0,dp(12),0,dp(8));body.addView(title,heading);
        TextView version=label("Scriflow "+updates.currentVersion(),15);version.setContentDescription("Installed version: "+updates.currentVersion());body.addView(version);
        previews=new CheckBox(activity);previews.setText("Include preview releases");previews.setTextSize(14);previews.setTextColor(Color.BLACK);
        previews.setButtonTintList(android.content.res.ColorStateList.valueOf(Color.BLACK));previews.setChecked(updates.includePreviews());
        previews.setOnCheckedChangeListener((button,checked)->updates.includePreviews(checked));body.addView(previews);
        CheckBox autoDelete=new CheckBox(activity);autoDelete.setText("Delete installer after a successful update");autoDelete.setTextSize(14);autoDelete.setTextColor(Color.BLACK);
        autoDelete.setButtonTintList(android.content.res.ColorStateList.valueOf(Color.BLACK));autoDelete.setChecked(updates.autoDelete());
        autoDelete.setOnCheckedChangeListener((button,checked)->updates.autoDelete(checked));body.addView(autoDelete);
        LinearLayout buttons=new LinearLayout(activity);buttons.setGravity(Gravity.CENTER_VERTICAL);
        check=button("Check for updates",updates::check);download=button("Download update",updates::download);cancel=button("Cancel download",updates::cancel);
        buttons.addView(check,new LinearLayout.LayoutParams(-2,dp(40)));
        LinearLayout.LayoutParams beside=new LinearLayout.LayoutParams(-2,dp(40));beside.setMarginStart(dp(8));buttons.addView(download,beside);
        LinearLayout.LayoutParams cancelSpace=new LinearLayout.LayoutParams(-2,dp(40));cancelSpace.setMarginStart(dp(8));buttons.addView(cancel,cancelSpace);body.addView(buttons);
        LinearLayout files=new LinearLayout(activity);files.setGravity(Gravity.CENTER_VERTICAL);
        install=button("Install update",this::install);remove=button("Remove saved installers…",this::confirmRemove);
        files.addView(install,new LinearLayout.LayoutParams(-2,dp(40)));
        LinearLayout.LayoutParams removeSpace=new LinearLayout.LayoutParams(-2,dp(40));removeSpace.setMarginStart(dp(8));files.addView(remove,removeSpace);body.addView(files);
        status=label("",13);status.setPadding(0,dp(6),0,dp(6));body.addView(status);
        body.addView(label("Updates come from Scriflow's GitHub releases. Android asks you to allow installations from Scriflow and confirm the update.",12));
        render();
    }
    void resume(){active=true;updates.observe(listener);render();}
    void pause(){active=false;updates.removeObserver(listener);}
    private void render(){
        NotebookUpdates.Snapshot state=updates.snapshot();boolean busy=state.phase==NotebookUpdates.Phase.CHECKING||state.phase==NotebookUpdates.Phase.DOWNLOADING||state.phase==NotebookUpdates.Phase.VERIFYING;
        previews.setEnabled(!busy);
        check.setAvailable(!busy);download.setVisibility(state.phase==NotebookUpdates.Phase.AVAILABLE?View.VISIBLE:View.GONE);
        cancel.setVisibility(state.phase==NotebookUpdates.Phase.DOWNLOADING?View.VISIBLE:View.GONE);
        install.setVisibility(!state.ready.isEmpty()&&state.readyCode>updates.currentCode()?View.VISIBLE:View.GONE);
        install.setAvailable(!busy);remove.setAvailable(!busy);
        String message=state.message;
        if(state.phase==NotebookUpdates.Phase.DOWNLOADING){
            message="Downloading "+state.available+" · "+Formatter.formatShortFileSize(activity,state.bytes)+" / "+Formatter.formatShortFileSize(activity,state.total);
        } else if(message.isEmpty()&&!state.ready.isEmpty())message=state.readyCode>updates.currentCode()?"Update "+state.ready+" is ready to install.":"Installer for "+state.ready+" is saved.";
        status.setText(message);
    }
    private void install(){
        if(!activity.getPackageManager().canRequestPackageInstalls()){
            AlertDialog prompt=new AlertDialog.Builder(activity).setTitle("Allow app updates")
                .setMessage("Allow installations from Scriflow in Android settings, then return here and tap Install update.")
                .setNegativeButton("Cancel",null).setPositiveButton("Open settings",(dialog,which)->{
                    try{activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+activity.getPackageName())));}
                    catch(ActivityNotFoundException error){problem("Android could not open installation settings.");}
                }).create();NotebookUi.show(prompt);return;
        }
        updates.prepareInstall(error->{
            if(!active||activity.isFinishing()||activity.isDestroyed())return;
            if(error!=null){problem(error);return;}
            Uri uri=updates.readyUri();if(uri==null){problem("The downloaded installer is no longer available.");return;}
            try{activity.startActivity(new Intent(Intent.ACTION_INSTALL_PACKAGE).setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));}
            catch(ActivityNotFoundException|SecurityException failure){problem("Android could not open the package installer.");}
        });
    }
    private void confirmRemove(){
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("Remove saved installers?")
            .setMessage("Downloaded Scriflow installers will be deleted. Your notebooks and other files are kept.")
            .setNegativeButton("Cancel",null).setPositiveButton("Remove",(choice,which)->updates.removeInstallers(error->{
                if(!active)return;if(error!=null)problem(error);else Toast.makeText(activity,"Saved installers removed",Toast.LENGTH_SHORT).show();
            })).create();NotebookUi.show(dialog);
    }
    private void problem(String text){Toast.makeText(activity,text,Toast.LENGTH_LONG).show();}
    private int dp(int value){return Math.round(value*activity.getResources().getDisplayMetrics().density);}
    private TextView label(String text,int size){TextView label=new TextView(activity);label.setText(text);label.setTextColor(Color.BLACK);label.setTextSize(size);return label;}
    private NotebookButton button(String text,Runnable action){NotebookButton button=new NotebookButton(activity);button.setText(text);button.setTextSize(13);NotebookUi.action(button,null);button.setOnClickListener(v->action.run());return button;}
}
