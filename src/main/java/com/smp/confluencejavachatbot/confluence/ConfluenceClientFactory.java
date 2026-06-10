package com.smp.confluencejavachatbot.confluence;

import com.smp.confluencejavachatbot.config.AppProperties;
import com.smp.confluencejavachatbot.dto.ConnectorMode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ConfluenceClientFactory {

    private final AppProperties appProperties;
    private final RestConfluenceClient restConfluenceClient;
    private final McpConfluenceClient mcpConfluenceClient;

    public ConfluenceClientFactory(AppProperties appProperties,
                                   RestConfluenceClient restConfluenceClient,
                                   McpConfluenceClient mcpConfluenceClient) {
        this.appProperties = appProperties;
        this.restConfluenceClient = restConfluenceClient;
        this.mcpConfluenceClient = mcpConfluenceClient;
    }

    public ConfluenceClient resolveClient(ConnectorMode requestedMode) {
        ConnectorMode mode = requestedMode == null || requestedMode == ConnectorMode.AUTO
                ? appProperties.connectorDefault()
                : requestedMode;
        if (mode == null || mode == ConnectorMode.REST || mode == ConnectorMode.AUTO) {
            return restConfluenceClient;
        }
        if (!StringUtils.hasText(appProperties.mcp().baseUrl())) {
            // Graceful fallback when MCP is requested but not configured.
            return restConfluenceClient;
        }
        return mcpConfluenceClient;
    }
}

