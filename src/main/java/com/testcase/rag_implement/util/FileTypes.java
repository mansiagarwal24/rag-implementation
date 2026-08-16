package com.testcase.rag_implement.util;

import java.util.Set;

/** Supported upload extensions and filename helpers. */
public final class FileTypes {

    public static final Set<String> SUPPORTED = Set.of("pdf", "docx", "txt", "md", "markdown");

    private FileTypes() {
    }

    /** Lowercase extension without the dot, or empty string if none. */
    public static String extension(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase();
    }

    public static boolean isSupported(String filename) {
        return SUPPORTED.contains(extension(filename));
    }
}
