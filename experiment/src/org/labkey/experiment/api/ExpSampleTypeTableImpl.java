/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.exp.query.ExpSampleTypeTable;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.view.ActionURL;
import org.labkey.experiment.controllers.exp.ExperimentController;

import java.util.Collections;

/**
 * User: jeckels
 * Date: Oct 17, 2007
 */
public class ExpSampleTypeTableImpl extends ExpTableImpl<ExpSampleTypeTable.Column> implements ExpSampleTypeTable
{
    public ExpSampleTypeTableImpl(String name, UserSchema schema, ContainerFilter cf)
    {
        super(name, ExperimentServiceImpl.get().getTinfoMaterialSource(), schema, new ExpSampleTypeImpl(new MaterialSource()), cf);
        addAllowablePermission(InsertPermission.class);
        addAllowablePermission(UpdatePermission.class);
    }

    @Override
    public MutableColumnInfo createColumn(String alias, Column column)
    {
        switch (column)
        {
            case Folder:
                var columnInfo = wrapColumn(alias, _rootTable.getColumn("Container"));
                ContainerForeignKey.initColumn(columnInfo, _userSchema, new ActionURL(ExperimentController.ShowMaterialSourceAction.class, getContainer()));
                return columnInfo;
            case Description:
            case LSID:
            case MaterialLSIDPrefix:
            case Name:
            case NameExpression:
            case RowId:
                return wrapColumn(alias, _rootTable.getColumn(column.toString()));
            case Created:
                return wrapColumn(alias, _rootTable.getColumn("Created"));
            case CreatedBy:
                return createUserColumn(alias, _rootTable.getColumn("CreatedBy"));
            case Modified:
                return wrapColumn(alias, _rootTable.getColumn("Modified"));
            case ModifiedBy:
                return createUserColumn(alias, _rootTable.getColumn("ModifiedBy"));
            case SampleCount:
            {
                SQLFragment sql = new SQLFragment("(SELECT COUNT(*) FROM " +
                    ExperimentServiceImpl.get().getTinfoMaterial() +
                    " m WHERE m.CpasType = " + ExprColumn.STR_TABLE_ALIAS + ".LSID)");
                ExprColumn sampleCountColumnInfo = new ExprColumn(this, "SampleCount", sql, JdbcType.INTEGER);
                sampleCountColumnInfo.setDescription("Contains the number of samples currently stored in this sample type");
                return sampleCountColumnInfo;
            }
            case Properties:
                return createPropertiesColumn(alias);
            default:
                throw new IllegalArgumentException("Unknown column " + column);
        }
    }

    @Override
    protected void populateColumns()
    {
        addColumn(ExpSampleTypeTable.Column.RowId).setHidden(true);
        addColumn(ExpSampleTypeTable.Column.Name);
        addColumn(ExpSampleTypeTable.Column.Description);
        addColumn(ExpSampleTypeTable.Column.NameExpression).setHidden(true);
        addColumn(ExpSampleTypeTable.Column.LSID).setHidden(true);
        addColumn(ExpSampleTypeTable.Column.MaterialLSIDPrefix).setHidden(true);
        addColumn(ExpSampleTypeTable.Column.Created);
        addColumn(ExpSampleTypeTable.Column.CreatedBy);
        addColumn(ExpSampleTypeTable.Column.Modified);
        addColumn(ExpSampleTypeTable.Column.ModifiedBy);
        addContainerColumn(ExpSampleTypeTable.Column.Folder, new ActionURL(ExperimentController.ListMaterialSourcesAction.class, getContainer()));
        addColumn(ExpSampleTypeTable.Column.SampleCount);
        addColumn(Column.Properties);

        DetailsURL detailsURL = new DetailsURL(new ActionURL(ExperimentController.ShowMaterialSourceAction.class, _userSchema.getContainer()),
                Collections.singletonMap("rowId", "RowId"));
        detailsURL.setContainerContext(_userSchema.getContainer());
        setDetailsURL(detailsURL);
        setImportURL(AbstractTableInfo.LINK_DISABLER);
    }

}
