package org.labkey.api.data;

import org.labkey.common.tools.TabLoader;

import java.util.ArrayList;
import java.util.Date;
import java.util.Map;
import java.io.IOException;
import java.io.File;
import java.sql.Types;
import java.sql.SQLException;

/**
 * Created by IntelliJ IDEA.
 * User: Matthew
 * Date: Jun 12, 2006
 * Time: 3:42:15 PM
 *
 *
 * NOTE: I would have put loadTempTable() on TabLoader, but it is
 * in the tools project.  That wouldn't work, so here's a subclass instead.
 */
public class TempTableLoader extends TabLoader
{
    public TempTableLoader(File src) throws IOException
    {
        super(src);
    }

    public TempTableLoader(File src, boolean hasColumnHeaders) throws IOException
    {
        super(src);
        _skipLines = hasColumnHeaders ? 1 : 0;
    }

    public void setReturnElementClass(Class returnElementClass)
    {
        throw new UnsupportedOperationException();
    }

    public Table.TempTableInfo loadTempTable(DbSchema schema) throws IOException, SQLException
    {
        //
        // Load the file
        //

        Map[] maps = (Map[])load();


        //
        // create TableInfo
        //

        SqlDialect dialect = schema.getSqlDialect();

        ArrayList<ColumnInfo> cols = new ArrayList<ColumnInfo>();
        for (ColumnDescriptor col : getColumns())
        {
            String sqlType = getSqlType(dialect, col.clazz);
            ColumnInfo colTT = new ColumnInfo(col.name);
            colTT.setSqlTypeName(sqlType);
            colTT.setNullable(true);
            cols.add(colTT);
        }

        // note: this call sets col.parentTable()
        Table.TempTableInfo tinfoTempTable = new Table.TempTableInfo(schema, "tsv", cols, null);
        String tempTableName = tinfoTempTable.getTempTableName();

        //
        // create table
        //

        StringBuilder sql = new StringBuilder();
        sql.append("CREATE TABLE ").append(tempTableName).append(" (");
        String comma = "";
        for (int i=0 ; i<cols.size() ; i++)
        {
            ColumnInfo col = cols.get(i);
            sql.append(comma);
            comma = ", ";

            if (col.getSqlTypeName().endsWith("VARCHAR")) // varchar or nvarchar
            {
                int length = -1;
                for (Map m : maps)
                {
                    _RowMap map = (_RowMap)m;
                    Object v = map.getArray()[i];
                    if (v instanceof String)
                        length = Math.max(((String)v).length(), length);
                }
                if (length == -1)
                    length = 100;
                sql.append(col.getSelectName()).append(" ").append(col.getSqlTypeName()).append("(").append(length).append(")");
            }
            else
            {
                sql.append(col.getSelectName()).append(" ").append(col.getSqlTypeName());
            }
        }
        sql.append(")");


        //
        // Track the table, it will be deleted when tinfoTempTable is GC'd
        //

        Table.execute(schema, sql.toString(), null);
        tinfoTempTable.track();

        //
        // Populate
        //

        StringBuilder sqlInsert = new StringBuilder();
        StringBuilder sqlValues = new StringBuilder();
        sqlInsert.append("INSERT INTO ").append(tempTableName).append(" (");
        sqlValues.append(" VALUES (");
        comma = "";
        for (ColumnInfo col : cols)
        {
            sqlInsert.append(comma).append(col.getSelectName());
            sqlValues.append(comma).append("?");
            comma = ",";
        }
        sqlInsert.append(") ");
        sqlInsert.append(sqlValues);
        sqlInsert.append(")");

        ArrayList<Object[]> paramList = new ArrayList<Object[]>(maps.length);
        for (Map m : maps)
            paramList.add(((_RowMap)m).getArray());

        Table.batchExecute(schema, sqlInsert.toString(), paramList);

        return tinfoTempTable;
    }


    /**
     * UNDONE: this should be more complete and move to a shared location
     * TODO: use a map
     * @see ColumnInfo
     */
    private String getSqlType(SqlDialect dialect, Class clazz)
    {
        int sqlType;

        if (clazz == String.class)
            sqlType = Types.VARCHAR;
        else if (clazz == Date.class)
            sqlType = Types.TIMESTAMP;
        else if (clazz == Integer.class || clazz == Integer.TYPE)
            sqlType = Types.INTEGER;
        else if (clazz == Double.class || clazz == Double.TYPE)
            sqlType = Types.DOUBLE;
        else if (clazz == Float.class || clazz == Float.TYPE)
            sqlType = Types.REAL;
        else if (clazz == String.class)
            sqlType = Types.VARCHAR;
        else if (clazz == Boolean.class || clazz == Boolean.TYPE)
            sqlType = Types.BOOLEAN;
        else if (clazz == Long.class || clazz == Long.TYPE)
            sqlType = Types.BIGINT;
        else
            sqlType = Types.VARCHAR;

        return ColumnInfo.sqlTypeNameFromSqlType(sqlType, dialect);
    }
}
