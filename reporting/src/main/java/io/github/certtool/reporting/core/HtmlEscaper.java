package io.github.certtool.reporting.core;

import java.util.Objects;

/**
 * Minimal HTML escaper for embedding user-controlled strings (rule titles, summaries, evidence,
 * remediation) into the report HTML.
 *
 * <p>Escapes the five characters that have HTML meaning: {@code &}, {@code <}, {@code >},
 * {@code "}, {@code '}. We intentionally avoid a full template engine — the renderer is one
 * hand-written method so we can audit it line by line.
 */
public final class HtmlEscaper {

    private HtmlEscaper() {}

    public static String escape(String input) {
        Objects.requireNonNull(input, "input");
        StringBuilder sb = new StringBuilder(input.length() + 16);
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&#39;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}