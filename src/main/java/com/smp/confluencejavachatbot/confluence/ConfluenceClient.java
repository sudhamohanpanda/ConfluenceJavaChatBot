package com.smp.confluencejavachatbot.confluence;

public interface ConfluenceClient {

    ConfluencePageData fetchPage(String rootPageId, String pageId, String parentPageId);

    default String resolveRootPageIdBySpaceKey(String spaceKey) {
        throw new IllegalStateException("Resolving root page from spaceKey is not supported by this connector");
    }
}

