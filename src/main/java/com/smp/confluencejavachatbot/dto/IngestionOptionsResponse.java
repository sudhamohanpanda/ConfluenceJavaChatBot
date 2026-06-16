package com.smp.confluencejavachatbot.dto;

import java.util.List;

public record IngestionOptionsResponse(
        List<RootPageOptionResponse> rootPages,
        List<String> spaceKeys,
        String defaultRootPageId,
        String defaultSpaceKey
) {
}

