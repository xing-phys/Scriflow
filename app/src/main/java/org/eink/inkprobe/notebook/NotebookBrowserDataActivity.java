package org.eink.inkprobe.notebook;

import android.app.*;
import android.os.Bundle;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import java.util.*;
import static org.eink.inkprobe.notebook.NotebookBrowserUi.*;

/** Browser profile controls are separate from bookmarks, snapshots and notebook storage. */
public final class NotebookBrowserDataActivity extends NotebookDisplayActivity {
    private LinearLayout sites;
    private TextView status;
    private NotebookButton clear,refresh,host;
    private AlertDialog dialog;
    private int generation;
    private boolean active;
    private final Map<String,Integer> listedSites=new TreeMap<>();
    private EditText siteFilter;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);getWindow().setWindowAnimations(0);
        LinearLayout root=column(this);root.setPadding(dp(this,18),dp(this,12),dp(this,18),dp(this,12));header(this,root,"Browser data",this::finish,deviceSettingsIsland());
        ScrollView scroll=new ScrollView(this);scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);LinearLayout body=column(this);scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        body.addView(label(this,"Search engine",19));RadioGroup searches=new RadioGroup(this);String[] providers={"DuckDuckGo","Google","Bing"};for(int i=0;i<providers.length;i++){RadioButton choice=new RadioButton(this);choice.setId(200+i);choice.setText(providers[i]);choice.setTextColor(Color.BLACK);searches.addView(choice);}searches.check(200+getSharedPreferences("browser-data",MODE_PRIVATE).getInt("search",0));searches.setOnCheckedChangeListener((group,id)->getSharedPreferences("browser-data",MODE_PRIVATE).edit().putInt("search",id-200).apply());body.addView(searches);space(body,dp(this,16));
        body.addView(label(this,"Cookies",19));RadioGroup choices=new RadioGroup(this);String[] names={"Block third-party cookies (default)","Allow all cookies","Block all cookies"};
        for(int i=0;i<names.length;i++){RadioButton choice=new RadioButton(this);choice.setId(100+i);choice.setText(names[i]);choice.setTextColor(Color.BLACK);choices.addView(choice);}choices.check(100+NotebookBrowserData.cookieMode(this));choices.setOnCheckedChangeListener((group,id)->NotebookBrowserData.cookies(this,id-100));body.addView(choices);
        body.addView(label(this,"Blocking cookies may prevent some sign-ins. Existing cookies are removed only when you choose Clear data.",13));space(body,dp(this,14));
        clear=button(this,"Clear browsing data…",this::confirmClear);body.addView(clear,new LinearLayout.LayoutParams(-1,dp(this,48)));space(body,dp(this,8));body.addView(label(this,"Bookmarks, pinned websites, saved offline pages and notebooks stay on this tablet. Local page previews are reset.",13));space(body,dp(this,24));
        body.addView(label(this,"Recent sites and cookie domains",19));body.addView(label(this,"Tap a host to clear its cookies, website storage and cache. A host’s embedded third-party sites have their own entries.",13));
        space(body,dp(this,12));LinearLayout tools=row(this);refresh=icon(this,NotebookIconDrawable.Icon.RETRY,"Refresh website data",this::refresh);host=button(this,"Choose a website…",this::chooseHost);tools.addView(host,new LinearLayout.LayoutParams(0,dp(this,48),1));tools.addView(refresh,beside(this,48,48));body.addView(tools);space(body,dp(this,12));siteFilter=input(this,"Find a website host","");field(siteFilter);siteFilter.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int c){}public void afterTextChanged(android.text.Editable e){}public void onTextChanged(CharSequence s,int a,int b,int c){renderSites();}});body.addView(siteFilter,new LinearLayout.LayoutParams(-1,dp(this,48)));status=label(this,"Reading browser data…",13);status.setPadding(0,dp(this,10),0,dp(this,4));body.addView(status);sites=column(this);body.addView(sites);setContentView(root);
    }
    @Override protected void onResume(){super.onResume();active=true;refresh();}
    @Override protected void onPause(){active=false;++generation;if(dialog!=null)dialog.dismiss();super.onPause();}
    private void availability(){boolean available=!NotebookBrowserData.clearing();clear.setAvailable(available);refresh.setAvailable(available);host.setAvailable(available);}
    private void refresh(){availability();if(NotebookBrowserData.clearing()){status.setText("Clearing browser data…");return;}final int request=++generation;status.setText("Reading browser data…");NotebookWebsiteTasks.run(()->new NotebookBrowserLibrary(getFilesDir()).read(),(library,error)->{
        if(!active||request!=generation)return;if(error!=null){status.setText(error.getMessage());return;}
        NotebookBrowserData.sites(this,(cookies,failure)->{if(!active||request!=generation)return;Map<String,Integer> values=new TreeMap<>();if(cookies!=null)values.putAll(cookies);for(NotebookBrowserLibrary.Visit visit:library.visits)values.putIfAbsent(NotebookBrowserLibrary.host(visit.url),0);listedSites.clear();listedSites.putAll(values);renderSites();if(failure!=null)status.setText(failure);
        });
    });}
    private void renderSites(){
        if(sites==null)return;sites.removeAllViews();String query=siteFilter.getText().toString().trim().toLowerCase(Locale.ROOT);int total=0,shown=0;
        for(Map.Entry<String,Integer> entry:listedSites.entrySet()){String name=entry.getKey();if(!name.contains(query))continue;total++;if(shown>=100)continue;shown++;NotebookButton item=button(this,name+" · "+entry.getValue()+" cookies",()->confirmHost(name));item.setContentDescription("Clear website data for "+name);LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(-1,dp(this,48));size.topMargin=dp(this,6);sites.addView(item,size);}
        status.setText(shown+" of "+total+" matching hosts · Cookie values stay private"+(total>100?" · Refine the host filter":""));
    }
    private void chooseHost(){EditText input=input(this,"Website address or host",getIntent().getStringExtra("host"));dialog=new AlertDialog.Builder(this).setTitle("Choose a website").setView(input).setNegativeButton("Cancel",null).setPositiveButton("Continue",null).create();NotebookUi.show(dialog);AlertDialog owner=dialog;owner.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{String name=NotebookBrowserData.host(input.getText().toString());owner.dismiss();confirmHost(name);}catch(IllegalArgumentException error){input.setError(error.getMessage());}});}
    private void confirmHost(String host){dialog=new AlertDialog.Builder(this).setTitle("Clear data for "+host+"?").setMessage("This closes open web pages and may sign you out. Cookies shared with subdomains may also be removed. Bookmarks, offline copies and notebooks are kept.").setNegativeButton("Cancel",null).setPositiveButton("Clear this host",(d,w)->perform(host,true,true,false)).create();NotebookUi.show(dialog);}
    private void confirmClear(){boolean[] selected={false,true,false};dialog=new AlertDialog.Builder(this).setTitle("Clear browsing data").setMultiChoiceItems(new String[]{"Cookies and website storage (signs you out)","Cached images and files","Browsing history"},selected,(d,which,checked)->selected[which]=checked).setNegativeButton("Cancel",null).setPositiveButton("Continue",(d,w)->{
        if(!selected[0]&&!selected[1]&&!selected[2])return;dialog=new AlertDialog.Builder(this).setTitle("Clear selected browser data?").setMessage("Open web pages will close. Bookmarks, pinned websites, offline copies and notebooks are kept.").setNegativeButton("Cancel",null).setPositiveButton("Clear data",(owner,which)->perform(null,selected[0],selected[1],selected[2])).create();NotebookUi.show(dialog);
    }).create();NotebookUi.show(dialog);}
    private void perform(String host,boolean cookies,boolean cache,boolean history){status.setText("Clearing browser data…");NotebookBrowserData.clear(this,host,cookies,cache,history,(value,error)->{if(isDestroyed())return;availability();if(error!=null){status.setText(error);return;}status.setText("Selected browser data cleared");if(active)refresh();});availability();}
}
