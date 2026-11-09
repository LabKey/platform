/*
 * Copyright (c) 2008-2026 LabKey Corporation
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

package org.labkey.api.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Callback to be notified when the server is shutting down. Complement to {@link StartupListener}.
 * User: brittp
 * Date: Dec 3, 2005
 */
public interface ShutdownListener
{
    /**
     * @return Friendly name used for logging shutdown progress
     */
    String getName();

    /**
     * called first, should be used only for non-blocking operations (set _shuttingDown=true, interrupt threads)
     * also possible to launch an async shutdown task here!
     */
    default void shutdownPre() {}

    /**
     * perform shutdown tasks
     */
    default void shutdownStarted() {}

    static ShutdownListener of(@NotNull String name, Runnable preShutdown)
    {
        return of(name, preShutdown, null);
    }

    static ShutdownListener of(String name, @Nullable Runnable preShutdown, @Nullable Runnable shutdown)
    {
        return new ShutdownListener()
        {
            @Override
            public String getName()
            {
                return name;
            }

            @Override
            public void shutdownStarted()
            {
                if (shutdown != null)
                {
                    shutdown.run();
                }

            }

            @Override
            public void shutdownPre()
            {
                if (preShutdown != null)
                {
                    preShutdown.run();
                }
            }
        };
    }
}
