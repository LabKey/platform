/*
 * Copyright (c) 2007-2014 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.query.AuditDisplayColumnFactory;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.PropertyForeignKey;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.UserIdForeignKey;
import org.labkey.api.query.UserIdRenderer;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * User: Karl Lum
 * Date: Oct 19, 2007
 */
@Deprecated
public class AuditLogTable extends FilteredTable<UserSchema>
{
    private AuditLogService.AuditViewFactory _viewFactory;

    public AuditLogTable(UserSchema schema, TableInfo tInfo, String viewFactoryName)
    {
        super(tInfo, schema);

        _viewFactory = AuditLogService.get().getAuditViewFactory(viewFactoryName);
        setInsertURL(LINK_DISABLER);

        ColumnInfo createdBy = wrapColumn("CreatedBy", getRealTable().getColumn("CreatedBy"));
        createdBy.setFk(new UserIdForeignKey(getUserSchema()));
        createdBy.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                return new UserIdRenderer.GuestAsBlank(colInfo);
            }
        });
        addColumn(createdBy);

        ColumnInfo impersonatedBy = wrapColumn("ImpersonatedBy", getRealTable().getColumn("ImpersonatedBy"));
        impersonatedBy.setFk(new UserIdForeignKey(getUserSchema()));
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
        if (_viewFactory != null)
        {
            //String sqlObjectId = "( SELECT objectid FROM exp.object WHERE exp.object.objecturi = " + ExprColumn.STR_TABLE_ALIAS + ".lsid)";
            String parentLsid = AuditLogService.get().getDomainURI(_viewFactory.getEventType());
            SimpleFilter filter = new SimpleFilter();
            filter.addCondition(FieldKey.fromParts("PropertyURI"), parentLsid, CompareType.STARTS_WITH);
            Collection<PropertyDescriptor> pds = new TableSelector(OntologyManager.getTinfoPropertyDescriptor(), filter, null).getCollection(PropertyDescriptor.class);

            if (!pds.isEmpty())
            {
                //ColumnInfo colProperty = new ExprColumn(this, "property", new SQLFragment(sqlObjectId), Types.INTEGER);
                ColumnInfo colProperty = wrapColumn("property", getRealTable().getColumn("Lsid"));
                Map<String, PropertyDescriptor> map = new TreeMap<>();
                for (PropertyDescriptor pd : pds)
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
        else
        {
            // issue: 14463, exclude dataset audit events from the general query
            SimpleFilter filter = new SimpleFilter();
            filter.addCondition(FieldKey.fromParts("EventType"), "DatasetAuditEvent", CompareType.NEQ);

            addCondition(filter);
        }

        if (isGuest(_userSchema.getUser()))
        {
            // issue: 14511 - return an empty result set for guests
            addCondition(new SQLFragment("1 = 0"));
        }

        getColumn("RowId").setHidden(true);
        getColumn("Lsid").setHidden(true);

        ColumnInfo projectId = getColumn("ProjectId");
        projectId.setLabel("Project");
        ContainerForeignKey.initColumn(projectId, schema);

        ColumnInfo containerId = getColumn("ContainerId");
        containerId.setLabel("Container");
        ContainerForeignKey.initColumn(containerId, schema);

        setDefaultVisibleColumns(visibleColumns);

        // finalize any table customizations
        setupTable();
    }

    protected void applyContainerFilter(ContainerFilter filter)
    {
        ColumnInfo containerColumn = _rootTable.getColumn("ContainerId");
        if (containerColumn != null && getContainer() != null)
        {
            clearConditions(containerColumn.getFieldKey());
            addCondition(new SimpleFilter(filter.createFilterClause(getSchema(), containerColumn.getFieldKey(), getContainer())));
        }
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        return new AuditLogUpdateService(this);
    }

    private boolean isGuest(UserPrincipal user)
    {
        return user instanceof User && user.isGuest();
    }

    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        // Don't allow deletes or updates for audit events, and don't let guests insert
        return ((perm.equals(InsertPermission.class) && !isGuest(user)) || perm.equals(ReadPermission.class)) &&
            getContainer().hasPermission(user, perm);
    }

    private List<FieldKey> getDefaultColumns()
    {
        List<FieldKey> columns = new ArrayList<>();
        if (_viewFactory != null)
        {
            final List<FieldKey> cols = _viewFactory.getDefaultVisibleColumns();
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
        if (_viewFactory != null)
        {
            _viewFactory.setupTable(this, _userSchema);
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

    @Override
    public String getDescription()
    {
        if (_viewFactory != null)
        {
            return StringUtils.defaultIfEmpty(_viewFactory.getDescription(), super.getDescription());
        }
        return super.getDescription();
    }
}
