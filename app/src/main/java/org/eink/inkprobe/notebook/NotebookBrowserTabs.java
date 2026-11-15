package org.eink.inkprobe.notebook;

import java.util.*;

/** Bounded foreground tab model. Engine surfaces are owned by the Activity, not this metadata. */
final class NotebookBrowserTabs {
    static final int MAX=12;
    static final class Tab {
        final String id;String url="",title="Start page";boolean home=true;
        Tab(String id){this.id=id;}
    }
    final List<Tab> items=new ArrayList<>();
    private String selected;
    NotebookBrowserTabs(){add();}
    Tab current(){return find(selected);}
    Tab find(String id){for(Tab tab:items)if(tab.id.equals(id))return tab;return null;}
    Tab add(){if(items.size()>=MAX)throw new IllegalStateException("Up to 12 tabs can be open on this tablet.");Tab tab=new Tab(UUID.randomUUID().toString());items.add(tab);selected=tab.id;return tab;}
    void select(String id){if(find(id)==null)throw new IllegalArgumentException("This tab was closed.");selected=id;}
    /** Reuse a start tab when available; at the limit Home is an unpersisted overlay. */
    void startHome(){for(Tab tab:items)if(tab.home){selected=tab.id;return;}if(items.size()<MAX)add();}
    void close(String id){Tab target=find(id);if(target==null)return;int index=items.indexOf(target);items.remove(target);if(items.isEmpty())add();else if(id.equals(selected))selected=items.get(Math.min(index,items.size()-1)).id;}
    String encode(){
        try{java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();java.io.DataOutputStream out=new java.io.DataOutputStream(bytes);out.writeInt(0x4e425431);out.writeUTF(selected);out.writeInt(items.size());for(Tab tab:items){out.writeUTF(tab.id);out.writeUTF(tab.url);out.writeUTF(tab.title==null?"":tab.title.substring(0,Math.min(1024,tab.title.length())));out.writeBoolean(tab.home);}out.flush();return Base64.getEncoder().encodeToString(bytes.toByteArray());}catch(java.io.IOException error){throw new IllegalStateException(error);}
    }
    boolean restore(String text){
        if(text==null||text.length()>192*1024)return false;
        try{java.io.DataInputStream input=new java.io.DataInputStream(new java.io.ByteArrayInputStream(Base64.getDecoder().decode(text)));if(input.readInt()!=0x4e425431)return false;String selected=input.readUTF();int count=input.readInt();if(count<1||count>MAX)return false;List<Tab> values=new ArrayList<>();Set<String> ids=new HashSet<>();for(int i=0;i<count;i++){String id=input.readUTF(),url=input.readUTF(),title=input.readUTF();boolean home=input.readBoolean();if(!id.matches("[a-f0-9-]{36}")||!ids.add(id)||title.length()>1024||!url.isEmpty()&&!NotebookBrowserAddress.isWebUrl(url))return false;Tab tab=new Tab(id);tab.url=url;tab.title=title;tab.home=home;values.add(tab);}if(!ids.contains(selected)||input.read()!=-1)return false;items.clear();items.addAll(values);this.selected=selected;return true;}catch(Exception error){return false;}
    }
    void clear(){for(Tab tab:items){tab.url="";tab.title="Start page";tab.home=true;}}
}
