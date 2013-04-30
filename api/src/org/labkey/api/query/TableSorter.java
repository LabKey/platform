package org.labkey.api.query;

import org.apache.log4j.Logger;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.data.MultiValuedForeignKey;
import org.labkey.api.data.TableInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
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
        Set<TableInfo> visited = new HashSet<TableInfo>(tables.size());
        List<TableInfo> sorted = new ArrayList<TableInfo>(tables.size());
        for (String tableName : startTables)
            depthFirstWalk(schemaName, tables, tables.get(tableName), visited, new LinkedList<TableInfo>(), sorted);

        return sorted;
    }

    private static void depthFirstWalk(String schemaName, Map<String, TableInfo> tables, TableInfo table, Set<TableInfo> visited, LinkedList<TableInfo> visiting, List<TableInfo> sorted)
    {
        // NOTE: loops exist in current schemas
        //   core.Containers has a self join to parent Container
        //   mothership.ServerSession.ServerInstallationId -> mothership.ServerInstallations.MostRecentSession -> mothership.ServerSession
        if (visiting.contains(table))
            throw new IllegalStateException("loop detected");

        if (visited.contains(table))
            return;

        visiting.addFirst(table);
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

            // Unforuntaely, we need to get the lookup table since some FKs don't expose .getLookupSchemaName() or .getLookupTableName()
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
                depthFirstWalk(schemaName, tables, lookupTable, visited, visiting, sorted);
        }

        sorted.add(table);
        visiting.removeFirst();
    }

}
