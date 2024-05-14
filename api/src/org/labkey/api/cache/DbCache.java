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

import org.apache.commons.collections4.Bag;
import org.apache.commons.collections4.bag.HashBag;
import org.apache.logging.log4j.Logger;
import org.labkey.api.data.DatabaseCache;
import org.labkey.api.data.TableInfo;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.Path;
import org.labkey.api.util.logging.LogHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Don't use this! Use CacheManager.getCache() or DatabaseCache instead. DbCache associates a DatabaseCache with each
 * participating TableInfo. The Table layer then invalidates the entire cache anytime it touches (insert, update, delete)
 * that TableInfo. This is easy, but very inefficient. Managers should use DatabaseCaches directly and handle
 * invalidation themselves.
 */
@Deprecated
public class DbCache
{
    private static final Logger LOG = LogHelper.getLogger(DbCache.class, "DbCache invalidations");
    private static final Map<Path, DatabaseCache<String, Object>> CACHES = new HashMap<>(10);

    public static DatabaseCache<String, Object> getCache(TableInfo tinfo, boolean create)
    {
        Path cacheKey = tinfo.getNotificationKey();
        assert null != cacheKey : "DbCache not supported for " + tinfo;

        synchronized(CACHES)
        {
            DatabaseCache<String, Object> cache = CACHES.get(cacheKey);

            if (null == cache && create)
            {
                cache = new DatabaseCache<>(tinfo.getSchema().getScope(), tinfo.getCacheSize(), "DbCache: " + tinfo.getName());
                CACHES.put(cacheKey, cache);
            }

            return cache;
        }
    }

    public static void put(TableInfo tinfo, String name, Object obj)
    {
        DatabaseCache<String, Object> cache = getCache(tinfo, true);
        cache.put(name, obj);
    }

    public static void put(TableInfo tinfo, String name, Object obj, long millisToLive)
    {
        DatabaseCache<String, Object> cache = getCache(tinfo, true);
        cache.put(name, obj, millisToLive);
    }

    public static Object get(TableInfo tinfo, String name)
    {
        DatabaseCache<String, Object> cache = getCache(tinfo, false);
        return null == cache ? null : cache.get(name);
    }

    public static void remove(TableInfo tinfo, String name)
    {
        DatabaseCache<String, Object> cache = getCache(tinfo, false);
        if (null != cache)
            cache.remove(name);
    }

    /** used by Table */
    public static void invalidateAll(TableInfo tinfo)
    {
        DatabaseCache<String, Object> cache = CACHES.get(tinfo.getNotificationKey());
        if (null != cache)
        {
            trackInvalidate(tinfo);
            cache.clear();
        }
    }

    public static void clear(TableInfo tinfo)
    {
        DatabaseCache<String, Object> cache = getCache(tinfo, false);
        if (null != cache)
        {
            trackInvalidate(tinfo);
            cache.clear();
        }
    }

    public static void removeUsingPrefix(TableInfo tinfo, String name)
    {
        DatabaseCache<String, Object> cache = getCache(tinfo, false);
        if (null != cache)
            cache.removeUsingFilter(new Cache.StringPrefixFilter(name));
    }

    /**
     * Everything below is temporary, meant to help irradicate the use of DbCache. If a TableInfo is deemed
     * "interesting" (it's in the process of being migrated):
     * - Each call to invalidateAll() or clear() causes its stack trace to be added to the tracking bag
     * - Each call to trackRemove() causes its stack trace to be removed from the tracking bag
     * A remove that's unsuccessful indicates no corresponding invalidateAll(), so log that. Anything left in the bag
     * indicates invalidateAll()/clear() calls with no corresponding remove. Use this to migrate a DbCache to our normal
     * DatabaseCache pattern, with explicit removes for invalidation. Leave the DbCache in place (until migration is
     * complete) and add calls to trackRemove() immediately after Table.insert(), Table.update(), and Table.delete()
     * calls. Ensure that nothing is left in the bag, meaning all Table-initiated invalidateAll() calls have
     * corresponding removes on the new cache. See CachingTestCase as an example.
     */

    private static final Bag<String> TRACKING_BAG = new HashBag<>();
    private static final Set<String> INTERESTING = Set.of("TestTable", "ExperimentRun", "Study");

    private static boolean isInteresting(TableInfo tinfo)
    {
        return getCache(tinfo, false) != null;
    }

    public static void trackInvalidate(TableInfo tinfo)
    {
        if (isInteresting(tinfo))
        {
            StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
            int linesToTrim = 2;

            // Trim all Table lines, if present
            for (int i = 2; i < stackTrace.length; i++)
            {
                if ("Table.java".equals(stackTrace[i].getFileName()))
                    linesToTrim = i;
                else if (linesToTrim > 2)
                    break;
            }

            String key = tinfo.getName() + ExceptionUtil.renderStackTrace(stackTrace, linesToTrim + 1);
            TRACKING_BAG.add(key);
        }
    }

    public static void trackRemove(TableInfo tinfo)
    {
        if (isInteresting(tinfo))
        {
            StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
            String trimmed = ExceptionUtil.renderStackTrace(stackTrace, 2);

            // Subtract one from the first line number so it matches the Table.* line (so the stack traces match exactly)
            int from = trimmed.indexOf(':') + 1;
            int to = trimmed.indexOf(')', from);
            String lineNumber = trimmed.substring(from, to);
            String key = tinfo.getName() + trimmed.replaceFirst(lineNumber, String.valueOf(Integer.valueOf(lineNumber) - 1));
            if (!TRACKING_BAG.remove(key, 1))
                LOG.info("Failed to remove " + key);
        }
    }

    public static void logUnmatched()
    {
        if (TRACKING_BAG.isEmpty())
        {
            LOG.info("No unmatched cache removes");
        }
        else
        {
            TRACKING_BAG.uniqueSet().forEach(key -> LOG.error("Unmatched {}", key));
        }
    }
}
