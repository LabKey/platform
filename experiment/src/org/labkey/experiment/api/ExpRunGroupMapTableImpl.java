/*
 * Copyright (c) 2010 LabKey Corporation
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

import org.labkey.api.data.*;
import org.labkey.api.exp.query.ExpRunGroupMapTable;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.UserSchema;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ExpRunGroupMapTableImpl extends ExpTableImpl<ExpRunGroupMapTable.Column> implements ExpRunGroupMapTable
{
    public ExpRunGroupMapTableImpl(String name, UserSchema schema)
    {
        super(name, ExperimentServiceImpl.get().getTinfoRunList(), schema, null);
    }

    @Override
    public ColumnInfo createColumn(String alias, Column column)
    {
        switch (column)
        {
            case RunGroup:
                return wrapColumn(alias, _rootTable.getColumn("ExperimentId"));

            case Run:
                return wrapColumn(alias, _rootTable.getColumn("ExperimentRunId"));

            default:
                throw new IllegalArgumentException("Unknown column " + column);
        }
    }

    @Override
    public void populate()
    {
        ExpSchema schema = getExpSchema();
        addColumn(Column.RunGroup).setFk(schema.getRunGroupIdForeignKey());
        addColumn(Column.Run).setFk(schema.getRunIdForeignKey());

        List<FieldKey> defaultVisibleColumns = new ArrayList<FieldKey>();
        defaultVisibleColumns.add(FieldKey.fromParts(Column.RunGroup));
        defaultVisibleColumns.add(FieldKey.fromParts(Column.Run));
        setDefaultVisibleColumns(defaultVisibleColumns);
    }

    /**
     * Use the Run's Container to filter the RunList table.
     * @param filter
     */
    @Override
    protected void applyContainerFilter(ContainerFilter filter)
    {
        clearConditions("FolderFilter");

        Collection<String> ids = getContainerFilter().getIds(getContainer());
        if (ids != null)
        {
            SQLFragment sql = new SQLFragment();
            sql.append("(SELECT er.Container FROM ");
            sql.append(ExperimentServiceImpl.get().getTinfoExperimentRun()).append(" er ");
            sql.append("WHERE er.RowId = ExperimentRunId");

            sql.append(") IN (");

            String q = "?";
            for (String id : ids)
            {
                sql.append(q);
                sql.add(id);
                q = ", ?";
            }

            sql.append(")");

            addCondition(sql, "FolderFilter");
        }
    }
}
