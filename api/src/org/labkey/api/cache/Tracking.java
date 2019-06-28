/*
 * Copyright (c) 2010-2019 LabKey Corporation
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
 * Date: Jul 8, 2010
 * Time: 10:44:59 AM
 */
public interface Tracking
{
    String getDebugName();

    @Nullable
    StackTraceElement[] getCreationStackTrace();

    Stats getStats();

    Stats getTransactionStats();

    CacheType getCacheType();

    // Maximum number of elements allowed in the cache
    int getLimit();

    // Current number of elements in the cache
    int size();

    long getDefaultExpires();
}
