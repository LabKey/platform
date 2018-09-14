package org.labkey.announcements.query;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.announcements.CommSchema;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.query.FilteredTable;

import static org.labkey.announcements.query.AnnouncementSchema.THREADS_TABLE_NAME;

public class ThreadsTable extends FilteredTable<AnnouncementSchema>
{
    public ThreadsTable(@NotNull AnnouncementSchema userSchema)
    {
        super(CommSchema.getInstance().getTableInfoThreads(), userSchema);

        wrapAllColumns(true);
        removeColumn(getColumn("Body"));
        removeColumn(getColumn("RendererType"));
        removeColumn(getColumn("DiscussionSrcIdentifier"));
        removeColumn(getColumn("DiscussionSrcUrl"));
        removeColumn(getColumn("Container"));
        ColumnInfo folderColumn = wrapColumn("Folder", getRealTable().getColumn("Container"));
        folderColumn.setFk(new ContainerForeignKey(userSchema));
        addColumn(folderColumn);

        setDescription("Contains one row per thread");
        setName(THREADS_TABLE_NAME);
        setPublicSchemaName(AnnouncementSchema.SCHEMA_NAME);
    }
}
