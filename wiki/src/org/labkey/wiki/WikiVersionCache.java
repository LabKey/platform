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

package org.labkey.wiki;

import org.labkey.api.announcements.CommSchema;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.cache.StringKeyCache;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.view.NotFoundException;
import org.labkey.wiki.WikiCache.WikiCacheLoader;
import org.labkey.wiki.model.WikiVersion;

/*
* User: adam
* Date: Dec 14, 2010
* Time: 10:58:59 PM
*/

// Cache wiki versions by rowId.  Wiki versions are immutable (we only insert them, never update them) so no real need
// to uncache them... although we could uncache them when their parent wikis are uncached to proactively make room for
// other cached objects.  This would require adding wiki rowid to the cache key.
public class WikiVersionCache
{
    private static final StringKeyCache<WikiVersion> CACHE = CacheManager.getStringKeyCache(50000, CacheManager.DAY, "Wiki Versions");

    static WikiVersion getVersion(Container c, final int version)
    {
        return CACHE.get(getCacheKey(c, version), c, new WikiCacheLoader<WikiVersion>() {
            @Override
            WikiVersion load(String key, Container c)
            {
                return loadVersionFromDatabase(version);
            }
        });
    }

    // Exposed outside this class only for junit tests
    static WikiVersion loadVersionFromDatabase(int version)
    {
        WikiVersion wikiversion = new TableSelector(CommSchema.getInstance().getTableInfoPageVersions(), new SimpleFilter(FieldKey.fromParts("RowId"), version), null).getObject(WikiVersion.class);

        if (wikiversion == null)
            throw new NotFoundException("Wiki version " + version + " not found");

        return wikiversion;
    }

    private static String getCacheKey(Container c, int version)
    {
        return c.getId() + "/" + version;
    }
}
