package org.eink.inkprobe.notebook;

/** A stationary finger hold, invalidated by writing, navigation, or additional contacts. */
final class NotebookPdfHold {
    private boolean waiting,consumed;private long began;private float x,y,slop;private int revision;
    void begin(long now,float x,float y,float slop,int revision){this.began=now;this.x=x;this.y=y;this.slop=slop;this.revision=revision;waiting=true;consumed=false;}
    void move(float x,float y){if(Math.hypot(x-this.x,y-this.y)>slop)waiting=false;}
    boolean fire(long now,long delay,int revision,boolean penDown){
        if(!waiting||penDown||revision!=this.revision||now-began<delay)return false;
        waiting=false;consumed=true;return true;
    }
    boolean consumed(){return consumed;}
    void cancel(){waiting=false;consumed=false;}
}
