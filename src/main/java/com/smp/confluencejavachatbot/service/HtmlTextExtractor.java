package com.smp.confluencejavachatbot.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

@Component
public class HtmlTextExtractor {

    public String toText(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }

        Document document = Jsoup.parse(html);

        document.select("br").append("\n");
        document.select("p,li,h1,h2,h3,h4,h5,h6,tr,pre,blockquote").prepend("\n").append("\n");

        String normalized = document.text()
                .replace("\n ", "\n")
                .replaceAll("[\\t\\x0B\\f\\r]+", " ")
                .replaceAll("\n{3,}", "\n\n")
                .trim();

        return Arrays.stream(normalized.split("\n"))
                .map(String::trim)
                .collect(Collectors.joining("\n"));
    }
}

