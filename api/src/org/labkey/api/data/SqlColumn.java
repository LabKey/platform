package org.labkey.api.data;

/**
 * Created by IntelliJ IDEA.
 * User: Matthew
 * Date: Mar 20, 2009
 * Time: 7:27:13 PM
 */
public interface SqlColumn
{
    String getName();
    SQLFragment getValueSql(String tableAlias);
    int getSqlTypeInt();
    ForeignKey getFk();
}
