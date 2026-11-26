package org.eink.inkprobe.notebook;

public final class NotebookReleaseVersionTest {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static int compare(String a,String b){return NotebookReleaseVersion.parse(a).compareTo(NotebookReleaseVersion.parse(b));}
    public static void main(String[] args){
        check(compare("0.49.0-alpha.1","0.49.0-beta.1")<0,"alpha precedes beta");
        check(compare("0.49.0-beta.10","0.49.0-beta.9")>0,"sequence is numeric");
        check(compare("0.49.0-beta.9","0.49.0-rc.1")<0,"beta precedes rc");
        check(compare("0.49.0-rc.99","0.49.0")<0,"stable follows prerelease");
        check(compare("0.50.0-alpha.1","0.49.99")>0,"base versions take priority");
        check(compare("1.0.0","0.999.999")>0,"major versions take priority");
        check(compare("999999999999999999.0.0","2.0.0")>0,"numeric components cannot overflow");
        for(String invalid:new String[]{"", "v0.49.0", "0.49", "0.49.0-rc", "0.49.0-rc.0", "00.1.0", "0.49.0-rc.01", "0.49.0+metadata", "../0.49.0"})
            check(NotebookReleaseVersion.parse(invalid)==null,"reject invalid version "+invalid);
        System.out.println("NotebookReleaseVersion: "+checks+" assertions passed.");
    }
}
