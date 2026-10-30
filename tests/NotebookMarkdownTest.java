package org.eink.inkprobe.notebook;

import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.CRC32;

/** Real storage, recovery, linked history, old-format migration and current-zoom geometry. */
public final class NotebookMarkdownTest {
    private static int checks;
    private static final List<File> roots=new ArrayList<>();
    private interface Action { void run() throws Exception; }
    private static byte[] pdf(String text){return ("%PDF-1.4\n"+text+"\n%%EOF\n").getBytes(StandardCharsets.UTF_8);}
    private static NotebookMarkdown markdown(){return new NotebookMarkdown("# 状态\n\n$\\ket{\\psi}$",false,640,320,pdf("vector"),640,480,pdf("source"));}
    private static NotebookFigure figure(){return NotebookFigure.markdown(UUID.randomUUID(),100,150,120,37,markdown()).fitted(200,300);}
    public static void main(String[] args)throws Exception{
        try{payload();durability();legacy();drafts();tiles();
            System.out.println("NotebookMarkdown: "+checks+" assertions passed (source/vector codec, v7 migration, backup, linked undo, draft recovery and zoom tiles).");
        }finally{for(File root:roots)NotebookBackup.deleteTree(root);}
    }
    private static void payload()throws Exception{
        byte[] rendered=pdf("vector"),source=pdf("code");
        NotebookMarkdown block=new NotebookMarkdown("a $b$ 中文",false,640,320,rendered,640,480,source);
        rendered[0]=0;source[0]=0;byte[] exposed=block.pdf();exposed[0]=0;
        check(block.pdf()[0]=='%',"appearance inputs and outputs are immutable");
        check(block.asSource(false)==block&&block.asSource(true).width()==640&&block.asSource(true).height()==480,"mode switching reuses vectors with the correct aspect");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();block.writeTo(new DataOutputStream(bytes));
        check(bytes.size()==block.byteCount(),"payload accounting includes UTF-8, both PDFs and every header");
        check(NotebookMarkdown.readFrom(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))).equals(block),"strict UTF-8 payload round trip");
        for(int n=0;n<bytes.size();n++){final int count=n;reject(()->NotebookMarkdown.readFrom(new DataInputStream(new ByteArrayInputStream(Arrays.copyOf(bytes.toByteArray(),count)))));}
        reject(()->new NotebookMarkdown("\ud800",false,640,320,pdf("a"),640,480,pdf("b")));
        reject(()->new NotebookMarkdown("x",false,Float.NaN,320,pdf("a"),640,480,pdf("b")));
        reject(()->new NotebookMarkdown("x",false,640,320,new byte[16],640,480,pdf("b")));
        reject(()->new NotebookMarkdown(new String(new char[NotebookMarkdown.MAX_SOURCE_BYTES+1]).replace('\0','x'),false,640,320,pdf("a"),640,480,pdf("b")));
        NotebookFigure original=figure(),raw=original.withMarkdown(original.markdown.asSource(true));
        check(raw.id.equals(original.id)&&raw.angle==original.angle&&raw.markdown.source.equals(original.markdown.source),"source toggle retains identity, source and orientation");
        check(!raw.imageKey.equals(original.imageKey)&&original.placed(0,0,30,15).imageKey==original.imageKey,"appearance invalidates only its own vector cache; placement shares immutable assets");
        check(original.duplicate().markdown==original.markdown,"copy retains exact source and both vector appearances");
        check(original.storedBytes()==original.byteCount()+original.markdown.byteCount(),"page budgets include Markdown data");
        reject(()->original.withMarkdown(null));
        check(javax.imageio.ImageIO.read(new ByteArrayInputStream(original.png()))!=null,"schema sentinel is a valid PNG");
    }
    private static void durability()throws Exception{
        File root=root();NotebookBook book=new NotebookBook(root);book.load();book.initializeSize(200,300);book.addPage();
        NotebookFigure original=figure();book.setFigures(1,Collections.singletonList(original));
        NotebookFigure moved=original.placed(30,80,120,60).fitted(200,300);UUID id=book.page(1).id;
        book.editFigure(1,2,id,original,moved,false);
        check(book.page(1).figures.isEmpty()&&book.page(2).figures.get(0).markdown.equals(original.markdown),"cross-page move retains editable source and vectors");
        check(book.undoOnPage(2)&&book.page(1).figures.get(0).equals(original)&&book.page(2).figures.isEmpty(),"one undo restores both Markdown pages");
        check(book.redoOnPage(1)&&book.page(2).figures.get(0).equals(moved),"one redo restores the exact block");
        NotebookFigure raw=moved.withMarkdown(moved.markdown.asSource(true)).fitted(200,300);
        book.editFigure(2,2,book.page(2).id,moved,raw,false);
        check(book.undoOnPage(2)&&book.page(2).figures.get(0).equals(moved),"source display mode is undoable");book.redoOnPage(2);
        NotebookBook reopened=new NotebookBook(root);reopened.load();check(reopened.page(2).figures.get(0).equals(raw),"reopen retains exact code mode and source");
        book.managePages(NotebookBook.PageAction.DUPLICATE,2,2);
        check(book.current().figures.get(0).markdown.equals(raw.markdown)&&!book.current().figures.get(0).id.equals(raw.id),"duplicating pages gives blocks independent identities");
        ByteArrayOutputStream archive=new ByteArrayOutputStream();NotebookBackup.write(archive,"Markdown",book.pageCount(),book::previewPage,()->false);
        NotebookBackup.Restored copy=NotebookBackup.read(new ByteArrayInputStream(archive.toByteArray()),new File(root(),"restore"),()->false);
        NotebookBook restored=new NotebookBook(copy.directory);restored.load();
        for(int n=1;n<=book.pageCount();n++)check(NotebookDocument.samePage(book.page(n),restored.page(n)),"backup retains page "+n);
        File failureRoot=root();NotebookDocument failing=new NotebookDocument(failureRoot);failing.load();failing.initializeSize(200,300);failing.setFigures(Collections.singletonList(original));
        File temporary=new File(failureRoot,"page.nbk.tmp");check(temporary.mkdir(),"inject writer failure");
        reject(()->failing.setFigures(Collections.singletonList(raw)));
        check(failing.current().figures.get(0).markdown.sourceMode,"failed publication keeps the intended source in memory");
        check(temporary.delete(),"retire failure");failing.retry();
        check(new NotebookStore(failureRoot).load().figures.get(0).equals(raw),"retry saves both source and vectors");
    }
    private static void legacy()throws Exception{
        File root=root();NotebookFigure image=new NotebookFigure(UUID.randomUUID(),20,30,80,40,37,NotebookFigureFixture.png(true));
        UUID id=UUID.randomUUID();ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream body=new DataOutputStream(bytes);
        body.writeByte(1);body.writeLong(id.getMostSignificantBits());body.writeLong(id.getLeastSignificantBits());
        body.writeInt(200);body.writeInt(300);body.writeByte(0);body.writeInt(0);body.writeByte(0);body.writeInt(0);body.writeInt(1);
        body.writeLong(image.id.getMostSignificantBits());body.writeLong(image.id.getLeastSignificantBits());
        body.writeFloat(image.x);body.writeFloat(image.y);body.writeFloat(image.width);body.writeFloat(image.height);body.writeFloat(image.angle);
        body.writeInt(image.byteCount());body.write(image.png());body.flush();byte[] payload=bytes.toByteArray();
        CRC32 crc=new CRC32();crc.update(payload);bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);
        out.writeInt(0x4e424b31);out.writeInt(7);out.writeInt(payload.length);out.writeInt(~payload.length);out.write(payload);out.writeInt((int)crc.getValue());out.flush();
        File file=new File(root,"page.nbk");Files.write(file.toPath(),bytes.toByteArray());
        NotebookDocument document=new NotebookDocument(root);document.load();
        check(document.current().figures.get(0).equals(image)&&Arrays.equals(bytes.toByteArray(),Files.readAllBytes(file.toPath())),"real v7 reads without rewriting original data");
        document.setFigures(Arrays.asList(image,figure()));
        check(ByteBuffer.wrap(Files.readAllBytes(file.toPath())).getInt(4)==8,"Markdown edit publishes format 8");
        check(Arrays.equals(bytes.toByteArray(),Files.readAllBytes(new File(root,"page.nbk.bak").toPath())),"migration keeps the exact v7 predecessor");
        check(new NotebookStore(root).load().figures.get(1).equals(figureFrom(document)),"migrated source and vectors reopen");
    }
    private static NotebookFigure figureFrom(NotebookDocument d){return d.current().figures.get(1);}
    private static NotebookMarkdownDraft draft(int book,String source){return new NotebookMarkdownDraft(UUID.randomUUID(),book,1,UUID.randomUUID(),UUID.randomUUID(),true,10,20,100,60,37,"",source);}
    private static void drafts()throws Exception{
        File root=root();NotebookMarkdownDraft a=draft(1,"original $\\psi$ 中文");NotebookMarkdownDraft.write(root,a);
        check(NotebookMarkdownDraft.read(root).source.equals(a.source),"typing is durable before compilation");
        check(NotebookMarkdownDraft.fromSnapshot(a.snapshot()).source.equals(a.source),"activity-state recovery retains Unicode and math escapes");
        NotebookMarkdownDraft b=a.withSource("new source");NotebookMarkdownDraft.write(root,b);
        Files.write(new File(root,"markdown-draft.bin").toPath(),new byte[]{0});
        check(NotebookMarkdownDraft.read(root).source.equals(a.source),"corrupt latest draft falls back to exact predecessor");
        NotebookMarkdownDraft.write(root,b);NotebookMarkdownDraft c=draft(2,"other book");NotebookMarkdownDraft.write(root,c);
        check(NotebookMarkdownDraft.read(root,1).source.equals(b.source)&&NotebookMarkdownDraft.read(root,2).source.equals(c.source),"another note's typing cannot overwrite a retained draft");
        NotebookMarkdownDraft.clear(root,b.token);
        check(NotebookMarkdownDraft.read(root,1)==null&&NotebookMarkdownDraft.read(root,2).token.equals(c.token),"retiring saved source removes only its own recovery files");
        NotebookMarkdownDraft.clear(root,c.token);check(NotebookMarkdownDraft.read(root)==null,"saved drafts do not reopen");
        reject(()->draft(1,"\ud800"));reject(()->NotebookMarkdownDraft.fromSnapshot(new byte[4]));
    }
    private static void tiles()throws Exception{
        NotebookFigure block=figure().placed(0,0,200,100).turned(0);
        List<NotebookPdfTiles.Tile> all=NotebookMarkdownTiles.visible(block,2,0,0,400,400);
        check(all.size()==1&&all.get(0).fullWidth==400&&all.get(0).fullHeight==200,"fit-width raster uses display resolution");
        check(NotebookMarkdownTiles.visible(block,2,500,500,400,400).isEmpty(),"offscreen blocks request no pixels");
        for(int angle=-180;angle<180;angle+=7)for(int zoom=1;zoom<=64;zoom*=2){
            NotebookFigure value=block.turned(angle);List<NotebookPdfTiles.Tile> visible=NotebookMarkdownTiles.visible(value,zoom,-value.centerX()*zoom+200,-value.centerY()*zoom+150,400,300);
            long pixels=0;for(NotebookPdfTiles.Tile tile:visible){pixels+=tile.pixels();check(tile.width<=512&&tile.height<=512,"tiles bound native allocation");}
            check(pixels<4L*1024*1024,"rotated high zoom work stays bounded by viewport rather than the full block");
            check(!visible.isEmpty(),"centered block remains visible at every angle and zoom");
        }
        reject(()->NotebookMarkdownTiles.visible(block,Float.NaN,0,0,400,300));reject(()->NotebookMarkdownTiles.visible(block,1,0,0,0,300));
    }
    private static File root()throws IOException{File root=Files.createTempDirectory("markdown-").toFile();roots.add(root);return root;}
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static void reject(Action action)throws Exception{try{action.run();}catch(IOException|IllegalArgumentException expected){checks++;return;}throw new AssertionError("Expected invalid input or injected failure");}
}
