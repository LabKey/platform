/*
 * Copyright (c) 2007-2009 LabKey Corporation
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

import org.labkey.api.exp.query.ExpSampleSetTable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.view.ActionURL;
import org.labkey.experiment.controllers.exp.ExperimentController;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.sql.Types;

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
                ColumnInfo columnInfo = wrapColumn(alias, _rootTable.getColumn("Container"));
                columnInfo.setFk(new ContainerForeignKey(new ActionURL(ExperimentController.ShowMaterialSourceAction.class, getContainer())));
                return columnInfo;
            case Created:
            case Modified:
            case Description:
            case LSID:
            case MaterialLSIDPrefix:
            case Name:
            case RowId:
                return wrapColumn(alias, _rootTable.getColumn(column.toString()));
            case Active:
            {
                SQLFragment sql = new SQLFragment("(CASE WHEN " + ExprColumn.STR_TABLE_ALIAS + ".LSID IN (SELECT MaterialSourceLSID FROM " +
                    ExperimentServiceImpl.get().getTinfoActiveMaterialSource() + " WHERE Container = ?) THEN ? ELSE ? END)");
                sql.add(_schema.getContainer());
                sql.add(Boolean.TRUE);
                sql.add(Boolean.FALSE);
                ExprColumn result = new ExprColumn(this, "Active", sql, Types.BOOLEAN);
                result.setFormatString("Yes;No");
                return result;
            }
            case SampleCount:
            {
                SQLFragment sql = new SQLFragment("(SELECT COUNT(*) FROM " +
                    ExperimentServiceImpl.get().getTinfoMaterial() +
                    " m WHERE m.CpasType = " + ExprColumn.STR_TABLE_ALIAS + ".LSID)");
                return new ExprColumn(this, "SampleCount", sql, Types.INTEGER);
            }
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
        addContainerColumn(ExpSampleSetTable.Column.Folder, new ActionURL(ExperimentController.ListMaterialSourcesAction.class, getContainer()));
        addColumn(ExpSampleSetTable.Column.Active);
        addColumn(ExpSampleSetTable.Column.SampleCount);

        setDetailsURL(new DetailsURL(new ActionURL(ExperimentController.ShowMaterialSourceAction.class, _schema.getContainer()),
                Collections.singletonMap("rowId", "RowId")));
    }
}
