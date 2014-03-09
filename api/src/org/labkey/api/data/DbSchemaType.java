/*
 * Copyright (c) 2013-2014 LabKey Corporation
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
    Module("module", CacheManager.YEAR, true),
    Provisioned("provisioned", CacheManager.YEAR, false),
    Bare("bare", CacheManager.HOUR, false),
    All("", 0, false)
    {
        @Override
        protected long getCacheTimeToLive()
        {
            throw new IllegalStateException("Should not be caching a schema of this type");
        }

        @Override
        boolean applyXmlMetaData()
        {
            throw new IllegalStateException("Should not be caching a schema of this type");
        }
    };

    private final String _cacheKeyPostFix;  // Postfix makes it easy for All type to remove all versions of a schema from the cache
    private final long _cacheTimeToLive;
    private final boolean _applyXmlMetaData;

    DbSchemaType(String cacheKeyPostFix, long cacheTimeToLive, boolean applyXmlMetaData)
    {
        _cacheKeyPostFix = cacheKeyPostFix;
        _cacheTimeToLive = cacheTimeToLive;
        _applyXmlMetaData = applyXmlMetaData;
    }

    String getCacheKey(String schemaName)
    {
        return schemaName.toLowerCase() + "|" + _cacheKeyPostFix;
    }

    long getCacheTimeToLive()
    {
        return _cacheTimeToLive;
    }

    boolean applyXmlMetaData()
    {
        return _applyXmlMetaData;
    }
}
