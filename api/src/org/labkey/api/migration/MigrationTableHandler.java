package org.labkey.api.migration;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.util.GUID;

import java.util.Set;

/**
 * Rarely needed, this interface lets a module filter the rows of another module's table. The specific use case: LabBook
 * needs to filter the compliance.SignedSnapshots table of snapshots associated with Notebooks that are excluded by a
 * NotebookFilter.
 */
public interface MigrationTableHandler
{
    TableInfo getTableInfo();
    // This method is invoked during cloning migration
    default void adjustFilter(TableInfo sourceTable, SimpleFilter filter, Set<GUID> containers){}
    // This method is invoked during SQL Server migration primarily to map GUID values to lowercase
    default ColumnInfo handleColumn(ColumnInfo sourceColumn)
    {
        return sourceColumn;
    }
}
