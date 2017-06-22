/*
 * Copyright (c) 2006-2017 LabKey Corporation
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

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.exp.PropertyColumn;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.property.ExperimentProperty;
import org.labkey.api.exp.query.ExpProtocolTable;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.RowIdForeignKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.view.ActionURL;
import org.labkey.experiment.controllers.exp.ExperimentController;

import java.util.Collections;

public class ExpProtocolTableImpl extends ExpTableImpl<ExpProtocolTable.Column> implements ExpProtocolTable
{
    public ExpProtocolTableImpl(String name, UserSchema schema)
    {
        super(name, ExperimentServiceImpl.get().getTinfoProtocol(), schema, new ExpProtocolImpl(new Protocol()));
        setTitleColumn("Name");
    }

    public ColumnInfo createColumn(String alias, Column column)
    {
        switch (column)
        {
            case RowId:
                return wrapColumn(alias, _rootTable.getColumn("RowId"));
            case Name:
                return wrapColumn(alias, _rootTable.getColumn("Name"));
            case Description:
                return wrapColumn(alias, _rootTable.getColumn("ProtocolDescription"));
            case LSID:
                return wrapColumn(alias, _rootTable.getColumn("LSID"));
            case Folder:
                return wrapColumn(alias, _rootTable.getColumn("Container"));
            case Created:
                return wrapColumn(alias, _rootTable.getColumn("Created"));
            case CreatedBy:
                return createUserColumn(alias, _rootTable.getColumn("CreatedBy"));
            case Modified:
                return wrapColumn(alias, _rootTable.getColumn("Modified"));
            case ModifiedBy:
                return createUserColumn(alias, _rootTable.getColumn("ModifiedBy"));
            case Instrument:
                return wrapColumn(alias, _rootTable.getColumn("Instrument"));
            case Software:
                return wrapColumn(alias, _rootTable.getColumn("Software"));
            case ApplicationType:
                return wrapColumn(alias, _rootTable.getColumn("ApplicationType"));
            case ProtocolImplementation:
            {
                PropertyDescriptor pd = ExperimentProperty.PROTOCOLIMPLEMENTATION.getPropertyDescriptor();
                PropertyColumn col = new PropertyColumn(pd, this, "lsid", getContainer(), getUserSchema().getUser(), true);
                col.setName(alias);
                col.setHidden(true);
                return col;
            }
        }
        throw new IllegalArgumentException("Unknown column " + column);
    }

    public void populate()
    {
        ColumnInfo colRowId = addColumn(Column.RowId);
        colRowId.setHidden(true);
        colRowId.setFk(new RowIdForeignKey(colRowId));
        colRowId.setKeyField(true);

        ColumnInfo colName = addColumn(Column.Name);
        setTitleColumn(colName.getName());

        ColumnInfo colLSID = addColumn(Column.LSID);
        colLSID.setHidden(true);

        addContainerColumn(Column.Folder, null);
        addColumn(Column.Created);
        addColumn(Column.CreatedBy);

        addColumn(Column.Modified).setHidden(true);
        addColumn(Column.ModifiedBy).setHidden(true);

        addColumn(Column.Description);
        addColumn(Column.Instrument);
        addColumn(Column.Software);
        addColumn(Column.ApplicationType);
        addColumn(Column.ProtocolImplementation);

        ActionURL urlDetails = new ActionURL(ExperimentController.ProtocolDetailsAction.class, _userSchema.getContainer());
        setDetailsURL(new DetailsURL(urlDetails, Collections.singletonMap("rowId", "RowId")));
    }
}
