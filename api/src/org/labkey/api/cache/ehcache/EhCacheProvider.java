/*
 * Copyright (c) 2010-2011 LabKey Corporation
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
import org.labkey.api.util.ContextListener;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.ShutdownListener;

import javax.servlet.ServletContextEvent;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicLong;

/**
 * User: adam
 * Date: Jul 8, 2010
 * Time: 1:46:40 PM
 */
// Do not use CacheProvider implementations directly; use CacheManager.getCache() to get a cache
public class EhCacheProvider implements CacheProvider, ShutdownListener
{
    private static final Logger LOG = Logger.getLogger(EhCacheProvider.class);
    private static final EhCacheProvider INSTANCE = new EhCacheProvider();
    private static final AtomicLong cacheCount = new AtomicLong(0);

    static final CacheManager MANAGER;

    static
    {
        InputStream is = null;

        try
        {
            is = EhCacheProvider.class.getResourceAsStream("ehcache.xml");
            MANAGER = new CacheManager(is);
        }
        finally
        {
            IOUtils.closeQuietly(is);
        }

        ContextListener.addShutdownListener(INSTANCE);
    }

    public static CacheProvider getInstance()
    {
        return INSTANCE;
    }

    private EhCacheProvider()
    {
    }

    @Override
    public void shutdownPre(ServletContextEvent servletContextEvent)
    {
    }

    @Override
    public void shutdownStarted(ServletContextEvent servletContextEvent)
    {
        LOG.info("Shutting down Ehcache");
        MANAGER.shutdown();
    }

    @Override
    public <K, V> SimpleCache<K, V> getSimpleCache(String debugName, int limit, long defaultTimeToLive, boolean temporary)
    {
        // Every Ehcache requires a unique name.  We create many temporary caches with overlapping names, so append a unique counter.
        // Consider: a cache pool for temporary caches?
        CacheConfiguration config = new CacheConfiguration(debugName + "_" + cacheCount.incrementAndGet(), limit == org.labkey.api.cache.CacheManager.UNLIMITED ? 0 : limit);
        config.setTimeToLiveSeconds(defaultTimeToLive / 1000);
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
            assert MemTracker.put(ehCache);

        return new EhSimpleCache<K, V>(ehCache);
    }
}
