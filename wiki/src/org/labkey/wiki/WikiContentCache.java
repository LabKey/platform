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

import org.labkey.api.cache.CacheManager;
import org.labkey.api.cache.StringKeyCache;
import org.labkey.api.data.Container;
import org.labkey.api.wiki.FormattedHtml;
import org.labkey.wiki.model.Wiki;
import org.labkey.wiki.model.WikiVersion;

/**
 * User: adam
 * Date: Oct 8, 2010
 * Time: 7:55:26 PM
 */
public class WikiContentCache
{
    private static final StringKeyCache<String> CONTENT_CACHE = CacheManager.getStringKeyCache(50000, CacheManager.DAY, "Wiki Content");

    public static String getHtml(Container c, Wiki wiki, WikiVersion version, boolean cache)
    {
        if (!cache)
            return WikiManager.get().formatWiki(c, wiki, version).getHtml();

        String key = c.getId() + "/" + wiki.getName() + "/" + version.getVersion();
        String html = CONTENT_CACHE.get(key);

        if (null == html)
        {
            FormattedHtml formattedHtml = WikiManager.get().formatWiki(c, wiki, version);
            html = formattedHtml.getHtml();

            if (!formattedHtml.isVolatile())
                CONTENT_CACHE.put(key, html);
        }

        return html;
    }

    public static void uncache(Container c, String wikiName)
    {
        CONTENT_CACHE.removeUsingPrefix(c.getId() + "/" + wikiName + "/");
    }

    public static void uncache(Container c)
    {
        CONTENT_CACHE.removeUsingPrefix(c.getId() + "/");
    }
}
