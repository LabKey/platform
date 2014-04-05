package org.labkey.api.data;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.QueryForeignKey;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * User: kevink
 * Date: 4/4/14
 *
 * Convenience class to add columns from the extension table to the primary table.
 */
public final class TableExtension
{
    private final AbstractTableInfo _primaryTable;
    private final TableInfo _extensionTable;
    private final ColumnInfo _extensionCol;
    private final QueryForeignKey _extensionFK;

    protected TableExtension(AbstractTableInfo primaryTable, TableInfo extensionTable, ColumnInfo extensionCol, QueryForeignKey extensionFK)
    {
        _primaryTable = primaryTable;
        _extensionTable = extensionTable;
        _extensionCol = extensionCol;
        _extensionFK = extensionFK;
    }

    public static TableExtension create(AbstractTableInfo primaryTable, TableInfo extensionTable, String foreignKey, String lookupKey)
    {
        ColumnInfo extensionCol = primaryTable.getColumn(foreignKey);
        assert extensionCol != null;

        QueryForeignKey extensionFK = new QueryForeignKey(extensionTable, null, lookupKey, null);

        return new TableExtension(primaryTable, extensionTable, extensionCol, extensionFK);
    }

    public TableInfo getExtensionTable()
    {
        return _extensionTable;
    }

    public Collection<ColumnInfo> addAllColumns()
    {
        String lookupKey = _extensionFK.getLookupColumnName();
        List<ColumnInfo> baseColumns = _extensionTable.getColumns();
        Collection<ColumnInfo> columns = new ArrayList<>(baseColumns.size());
        for (ColumnInfo col : baseColumns)
        {
            // Skip the lookup column itself
            if (col.getName().equalsIgnoreCase(lookupKey))
                continue;

            ColumnInfo lookupCol = addExtensionColumn(col, col.getName());
            columns.add(lookupCol);
        }

        return columns;
    }

    public ColumnInfo addExtensionColumn(String baseColName, @Nullable String newColName)
    {
        ColumnInfo baseCol = _extensionTable.getColumn(baseColName);
        return addExtensionColumn(baseCol, newColName);
    }

    public ColumnInfo addExtensionColumn(ColumnInfo baseCol, @Nullable String newColName)
    {
        newColName = Objects.toString(newColName, baseCol.getName());

        ColumnInfo lookupCol = _extensionFK.createLookupColumn(_extensionCol, baseCol.getName());
        AliasedColumn aliased = new AliasedColumn(_primaryTable, newColName, lookupCol);
        if (lookupCol.isHidden())
            aliased.setHidden(true);

        return _primaryTable.addColumn(aliased);
    }

}
