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
package org.labkey.api.cache.ehcache;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;

/**
 * User: adam
 * Date: 3/21/12
 * Time: 12:42 PM
 */

// We've run into concurrency problems during the creation/deletion of EhCache instances, see #14387.  EhCache is fixing
// this particular issue, but they recommend external synchronization, see https://jira.terracotta.org/jira/browse/EHC-931.
// This class implements that synchronization.
public class SafeCacheManager
{
    private final CacheManager _cacheManager;

    public SafeCacheManager(CacheManager cacheManager)
    {
        _cacheManager = cacheManager;
    }

    public synchronized void shutdown()
    {
        _cacheManager.shutdown();
    }

    public synchronized void addCache(Cache ehCache)
    {
        _cacheManager.addCache(ehCache);
    }

    public synchronized String[] getCacheNames()
    {
        return _cacheManager.getCacheNames();
    }

    public synchronized void removeCache(String name)
    {
        _cacheManager.removeCache(name);
    }
}
