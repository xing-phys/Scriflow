package org.eink.inkprobe.notebook;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.*;
import java.util.zip.CRC32;

/** Rotation geometry, old-format migration and durable linked figure moves on real files. */
public final class NotebookFigureTransformTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        geometry(); legacy(); moves(); recovery();
        System.out.println("NotebookFigureTransform: " + checks + " assertions passed (rotation, v6 migration, cross-page history and crash recovery).");
    }
    private static NotebookFigure figure() throws Exception {
        return new NotebookFigure(UUID.randomUUID(), 50, 60, 80, 40, NotebookFigureFixture.png(true));
    }
    private static void geometry() throws Exception {
        NotebookFigure original = figure();
        NotebookFigure rotated = NotebookFigureGeometry.rotate(original, original.centerX()+100, original.centerY(),
            original.centerX()+100*(float)Math.cos(Math.toRadians(37)), original.centerY()+100*(float)Math.sin(Math.toRadians(37)));
        check(Math.abs(rotated.angle-37)<.001 && rotated.imageKey==original.imageKey && Arrays.equals(original.png(),rotated.png()), "free rotation never resamples or copies source pixels");
        check(rotated.contains(rotated.centerX(),rotated.centerY())&&!rotated.contains(rotated.left()+.01f,rotated.top()+.01f), "hit test follows rotated rectangle rather than its envelope");
        Random random = new Random(20261029);
        for (int i=0;i<1000;i++) {
            NotebookFigure value = original.placed(-400+random.nextFloat()*800,-400+random.nextFloat()*1000,
                1+random.nextFloat()*400,1+random.nextFloat()*500).turned(-180+random.nextFloat()*360).fitted(200,300);
            check(value.left()>=-.001 && value.top()>=-.001 && value.right()<=200.001 && value.bottom()<=300.001, "rotated envelope fits its page");
            NotebookPage.empty().withSize(200,300).withFigures(Collections.singletonList(value));
            float ax=value.cornerX(0),ay=value.cornerY(0);
            NotebookFigure enlarged=NotebookFigureGeometry.resize(value,ax+(value.cornerX(2)-ax)*1.7f,ay+(value.cornerY(2)-ay)*1.7f);
            check(Math.abs(enlarged.cornerX(0)-ax)<.002 && Math.abs(enlarged.cornerY(0)-ay)<.002
                && Math.abs(enlarged.width/enlarged.height-value.width/value.height)<.001, "rotated resize fixes the opposite corner and aspect ratio");
        }
        NotebookFigure portrait = original.placed(-150,200,500,100).turned(90);
        NotebookPage.empty().withSize(200,600).withFigures(Collections.singletonList(portrait));
        check(portrait.left()>=0&&portrait.right()<=200&&portrait.width>200,"valid rotated images can have a negative unrotated origin");
        NotebookViewport viewport = new NotebookViewport(Arrays.asList(new NotebookViewport.Size(200,300),new NotebookViewport.Size(400,600)),300,400,12,1.7f,30);
        float scroll=viewport.pageScroll(2),x=viewport.pageLeft(2)+120*viewport.scale(2),y=viewport.pageTop(2,scroll)+150*viewport.scale(2);
        NotebookFigure moved=NotebookFigureGeometry.move(viewport,rotated,2,10,15,x,y,scroll);
        check(Math.abs(moved.centerX()-110)<.001&&Math.abs(moved.centerY()-135)<.001&&moved.angle==rotated.angle,"cross-page drag keeps its logical grab offset, scale and rotation");
    }
    private static void legacy() throws Exception {
        File root=Files.createTempDirectory("figure-v6-").toFile();NotebookFigure value=figure();
        UUID page=UUID.randomUUID();ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream body=new DataOutputStream(bytes);
        body.writeByte(1);body.writeLong(page.getMostSignificantBits());body.writeLong(page.getLeastSignificantBits());
        body.writeInt(200);body.writeInt(300);body.writeByte(0);body.writeInt(0);body.writeByte(0);body.writeInt(0);body.writeInt(1);
        body.writeLong(value.id.getMostSignificantBits());body.writeLong(value.id.getLeastSignificantBits());
        body.writeFloat(value.x);body.writeFloat(value.y);body.writeFloat(value.width);body.writeFloat(value.height);body.writeInt(value.byteCount());body.write(value.png());body.flush();
        byte[] payload=bytes.toByteArray();CRC32 crc=new CRC32();crc.update(payload);bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);
        out.writeInt(0x4e424b31);out.writeInt(6);out.writeInt(payload.length);out.writeInt(~payload.length);out.write(payload);out.writeInt((int)crc.getValue());out.flush();
        File file=new File(root,"page.nbk");Files.write(file.toPath(),bytes.toByteArray());
        NotebookDocument document=new NotebookDocument(root);document.load();
        check(document.current().figures.get(0).angle==0&&Arrays.equals(bytes.toByteArray(),Files.readAllBytes(file.toPath())),"v6 reads unchanged with zero rotation");
        NotebookFigure rotated=value.turned(37).fitted(200,300);document.setFigures(Collections.singletonList(rotated));
        check(ByteBuffer.wrap(Files.readAllBytes(file.toPath())).getInt(4)==7,"an edit publishes rotation-capable v7");
        check(Arrays.equals(bytes.toByteArray(),Files.readAllBytes(new File(root,"page.nbk.bak").toPath())),"migration preserves exact v6 predecessor");
        check(new NotebookStore(root).load().figures.get(0).equals(rotated),"rotation, PNG and identity survive reopening");
        document.undo();check(document.current().figures.get(0).equals(value),"rotation is undoable");
    }
    private static NotebookBook book(File root) throws Exception {
        NotebookBook book=new NotebookBook(root);book.load();book.initializeSize(200,300);book.addPage();return book;
    }
    private static void moves() throws Exception {
        File root=Files.createTempDirectory("figure-move-").toFile();NotebookBook book=book(root);
        NotebookFigure value=figure().turned(37).fitted(200,300);book.setFigures(1,Collections.singletonList(value));
        NotebookPage first=book.page(1),second=book.page(2);NotebookFigure target=value.placed(70,100,90,45).fitted(200,300);
        book.editFigure(1,2,first.id,value,target,false);
        check(book.page(1).figures.isEmpty()&&book.page(2).figures.equals(Collections.singletonList(target)),"cross-page move removes source and publishes destination");
        check(book.undoOnPage(2)&&book.page(1).figures.equals(Collections.singletonList(value))&&book.page(2).figures.isEmpty(),"one undo restores both pages");
        check(book.redoOnPage(1)&&book.page(1).figures.isEmpty()&&book.page(2).figures.equals(Collections.singletonList(target)),"one redo restores the moved figure");
        NotebookFigure copy=target.duplicate().placed(20,20,40,20).fitted(200,300);
        book.editFigure(2,2,second.id,null,copy,true);check(book.page(2).figures.size()==2&&!copy.id.equals(target.id),"paste has an independent identity");
        book.editFigure(2,2,second.id,copy,null,false);check(book.page(2).figures.size()==1,"cut/delete removes only the selected figure");
        book.undoOnPage(2);check(book.page(2).figures.size()==2,"cut/delete is undoable");
        check(!book.canUndoOnPage(1),"later edits protect linked move history on the other page");
        ByteArrayOutputStream backup=new ByteArrayOutputStream();NotebookBackup.write(backup,"Rotated figures",book.pageCount(),book::previewPage,()->false);
        NotebookBackup.Restored archive=NotebookBackup.read(new ByteArrayInputStream(backup.toByteArray()),new File(Files.createTempDirectory("figure-backup-").toFile(),"restored"),()->false);NotebookBook restored=new NotebookBook(archive.directory);restored.load();
        check(restored.page(2).figures.equals(book.page(2).figures),"backup retains arbitrary rotation and independent copies");
    }
    private static void recovery() throws Exception {
        File root=Files.createTempDirectory("figure-recover-").toFile();NotebookBook book=book(root);NotebookFigure value=figure().turned(-53).fitted(200,300);
        book.setFigures(1,Collections.singletonList(value));NotebookPage a=book.page(1),b=book.page(2);
        NotebookFigure moved=value.placed(80,130,value.width,value.height).fitted(200,300);
        NotebookPage afterA=a.withFigures(Collections.emptyList()),afterB=b.withFigures(Collections.singletonList(moved));
        NotebookInkTransfer.prepare(root,1,2,a,afterA,b,afterB);
        NotebookStore destination=new NotebookStore(new File(root,"pages/2"));destination.load();destination.replaceContent(afterB);
        NotebookBook reopened=new NotebookBook(root);reopened.load();
        check(reopened.page(1).figures.isEmpty()&&reopened.page(2).figures.equals(Collections.singletonList(moved)),"crash after destination publish completes source removal without image loss or duplication");
        check(!NotebookInkTransfer.pending(root),"recovered figure transaction retires its journal");
    }
    private static void check(boolean value,String message) { checks++;if(!value)throw new AssertionError(message); }
}
