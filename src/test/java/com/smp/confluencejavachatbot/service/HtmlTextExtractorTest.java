package com.smp.confluencejavachatbot.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class HtmlTextExtractorTest {

    private final HtmlTextExtractor extractor = new HtmlTextExtractor();

    @Test
    void shouldConvertHtmlToReadableText() {
        String html = """
                <h1>Platform</h1>
                <p>Welcome to <b>Lena</b></p>
                <ul><li>Feature A</li><li>Feature B</li></ul>
                <pre>code line</pre>
                """;

        String text = extractor.toText(html);

        assertTrue(text.contains("Platform"));
        assertTrue(text.contains("Welcome to Lena"));
        assertTrue(text.contains("Feature A"));
        assertTrue(text.contains("code line"));
    }
}

