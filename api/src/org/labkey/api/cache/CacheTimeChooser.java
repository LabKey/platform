/*
 * Copyright (c) 2012 LabKey Corporation
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
package org.labkey.api.cache;

import org.jetbrains.annotations.Nullable;

/**
 * User: adam
 * Date: 10/18/12
 * Time: 7:32 AM
 */

// Determines the time-to-live setting for a specific element that's about to be cached. Particularly useful in a
// BlockingCache, where puts are implicit.
public interface CacheTimeChooser<K>
{
    // Null means use the default; non-null means override the default with a specific TTL

    // Note: Ideally, the value would be passed in as well, however, in a BlockingCache, we put() a wrapper into
    // the cache before the value is loaded. If we find cases where the value is critical to determining the
    // TTL for an element then we should rework BlockingCache to change the TTL or re-put() the wrapper when a
    // custom TTL is provided.
    @Nullable Long getTimeToLive(K key, @Nullable Object argument);
}
