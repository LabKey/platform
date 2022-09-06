package org.labkey.api.exp.api;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.FolderExportContext;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.TableInfo;

import java.util.Collection;

public interface ColumnExporter
{
    boolean shouldExcludeColumn(TableInfo tableInfo, ColumnInfo col, FolderExportContext context);

    @Nullable Collection<ColumnInfo> getExportColumns(TableInfo tinfo, ColumnInfo col, FolderExportContext ctx);

}
