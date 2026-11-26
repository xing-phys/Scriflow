package org.eink.inkprobe.notebook;

/** Emulator-only entry point for the real note reader with a software ink surface. */
public final class NotebookReadingSelfTestActivity extends NotebookActivity {
    @Override public void onCreate(android.os.Bundle state){
        // A disposable notebook can reuse a slot whose earlier test workspace was saved.
        if(state==null&&getIntent().getBooleanExtra("freshFixture",false))getPreferences(MODE_PRIVATE).edit().clear().apply();
        super.onCreate(state);
    }
    private final android.os.Handler testHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable report=new Runnable(){@Override public void run(){
        try{
            NotebookViewport viewport=(NotebookViewport)value(NotebookActivity.class,"viewport",NotebookReadingSelfTestActivity.this);
            if(viewport!=null){
                float scroll=(Float)value(NotebookActivity.class,"scrollY",NotebookReadingSelfTestActivity.this);
                java.lang.reflect.Method ready=NotebookActivity.class.getDeclaredMethod("canEdit");ready.setAccessible(true);
                org.json.JSONObject result=new org.json.JSONObject().put("time",System.currentTimeMillis()).put("width",viewport.width).put("height",viewport.height)
                    .put("scroll",scroll).put("maxScroll",viewport.maxScroll()).put("pan",viewport.pan).put("singlePage",viewport.singlePage)
                    .put("spread",viewport.spread).put("cover",viewport.cover).put("horizontal",viewport.horizontal)
                    .put("navigationPage",viewport.navigationPage(scroll)).put("editable",ready.invoke(NotebookReadingSelfTestActivity.this))
                    .put("zoom",(Float)value(NotebookActivity.class,"zoom",NotebookReadingSelfTestActivity.this))
                    .put("notebookId",((NotebookSession)value(NotebookActivity.class,"session",NotebookReadingSelfTestActivity.this)).notebookId());
                NotebookReadingMenu menu=(NotebookReadingMenu)value(NotebookActivity.class,"readingMenu",NotebookReadingSelfTestActivity.this);
                NotebookInkBackend backend=(NotebookInkBackend)value(NotebookActivity.class,"ink",NotebookReadingSelfTestActivity.this);
                result.put("menuOpen",menu!=null).put("previewReady",menu!=null
                    && !(Boolean)value(NotebookActivity.class,"scrolling",NotebookReadingSelfTestActivity.this)
                    && !(Boolean)value(NotebookActivity.class,"settleRead",NotebookReadingSelfTestActivity.this)
                    && !(Boolean)value(NotebookActivity.class,"fetching",NotebookReadingSelfTestActivity.this))
                    .put("viewportUpdates",backend instanceof NotebookBrowserTestInk?((NotebookBrowserTestInk)backend).viewportUpdates():-1);
                if(menu!=null)result.put("crop",menu.selectedSettings().crop.name()).put("contrast",menu.selectedSettings().contrast);
                NotebookCropEditor crop=(NotebookCropEditor)value(NotebookActivity.class,"cropEditor",NotebookReadingSelfTestActivity.this);
                if(crop!=null&&crop.isShown()&&crop.getWidth()>0&&crop.getHeight()>0){
                    int[] position=new int[2];crop.getLocationOnScreen(position);
                    result.put("cropLeft",position[0]).put("cropTop",position[1]).put("cropWidth",crop.getWidth()).put("cropHeight",crop.getHeight())
                        .put("density",getResources().getDisplayMetrics().density);
                }
                try(java.io.FileOutputStream output=new java.io.FileOutputStream(new java.io.File(getCacheDir(),"reading-ui-state.json"))){output.write(result.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}
            }
        }catch(Exception ignored){}
        testHandler.postDelayed(this,300);
    }};
    private static Object value(Class<?> owner,String name,Object instance)throws Exception{
        java.lang.reflect.Field field=owner.getDeclaredField(name);field.setAccessible(true);return field.get(instance);
    }
    @Override protected void onResume(){super.onResume();testHandler.post(report);}
    @Override protected void onPause(){testHandler.removeCallbacks(report);super.onPause();}
    @Override protected void onNewIntent(android.content.Intent intent){
        super.onNewIntent(intent);
        if(intent.getBooleanExtra("openInfo",false)) {
            testHandler.postDelayed(()->{try{
                java.lang.reflect.Method info=NotebookActivity.class.getDeclaredMethod("pageInformation");
                info.setAccessible(true);info.invoke(this);
            }catch(Exception error){android.util.Log.e("ReadingSelfTest","Cannot open page information",error);}},500);
            return;
        }
        if(intent.getBooleanExtra("openOutline",false)) {
            testHandler.postDelayed(()->{try{
                java.lang.reflect.Method outline=NotebookActivity.class.getDeclaredMethod("showOutline");
                outline.setAccessible(true);outline.invoke(this);
            }catch(Exception error){android.util.Log.e("ReadingSelfTest","Cannot open outline",error);}},500);
            return;
        }
        if(!intent.getBooleanExtra("openReading",false))return;
        testHandler.postDelayed(()->{
            try{
                NotebookStatusIslands islands=(NotebookStatusIslands)value(NotebookActivity.class,"statusIslands",this);
                if(!islands.actionsShowing())((android.view.View)value(NotebookStatusIslands.class,"more",islands)).performClick();
                testHandler.postDelayed(()->{try{
                    @SuppressWarnings("unchecked") java.util.Map<NotebookStatusIslands.Action,NotebookIconButton> buttons=
                        (java.util.Map<NotebookStatusIslands.Action,NotebookIconButton>)value(NotebookStatusIslands.class,"actionButtons",islands);
                    buttons.get(NotebookStatusIslands.Action.READING).performClick();
                }catch(Exception error){android.util.Log.e("ReadingSelfTest","Cannot open reading menu",error);}},500);
            }catch(Exception error){android.util.Log.e("ReadingSelfTest","Cannot expand note controls",error);}
        },500);
    }
    @Override protected NotebookInkBackend createWritingSurface(NotebookPage initial, NotebookInkBackend.Listener listener) {
        return new NotebookBrowserTestInk(this, initial, listener);
    }
}
