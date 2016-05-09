/*
 * Copyright (c) 2010-2016 LabKey Corporation
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
package org.labkey.issue.experimental.query;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.LookupColumn;
import org.labkey.api.data.Parameter;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableExtension;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.UpdateableTableInfo;
import org.labkey.api.etl.DataIterator;
import org.labkey.api.etl.DataIteratorBuilder;
import org.labkey.api.etl.DataIteratorContext;
import org.labkey.api.etl.DataIteratorUtil;
import org.labkey.api.etl.LoggingDataIterator;
import org.labkey.api.etl.SimpleTranslator;
import org.labkey.api.etl.TableInsertDataIterator;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.issues.IssuesSchema;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.DuplicateKeyException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.PdLookupForeignKey;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.RowIdForeignKey;
import org.labkey.api.query.UserIdForeignKey;
import org.labkey.api.query.UserIdRenderer;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.util.StringExpression;
import org.labkey.api.view.ActionURL;
import org.labkey.issue.IssuesController;
import org.labkey.issue.experimental.actions.NewDetailsAction;
import org.labkey.issue.model.IssueListDef;
import org.labkey.issue.model.IssueManager;
import org.labkey.issue.query.IssuesQuerySchema;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by klum on 4/5/2016.
 */
public class IssuesTable extends FilteredTable<IssuesQuerySchema> implements UpdateableTableInfo
{
    private static final Logger LOG = Logger.getLogger(IssuesTable.class);

    private Set<Class<? extends Permission>> _allowablePermissions = new HashSet<>();
    private IssueListDef _issueDef;
    private TableExtension _extension;

    public IssuesTable(IssuesQuerySchema schema, IssueListDef issueDef)
    {
        super(IssuesSchema.getInstance().getTableInfoIssues(), schema);

        _issueDef = issueDef;

        _allowablePermissions.add(InsertPermission.class);
        _allowablePermissions.add(UpdatePermission.class);
        _allowablePermissions.add(ReadPermission.class);

        addAllColumns();
        setDefaultColumns();
    }

