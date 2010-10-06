package org.labkey.api.data;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Sep 30, 2010
 * Time: 9:25:44 AM
 */

public class NullColumnInfo extends ColumnInfo
{
    public NullColumnInfo(TableInfo parent, String name, String sqlType)
    {
        super(name, parent);
        setSqlTypeName(sqlType);
    }

    @Override
    public SQLFragment getValueSql(String tableAliasName)
    {
        return new SQLFragment(nullValue(getSqlTypeName()));
    }

    public static String nullValue(String typeName)
    {
        return "CAST(NULL AS " + typeName + ")";
    }
}


