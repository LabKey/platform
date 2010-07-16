/*
 * Copyright (c) 2010 LabKey Corporation
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
import net.sf.ehcache.Element;
import net.sf.ehcache.config.CacheConfiguration;
import org.apache.log4j.Logger;
import org.labkey.api.cache.BasicCache;
import org.labkey.api.cache.CacheProvider;
import org.labkey.api.util.ContextListener;
import org.labkey.api.util.Filter;
import org.labkey.api.util.ShutdownListener;

import javax.servlet.ServletContextEvent;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * User: adam
 * Date: Jul 8, 2010
 * Time: 1:46:40 PM
 */
public class EhCacheProvider implements CacheProvider, ShutdownListener
{
    private static final Logger LOG = Logger.getLogger(EhCacheProvider.class);
    private static final EhCacheProvider INSTANCE = new EhCacheProvider();
    private static final CacheManager MANAGER = new CacheManager();
    private static final AtomicLong cacheCount = new AtomicLong(0);

    static
    {
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
        MANAGER.shutdown();
    }

    @Override
    public <K, V> BasicCache<K, V> getBasicCache(String debugName, int limit, long defaultTimeToLive)
    {
        // Every Ehcache requires a unique name.  We create many temporary caches with overlapping names, so append a unique counter.
        // Consider: a cache pool for temporary caches?
        CacheConfiguration config = new CacheConfiguration(debugName + "_" + cacheCount.incrementAndGet(), limit);
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

        return new EhBasicCache<K, V>(ehCache);
    }

    private static class EhBasicCache<K, V> implements BasicCache<K, V>
    {
        private final Cache _cache;

        private EhBasicCache(Cache cache)
        {
            _cache = cache;
        }

        @Override
        public V put(K key, V value)
        {
            Element element = new Element(key, value);
            _cache.put(element);
            return null;
        }

        @Override
        public V put(K key, V value, long timeToLive)
        {
            Element element = new Element(key, value);
            element.setTimeToLive((int)timeToLive / 1000);
            _cache.put(element);
            return null;
        }

        @Override
        public V get(K key)
        {
            Element e = _cache.get(key);

            if (null == e)
                return null;
            else
                return  (V)e.getObjectValue();
        }

        @Override
        public V remove(K key)
        {
            _cache.remove(key);
            return null;
        }

        @Override
        public int removeUsingFilter(Filter<K> filter)
        {
            int removes = 0;
            List<K> keys = _cache.getKeys();

            for (K key : keys)
            {
                if (filter.accept(key))
                {
                    remove(key);
                    removes++;
                }
            }

            return removes;
        }

        @Override
        public void clear()
        {
            _cache.removeAll();
        }

        @Override
        public int getLimit()
        {
            return _cache.getCacheConfiguration().getMaxElementsInMemory();
        }

        @Override
        public int size()
        {
            return _cache.getSize();
        }

        @Override
        public long getDefaultExpires()
        {
            return _cache.getCacheConfiguration().getTimeToLiveSeconds();
        }

        @Override
        public CacheType getCacheType()
        {
            return CacheType.NonDeterministicLRU;
        }

        @Override
        public void close()
        {
            int before = MANAGER.getCacheNames().length;
            MANAGER.removeCache(_cache.getName());
            int after = MANAGER.getCacheNames().length;

            LOG.debug("Closing \"" + _cache.getName() + "\"  Before: " + before + ", After: " + after); 
        }
    }
}
