package org.labkey.study.query;

import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ForeignKey;
import org.labkey.study.StudySchema;

public class SiteTable extends StudyTable
{
    static public ForeignKey fkFor(StudyQuerySchema schema)
    {
        return new QueryForeignKey(schema, "Site", "RowId", "Label");
    }

    public SiteTable(StudyQuerySchema schema)
    {
        super(schema, StudySchema.getInstance().getTableInfoSite());
        _schema = schema;
        for (ColumnInfo baseColumn : _rootTable.getColumns())
        {
            String name = baseColumn.getName();
            if ("Container".equalsIgnoreCase(name) || "EntityId".equalsIgnoreCase(name))
                continue;
            ColumnInfo column = addWrapColumn(baseColumn);
            if ("RowId".equalsIgnoreCase(name))
            {
                // If there were a pageflow action which showed details on a particular site, we would set the fk of rowid here.
                // column.setFk(new TitleForeignKey(getBaseDetailsURL(), _rootTable.getColumn("RowId"), _rootTable.getColumn("Label"), "siteId"));
            }
        }
    }
}
