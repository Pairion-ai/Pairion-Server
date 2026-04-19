package com.pairion.agent.util;

import java.util.regex.Pattern;

/**
 * Strips markdown formatting from text before TTS synthesis.
 *
 * <p>LLM responses frequently contain markdown that is not suitable for speech: bold/italic
 * markers, headings, code blocks, links, and blockquotes. This utility converts such text to
 * plain prose that a TTS engine can read naturally.
 *
 * <p>This is a pure function with no external dependencies, designed for inline use in the
 * agent turn loop.
 */
public final class MarkdownStripper {

    /** Pattern matching markdown links: [text](url) → text. */
    private static final Pattern LINK_PATTERN = Pattern.compile("\\[([^\\]]+)\\]\\([^)]*\\)");

    /** Pattern matching heading markers at the start of a line: # ## ### etc. */
    private static final Pattern HEADING_PATTERN = Pattern.compile("(?m)^#{1,6}\\s*");

    /** Pattern matching blockquote markers at the start of a line: > */
    private static final Pattern BLOCKQUOTE_PATTERN = Pattern.compile("(?m)^>\\s*");

    /** Pattern matching horizontal rules: --- or *** on their own line. */
    private static final Pattern HORIZONTAL_RULE_PATTERN =
            Pattern.compile("(?m)^(---|\\*\\*\\*)\\s*$");

    /** Pattern matching bold markers: **text** */
    private static final Pattern BOLD_PATTERN = Pattern.compile("\\*\\*");

    /** Pattern matching remaining single asterisks (italic markers). */
    private static final Pattern ITALIC_PATTERN = Pattern.compile("\\*");

    /** Pattern matching backtick code markers (inline and fence). */
    private static final Pattern BACKTICK_PATTERN = Pattern.compile("`+");

    /** Pattern collapsing runs of whitespace (spaces, tabs, newlines) to a single space. */
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    private MarkdownStripper() {
        // utility class — no instances
    }

    /**
     * Strips all markdown formatting from {@code text} and returns clean prose.
     *
     * <p>Operations applied in order:
     * <ol>
     *   <li>Markdown links {@code [text](url)} → {@code text}
     *   <li>Horizontal rules ({@code ---}, {@code ***}) → removed
     *   <li>Heading markers ({@code #}, {@code ##}, …) → removed
     *   <li>Blockquote markers ({@code >}) → removed
     *   <li>Bold markers ({@code **}) → removed
     *   <li>Italic markers ({@code *}) → removed
     *   <li>Backtick code markers → removed
     *   <li>Whitespace collapsed and result trimmed
     * </ol>
     *
     * @param text the raw text, possibly containing markdown; may be null
     * @return plain text suitable for TTS synthesis; empty string if input is null or blank
     */
    public static String strip(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String result = text;

        // Replace [text](url) with just text
        result = LINK_PATTERN.matcher(result).replaceAll("$1");

        // Remove horizontal rules before bold/italic to avoid misinterpreting --- as leftover *
        result = HORIZONTAL_RULE_PATTERN.matcher(result).replaceAll("");

        // Remove heading markers
        result = HEADING_PATTERN.matcher(result).replaceAll("");

        // Remove blockquote markers
        result = BLOCKQUOTE_PATTERN.matcher(result).replaceAll("");

        // Remove bold markers (**) before single-asterisk italic pass
        result = BOLD_PATTERN.matcher(result).replaceAll("");

        // Remove remaining italic markers (*)
        result = ITALIC_PATTERN.matcher(result).replaceAll("");

        // Remove backticks
        result = BACKTICK_PATTERN.matcher(result).replaceAll("");

        // Collapse whitespace and trim
        result = WHITESPACE_PATTERN.matcher(result).replaceAll(" ").trim();

        return result;
    }
}
