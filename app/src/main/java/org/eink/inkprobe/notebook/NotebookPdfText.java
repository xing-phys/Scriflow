package org.eink.inkprobe.notebook;

import java.text.BreakIterator;
import java.text.Normalizer;
import java.util.*;

/** Embedded PDF text in normalized, rotated source-page coordinates. No OCR or source edits. */
final class NotebookPdfText {
    static final class Glyph {
        final String text; final float left,top,right,bottom;
        Glyph(String text,float left,float top,float right,float bottom){
            this.text=Normalizer.normalize(text,Normalizer.Form.NFKC);
            this.left=left;this.top=top;this.right=right;this.bottom=bottom;
        }
    }
    static final class Word {
        final String text;final float left,top,right,bottom;
        Word(String text,float left,float top,float right,float bottom){this.text=text;this.left=left;this.top=top;this.right=right;this.bottom=bottom;}
        boolean contains(float x,float y){return x>=left&&x<=right&&y>=top&&y<=bottom;}
    }
    final List<Word> words;
    NotebookPdfText(List<Word> words){this.words=Collections.unmodifiableList(new ArrayList<>(words));}
    Word at(float x,float y){
        Word best=null;float area=Float.MAX_VALUE;
        for(Word word:words)if(word.contains(x,y)){
            float a=(word.right-word.left)*(word.bottom-word.top);
            if(a<area){best=word;area=a;}
        }
        return best;
    }
    static void append(List<Word> result,List<Glyph> glyphs){
        StringBuilder text=new StringBuilder();List<Integer> ends=new ArrayList<>();
        for(Glyph glyph:glyphs){text.append(glyph.text);ends.add(text.length());}
        BreakIterator iterator=BreakIterator.getWordInstance(Locale.getDefault());iterator.setText(text.toString());
        int start=iterator.first(),firstGlyph=0;
        for(int end=iterator.next();end!=BreakIterator.DONE;start=end,end=iterator.next()){
            String word=text.substring(start,end);boolean letters=false;
            for(int i=0;i<word.length();){int code=word.codePointAt(i);letters|=Character.isLetterOrDigit(code);i+=Character.charCount(code);}
            if(!letters)continue;
            while(firstGlyph<ends.size()&&ends.get(firstGlyph)<=start)firstGlyph++;
            float l=1,t=1,r=0,b=0;int previous=firstGlyph==0?0:ends.get(firstGlyph-1);
            for(int i=firstGlyph;i<glyphs.size()&&previous<end;i++){
                int next=ends.get(i);Glyph g=glyphs.get(i);
                if(next>start&&previous<end){l=Math.min(l,g.left);t=Math.min(t,g.top);r=Math.max(r,g.right);b=Math.max(b,g.bottom);}
                previous=next;
            }
            l=Math.max(0,l);t=Math.max(0,t);r=Math.min(1,r);b=Math.min(1,b);
            if(r>l&&b>t)result.add(new Word(word,l,t,r,b));
        }
    }
}
