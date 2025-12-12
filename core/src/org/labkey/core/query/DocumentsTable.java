package org.labkey.core.query;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.UserIdQueryForeignKey;

public class DocumentsTable extends FilteredTable<CoreQuerySchema>
{
    public DocumentsTable(@NotNull CoreQuerySchema userSchema, ContainerFilter cf)
    {
        super(CoreSchema.getInstance().getTableInfoDocuments(), userSchema, cf);
        wrapAllColumns(true);
        getMutableColumnOrThrow("RowId").setHidden(true);
        getMutableColumnOrThrow("ModifiedBy").setHidden(true);
        getMutableColumnOrThrow("Modified").setHidden(true);
        MutableColumnInfo owner = getMutableColumnOrThrow("Owner");
        owner.setHidden(true);
        owner.setFk(new UserIdQueryForeignKey(_userSchema));
        getMutableColumnOrThrow("Parent").setHidden(true);
        getMutableColumnOrThrow("DocumentSize").setFormat("#,##0");
        getMutableColumnOrThrow("Document").setHidden(true);
        getMutableColumnOrThrow("LastIndexed").setHidden(true);
    }
}
