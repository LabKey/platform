package org.labkey.api.data;

import org.labkey.api.cache.CacheManager;

/**
* User: adam
* Date: 8/14/13
* Time: 5:46 AM
*/
public enum DbSchemaType
{
    // TODO: Create Uncached type?  Might make sense for non-external schema usages of Bare
    Module("module", CacheManager.YEAR)
    {
        @Override
        DbSchema loadSchema(DbScope scope, String rquestedSchemaName) throws Exception
        {
            return scope.loadSchema(rquestedSchemaName, this);
        }
    },
    Bare("bare", CacheManager.HOUR)
    {
        @Override
        DbSchema loadSchema(DbScope scope, String rquestedSchemaName) throws Exception
        {
            return scope.loadBareSchema(rquestedSchemaName, this);
        }
    },
    All("", 0)
    {
        @Override
        DbSchema loadSchema(DbScope scope, String rquestedSchemaName) throws Exception
        {
            throw new IllegalStateException("Should not be loading a schema of this type");
        }

        @Override
        protected long getCacheTimeToLive()
        {
            throw new IllegalStateException("Should not be caching a schema of this type");
        }
    };

    private final String _cacheKeyPostFix;
    private final long _cacheTimeToLive;

    DbSchemaType(String cacheKeyPostFix, long cacheTimeToLive)
    {
        _cacheKeyPostFix = cacheKeyPostFix;
        _cacheTimeToLive = cacheTimeToLive;
    }

    abstract DbSchema loadSchema(DbScope scope, String requestedSchemaName) throws Exception;

    String getCacheKey(String schemaName)
    {
        return schemaName.toLowerCase() + "|" + _cacheKeyPostFix;
    }

    long getCacheTimeToLive()
    {
        return _cacheTimeToLive;
    }
}
