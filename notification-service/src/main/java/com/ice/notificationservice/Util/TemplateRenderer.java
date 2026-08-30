package com.ice.notificationservice.Util;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TemplateRenderer {
    private static final Pattern VAR = Pattern.compile("\\{\\{\\s*(\\w+)\\s*}}");

    private TemplateRenderer() {}

    public static String render(String template, Map<String, Object> vars) {
        Matcher m = VAR.matcher(template);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            Object v = vars.get(m.group(1));
            m.appendReplacement(out, Matcher.quoteReplacement(v == null ? "" : String.valueOf(v)));
        }
        m.appendTail(out);
        return out.toString();
    }
}
