package com.tom_roush.pdfbox.android;

import java.io.*;

/** Host-only AssetManager adapter; actual CMaps/fonts come from the checksum-verified runtime AAR. */
public final class PDFBoxResourceLoader {
    public static void init(android.content.Context unused) { }
    public static boolean isReady() { return true; }
    public static InputStream getStream(String name)throws IOException {
        InputStream stream=PDFBoxResourceLoader.class.getResourceAsStream("/"+name);
        if(stream==null)throw new FileNotFoundException("Missing shipped PDFBox resource: "+name);
        return stream;
    }
}
