package org.eink.inkprobe.notebook;

import java.io.*;
import java.util.*;

/** Synthetic Android-runtime baseline; never opens the user's library or displays note contents. */
public final class NotebookFoundationBenchmark {
    public static void main(String[] args) throws Exception {
        File root = new File(args[0]);
        if (root.exists()) throw new IOException("Benchmark output already exists");
        if (!root.mkdirs()) throw new IOException("Cannot create benchmark directory");
        File source = new File(root,"source"); NotebookLibrary library = new NotebookLibrary(source);
        library.load(); library.initializeSize(1404,1688);
        long begin=System.nanoTime();
        for (int i=0;i<100;i++) {
            List<NotebookPage.Point> points=new ArrayList<>();
            for(int j=0;j<128;j++) points.add(new NotebookPage.Point(10+j*5,30+i*10,j*8L,.2f+j/128f));
            library.append(new NotebookPage.Stroke(2,4,0xff000000,points));
        }
        long written=System.nanoTime();
        File backup=new File(root,"synthetic.inkbook");
        try(FileOutputStream out=new FileOutputStream(backup)) {
            NotebookBackup.write(out,"Synthetic",1,library::previewPage,()->false); out.getFD().sync();
        }
        long backed=System.nanoTime(); NotebookBackup.Restored restored;
        try(FileInputStream input=new FileInputStream(backup)) {
            restored=NotebookBackup.read(input,new File(root,"restored"),()->false);
        }
        long end=System.nanoTime(); NotebookPage copy=new NotebookStore(restored.directory).load();
        if(copy.strokes.size()!=100 || copy.pointCount!=12800 || !copy.id.equals(library.current().id))
            throw new AssertionError("Round trip mismatch");
        for (int i=0;i<copy.strokes.size();i++) {
            NotebookPage.Stroke a=library.current().strokes.get(i), b=copy.strokes.get(i);
            if(!a.id.equals(b.id) || a.width!=b.width || a.color!=b.color || a.penType!=b.penType)
                throw new AssertionError("Stroke metadata changed");
            for(int j=0;j<a.points.size();j++) {
                NotebookPage.Point p=a.points.get(j), q=b.points.get(j);
                if(p.x!=q.x || p.y!=q.y || p.pressure!=q.pressure || p.time!=q.time)
                    throw new AssertionError("Pen sample changed");
            }
        }
        String json="{\"strokes\":100,\"points\":12800,\"append_ms\":"+(written-begin)/1e6
            +",\"backup_ms\":"+(backed-written)/1e6+",\"restore_ms\":"+(end-backed)/1e6
            +",\"archive_bytes\":"+backup.length()+",\"verified\":true}";
        System.out.println(json);
        try(FileOutputStream out=new FileOutputStream(new File(root,"result.json"))) { out.write(json.getBytes("UTF-8")); }
    }
}
