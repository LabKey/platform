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

package org.labkey.query.data;

import org.labkey.api.data.*;
import org.labkey.api.query.*;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;
import org.labkey.data.xml.TableType;
import org.labkey.query.controllers.dbuserschema.DbUserSchemaController;
import org.labkey.query.view.DbUserSchema;
import org.apache.commons.beanutils.ConvertUtils;

import java.util.*;

public class DbUserSchemaTable extends FilteredTable
{
    DbUserSchema _schema;
    TableType _metadata;
    SchemaTableInfo _schemaTableInfo;
    String _containerId;
    public DbUserSchemaTable(DbUserSchema schema, SchemaTableInfo table, TableType metadata)
    {
        super(table);
        _schema = schema;
        _metadata = metadata;
        wrapAllColumns(true);
        resetFks();
        if (metadata != null)
        {
            loadFromXML(schema, metadata, null);
        }
    }

    protected void resetFks()
    {
        for(ColumnInfo col : getColumns())
        {
            //if the column has an fk AND if the fk table comes from the same schema
            //reset the fk so that it creates the table through the schema, thus returning
            //a DbUserSchemaTable instead of a SchemaTableInfo. The former is public via Query
            //while the latter is not.
            if(null != col.getFk() && _schema.getDbSchema().getName().equalsIgnoreCase(col.getFkTableInfo().getSchema().getName()))
            {
                final String fkTableName = col.getFkTableInfo().getName();

                //FIX: 5661
                //get the column name in the target FK table that it would have joined against
                //the existing fks should be of type SchemaForeignKey, so try to downcast to that
                //so that we can get the declared lookup column
                String pkColName = col.getFk().getLookupColumnName();
                if(null == pkColName && col.getFkTableInfo().getPkColumnNames().size() == 1)
                    pkColName = col.getFkTableInfo().getPkColumnNames().get(0);

                if(null != pkColName)
                {
                    col.setFk(new LookupForeignKey(pkColName)
                    {
                        public TableInfo getLookupTableInfo()
                        {
                            return _schema.getTable(fkTableName);
                        }
                    });
                } //if we know the pk col name
            } //if has FK and FK table from same schema

            //if the column is named "CreatedBy" or "ModifiedBy", the Table layer automagically
            //fills these out with user ids, so set their fks to use a UserIdForeignKey
            if("CreatedBy".equalsIgnoreCase(col.getName()) || "ModifiedBy".equalsIgnoreCase(col.getName()))
            {
                if(null == col.getFk())
                    col.setFk(new UserIdQueryForeignKey(_schema.getUser(), _schema.getContainer()));
            }

        } //for each column
    }

    public boolean hasPermission(User user, int perm)
    {
        if (!_schema.areTablesEditable())
            return false;
        List<ColumnInfo> columns = getPkColumns();
        // Consider: allow multi-column keys
        if (columns.size() != 1)
        {
            return false;
        }
        return _schema.getContainer().hasPermission(user, perm);
    }

    public void setContainer(String containerId)
    {
        if (containerId == null)
            return;
        ColumnInfo colContainer = getRealTable().getColumn("container");
        if (colContainer != null)
        {
            getColumn("container").setReadOnly(true);
            addCondition(colContainer, containerId);
            _containerId = containerId;
        }
    }

    public String getContainerId()
    {
        return _containerId;
    }

    public ActionURL delete(User user, ActionURL srcURL, QueryUpdateForm form) throws Exception
    {
        Set<String> ids = DataRegionSelection.getSelected(form.getViewContext(), true);
        List<ColumnInfo> pk = getPkColumns();
        if (pk.size() != 1)
            throw new IllegalStateException("Primary key must have 1 column in it, found: " + pk.size());
        ColumnInfo pkColumn = pk.get(0);
        Collection pkValues = ids;
        if (pkColumn.getJavaClass() != String.class)
        {
            pkValues = new LinkedList();
            for (String id : ids)
            {
                Object value = ConvertUtils.convert(id, pkColumn.getJavaClass());
                pkValues.add(value);
            }
        }

        SimpleFilter filter = new SimpleFilter();
        if (_containerId != null)
        {
            filter.addCondition("container", _containerId);
        }
        filter.addInClause(pk.get(0).getName(), pkValues);
        Table.delete(getRealTable(), filter);
        return srcURL;
    }

    public ActionURL urlFor(DbUserSchemaController.Action action)
    {
        ActionURL url = _schema.getContainer().urlFor(action);
        url.addParameter(QueryParam.schemaName.toString(), _schema.getSchemaName());
        url.addParameter(QueryParam.queryName.toString(), getName());
        return url;
    }

}
