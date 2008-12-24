/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

package org.labkey.experiment.api;

import org.labkey.api.exp.api.ExpSampleSetTable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.FieldKey;

import java.util.List;
import java.util.ArrayList;

/**
 * User: jeckels
 * Date: Oct 17, 2007
 */
public class ExpSampleSetTableImpl extends ExpTableImpl<ExpSampleSetTable.Column> implements ExpSampleSetTable
{
    public ExpSampleSetTableImpl(String name, String alias, UserSchema schema)
    {
        super(name, alias, ExperimentServiceImpl.get().getTinfoMaterialSource(), schema);
    }

    public ColumnInfo createColumn(String alias, Column column)
    {
        switch (column)
        {
            case Folder:
                return wrapColumn(alias, _rootTable.getColumn("Container"));
            case Created:
            case Modified:
            case Description:
            case LSID:
            case MaterialLSIDPrefix:
            case Name:
            case RowId:
                return wrapColumn(alias, _rootTable.getColumn(column.toString()));
            default:
                throw new IllegalArgumentException("Unknown column " + column);
        }
    }

    public void populate()
    {
        addColumn(ExpSampleSetTable.Column.RowId).setIsHidden(true);
        addColumn(ExpSampleSetTable.Column.Name);
        addColumn(ExpSampleSetTable.Column.Description);
        addColumn(ExpSampleSetTable.Column.LSID).setIsHidden(true);
        addColumn(ExpSampleSetTable.Column.MaterialLSIDPrefix).setIsHidden(true);
        addColumn(ExpSampleSetTable.Column.Created);
        addColumn(ExpSampleSetTable.Column.Folder);

        List<FieldKey> defaultVisibleColumns = new ArrayList<FieldKey>(getDefaultVisibleColumns());
        defaultVisibleColumns.remove(FieldKey.fromParts(ExpSampleSetTable.Column.Folder));
        setDefaultVisibleColumns(defaultVisibleColumns);

    }
}
