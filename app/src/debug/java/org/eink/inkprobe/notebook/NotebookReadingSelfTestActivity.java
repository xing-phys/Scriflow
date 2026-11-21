package org.eink.inkprobe.notebook;

/** Emulator-only entry point for the real note reader with a software ink surface. */
public final class NotebookReadingSelfTestActivity extends NotebookActivity {
    private final android.os.Handler testHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable report=new Runnable(){@Override public void run(){
        try{
            NotebookViewport viewport=(NotebookViewport)value(NotebookActivity.class,"viewport",NotebookReadingSelfTestActivity.this);
            if(viewport!=null){
                float scroll=(Float)value(NotebookActivity.class,"scrollY",NotebookReadingSelfTestActivity.this);
                java.lang.reflect.Method ready=NotebookActivity.class.getDeclaredMethod("canEdit");ready.setAccessible(true);
                org.json.JSONObject result=new org.json.JSONObject().put("time",System.currentTimeMillis()).put("width",viewport.width).put("height",viewport.height)
                    .put("scroll",scroll).put("maxScroll",viewport.maxScroll()).put("pan",viewport.pan).put("singlePage",viewport.singlePage)
                    .put("navigationPage",viewport.navigationPage(scroll)).put("editable",ready.invoke(NotebookReadingSelfTestActivity.this));
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
