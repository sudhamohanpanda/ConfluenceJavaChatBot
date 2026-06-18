package com.smp.confluencejavachatbot.dto;

public record PageLineReport(
        String pageId,
        String pageTitle,
        int numberOfLines
) {
}

