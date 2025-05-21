package org.labkey.api.exp.api;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.TableInfo;
import org.labkey.api.search.SearchService;
import org.labkey.api.webdav.WebdavResource;

public interface ExpSearchable
{
    @Nullable WebdavResource createIndexDocument(@Nullable TableInfo table);

    default void index()
    {
        index(null);
    }

    default void index(@Nullable SearchService.IndexTask task)
    {
        index(task, null);
    }

    default void index(@Nullable SearchService.IndexTask task, @Nullable SearchService.PRIORITY priority)
    {
        index(task, priority, null);
    }

    default void index(@Nullable SearchService.IndexTask task, @Nullable SearchService.PRIORITY priority, @Nullable TableInfo table)
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
            task.addResource(doc, priority == null ? SearchService.PRIORITY.item : priority);
    }
}
