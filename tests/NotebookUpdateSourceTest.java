package org.eink.inkprobe.notebook;

public final class NotebookUpdateSourceTest {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        String v="0.49.0-rc.1",name=NotebookUpdateSource.apkName(v),url="https://github.com/xing-phys/Scriflow/releases/download/v"+v+"/"+name;
        check(NotebookUpdateSource.releaseAsset(url,v,false),"exact project APK");
        check(NotebookUpdateSource.releaseAsset(url+".sha256",v,true),"exact checksum asset");
        for(String bad:new String[]{url+"?token=x",url+"#x",url.replace("https:","http:"),url.replace("github.com","github.com.evil.test"),url.replace("Scriflow","other"),url.replace("/v0.49", "/v0.48"),url.replace("github.com/","user@github.com/")})
            check(!NotebookUpdateSource.releaseAsset(bad,v,false),"reject unrelated asset");
        check(NotebookUpdateSource.trustedHttps("https://release-assets.githubusercontent.com/a?sig=x"),"release CDN accepts signed queries");
        check(!NotebookUpdateSource.trustedHttps("https://release-assets.githubusercontent.com.evil.test/a"),"lookalike CDN rejected");
        check(!NotebookUpdateSource.trustedHttps("http://github.com/a"),"cleartext rejected");
        String hash="0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        check(hash.equals(NotebookUpdateSource.checksum(hash+"  "+name+"\n",v)),"matching checksum");
        check(NotebookUpdateSource.checksum(hash+"  other.apk\n",v)==null,"checksum names its APK");
        check(NotebookUpdateSource.checksum(hash+"  "+name+"\n"+hash+"  extra.apk\n",v)==null,"extra checksum lines rejected");
        System.out.println("NotebookUpdateSource: "+checks+" assertions passed.");
    }
}
