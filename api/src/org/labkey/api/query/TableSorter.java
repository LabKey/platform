/*
 * Copyright (c) 2013-2016 LabKey Corporation
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
package org.labkey.api.query;

import org.apache.log4j.Logger;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.data.MultiValuedForeignKey;
import org.labkey.api.data.TableInfo;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Tuple3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

/**
 * User: kevink
 * Date: 4/30/13
 */
public final class TableSorter
{
    /**
     * Get a topologically sorted list of TableInfos within this schema.
     *
     * @throws IllegalStateException if a loop is detected.
     */
    public static List<TableInfo> sort(UserSchema schema)
    {
        String schemaName = schema.getName();
        Set<String> tableNames = new HashSet<>(schema.getTableNames());
        Map<String, TableInfo> tables = new CaseInsensitiveHashMap<>();
        for (String tableName : tableNames)
            tables.put(tableName, schema.getTable(tableName));

        return sort(schemaName, tables);
    }

    /**
     * Get a topologically sorted list of TableInfos within this schema.
     *
     * @throws IllegalStateException if a loop is detected.
     */
    public static List<TableInfo> sort(DbSchema schema)
    {
        String schemaName = schema.getName();
        Set<String> tableNames = new HashSet<>(schema.getTableNames());
        Map<String, TableInfo> tables = new CaseInsensitiveHashMap<>();
        for (String tableName : tableNames)
            tables.put(tableName, schema.getTable(tableName));

        return sort(schemaName, tables);
    }

    private static List<TableInfo> sort(String schemaName, Map<String, TableInfo> tables)
    {
        if (tables.isEmpty())
            return Collections.emptyList();

        // Find all tables with no incoming FKs
        Set<String> startTables = new CaseInsensitiveHashSet();
        startTables.addAll(tables.keySet());
        for (String tableName : tables.keySet())
        {
            TableInfo table = tables.get(tableName);
            for (ColumnInfo column : table.getColumns())
            {
                // Skip calculated columns (e.g., ExprColumn)
                if (column.isCalculated())
                    continue;

                ForeignKey fk = column.getFk();

                // Skip fake FKs that just wrap the RowId
                if (fk == null || fk instanceof RowIdForeignKey || fk instanceof MultiValuedForeignKey)
                    continue;

                // Unfortunately, we need to get the lookup table since some FKs don't expose .getLookupSchemaName() or .getLookupTableName()
                TableInfo t = null;
                try
                {
                    t = fk.getLookupTableInfo();
                }
                catch (QueryParseException qpe)
                {
                    // ignore and try to continue
                    String msg = String.format("Failed to traverse fk (%s, %s, %s) from (%s, %s)",
                            fk.getLookupSchemaName(), fk.getLookupTableName(), fk.getLookupColumnName(), tableName, column.getName());
                    Logger.getLogger(TableSorter.class).warn(msg, qpe);
                }

                // Skip lookups to other schemas
                if (!(schemaName.equalsIgnoreCase(fk.getLookupSchemaName()) || (t != null && schemaName.equalsIgnoreCase(t.getPublicSchemaName()))))
                    continue;

                // Get the lookupTableName: Attempt to use FK name first, then use the actual table name if it exists and is in the set of known tables.
                String lookupTableName = fk.getLookupTableName();
                if (!tables.containsKey(lookupTableName) && (t != null && tables.containsKey(t.getName())))
                    lookupTableName = t.getName();

                // Skip self-referencing FKs
                if (schemaName.equalsIgnoreCase(fk.getLookupSchemaName()) && lookupTableName.equals(table.getName()))
                    continue;

                // Remove the lookup table from the set of tables with no incoming FK
                startTables.remove(lookupTableName);
            }
        }

        if (startTables.isEmpty())
            throw new IllegalArgumentException("No tables without incoming FKs found");

        // Depth-first topological sort of the tables starting with the startTables
        Set<TableInfo> visited = new HashSet<>(tables.size());
        List<TableInfo> sorted = new ArrayList<>(tables.size());
        for (String tableName : startTables)
            depthFirstWalk(schemaName, tables, tables.get(tableName), visited, new LinkedList<>(), sorted);

        return sorted;
    }

