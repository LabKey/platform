/*
 * Copyright (c) 2010-2014 LabKey Corporation
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
import net.sf.ehcache.config.CacheConfiguration;
import org.apache.log4j.Logger;
import org.labkey.api.cache.CacheProvider;
import org.labkey.api.cache.SimpleCache;
import org.labkey.api.util.MemTracker;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * User: adam
 * Date: Jul 8, 2010
 * Time: 1:46:40 PM
 */
// Do not use CacheProvider implementations directly; use CacheManager.getCache() to get a cache
public class EhCacheProvider implements CacheProvider
{
    private static final Logger LOG = Logger.getLogger(EhCacheProvider.class);
    private static final EhCacheProvider INSTANCE = new EhCacheProvider();

    private final AtomicLong cacheCount = new AtomicLong(0);
    private final SafeCacheManager MANAGER;
    private final List<WeakReference<Cache>> ehCacheReferenceList;

    public static EhCacheProvider getInstance()
    {
        return INSTANCE;
    }

    private EhCacheProvider()
    {
        final CacheManager cm;

        try (InputStream is = EhCacheProvider.class.getResourceAsStream("ehcache.xml"))
        {
            cm = new CacheManager(is);
            MANAGER = new SafeCacheManager(cm);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }

        List<WeakReference<Cache>> list;

        // Leave this in place to allow monitoring the size of the internal EhCache reference list. See #19480.
        try
        {
            Field craField = cm.getClass().getDeclaredField("cacheRejoinAction");
            craField.setAccessible(true);
            Object cra = craField.get(cm);
            Field cachedField = cra.getClass().getDeclaredField("caches");
            cachedField.setAccessible(true);
            list = (List<WeakReference<Cache>>) cachedField.get(cra);
        }
        catch (NoSuchFieldException | IllegalAccessException e)
        {
            LOG.error("Could not access EhCache reference list via reflection", e);
            list = null;
        }

        ehCacheReferenceList = list;
    }

    public void shutdown()
    {
        LOG.info("Shutting down Ehcache");
        MANAGER.shutdown();
    }

    @Override
    public <K, V> SimpleCache<K, V> getSimpleCache(String debugName, int limit, long defaultTimeToLive, long defaultTimeToIdle, boolean temporary)
    {
        // Every Ehcache requires a unique name.  We create many temporary caches with overlapping names, so append a unique counter.
        // Consider: a cache pool for temporary caches?
        CacheConfiguration config = new CacheConfiguration(debugName + "_" + cacheCount.incrementAndGet(), limit == org.labkey.api.cache.CacheManager.UNLIMITED ? 0 : limit);
        config.setTimeToLiveSeconds(defaultTimeToLive == org.labkey.api.cache.CacheManager.UNLIMITED ? 0 : defaultTimeToLive / 1000);
        config.setTimeToIdleSeconds(defaultTimeToIdle == org.labkey.api.cache.CacheManager.UNLIMITED ? 0 : defaultTimeToIdle / 1000);
        Cache ehCache = new Cache(config);
        MANAGER.addCache(ehCache);

        if (LOG.isDebugEnabled())
        {
            String[] names = MANAGER.getCacheNames();
            StringBuilder sb = new StringBuilder("Caches managed by Ehcache: " + names.length);

            for (String name : names)
            {
                sb.append("\n  ");
                sb.append(name);
            }

            LOG.debug(sb);
        }

        // Memtrack temporary caches to ensure they're destroyed
        if (temporary)
            MemTracker.getInstance().put(ehCache);

        return new EhSimpleCache<>(ehCache);
    }

    void closeCache(Cache cache)
    {
        MANAGER.removeCache(cache.getName());

        // We've upgraded EhCache to 2.6.8, so we no longer need to modify ehCacheReferenceList. Just log its size to keep us (and EhCache) honest. See #19480.
        if (null != ehCacheReferenceList)
            LOG.debug("Caches in EhCache reference list: " + ehCacheReferenceList.size());

        LOG.debug("Closing \"" + cache.getName() + "\".  Ehcaches: " + MANAGER.getCacheNames().length);
    }
}
