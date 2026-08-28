package com.ice.shippingservice.Util;

import java.text.Normalizer;
import java.util.List;
import java.util.regex.Pattern;

public class AddressNormalizer {
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final List<String> PREFIXES = List.of(
            "thanh pho ", "tp ", "tp. ", "tinh ", "quan ", "huyen ",
            "phuong ", "xa ", "thi tran ", "thi xa ");

    private AddressNormalizer() {}

    /** "TP. Hồ Chí Minh" -> "ho chi minh" ; "Quận 1" -> "1" */
    public static String normalize(String raw) {
        if (raw == null) return "";
        String s = Normalizer.normalize(raw, Normalizer.Form.NFD);
        s = DIACRITICS.matcher(s).replaceAll("");      // bỏ dấu
        s = s.toLowerCase().replace(".", " ").replaceAll("\\s+", " ").trim();
        for (String p : PREFIXES) {
            if (s.startsWith(p)) { s = s.substring(p.length()).trim(); break; }
        }
        return s;
    }
}
