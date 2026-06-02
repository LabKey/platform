/*
 * Copyright (c) 2025-2026 LabKey Corporation
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
package org.labkey.api.migration;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.util.GUID;

import java.util.Set;

/**
 * <p>In the cloning migration case, lets a module filter the rows of another module's table. The specific use case:
 * LabBook needs to filter the compliance.SignedSnapshots table, removing snapshots associated with Notebooks that are
 * excluded by a NotebookFilter.
 * </p>
 * <p>
 * In the SQL Server migration case, lets a module replace specific select columns. This is primarily used to translate
 * GUID values residing in non-GUID columns to lowercase.
 * </p>
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
