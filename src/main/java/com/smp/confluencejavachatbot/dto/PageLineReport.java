package com.smp.confluencejavachatbot.dto;

public record PageLineReport(
        String pageID,
        String pageName,
        int numberOfLines
) {
}

