/*
 * Copyright (c) 2006-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.api.data;

import org.labkey.api.collections.RowMap;
import org.labkey.api.reader.ColumnDescriptor;
import org.labkey.api.reader.Loader;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * User: Matthew
 * Date: Jun 12, 2006
 * Time: 3:42:15 PM
 *
 * Creates a temp table from columns and data sourced from any Loader.  Should work with TSV files, Excel files, and
 * custom-built loaders.
 */
public class TempTableWriter
{
    private final Loader _loader;

    public TempTableWriter(Loader loader)
    {
        _loader = loader;
    }

    private List<ColumnInfo> generateColumns() throws IOException
    {
        ColumnDescriptor[] allColumns = _loader.getColumns();
        List<ColumnInfo> activeColumns = new ArrayList<>();

        for (ColumnDescriptor col : allColumns)
        {
            if (col.load)
            {
                JdbcType type = JdbcType.valueOf(col.clazz);
                ColumnInfo colTT = new ColumnInfo(col.name);
                colTT.setJdbcType(type);
                colTT.setNullable(true);
                activeColumns.add(colTT);
            }
        }

        return activeColumns;
    }

    // TODO: Use streaming DataIterator instead of Loader.load() to support larger files.  Would need to infer varchar column widths via
    // first n rows approach (and potentially check and ALTER if we find a larger width later)
    public TempTableInfo loadTempTable() throws IOException, SQLException
    {
        return loadTempTable(null);
    }

    public TempTableInfo loadTempTable(List<ColumnInfo> activeColumns) throws IOException, SQLException
    {
        //
        // Load the file
        //
        List<Map<String, Object>> maps = _loader.load();

        //
        // create TableInfo
        //
        if (activeColumns == null)
            activeColumns = generateColumns();

        // note: this call sets col.parentTable()
        TempTableInfo tinfoTempTable = new TempTableInfo("ttw", activeColumns, null);
        String tempTableName = tinfoTempTable.getTempTableName();

        //
        // create table
        //
        StringBuilder sql = new StringBuilder();
        sql.append("CREATE TABLE ").append(tempTableName).append(" (");
        String comma = "";

        for (int i = 0; i < activeColumns.size(); i++)
        {
            ColumnInfo col = activeColumns.get(i);
            sql.append(comma);
            comma = ", ";

            if (col.getSqlTypeName().endsWith("VARCHAR")) // varchar or nvarchar
            {
                int length = -1;

                for (Map m : maps)
                {
                    RowMap map = (RowMap)m;    // TODO: Don't assume this is a row map
                    Object v = map.get(i);
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
        tinfoTempTable.track();
        DbSchema schema = tinfoTempTable.getSchema();
        new SqlExecutor(schema).execute(sql);

        //
        // Populate
        //
        StringBuilder sqlInsert = new StringBuilder();
        StringBuilder sqlValues = new StringBuilder();
        sqlInsert.append("INSERT INTO ").append(tempTableName).append(" (");
        sqlValues.append(" VALUES (");
        comma = "";

        for (ColumnInfo col : activeColumns)
        {
            sqlInsert.append(comma).append(col.getSelectName());
            sqlValues.append(comma).append("?");
            comma = ",";
        }

        sqlInsert.append(") ");
        sqlInsert.append(sqlValues);
        sqlInsert.append(")");

        List<Collection<?>> paramList = new ArrayList<>(maps.size());

        for (Map<String, Object> m : maps)
            paramList.add(m.values());

        Table.batchExecute(schema, sqlInsert.toString(), paramList);

        // Update statistics on the new table -- without this, query planner might pick a terrible plan
        schema.getSqlDialect().updateStatistics(tinfoTempTable);

        return tinfoTempTable;
    }
}
