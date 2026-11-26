package org.eink.inkprobe.notebook;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.Arrays;

public final class NotebookDownloadStreamTest {
    private static int checks;
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        byte[] data = new byte[160000]; for (int i=0;i<data.length;i++) data[i]=(byte)(i*31);
        ByteArrayOutputStream output = new ByteArrayOutputStream(); final long[] progress={0};
        NotebookDownloadStream.Control active = new NotebookDownloadStream.Control() {
            public boolean cancelled(){return false;} public void progress(long bytes){progress[0]=bytes;}
        };
        long count=NotebookDownloadStream.copy(new ByteArrayInputStream(data),output,data.length,active);
        check(count==data.length&&progress[0]==count&&Arrays.equals(data,output.toByteArray()),"stream bytes and progress survive chunking");
        check(NotebookDownloadStream.copy(new ByteArrayInputStream(data),new ByteArrayOutputStream(),-1,active)==data.length,"unknown length succeeds");
        try { NotebookDownloadStream.copy(new ByteArrayInputStream(data),new ByteArrayOutputStream(),data.length+1,active); throw new AssertionError("truncation accepted"); }
        catch(IOException expected){checks++;}
        final boolean[] stopped={false}; ByteArrayOutputStream partial=new ByteArrayOutputStream();
        NotebookDownloadStream.Control cancel=new NotebookDownloadStream.Control(){public boolean cancelled(){return stopped[0];}public void progress(long n){stopped[0]=true;}};
        try { NotebookDownloadStream.copy(new ByteArrayInputStream(data),partial,-1,cancel); throw new AssertionError("cancel ignored"); }
        catch(InterruptedIOException expected){check(partial.size()>0&&partial.size()<data.length,"cancel interrupts copying");}
        System.out.println("NotebookDownloadStream: "+checks+" assertions passed.");
    }
}
