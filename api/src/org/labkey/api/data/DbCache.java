package org.labkey.api.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Copyright (C) 2004 Fred Hutchinson Cancer Research Center. All Rights Reserved.
 * User: migra
 * Date: Nov 30, 2005
 * Time: 4:41:26 PM
 *
 * DbCache is a wrapper that allocates one transaction aware cache per TableInfo
 */
public class DbCache
{
    // CONSIDER: move constant into web.xml
    static int CACHE_SIZE = 1000;

    static final WeakHashMap<TableInfo, DatabaseCache<Object>> tableCaches = new WeakHashMap<TableInfo, DatabaseCache<Object>>(100);


    public static DatabaseCache<Object> getCache(TableInfo tinfo)
    {
        synchronized(tableCaches)
        {
            DatabaseCache<Object> cache = tableCaches.get(tinfo);
            if (null == cache)
            {
                cache = new DatabaseCache<Object>(tinfo.getSchema().getScope(), CACHE_SIZE);
                cache.setDebugName(tinfo.getName());
                tableCaches.put(tinfo, cache);
            }
            return cache;
        }
    }
    

    /*
    With the introduction of Nick's query layer, we can now have multiple tableinfo objects
    for a single actual table.  These tableinfo objects may have different filters applied.
    As a result, we need to be very careful with our caching; we look up and store using
    Object .equals (by just letting the CacheMap do its thing), but on removal we need to
    remove all tableinfos that are bound to the same underlying table.  We gather that
    list of tables here:
    */
    private static List<TableInfo> getTableInfosByUnderlyingTable(TableInfo tinfo)
    {
        synchronized (tableCaches)
        {
            List<TableInfo> matchingCaches = new ArrayList<TableInfo>();
            for (Map.Entry<TableInfo, DatabaseCache<Object>> entry : tableCaches.entrySet())
            {
                if (entry.getKey().getName().equals(tinfo.getName()))
                    matchingCaches.add(entry.getKey());
            }
            return matchingCaches;
        }
    }


    public static void put(TableInfo tinfo, String name, Object obj)
    {
        DatabaseCache<Object> cacheMap = getCache(tinfo);
        cacheMap.put(name, obj);
    }


    public static Object put(TableInfo tinfo, String name, Object obj, long millisToLive)
    {
        DatabaseCache<Object> cacheMap = getCache(tinfo);
        return cacheMap.put(name, obj, millisToLive);
    }


    public static Object get(TableInfo tinfo, String name)
    {
        DatabaseCache cacheMap = getCache(tinfo);
        return cacheMap.get(name);
    }
    

    public static void remove(TableInfo tinfo, String name)
    {
        DatabaseCache cacheMap = getCache(tinfo);
        cacheMap.remove(name);
    }


    /** used by Table */
    static void invalidateAll(TableInfo tinfo)
    {
        if (tinfo.getSchema().getScope().isTransactionActive())
        {
            tinfo.getSchema().getScope().addTransactedInvalidate(tinfo);
        }
        else
        {
            // see comment above 'getTableCaches' for an explanation of why
            // we clear multiple caches here:
            synchronized(tableCaches)
            {
                List<TableInfo> tableInfos = getTableInfosByUnderlyingTable(tinfo);
                for (TableInfo matchingTinfo : tableInfos)
                {
                    DatabaseCache cacheMap = tableCaches.get(matchingTinfo);
                    if (null != cacheMap)
                        cacheMap.clear();
                }
            }
        }
    }


    public static void clear(TableInfo tinfo)
    {
        if (tinfo.getSchema().getScope().isTransactionActive())
        {
            tinfo.getSchema().getScope().addTransactedClear(tinfo);
        }
        else
        {
            DatabaseCache cacheMap = tableCaches.get(tinfo);
            if (null != cacheMap)
                cacheMap.clear();
        }
    }


    public static void removeUsingPrefix(TableInfo tinfo, String name)
    {
        DatabaseCache cacheMap = getCache(tinfo);
        cacheMap.removeUsingPrefix(name);
    }
}
