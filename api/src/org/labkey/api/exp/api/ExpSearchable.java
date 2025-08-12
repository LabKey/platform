package org.labkey.api.exp.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.TableInfo;
import org.labkey.api.search.SearchService;
import org.labkey.api.webdav.WebdavResource;

public interface ExpSearchable
{
    @Nullable WebdavResource createIndexDocument(@Nullable TableInfo table);

    default void index(@NotNull SearchService.TaskIndexingQueue queue, @Nullable TableInfo table)
    {
        var expScope = DbSchema.get("exp", DbSchemaType.Module).getScope();
        var doc = expScope.executeWithRetryReadOnly((tx) -> createIndexDocument(table));
        if (doc != null)
            queue.addResource(doc);
    }
}
