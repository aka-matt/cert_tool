package io.github.certtool.reporting.core;

import java.util.Objects;

/**
 * Escapes a string for safe inclusion in a Markdown table cell.
 *
 * <p>Only the characters that break a GFM table cell need escaping here: pipe characters
 * ({@code |}) and embedded newlines (which terminate the row). Backticks and other Markdown
 * metacharacters are left alone because most renderers treat them as inline code within a
 * table cell without breaking layout.
 *
 * <p>The escape rules are intentionally narrow so the rendered text reads naturally. The
 * SanitizationGuard catches the dangerous patterns (passwords, PEM markers) downstream.
 */
public final class MarkdownEscaper {

    private MarkdownEscaper() {}

    /**
     * Returns a Markdown-safe representation of {@code input}. Pipes are escaped with a
     * preceding backslash; newlines are collapsed to a space.
     */
    public static String escape(String input) {
        Objects.requireNonNull(input, "input");
        StringBuilder sb = new StringBuilder(input.length() + 8);
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                case '|' -> sb.append("\\|");
                case '\r' -> { /* drop — handled with \n */ }
                case '\n' -> sb.append(' ');
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}