package org.eink.inkprobe.notebook;

import java.util.*;

/** Per-notebook view preferences; saved ink and exported page geometry remain authoritative. */
public final class NotebookReadingSettings {
    interface Detection{NotebookCropBox get(int sourcePage,NotebookCropBox area);}
    public enum Crop { NONE, AUTO, SEMI_AUTO, MANUAL }
    public enum Scope { PAGE, ALL, ODD, EVEN }
    public static final NotebookReadingSettings DEFAULT = new NotebookReadingSettings(Crop.NONE,false,1,
        NotebookCropBox.FULL,NotebookCropBox.FULL,Collections.emptyMap());
    public final Crop crop;
    /** True for discrete page turns; false for continuous scrolling. */
    public final boolean paged;
    public final boolean doublePage;
    /** In a spread, page 1 is a cover on the right; subsequent pairs are 2–3, 4–5, etc. */
    public final boolean cover;
    /** Continuous scrolling direction, remembered while page turns are selected. */
    public final boolean horizontal;
    public final float contrast;
    public final NotebookCropBox odd,even;
    public final Map<UUID,NotebookCropBox> pages;
    public NotebookReadingSettings(Crop crop,boolean singlePage,float contrast,NotebookCropBox odd,
            NotebookCropBox even,Map<UUID,NotebookCropBox> pages) {
        this(crop,singlePage,false,false,false,contrast,odd,even,pages);
    }
    private NotebookReadingSettings(Crop crop,boolean paged,boolean doublePage,boolean horizontal,boolean cover,float contrast,NotebookCropBox odd,
            NotebookCropBox even,Map<UUID,NotebookCropBox> pages) {
        if(crop==null||!Float.isFinite(contrast)||contrast<.2f||contrast>3||odd==null||even==null||pages==null||pages.size()>NotebookIndex.MAX_PAGES)
            throw new IllegalArgumentException("Invalid reading settings");
        LinkedHashMap<UUID,NotebookCropBox> copy=new LinkedHashMap<>();
        for(Map.Entry<UUID,NotebookCropBox> e:pages.entrySet()){if(e.getKey()==null||e.getValue()==null)throw new IllegalArgumentException("Invalid page crop");copy.put(e.getKey(),e.getValue());}
        this.crop=crop;this.paged=paged;this.doublePage=doublePage;this.horizontal=horizontal;this.cover=cover;this.contrast=contrast;this.odd=odd;this.even=even;this.pages=Collections.unmodifiableMap(copy);
    }
    public NotebookCropBox manual(int page,UUID id){NotebookCropBox box=pages.get(id);return box!=null?box:page%2==1?odd:even;}
    public NotebookCropBox area(int page,UUID id,NotebookCropBox detected) {
        if(crop==Crop.NONE)return NotebookCropBox.FULL;
        if(crop==Crop.AUTO)return detected==null?NotebookCropBox.FULL:detected;
        NotebookCropBox box=manual(page,id);
        return crop==Crop.MANUAL||detected==null?box:detected;
    }
    public NotebookReadingSettings crop(Crop value){return new NotebookReadingSettings(value,paged,doublePage,horizontal,cover,contrast,odd,even,pages);}
    public NotebookReadingSettings paged(boolean value){return new NotebookReadingSettings(crop,value,doublePage,horizontal,cover,contrast,odd,even,pages);}
    public NotebookReadingSettings doublePage(boolean value){return new NotebookReadingSettings(crop,paged,value,horizontal,cover,contrast,odd,even,pages);}
    public NotebookReadingSettings horizontal(boolean value){return new NotebookReadingSettings(crop,paged,doublePage,value,cover,contrast,odd,even,pages);}
    public NotebookReadingSettings cover(boolean value){return new NotebookReadingSettings(crop,paged,doublePage,horizontal,value,contrast,odd,even,pages);}
    public NotebookReadingSettings contrast(float value){return new NotebookReadingSettings(crop,paged,doublePage,horizontal,cover,value,odd,even,pages);}
    public NotebookReadingSettings area(Scope scope,UUID id,NotebookCropBox box) {
        Map<UUID,NotebookCropBox> values=new LinkedHashMap<>(pages);NotebookCropBox a=odd,b=even;
        if(scope==Scope.PAGE){if(id==null)throw new IllegalArgumentException("Missing crop page");values.put(id,box);}
        else if(scope==Scope.ALL){a=b=box;values.clear();}
        else if(scope==Scope.ODD){a=box;values.remove(id);}else{b=box;values.remove(id);}
        return new NotebookReadingSettings(crop,paged,doublePage,horizontal,cover,contrast,a,b,values);
    }
    List<NotebookViewport.Size> layout(List<NotebookViewport.Size> original,Detection detector){
        if(crop==Crop.NONE)return original;
        List<NotebookViewport.Size> result=new ArrayList<>(original.size());
        for(int n=0;n<original.size();n++){
            NotebookViewport.Size size=original.get(n);NotebookCropBox box=NotebookCropBox.FULL;
            if(size.pdfPage>0){NotebookCropBox roi=crop==Crop.AUTO?NotebookCropBox.FULL:manual(n+1,size.id);
                box=area(n+1,size.id,crop==Crop.MANUAL?null:detector.get(size.pdfPage,roi));}
            result.add(size.cropped(box));
        }
        return Collections.unmodifiableList(result);
    }
    @Override public boolean equals(Object o){if(!(o instanceof NotebookReadingSettings))return false;NotebookReadingSettings s=(NotebookReadingSettings)o;return crop==s.crop&&paged==s.paged&&doublePage==s.doublePage&&horizontal==s.horizontal&&cover==s.cover&&contrast==s.contrast&&odd.equals(s.odd)&&even.equals(s.even)&&pages.equals(s.pages);}
    @Override public int hashCode(){return Objects.hash(crop,paged,doublePage,horizontal,cover,contrast,odd,even,pages);}
}
