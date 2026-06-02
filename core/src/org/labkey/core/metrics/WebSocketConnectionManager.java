/*
 * Copyright (c) 2021-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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