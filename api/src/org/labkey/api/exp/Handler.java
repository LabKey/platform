/*
 * Copyright (c) 2005-2013 LabKey Corporation
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

package org.labkey.api.exp;

import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * User: jeckels
 * Date: Dec 7, 2005
 */
public interface Handler<HandledType>
{
    public enum Priority
    {
        LOW, MEDIUM, HIGH;

        @Nullable
        public static <H extends Handler<V>, V> H findBestHandler(Collection<H> handlers, V value)
        {
            H bestHandler = null;
            Handler.Priority bestPriority = null;
            for (H handler : handlers)
            {
                Handler.Priority priority = handler.getPriority(value);
                if (priority != null)
                {
                    if (bestPriority == null || bestPriority.compareTo(priority) < 0)
                    {
                        bestHandler = handler;
                        bestPriority = priority;
                    }
                }
            }
            return bestHandler;
        }
    }

    /** @return null if this handler cannot handle the object */ 
    public Priority getPriority(HandledType object);
}
