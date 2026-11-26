package org.eink.inkprobe.notebook;

import java.math.BigInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Ordering for this project's stable, alpha, beta and release-candidate tags. */
final class NotebookReleaseVersion implements Comparable<NotebookReleaseVersion> {
    private static final Pattern FORMAT = Pattern.compile("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)(?:-(alpha|beta|rc)\\.([1-9][0-9]*))?");
    final String value; final boolean preview; private final BigInteger major, minor, patch, sequence; private final int phase;
    private NotebookReleaseVersion(String value, Matcher match) {
        this.value=value; major=new BigInteger(match.group(1)); minor=new BigInteger(match.group(2)); patch=new BigInteger(match.group(3));
        String kind=match.group(4); preview=kind!=null;
        phase=kind==null?3:kind.equals("rc")?2:kind.equals("beta")?1:0;
        sequence=preview?new BigInteger(match.group(5)):BigInteger.ZERO;
    }
    static NotebookReleaseVersion parse(String value) {
        if(value==null||value.length()>100)return null; Matcher match=FORMAT.matcher(value);
        return match.matches()?new NotebookReleaseVersion(value,match):null;
    }
    public int compareTo(NotebookReleaseVersion other) {
        int order=major.compareTo(other.major); if(order!=0)return order;
        order=minor.compareTo(other.minor); if(order!=0)return order;
        order=patch.compareTo(other.patch); if(order!=0)return order;
        order=Integer.compare(phase,other.phase); return order!=0?order:sequence.compareTo(other.sequence);
    }
}
