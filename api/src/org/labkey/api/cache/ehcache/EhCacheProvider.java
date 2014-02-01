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
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.labkey.api.cache.CacheProvider;
import org.labkey.api.cache.SimpleCache;
import org.labkey.api.util.MemTracker;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
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
    private static final AtomicLong cacheCount = new AtomicLong(0);
    private static final SafeCacheManager MANAGER;

    private static List<WeakReference<Cache>> ehCacheReferenceList;

    static
    {
        InputStream is = null;

        try
        {
            is = EhCacheProvider.class.getResourceAsStream("ehcache.xml");
            CacheManager cm = new CacheManager(is);
            MANAGER = new SafeCacheManager(cm);

            // Temporary fix for #19480 in 13.2 and 13.3. TODO: Replace with EhCache upgrade in 14.1
            try
            {
                Field craField = cm.getClass().getDeclaredField("cacheRejoinAction");
                craField.setAccessible(true);
                Object cra = craField.get(cm);
                Field cachedField = cra.getClass().getDeclaredField("caches");
                cachedField.setAccessible(true);
                ehCacheReferenceList = (List<WeakReference<Cache>>)cachedField.get(cra);
            }
            catch (NoSuchFieldException | IllegalAccessException e)
            {
                LOG.error("Could not access EhCache reference list via reflection", e);
                ehCacheReferenceList = null;
            }
        }
        finally
        {
            IOUtils.closeQuietly(is);
        }
    }

    public static EhCacheProvider getInstance()
    {
        return INSTANCE;
    }

    private EhCacheProvider()
    {
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
        EhCacheProvider.MANAGER.removeCache(cache.getName());

        // Temporary fix for #19480 in 13.2 and 13.3. TODO: Replace with EhCache upgrade in 14.1
        if (null != ehCacheReferenceList)
        {
            LOG.debug("Caches in EhCache reference list: " + ehCacheReferenceList.size());

            Collection<WeakReference<Cache>> toRemove = new ArrayList<WeakReference<Cache>>();

            for (final WeakReference<Cache> cacheRef : ehCacheReferenceList) {
                Cache c = cacheRef.get();
                if (c == null) {
                    toRemove.add(cacheRef);
                    continue;
                }

                if (c == cache) {
                    toRemove.add(cacheRef);
                }
            }

            ehCacheReferenceList.removeAll(toRemove);
        }

        LOG.debug("Closing \"" + cache.getName() + "\".  Ehcaches: " + MANAGER.getCacheNames().length);
    }
}