    private void addAllColumns()
    {
        UserSchema schema = getUserSchema();

        setDescription("Contains one row per registered issue");

        ActionURL base = IssuesController.issueURL(_userSchema.getContainer(), NewDetailsAction.class);
        DetailsURL detailsURL = new DetailsURL(base, Collections.singletonMap("issueId", "IssueId"));
        setDetailsURL(detailsURL);

        IssueManager.EntryTypeNames names = IssueManager.getEntryTypeNames(getContainer());
        ColumnInfo issueIdColumn = wrapColumn(_rootTable.getColumn("IssueId"));
        issueIdColumn.setFk(new RowIdForeignKey(issueIdColumn)
        {
            public ColumnInfo createLookupColumn(ColumnInfo parent, String displayField)
            {
                if (displayField == null)
                    return null;
                return super.createLookupColumn(parent, displayField);
            }
        });

        issueIdColumn.setKeyField(true);
        issueIdColumn.setLabel(names.singularName + " ID");
        issueIdColumn.setURL(detailsURL);
        addColumn(issueIdColumn);

        ColumnInfo folder = new AliasedColumn(this, "Folder", _rootTable.getColumn("container"));
        folder.setHidden(true);
        ContainerForeignKey.initColumn(folder, _userSchema);
        addColumn(folder);

//        addWrapColumn(_rootTable.getColumn("Title"));
        ColumnInfo assignedTo = wrapColumn("AssignedTo", _rootTable.getColumn("AssignedTo"));
        assignedTo.setFk(new UserIdForeignKey(getUserSchema()));
        assignedTo.setDisplayColumnFactory(UserIdRenderer.GuestAsBlank::new);
        addColumn(assignedTo);

//        addColumn(new AliasedColumn(this, "Priority", _rootTable.getColumn("Priority")));

        addWrapColumn(_rootTable.getColumn("Status"));

        ColumnInfo modifiedBy = wrapColumn(_rootTable.getColumn("ModifiedBy"));
        UserIdForeignKey.initColumn(modifiedBy);
        addColumn(modifiedBy);
        addWrapColumn(_rootTable.getColumn("Modified"));

        ColumnInfo createdBy = wrapColumn(_rootTable.getColumn("CreatedBy"));
        UserIdForeignKey.initColumn(createdBy);
        addColumn(createdBy);
        addWrapColumn(_rootTable.getColumn("Created"));

        ColumnInfo resolvedBy = wrapColumn(_rootTable.getColumn("ResolvedBy"));
        UserIdForeignKey.initColumn(resolvedBy);
        addColumn(resolvedBy);

        addWrapColumn(_rootTable.getColumn("Resolved"));
        //addColumn(new AliasedColumn(this, "Resolution", _rootTable.getColumn("Resolution")));

        ColumnInfo entityId = addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("EntityId")));
        entityId.setHidden(true);

        TableInfo defTable = _issueDef.createTable(getUserSchema().getUser());
        _extension = TableExtension.create(this, defTable, "entityId", "entityId", LookupColumn.JoinType.inner);

        // add the domain columns
        Collection<ColumnInfo> cols = new ArrayList<>(20);
        for (ColumnInfo col : defTable.getColumns())
        {
            // Skip the lookup column itself
            String colName = col.getName();
            if (colName.equalsIgnoreCase(_extension.getLookupColumnName()) || colName.equalsIgnoreCase("container"))
                continue;

            cols.add(_extension.addExtensionColumn(col, colName));
        }

        HashMap<String,DomainProperty> properties = new HashMap<>();
        for (DomainProperty dp : _issueDef.getDomain(getUserSchema().getUser()).getProperties())
            properties.put(dp.getPropertyURI(), dp);

        for (ColumnInfo col : cols)
        {
            String propertyURI = col.getPropertyURI();
            if (null != propertyURI)
            {
                DomainProperty dp = properties.get(propertyURI);
                PropertyDescriptor pd = (null==dp) ? null : dp.getPropertyDescriptor();

                if (null != dp && null != pd)
                {
                    if (pd.getLookupQuery() != null || pd.getConceptURI() != null)
                    {
                        col.setFk(new PdLookupForeignKey(schema.getUser(), pd, schema.getContainer()));
                    }

                    if (pd.getPropertyType() == PropertyType.MULTI_LINE)
                    {
                        col.setDisplayColumnFactory(colInfo -> {
                            DataColumn dc = new DataColumn(colInfo);
                            dc.setPreserveNewlines(true);
                            return dc;
                        });
                    }
                }
            }
        }
    }

    private void setDefaultColumns()
    {
        List<FieldKey> columns = new ArrayList<>();

        columns.add(FieldKey.fromParts("IssueId"));
        columns.add(FieldKey.fromParts("Type"));
        columns.add(FieldKey.fromParts("Area"));
        columns.add(FieldKey.fromParts("Title"));
        columns.add(FieldKey.fromParts("AssignedTo"));
        columns.add(FieldKey.fromParts("Priority"));
        columns.add(FieldKey.fromParts("Status"));
        columns.add(FieldKey.fromParts("Milestone"));

        setDefaultVisibleColumns(columns);
    }

    @Nullable
    @Override
    public QueryUpdateService getUpdateService()
    {
        return new IssuesUpdateService(this);
    }

    protected final boolean isAllowedPermission(Class<? extends Permission> perm)
    {
        return _allowablePermissions.contains(perm);
    }

    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        if (getUpdateService() != null)
        {
            return isAllowedPermission(perm) && _userSchema.getContainer().hasPermission(user, perm);
        }
        return false;
    }

    @Override
    public boolean insertSupported()
    {
        return true;
    }

    @Override
    public boolean updateSupported()
    {
        return true;
    }

    @Override
    public boolean deleteSupported()
    {
        return false;
    }

    @Override
    public TableInfo getSchemaTableInfo()
    {
        return ((FilteredTable)getRealTable()).getRealTable();
    }

    @Override
    public ObjectUriType getObjectUriType()
    {
        return ObjectUriType.schemaColumn;
    }

    @Nullable
    @Override
    public String getObjectURIColumnName()
    {
        return "EntityId";
    }

    @Nullable
    @Override
    public String getObjectIdColumnName()
    {
        return null;
    }

    @Nullable
    @Override
    public CaseInsensitiveHashMap<String> remapSchemaColumns()
    {
        return null;
    }

    @Nullable
    @Override
    public CaseInsensitiveHashSet skipProperties()
    {
        return null;
    }

    @Override
    public DataIteratorBuilder persistRows(DataIteratorBuilder data, DataIteratorContext context)
    {
        DataIteratorBuilder step0 = new IssuesDataIteratorBuilder(data, context, getUserSchema().getUser());
        return step0;
    }

    @Override
    public Parameter.ParameterMap insertStatement(Connection conn, User user) throws SQLException
    {
        return null;
    }

    @Override
    public Parameter.ParameterMap updateStatement(Connection conn, User user, Set<String> columns) throws SQLException
    {
        return null;
    }

    @Override
    public Parameter.ParameterMap deleteStatement(Connection conn) throws SQLException
    {
        return null;
    }

    private class IssuesUpdateService extends DefaultQueryUpdateService
    {
        public IssuesUpdateService(IssuesTable table)
        {
            super(table, table.getRealTable());
        }

        @Override
        public int importRows(User user, Container container, DataIteratorBuilder rows, BatchValidationException errors, @Nullable Map<Enum,Object> configParameters, Map<String, Object> extraScriptContext)
                throws SQLException
        {
            return _importRowsUsingETL(user, container, rows, null, getDataIteratorContext(errors, InsertOption.IMPORT, configParameters), extraScriptContext);
        }

        @Override
        public List<Map<String, Object>> insertRows(User user, Container container, List<Map<String, Object>> rows, BatchValidationException errors, @Nullable Map<Enum, Object> configParameters, Map<String, Object> extraScriptContext) throws DuplicateKeyException, QueryUpdateServiceException, SQLException
        {
            List<Map<String, Object>> results = super._insertRowsUsingETL(user, container, rows, getDataIteratorContext(errors, InsertOption.INSERT, configParameters), extraScriptContext);
            return results;
        }

        @Override
        protected Map<String, Object> _update(User user, Container c, Map<String, Object> row, Map<String, Object> oldRow, Object[] keys) throws SQLException, ValidationException
        {
            // entityId is not the pk but is needed to update the issuedefs provisioned table
            String entityId = (String)oldRow.get("entityid");
            if (entityId == null)
                throw new ValidationException("entityid required to update row");

            // update issues.issues
            Map<String, Object> ret = new CaseInsensitiveHashMap<>(super._update(user, c, row, oldRow, keys));

            // update provisioned table -- note that entityId isn't the PK so we need to use the filter to update the correct row instead
            keys = new Object[] {};
            TableInfo t = _issueDef.createTable(user);
            SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("EntityId"), entityId);
            ret.putAll(Table.update(user, t, row, keys, filter, Level.DEBUG));

            return ret;
        }
    }

    private class IssuesDataIteratorBuilder implements DataIteratorBuilder
    {
        private DataIteratorContext _context;
        private final DataIteratorBuilder _in;

        private User _user;

        IssuesDataIteratorBuilder(@NotNull DataIteratorBuilder in, DataIteratorContext context, User user)
        {
            _context = context;
            _in = in;
            _user = user;
        }

        @Override
        public DataIterator getDataIterator(DataIteratorContext context)
        {
            _context = context;
            DataIterator input = _in.getDataIterator(context);
            if (null == input)
                return null;

            final Container c = getContainer();
            final Map<String, Integer> colNameMap = DataIteratorUtil.createColumnNameMap(input);

            SimpleTranslator step0 = new SimpleTranslator(input, context);
            step0.selectAll();

            // Ensure we have a listDefId column and it is of the right value
            ColumnInfo issueDefCol = IssuesSchema.getInstance().getTableInfoIssues().getColumn("issueDefId");
            step0.addColumn(issueDefCol, new SimpleTranslator.ConstantColumn(_issueDef.getRowId()));

            // Insert into exp.data then the provisioned table
            DataIteratorBuilder step2 = TableInsertDataIterator.create(DataIteratorBuilder.wrap(step0), IssuesSchema.getInstance().getTableInfoIssues(), c, context);
            DataIteratorBuilder step3 = TableInsertDataIterator.create(step2, _issueDef.createTable(getUserSchema().getUser()), c, context);

            return LoggingDataIterator.wrap(step3.getDataIterator(context));
        }
    }
}
