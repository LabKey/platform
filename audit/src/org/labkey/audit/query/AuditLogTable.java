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

package org.labkey.audit.query;

import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.query.AuditDisplayColumnFactory;
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.data.*;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.query.*;

import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Oct 19, 2007
 */
public class AuditLogTable extends FilteredTable
{
    private String _viewFactoryName;

    public AuditLogTable(QuerySchema schema, TableInfo tInfo, String viewFactoryName)
    {
        super(tInfo);

        _viewFactoryName = viewFactoryName;
        
        ColumnInfo createdBy = wrapColumn("CreatedBy", getRealTable().getColumn("CreatedBy"));
        createdBy.setFk(new UserIdForeignKey());
        createdBy.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                return new UserIdRenderer.GuestAsBlank(colInfo);
            }
        });
        addColumn(createdBy);

        ColumnInfo impersonatedBy = wrapColumn("ImpersonatedBy", getRealTable().getColumn("ImpersonatedBy"));
        impersonatedBy.setFk(new UserIdForeignKey());
        impersonatedBy.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                return new UserIdRenderer.GuestAsBlank(colInfo);
            }
        });
        addColumn(impersonatedBy);

        addColumn(wrapColumn("RowId", getRealTable().getColumn("RowId")));
        addColumn(wrapColumn("Date", getRealTable().getColumn("Created")));
        addColumn(wrapColumn("EventType", getRealTable().getColumn("EventType")));
        addColumn(wrapColumn("Comment", getRealTable().getColumn("Comment")));

        addColumn(wrapColumn("Key1", getRealTable().getColumn("Key1")));
        addColumn(wrapColumn("Key2", getRealTable().getColumn("Key2")));
        addColumn(wrapColumn("Key3", getRealTable().getColumn("Key3")));
        addColumn(wrapColumn("IntKey1", getRealTable().getColumn("IntKey1")));
        addColumn(wrapColumn("IntKey2", getRealTable().getColumn("IntKey2")));
        addColumn(wrapColumn("IntKey3", getRealTable().getColumn("IntKey3")));

        addColumn(wrapColumn("EntityId", getRealTable().getColumn("EntityId")));
        addColumn(wrapColumn("ContainerId", getRealTable().getColumn("ContainerId")));
        addColumn(wrapColumn("ProjectId", getRealTable().getColumn("ProjectId")));
        addColumn(wrapColumn("Lsid", getRealTable().getColumn("Lsid")));

        List<FieldKey> visibleColumns = getDefaultColumns();

        // in addition to the hard table columns, join in any ontology table columns associated with this query view
        if (_viewFactoryName != null)
        {
            String sqlObjectId = "( SELECT objectid FROM exp.object WHERE exp.object.objecturi = " + ExprColumn.STR_TABLE_ALIAS + ".lsid)";
            try
            {
                String parentLsid = AuditLogService.get().getDomainURI(_viewFactoryName);
                SimpleFilter filter = new SimpleFilter();
                filter.addCondition("PropertyURI", parentLsid, CompareType.STARTS_WITH);
                PropertyDescriptor[] pds = Table.select(OntologyManager.getTinfoPropertyDescriptor(), Table.ALL_COLUMNS, filter, null, PropertyDescriptor.class);

                if (pds.length > 0)
                {
                    ColumnInfo colProperty = new ExprColumn(this, "property", new SQLFragment(sqlObjectId), Types.INTEGER);
                    Map<String, PropertyDescriptor> map = new TreeMap<String, PropertyDescriptor>();
                    for(PropertyDescriptor pd : pds)
                    {
                        if (pd.getPropertyType() == PropertyType.DOUBLE)
                            pd.setFormat("0.##");
                        map.put(pd.getName(), pd);
                        //visibleColumns.add(new FieldKey(keyProp, pd.getName()));
                    }
                    colProperty.setFk(new PropertyForeignKey(map, schema));
                    colProperty.setIsUnselectable(true);
                    addColumn(colProperty);
                }
            }
            catch (SQLException e)
            {
                throw new RuntimeSQLException(e);
            }
        }
        getColumn("RowId").setIsHidden(true);
        getColumn("Lsid").setIsHidden(true);

        ColumnInfo projectId = getColumn("ProjectId");
        projectId.setCaption("Project");
        ContainerForeignKey.initColumn(projectId);

        ColumnInfo containerId = getColumn("ContainerId");
        containerId.setCaption("Container");
        ContainerForeignKey.initColumn(containerId);

        setDefaultVisibleColumns(visibleColumns);

        // finalize any table customizations
        setupTable();
    }

    private List<FieldKey> getDefaultColumns()
    {
        List<FieldKey> columns = new ArrayList<FieldKey>();
        AuditLogService.AuditViewFactory factory = AuditLogService.get().getAuditViewFactory(_viewFactoryName);
        if (factory != null)
        {
            final List<FieldKey> cols = factory.getDefaultVisibleColumns();
            if (!cols.isEmpty())
                columns = cols;
        }

        if (columns.isEmpty())
        {
            columns.add(FieldKey.fromParts("CreatedBy"));
            columns.add(FieldKey.fromParts("ImpersonatedBy"));
            columns.add(FieldKey.fromParts("Date"));
            columns.add(FieldKey.fromParts("Comment"));
            columns.add(FieldKey.fromParts("EventType"));
            columns.add(FieldKey.fromParts("ContainerId"));
        }
        return columns;
    }

    private void setupTable()
    {
        AuditLogService.AuditViewFactory factory = AuditLogService.get().getAuditViewFactory(_viewFactoryName);
        if (factory != null)
        {
            factory.setupTable(this);
        }
    }

    public void setDisplayColumnFactory(String columnName, AuditDisplayColumnFactory factory)
    {
        ColumnInfo col = getColumn(columnName);
        if (col != null)
        {
            factory.init(col);
            col.setDisplayColumnFactory(factory);
        }
        else
            throw new IllegalStateException("Column does not exist: " + columnName);
    }
}
