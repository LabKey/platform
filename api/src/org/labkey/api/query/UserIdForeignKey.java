package org.labkey.api.query;

import org.labkey.api.data.*;

public class UserIdForeignKey extends LookupForeignKey
{
    static public ColumnInfo initColumn(ColumnInfo column)
    {
        column.setFk(new UserIdForeignKey());
        column.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                return new UserIdRenderer(colInfo);
            }
        });
        return column;
    }


    public UserIdForeignKey()
    {
        super("UserId", "DisplayName");
    }

    public TableInfo getLookupTableInfo()
    {
        TableInfo tinfoUsersData = CoreSchema.getInstance().getTableInfoUsersData();
        FilteredTable ret = new FilteredTable(tinfoUsersData);
        ret.addWrapColumn(tinfoUsersData.getColumn("UserId"));
        ret.addColumn(ret.wrapColumn("DisplayName", tinfoUsersData.getColumn("DisplayName")));
        ret.setTitleColumn("DisplayName");
        return ret;
    }
}