    private static void depthFirstWalk(String schemaName, Map<String, TableInfo> tables, TableInfo table, Set<TableInfo> visited, LinkedList<Tuple3<TableInfo, ColumnInfo, TableInfo>> visitingPath, List<TableInfo> sorted)
    {
        // NOTE: loops exist in current schemas
        //   core.Containers has a self join to parent Container
        //   mothership.ServerSession.ServerInstallationId -> mothership.ServerInstallations.MostRecentSession -> mothership.ServerSession
        if (hasLoop(visitingPath, table))
            throw new IllegalStateException("Loop detected: " + formatPath(visitingPath));

        if (visited.contains(table))
            return;

        visited.add(table);

        for (ColumnInfo column : table.getColumns())
        {
            // Skip calculated columns (e.g., ExprColumn)
            if (column.isCalculated())
                continue;

            ForeignKey fk = column.getFk();

            // Skip fake FKs that just wrap the RowId
            if (fk == null || fk instanceof RowIdForeignKey || fk instanceof MultiValuedForeignKey)
                continue;

            // Unfortunately, we need to get the lookup table since some FKs don't expose .getLookupSchemaName() or .getLookupTableName()
            TableInfo t = null;
            try
            {
                t = fk.getLookupTableInfo();
            }
            catch (QueryParseException qpe)
            {
                // We've already reported this error once; ignore and try to continue
            }

            // Skip lookups to other schemas
            if (!(schemaName.equalsIgnoreCase(fk.getLookupSchemaName()) || (t != null && schemaName.equalsIgnoreCase(t.getPublicSchemaName()))))
                continue;

            // Get the lookupTableName: Attempt to use FK name first, then use the actual table name if it exists and is in the set of known tables.
            String lookupTableName = fk.getLookupTableName();
            if (!tables.containsKey(lookupTableName) && (t != null && tables.containsKey(t.getName())))
                lookupTableName = t.getName();

            // Skip self-referencing FKs
            if (schemaName.equalsIgnoreCase(fk.getLookupSchemaName()) && lookupTableName.equals(table.getName()))
                continue;

            // Continue depthFirstWalk if the lookup table is found in the schema (e.g. it exists in this schema and isn't a query)
            TableInfo lookupTable = tables.get(lookupTableName);
            if (lookupTable != null)
            {
                visitingPath.addLast(Tuple3.of(table, column, lookupTable));
                depthFirstWalk(schemaName, tables, lookupTable, visited, visitingPath, sorted);
                visitingPath.removeLast();
            }
        }

        sorted.add(table);
    }

    // Check if the TableInfo is found along the currently visiting path
    private static boolean hasLoop(LinkedList<Tuple3<TableInfo, ColumnInfo, TableInfo>> path, TableInfo table)
    {
        return path.stream().anyMatch(tuple -> table.equals(tuple.first));
    }

    private static String formatPath(LinkedList<Tuple3<TableInfo, ColumnInfo, TableInfo>> path)
    {
        StringBuilder sb = new StringBuilder();
        Iterator<Tuple3<TableInfo, ColumnInfo, TableInfo>> iter = path.listIterator();
        while (iter.hasNext())
        {
            Tuple3<TableInfo, ColumnInfo, TableInfo> tuple = iter.next();
            TableInfo table = tuple.first;
            ColumnInfo col = tuple.second;
            TableInfo lookupTable = tuple.third;
            //sb.append(String.format("%s.%s.%s -> %s.%s", table.getPublicSchemaName(), table.getPublicName(), col.getName(), lookupTable.getPublicSchemaName(), lookupTable.getPublicName()));
            sb.append(String.format("%s.%s -> %s", table.getPublicName(), col.getName(), lookupTable.getPublicName()));
            if (iter.hasNext())
                sb.append("\n");
        }
        return sb.toString();
    }

}
