/*
 * Copyright (c) 2013-2026 LabKey Corporation
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

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.data.MultiValuedForeignKey;
import org.labkey.api.data.TableInfo;
import org.labkey.api.util.Tuple3;
import org.labkey.api.util.logging.LogHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public final class TableSorter
{
    private static final Logger LOG = LogHelper.getLogger(TableSorter.class, "Warnings about foreign key traversal");

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

        return sort(schemaName, tables, false);
    }

    /**
     * Get a topologically sorted list of TableInfos within this schema.
     *
     * @throws IllegalStateException if a loop is detected.
     */
    public static List<TableInfo> sort(DbSchema schema)
    {
        return sort(schema, false);
    }

    /**
     * Get a topologically sorted list of TableInfos within this schema.
     *
     * @throws IllegalStateException if a loop is detected and tolerateLoops is false.
     */
    public static List<TableInfo> sort(DbSchema schema, boolean tolerateLoops)
    {
        String schemaName = schema.getName();
        Set<String> tableNames = new HashSet<>(schema.getTableNames());
        Map<String, TableInfo> tables = new CaseInsensitiveHashMap<>();
        for (String tableName : tableNames)
            tables.put(tableName, schema.getTable(tableName));

        return sort(schemaName, tables, tolerateLoops);
    }

    public static @Nullable ForeignKey getForeignKey(TableInfo table, ColumnInfo column, boolean logErrors)
    {
        // Skip calculated columns (e.g., ExprColumn)
        if (column.isCalculated())
            return null;

        ForeignKey fk = column.getFk();

        // Skip fake FKs that just wrap the RowId
        if (fk == null || fk instanceof RowIdForeignKey || fk instanceof MultiValuedForeignKey)
            return null;

        try
        {
            // Ensure attempting to resolve table doesn't throw. Note that TableInfo could be null.
            fk.getLookupTableInfo();
            return fk;
        }
        catch (QueryParseException qpe)
        {
            // ignore and try to continue
            if (logErrors)
            {
                String msg = String.format("Failed to resolve fk (%s, %s, %s) from (%s, %s)",
                        fk.getLookupSchemaName(), fk.getLookupTableName(), fk.getLookupColumnName(), table.getName(), column.getName());
                LOG.warn(msg, qpe);
            }
        }

        return null;
    }

    private static @Nullable String getLookupTableName(String schemaName, Map<String, TableInfo> tables, TableInfo table, ColumnInfo column, boolean logErrors)
    {
        ForeignKey fk = getForeignKey(table, column, logErrors);
        if (null == fk)
            return null;

        String lookupSchemaName = fk.getLookupSchemaName();
        TableInfo t = fk.getLookupTableInfo();

        // Skip lookups to other schemas
        if (!(schemaName.equalsIgnoreCase(lookupSchemaName) || (t != null && schemaName.equalsIgnoreCase(t.getPublicSchemaName()))))
            return null;

        // Get the lookupTableName: Attempt to use FK name first, then use the actual table name if it exists and is in the set of known tables.
        String lookupTableName = fk.getLookupTableName();
        if (!tables.containsKey(lookupTableName) && (t != null && tables.containsKey(t.getName())))
            lookupTableName = t.getName();

        // Skip self-referencing FKs
        if (schemaName.equalsIgnoreCase(lookupSchemaName) && lookupTableName.equalsIgnoreCase(table.getName()))
            return null;

        return lookupTableName;
    }

    private static List<TableInfo> sort(String schemaName, Map<String, TableInfo> tables, boolean tolerateLoops)
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
                String lookupTableName = getLookupTableName(schemaName, tables, table, column, true);
                if (lookupTableName != null)
                {
                    // Remove the lookup table from the set of tables with no incoming FK
                    startTables.remove(lookupTableName);
                }
            }
        }

        if (startTables.isEmpty())
        {
            String msg = "No tables without incoming FKs found in schema '" + schemaName + "'";
            if (anyHaveContainerColumn(tables.values().stream()))
                throw new IllegalStateException(msg);

            LOG.warn(msg);
            return Collections.emptyList();
        }

        // Depth-first topological sort of the tables starting with the startTables
        Set<TableInfo> visited = new HashSet<>(tables.size());
        List<TableInfo> sorted = new ArrayList<>(tables.size());
        for (String tableName : startTables)
            depthFirstWalk(schemaName, tables, tables.get(tableName), visited, new LinkedList<>(), sorted, tolerateLoops);

        return sorted;
    }

    private static void depthFirstWalk(String schemaName, Map<String, TableInfo> tables, TableInfo table, Set<TableInfo> visited, LinkedList<Tuple3<TableInfo, ColumnInfo, TableInfo>> visitingPath, List<TableInfo> sorted, boolean tolerateLoops)
    {
        if (hasLoop(visitingPath, table))
        {
            String msg = "Loop detected in schema '" + schemaName + "':\n" + formatPath(visitingPath);
            if (!tolerateLoops && anyHaveContainerColumn(visitingPath))
                throw new IllegalStateException(msg);

            LOG.warn(msg);

            if (!tolerateLoops)
                return;
        }

        if (visited.contains(table))
            return;

        visited.add(table);

        for (ColumnInfo column : table.getColumns())
        {
            // We've already reported errors
            String lookupTableName = getLookupTableName(schemaName, tables, table, column, false);
            if (lookupTableName != null)
            {
                // Continue depthFirstWalk if the lookup table is found in the schema (e.g. it exists in this schema and isn't a query)
                TableInfo lookupTable = tables.get(lookupTableName);
                if (lookupTable != null)
                {
                    visitingPath.addLast(Tuple3.of(table, column, lookupTable));
                    depthFirstWalk(schemaName, tables, lookupTable, visited, visitingPath, sorted, tolerateLoops);
                    visitingPath.removeLast();
                }
            }
        }

        sorted.add(table);
    }

    // Check if the TableInfo is found along the currently visiting path
    private static boolean hasLoop(LinkedList<Tuple3<TableInfo, ColumnInfo, TableInfo>> path, TableInfo table)
    {
        return path.stream().anyMatch(tuple -> table.equals(tuple.first));
    }

    // returns true if any table has a container column
    private static boolean anyHaveContainerColumn(LinkedList<Tuple3<TableInfo, ColumnInfo, TableInfo>> path)
    {
        return anyHaveContainerColumn(path.stream().map(Tuple3::getKey));
    }

    // returns true if any table has a container column
    private static boolean anyHaveContainerColumn(Stream<TableInfo> tables)
    {
        return tables.anyMatch(TableSorter::hasContainerColumn);
    }

    private static boolean hasContainerColumn(TableInfo table)
    {
        return table.getColumns().stream().anyMatch(TableSorter::isContainerColumn);
    }

    private static boolean isContainerColumn(ColumnInfo c)
    {
        return "folder".equalsIgnoreCase(c.getName()) || "container".equalsIgnoreCase(c.getName());
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
            sb.append(String.format("%s.%s -> %s", table.getName(), col.getName(), lookupTable.getName()));
            if (iter.hasNext())
                sb.append("\n");
        }

        return sb.toString();
    }
}
