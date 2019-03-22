/*
 * Copyright (c) 2010-2012 LabKey Corporation
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

/**
 * User: adam
 * Date: Jul 8, 2010
 * Time: 10:39:13 AM
 */
public interface CacheProvider
{
    /**
     * Creates a new SimpleCache.
     *
     * @param debugName          Name to display on admin screen and in logging
     * @param limit              Maximum number of entries to hold in this cache; an integer value or CacheManager.UNLIMITED
     * @param defaultTimeToLive  TTL in milliseconds; an integer value or CacheManager.UNLIMITED
     * @param defaultTimeToIdle  TTI in milliseconds; an integer value or CacheManager.UNLIMITED
     * @param temporary          True means temporary (not tracked by memtracker)
     * @param <K>                Key type
     * @param <V>                Value type
     * @return                   A new cache created by the provider
     */
    <K, V> SimpleCache<K, V> getSimpleCache(String debugName, int limit, long defaultTimeToLive, long defaultTimeToIdle, boolean temporary);
    void shutdown();
}
