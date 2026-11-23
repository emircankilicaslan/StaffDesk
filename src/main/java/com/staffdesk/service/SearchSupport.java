package com.staffdesk.service;

import java.util.Locale;

/** Arama kalıbı yardımcıları: LIKE joker karakterleri kullanıcı girdisinde düz metin sayılır. */
final class SearchSupport {

    static final char ESCAPE = '\\';
    static final int MAX_LENGTH = 100;

    private SearchSupport() {}

    /** Boş girdi için null; aksi halde küçük harfe çevrilmiş, kaçışlı ve %...% sarmalı kalıp. */
    static String pattern(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        String t = q.strip();
        if (t.length() > MAX_LENGTH) {
            t = t.substring(0, MAX_LENGTH);
        }
        return "%" + t.toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }
}
