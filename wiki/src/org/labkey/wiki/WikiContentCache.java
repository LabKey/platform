package org.labkey.wiki;

import org.labkey.api.cache.CacheManager;
import org.labkey.api.cache.StringKeyCache;
import org.labkey.api.data.Container;
import org.labkey.api.wiki.FormattedHtml;
import org.labkey.wiki.model.Wiki;
import org.labkey.wiki.model.WikiVersion;

import java.sql.SQLException;

/**
 * User: adam
 * Date: Oct 8, 2010
 * Time: 7:55:26 PM
 */
public class WikiContentCache
{
    private static final StringKeyCache<String> CONTENT_CACHE = CacheManager.getStringKeyCache(10000, CacheManager.DAY, "Wiki Content");

    public static String getHtml(Container c, Wiki wiki, WikiVersion version) throws SQLException
    {
        String key = c.getId() + "/" + wiki.getName() + "/" + version.getVersion();
        String html = CONTENT_CACHE.get(key);

        if (null == html)
        {
            FormattedHtml formattedHtml = WikiManager.formatWiki(c, wiki, version);
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
