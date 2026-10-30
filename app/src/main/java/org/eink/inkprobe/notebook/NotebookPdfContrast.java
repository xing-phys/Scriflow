package org.eink.inkprobe.notebook;

/** Display-only gamma: endpoints and alpha stay intact; identity does no pixel work. */
final class NotebookPdfContrast {
    static int[] table(float factor) {
        if(!Float.isFinite(factor)||factor<.2f||factor>3)throw new IllegalArgumentException("Invalid PDF contrast");
        int[] result=new int[256];for(int n=0;n<256;n++)result[n]=Math.max(0,Math.min(255,(int)Math.round(255*Math.pow(n/255.0,factor))));return result;
    }
    static void apply(int[] pixels,int[] table) {
        if(pixels==null||table==null||table.length!=256)throw new IllegalArgumentException("Invalid contrast pixels");
        for(int n=0;n<pixels.length;n++){
            int pixel=pixels[n],rgb=pixel&0x00ffffff;if(rgb==0||rgb==0x00ffffff)continue;
            int r=(pixel>>>16)&255,g=(pixel>>>8)&255,b=pixel&255;
            pixels[n]=(pixel&0xff000000)|(table[r]<<16)|(table[g]<<8)|table[b];
        }
    }
}
