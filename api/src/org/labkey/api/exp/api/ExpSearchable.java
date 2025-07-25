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

    default void index(@NotNull SearchService.PRIORITY priority)
    {
        index(priority, null);
    }

    default void index(@NotNull SearchService.PRIORITY priority, @Nullable SearchService.IndexTask task)
    {
        index(priority, task, null);
    }

    default void index(@NotNull SearchService.PRIORITY priority, @Nullable SearchService.IndexTask task, @Nullable TableInfo table)
    {
        if (task == null)
        {
            SearchService ss = SearchService.get();
            if (null == ss)
                return;

            task = ss.defaultTask();
        }

        var expScope = DbSchema.get("exp", DbSchemaType.Module).getScope();
        var doc = expScope.executeWithRetryReadOnly((tx) -> createIndexDocument(table));
        if (doc != null)
            task.addResource(doc, priority);
    }
}
