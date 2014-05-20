package org.labkey.announcements.query;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.announcements.CommSchema;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.data.DatabaseTableType;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.Permission;

/**
 * Created by Nick Arnold on 5/16/14.
 */
public class RSSFeedsTable extends FilteredTable<AnnouncementSchema>
{
    public RSSFeedsTable(AnnouncementSchema schema)
    {
        super(CommSchema.getInstance().getTableInfoRSSFeeds(), schema);

        //
        // Handle columns
        //
        wrapAllColumns(true);

        ColumnInfo containerColumn = getColumn("Container");
        containerColumn.setFk(new ContainerForeignKey(schema));
        containerColumn.setUserEditable(false);
        containerColumn.setLabel("Folder");

        setDescription("Contains one row per RSS feed.");
        setName(AnnouncementSchema.RSS_FEEDS_TABLE_NAME);
        setPublicSchemaName(AnnouncementSchema.SCHEMA_NAME);
    }

    @Nullable
    @Override
    public QueryUpdateService getUpdateService()
    {
        TableInfo table = getRealTable();
        if (table != null && table.getTableType() == DatabaseTableType.TABLE)
            return new DefaultQueryUpdateService(this, table);

        return null;
    }

    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        return getContainer().hasPermission(user, perm);
    }
}
