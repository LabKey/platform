package org.labkey.search.model;

import org.labkey.api.data.DbSchema;
import org.labkey.api.data.TableInfo;

/**
 * User: adam
 * Date: 6/3/13
 * Time: 9:41 PM
 */
public class SearchSchema
{
    private static final SearchSchema INSTANCE = new SearchSchema();

    public static SearchSchema getInstance()
    {
        return INSTANCE;
    }

    private SearchSchema()
    {
    }

    public DbSchema getSchema()
    {
        return DbSchema.get("search");
    }

    public TableInfo getCrawlCollectionsTable()
    {
        return getSchema().getTable("CrawlCollections");
    }
}
