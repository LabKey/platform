package org.labkey.core.metrics;

import org.labkey.api.usageMetrics.UsageMetricsProvider;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class WebSocketConnectionManager implements UsageMetricsProvider
{
    private static final WebSocketConnectionManager _instance = new WebSocketConnectionManager();

    final private AtomicInteger successCounter = new AtomicInteger();
    final private AtomicInteger failureCounter = new AtomicInteger();

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
            successCounter.incrementAndGet();
        else
            failureCounter.incrementAndGet();
    }

    public boolean showWarning()
    {
        return successCounter.get() == 0 && failureCounter.get() > 0;
    }

    @Override
    public Map<String, Object> getUsageMetrics()
    {
        return Map.of(
            "webSocketConnections", Map.of(
                    "success", successCounter.get(),
                    "failure", failureCounter.get()
                )
        );
    }
}