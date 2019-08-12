package org.labkey.assay;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.search.SearchService;
import org.labkey.api.search.SearchService.DocumentProvider;
import org.labkey.api.search.SearchService.IndexTask;
import org.labkey.api.assay.AssayService;

import java.util.Date;

public class AssayDocumentProvider implements DocumentProvider
{
    @Override
    public void enumerateDocuments(IndexTask task, @NotNull Container c, @Nullable Date modifiedSince)
    {
        Runnable runEnumerate = () -> AssayService.get().indexAssays(task, c);
        task.addRunnable(runEnumerate, SearchService.PRIORITY.group);
    }

    @Override
    public void indexDeleted()
    {
    }
}
