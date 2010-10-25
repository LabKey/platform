package org.labkey.api.data;

import org.labkey.api.util.GUID;

import java.sql.SQLException;
import java.util.List;

/**
* User: matt
* Date: Oct 23, 2010
* Time: 3:08:13 PM
*/
public class TempTableInfo extends SchemaTableInfo
{
    TempTableTracker _ttt;
    String _tempTableName;
    String _unique;

    static private String shortGuid()
    {
        String guid = GUID.makeGUID();
        StringBuilder sb = new StringBuilder(guid.length());
        for (int i=0 ; i<guid.length() ; i++)
        {
            char ch = guid.charAt(i);
            if (ch != '-')
                sb.append(ch);
        }
        return sb.toString();
    }

    public TempTableInfo(DbSchema parentSchema, String name, List<ColumnInfo> cols, List<String> pk)
    {
        super(parentSchema);

        _unique = shortGuid();
        _tempTableName = parentSchema.getSqlDialect().getGlobalTempTablePrefix() + name + "$" + _unique;

        // overwrite selectName to not use schema/owner name
        this.name = name;
        selectName = new SQLFragment(_tempTableName);
        for (ColumnInfo col : cols)
            col.setParentTable(this);
        columns.addAll(cols);
        if (pk != null)
            setPkColumnNames(pk);
        setTableType(TABLE_TYPE_TABLE);
    }

    public void setButtonBarConfig(ButtonBarConfig bbarConfig)
    {
        _buttonBarConfig = bbarConfig;
    }

    public String getSelectName()
    {
        return _tempTableName;
    }


    public String getTempTableName()
    {
        return _tempTableName;
    }


    /** Call this method when table is physically created */
    public void track()
    {
        _ttt = TempTableTracker.track(getSchema(), getTempTableName(), this);
    }


    public void delete()
    {
        _ttt.delete();
    }

    public boolean verify()
    {
        try
        {
            Table.isEmpty(this);
            return true;
        }
        catch (SQLException e)
        {
            return false;
        }
    }
}
