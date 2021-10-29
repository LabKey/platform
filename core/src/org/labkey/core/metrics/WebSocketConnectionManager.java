package org.labkey.core.metrics;

import org.labkey.api.usageMetrics.UsageMetricsProvider;

import java.util.Map;

public class WebSocketConnectionManager implements UsageMetricsProvider
{
    private static final WebSocketConnectionManager _instance = new WebSocketConnectionManager();

    private int successCounter = 0;
    private int failureCounter = 0;

    private WebSocketConnectionManager()
    {
    }

    public static WebSocketConnectionManager getInstance()
    {
        return _instance;
    }

    public void incrementCounter(boolean success)
    {
        if (success)
            successCounter++;
        else
            failureCounter++;
    }

    public boolean showWarning()
    {
        return successCounter == 0 && failureCounter > 0;
    }

    @Override
    public Map<String, Object> getUsageMetrics()
    {
        return Map.of(
            "webSocketConnections", Map.of(
                    "success", successCounter,
                    "failure", failureCounter
                )
        );
    }
}