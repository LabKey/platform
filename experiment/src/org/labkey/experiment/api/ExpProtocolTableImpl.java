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

import org.apache.commons.beanutils.ConvertUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.PropertyColumn;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.property.ExperimentProperty;
import org.labkey.api.exp.query.ExpProtocolTable;
import org.labkey.api.query.AbstractQueryUpdateService;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.DuplicateKeyException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.RowIdForeignKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.experiment.controllers.exp.ExperimentController;

import java.sql.SQLException;
import java.util.Collections;
import java.util.Map;

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

    @Override
    public @Nullable QueryUpdateService getUpdateService()
    {
        return new ExpProtocolUpdateService(this);
    }

    private static class ExpProtocolUpdateService extends AbstractQueryUpdateService
    {
        protected ExpProtocolUpdateService(ExpProtocolTableImpl queryTable)
        {
            super(queryTable);
        }

        @Override
        protected ExpProtocolTableImpl getQueryTable()
        {
            return (ExpProtocolTableImpl)super.getQueryTable();
        }

        private Integer getRowId(Map<String, Object> row)
        {
            Object rowIdRaw = row.get(Column.RowId.toString());
            if (rowIdRaw != null)
            {
                Integer rowId = (Integer) ConvertUtils.convert(rowIdRaw.toString(), Integer.class);
                if (rowId != null)
                    return rowId;
            }
            return null;
        }

        private String getLsid(Map<String, Object> row)
        {
            Object lsidRaw = row.get(Column.LSID.toString());
            if (lsidRaw != null)
            {
                String lsid = lsidRaw.toString().trim();
                if (!lsid.isEmpty())
                    return lsid;
            }
            return null;
        }

        private ExpProtocol getProtocol(Map<String, Object> row)
        {
            Integer rowId = getRowId(row);
            if (rowId != null)
                return ExperimentServiceImpl.get().getExpProtocol(rowId.intValue());

            String lsid = getLsid(row);
            if (lsid != null)
                return ExperimentServiceImpl.get().getExpProtocol(lsid);

            return null;
        }

        @Override
        protected Map<String, Object> getRow(User user, Container container, Map<String, Object> keys) throws InvalidKeyException, QueryUpdateServiceException, SQLException
        {
            SimpleFilter filter = SimpleFilter.createContainerFilter(container);

            Integer rowId = getRowId(keys);
            if (rowId != null)
            {
                filter.addCondition(FieldKey.fromParts(Column.RowId.name()), rowId);
                return new TableSelector(getQueryTable(), filter, null).getMap();
            }

            String lsid = getLsid(keys);
            if (lsid != null)
            {
                filter.addCondition(FieldKey.fromParts(Column.LSID.name()), lsid);
                return new TableSelector(getQueryTable(), filter, null).getMap();
            }

            return null;
        }

        @Override
        protected Map<String, Object> insertRow(User user, Container container, Map<String, Object> row) throws DuplicateKeyException, ValidationException, QueryUpdateServiceException, SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        protected Map<String, Object> updateRow(User user, Container container, Map<String, Object> row, @NotNull Map<String, Object> oldRow) throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        protected Map<String, Object> deleteRow(User user, Container container, Map<String, Object> oldRow) throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
        {
            ExpProtocol protocol = getProtocol(oldRow);
            if (protocol == null)
                throw new UnauthorizedException("The protocol was not found");

            if (!protocol.getContainer().equals(container))
                throw new UnauthorizedException("Can't delete protocol from different container");

            protocol.delete(user);
            return oldRow;
        }
    }
}
