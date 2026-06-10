package com.smp.confluencejavachatbot.confluence;

public interface ConfluenceClient {

    ConfluencePageData fetchPage(String rootPageId, String pageId, String parentPageId);
}

