/*
 * Copyright (c) 2026 LabKey Corporation
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
package org.labkey.api.websocket;

import org.labkey.api.util.QuietCloser;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Live count of inbound WebSocket connections, guests included. Each holds a Tomcat connector connection slot for as
 * long as the client keeps the socket open, so it competes with every other request against maxConnections.
 * See GH Issue 1574.
 */
public class WebSocketTracker
{
    private static final AtomicInteger OPEN_COUNT = new AtomicInteger();

    private WebSocketTracker()
    {
    }

    /** Call from onOpen(); close the result from onClose(). Closing more than once is a no-op. */
    public static QuietCloser opened()
    {
        OPEN_COUNT.incrementAndGet();
        AtomicBoolean closed = new AtomicBoolean();

        return () -> {
            if (closed.compareAndSet(false, true))
                OPEN_COUNT.decrementAndGet();
        };
    }

    public static int getOpenCount()
    {
        return OPEN_COUNT.get();
    }
}
