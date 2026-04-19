package com.pairion.agent.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Tests for {@link MarkdownStripper}. */
class MarkdownStripperTest {

    // ── Null / blank ────────────────────────────────────────────────────────

    @Test
    void nullInputReturnsEmpty() {
        assertThat(MarkdownStripper.strip(null)).isEmpty();
    }

    @Test
    void blankInputReturnsEmpty() {
        assertThat(MarkdownStripper.strip("   ")).isEmpty();
    }

    @Test
    void emptyInputReturnsEmpty() {
        assertThat(MarkdownStripper.strip("")).isEmpty();
    }

    // ── Bold (**) ───────────────────────────────────────────────────────────

    @Test
    void stripsBold() {
        assertThat(MarkdownStripper.strip("**bold** text")).isEqualTo("bold text");
    }

    @Test
    void stripsBoldInline() {
        assertThat(MarkdownStripper.strip("The **weather** is nice."))
                .isEqualTo("The weather is nice.");
    }

    // ── Italic (*) ──────────────────────────────────────────────────────────

    @Test
    void stripsItalic() {
        assertThat(MarkdownStripper.strip("*italic* word")).isEqualTo("italic word");
    }

    @Test
    void stripsItalicInline() {
        assertThat(MarkdownStripper.strip("It is *very* warm today."))
                .isEqualTo("It is very warm today.");
    }

    // ── Headings (#) ────────────────────────────────────────────────────────

    @Test
    void stripsH1Heading() {
        assertThat(MarkdownStripper.strip("# Weather Report")).isEqualTo("Weather Report");
    }

    @Test
    void stripsH2Heading() {
        assertThat(MarkdownStripper.strip("## Summary")).isEqualTo("Summary");
    }

    @Test
    void stripsH3Heading() {
        assertThat(MarkdownStripper.strip("### Details")).isEqualTo("Details");
    }

    @Test
    void stripsMultilineHeadings() {
        String input = "# Title\n## Section\nPlain text.";
        assertThat(MarkdownStripper.strip(input)).isEqualTo("Title Section Plain text.");
    }

    // ── Backticks ───────────────────────────────────────────────────────────

    @Test
    void stripsInlineCode() {
        assertThat(MarkdownStripper.strip("Use `System.out.println` to print."))
                .isEqualTo("Use System.out.println to print.");
    }

    @Test
    void stripsCodeFence() {
        assertThat(MarkdownStripper.strip("```\ncode block\n```")).isEqualTo("code block");
    }

    @Test
    void stripsTripleBackticks() {
        assertThat(MarkdownStripper.strip("```java\nint x = 1;\n```"))
                .isEqualTo("java int x = 1;");
    }

    // ── Blockquotes (>) ─────────────────────────────────────────────────────

    @Test
    void stripsBlockquote() {
        assertThat(MarkdownStripper.strip("> This is a quote.")).isEqualTo("This is a quote.");
    }

    @Test
    void stripsMultilineBlockquote() {
        String input = "> Line one.\n> Line two.";
        assertThat(MarkdownStripper.strip(input)).isEqualTo("Line one. Line two.");
    }

    // ── Horizontal rules (--- / ***) ────────────────────────────────────────

    @Test
    void stripsHorizontalRuleDash() {
        String input = "Before\n---\nAfter";
        assertThat(MarkdownStripper.strip(input)).isEqualTo("Before After");
    }

    @Test
    void stripsHorizontalRuleAsterisk() {
        String input = "Before\n***\nAfter";
        assertThat(MarkdownStripper.strip(input)).isEqualTo("Before After");
    }

    // ── Links ([text](url)) ─────────────────────────────────────────────────

    @Test
    void stripsLinkKeepsText() {
        assertThat(MarkdownStripper.strip("[click here](https://example.com)"))
                .isEqualTo("click here");
    }

    @Test
    void stripsLinkInSentence() {
        assertThat(MarkdownStripper.strip("Visit [Pairion](https://pairion.ai) today."))
                .isEqualTo("Visit Pairion today.");
    }

    @Test
    void stripsMultipleLinks() {
        assertThat(
                        MarkdownStripper.strip(
                                "[first](http://a.com) and [second](http://b.com)"))
                .isEqualTo("first and second");
    }

    // ── Whitespace collapsing ────────────────────────────────────────────────

    @Test
    void collapsesMultipleNewlines() {
        assertThat(MarkdownStripper.strip("Hello\n\n\nWorld")).isEqualTo("Hello World");
    }

    @Test
    void collapsesMultipleSpaces() {
        assertThat(MarkdownStripper.strip("too   many   spaces")).isEqualTo("too many spaces");
    }

    @Test
    void trimLeadingAndTrailingWhitespace() {
        assertThat(MarkdownStripper.strip("  hello world  ")).isEqualTo("hello world");
    }

    // ── Combined / realistic LLM output ────────────────────────────────────

    @Test
    void stripsComplexMarkdownResponse() {
        String input =
                "## Weather Update\n\n"
                        + "The current temperature in Dallas is **72°F** with *clear skies*.\n\n"
                        + "> Feels like a great day!\n\n"
                        + "---\n\n"
                        + "Check [Weather.com](https://weather.com) for more details.";

        String result = MarkdownStripper.strip(input);

        assertThat(result)
                .isEqualTo(
                        "Weather Update The current temperature in Dallas is 72°F with clear"
                                + " skies. Feels like a great day! Check Weather.com for more"
                                + " details.");
    }

    @Test
    void plainTextPassesThroughUnchanged() {
        String plain = "The weather in Dallas is seventy-two degrees and sunny.";
        assertThat(MarkdownStripper.strip(plain)).isEqualTo(plain);
    }

    @Test
    void boldAndItalicCombined() {
        assertThat(MarkdownStripper.strip("***bold and italic***")).isEqualTo("bold and italic");
    }
}
