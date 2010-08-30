package org.labkey.announcements;

import org.labkey.api.announcements.CommSchema;
import org.labkey.api.data.*;
import org.labkey.api.query.*;
import org.labkey.api.security.User;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/**
 * Created by IntelliJ IDEA.
 * User: Nick
 * Date: Jul 1, 2010
 * Time: 4:04:11 PM
 */
public class AnnouncementSchema extends UserSchema
{
    public static final String SCHEMA_NAME = "announcement";
    public static final String ANNOUNCEMENT_TABLE_NAME = "announcement";
    private static final Set<String> TABLE_NAMES;

    static
    {
        Set<String> names = new TreeSet<String>();
        names.add(ANNOUNCEMENT_TABLE_NAME);
        TABLE_NAMES = Collections.unmodifiableSet(names);
    }

    public static void register()
    {
        DefaultSchema.registerProvider(SCHEMA_NAME, new DefaultSchema.SchemaProvider()
        {
            public QuerySchema getSchema(DefaultSchema schema)
            {
                return new AnnouncementSchema(schema.getUser(), schema.getContainer());
            }
        });
    }

    public AnnouncementSchema(User user, Container container)
    {
        super(SCHEMA_NAME, "Contains announcementModels", user, container, CommSchema.getInstance().getSchema());
    }

    @Override
    protected TableInfo createTable(String name)
    {
        if (ANNOUNCEMENT_TABLE_NAME.equalsIgnoreCase(name))
        {
            FilteredTable table = new FilteredTable(CommSchema.getInstance().getTableInfoAnnouncements(), getContainer());
            table.wrapAllColumns(true);
            table.removeColumn(table.getColumn("Container"));
            ColumnInfo folderColumn = table.wrapColumn("Folder", table.getRealTable().getColumn("Container"));
            folderColumn.setFk(new ContainerForeignKey());
            table.addColumn(folderColumn);
            table.setDescription("Contains one row per announcementModel");

            table.getColumn("CreatedBy").setFk(new UserIdQueryForeignKey(getUser(), getContainer()));
            table.getColumn("ModifiedBy").setFk(new UserIdQueryForeignKey(getUser(), getContainer()));
            return table;
        }
        return null;
    }

    @Override
    public Set<String> getTableNames()
    {
        return TABLE_NAMES;
    }
}
