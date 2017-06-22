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

package org.labkey.issue.query;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.collections.NamedObject;
import org.labkey.api.collections.NamedObjectList;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.data.MultiValuedDisplayColumn;
import org.labkey.api.data.MultiValuedForeignKey;
import org.labkey.api.data.MultiValuedLookupColumn;
import org.labkey.api.data.Parameter;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableExtension;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.UpdateableTableInfo;
import org.labkey.api.defaults.DefaultValueService;
import org.labkey.api.dataiterator.DataIterator;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.dataiterator.DataIteratorUtil;
import org.labkey.api.dataiterator.LoggingDataIterator;
import org.labkey.api.dataiterator.SimpleTranslator;
import org.labkey.api.dataiterator.TableInsertDataIterator;
import org.labkey.api.dataiterator.WrapperDataIterator;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.issues.AbstractIssuesListDefDomainKind;
import org.labkey.api.issues.IssuesSchema;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.DuplicateKeyException;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.PdLookupForeignKey;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.RowIdForeignKey;
import org.labkey.api.query.UserIdForeignKey;
import org.labkey.api.query.UserIdRenderer;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.util.ContainerContext;
import org.labkey.api.util.SimpleNamedObject;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.view.ActionURL;
import org.labkey.issue.IssuesController;
import org.labkey.issue.model.Issue;
import org.labkey.issue.model.IssueListDef;
import org.labkey.issue.model.IssueManager;
import org.labkey.issue.model.IssuePage;

import java.io.IOException;
import java.io.Writer;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class IssuesTable extends FilteredTable<IssuesQuerySchema> implements UpdateableTableInfo
{
    private static final Logger LOG = Logger.getLogger(IssuesTable.class);

    private Set<Class<? extends Permission>> _allowablePermissions = new HashSet<>();
    private IssueListDef _issueDef;
    private TableExtension _extension;
    private List<FieldKey> _extraDefaultColumns = new ArrayList<>();

    public IssuesTable(IssuesQuerySchema schema, IssueListDef issueDef)
    {
        super(IssuesSchema.getInstance().getTableInfoIssues(), schema);
        setName(issueDef.getName());
        setTitle(issueDef.getLabel());
        setTitleColumn("Title");

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
        Set<String> baseProps = new CaseInsensitiveHashSet();
        Map<String, String> colNameMap = new HashMap<>();
        for (String colName : getDomainKind().getReservedPropertyNames(getDomain()))
        {
            colNameMap.put(colName.toLowerCase(), colName);
        }
        baseProps.addAll(getDomainKind().getReservedPropertyNames(getDomain()));

        setDescription("Contains a row for each issue created in this folder.");

        ActionURL base = IssuesController.issueURL(_userSchema.getContainer(), IssuesController.DetailsAction.class);
        DetailsURL detailsURL = new DetailsURL(base, Collections.singletonMap("issueId", "IssueId"));
        setDetailsURL(detailsURL);

        IssueManager.EntryTypeNames names = IssueManager.getEntryTypeNames(getContainer(), _issueDef.getName());
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
        // When no sorts are added by views, QueryServiceImpl.createDefaultSort() adds the primary key's default sort direction
        issueIdColumn.setSortDirection(Sort.SortDirection.DESC);
        addColumn(issueIdColumn);

        ColumnInfo folder = new AliasedColumn(this, "Folder", _rootTable.getColumn("container"));
        folder.setHidden(true);
        ContainerForeignKey.initColumn(folder, _userSchema);
        addColumn(folder);

        ColumnInfo related = addColumn(new AliasedColumn(this, "Related", issueIdColumn));
        related.setKeyField(false);

        DetailsURL relatedURL = new DetailsURL(base, Collections.singletonMap("issueId", FieldKey.fromParts("Related", "IssueId")));
        relatedURL.setContainerContext(new ContainerContext.FieldKeyContext(FieldKey.fromParts("Related", "Folder")));
        related.setURL(relatedURL);

        QueryForeignKey qfk = new QueryForeignKey(getUserSchema(), getContainer(), "RelatedIssues", "IssueId", null)
        {
            @Override
            public TableInfo getLookupTableInfo()
            {
                TableInfo table = super.getLookupTableInfo();

                ColumnInfo lookupColumn = table.getColumn(getLookupColumnName());
                lookupColumn.setFk(new QueryForeignKey(getUserSchema(), getLookupContainer(), IssuesQuerySchema.ALL_ISSUE_TABLE, "issueid", null));
                ColumnInfo junctionColumn = table.getColumn("RelatedIssueId");
                junctionColumn.setFk(new QueryForeignKey(getUserSchema(), getLookupContainer(), IssuesQuerySchema.ALL_ISSUE_TABLE, "issueid", null));

                return table;
            }
        };

        related.setFk(new MultiValuedForeignKey(qfk, "RelatedIssueId", "IssueId")
        {
            @Override
            protected MultiValuedLookupColumn createMultiValuedLookupColumn(ColumnInfo relatedIssueId, ColumnInfo parent, ColumnInfo childKey, ColumnInfo junctionKey, ForeignKey fk)
            {
                relatedIssueId.setDisplayColumnFactory(new URLTitleDisplayColumnFactory("Issue ${Related/IssueId}: ${Related/Title:htmlEncode}"));

                return super.createMultiValuedLookupColumn(relatedIssueId, parent, childKey, junctionKey, fk);
            }
        });

        related.setDisplayColumnFactory(colInfo -> {
            DataColumn dataColumn = new DataColumn(colInfo) {
                @Override
                public boolean isSortable()
                {
                    return false;
                }

                @Override
                public boolean isFilterable()
                {
                    return false;
                }
            };
            dataColumn.setURLTitle(new StringExpressionFactory.FieldKeyStringExpression("Issue ${Related/IssueId}: ${Related/Title:htmlEncode}", false, StringExpressionFactory.AbstractStringExpression.NullValueBehavior.NullResult));

            return new MultiValuedDisplayColumn(dataColumn, true);
        });

        ColumnInfo entityId = addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("EntityId")));
        entityId.setHidden(true);

        ColumnInfo issueDefId = addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("IssueDefId")));
        issueDefId.setHidden(true);

        ColumnInfo duplicateCol = addWrapColumn(_rootTable.getColumn("Duplicate"));
        duplicateCol.setURL(new DetailsURL(base, Collections.singletonMap("issueId", "Duplicate")));
        duplicateCol.setDisplayColumnFactory(new URLTitleDisplayColumnFactory("Issue ${Duplicate}: ${Duplicate/Title:htmlEncode}"));
        duplicateCol.setFk(new QueryForeignKey(IssuesSchema.getInstance().getTableInfoIssues(), getContainer(), "IssueId", "IssueId"));

        TableInfo defTable = _issueDef.createTable(getUserSchema().getUser());

        HashMap<String,DomainProperty> properties = new HashMap<>();
        for (DomainProperty dp : _issueDef.getDomain(getUserSchema().getUser()).getProperties())
            properties.put(dp.getPropertyURI(), dp);

        // add the domain columns
        Collection<ColumnInfo> cols = new ArrayList<>(20);
        for (ColumnInfo col : defTable.getColumns())
        {
            // omit protected columns
            if (properties.containsKey(col.getPropertyURI()))
            {
                if (!IssuePage.shouldDisplay(properties.get(col.getPropertyURI()), _userSchema.getContainer(), _userSchema.getUser()))
                    continue;
            }
            // Skip the lookup column itself
            String colName = col.getName();
            if (colNameMap.containsKey(colName))
                colName = colNameMap.get(colName);

            if (colName.equalsIgnoreCase("entityId") || ignoreColumn(colName))
                continue;

            ColumnInfo extensionCol = new ExprColumn(this, colName, col.getValueSql(ExprColumn.STR_TABLE_ALIAS), col.getJdbcType());
            addColumn(extensionCol);
            extensionCol.copyAttributesFrom(col);

            if (col.isHidden())
                extensionCol.setHidden(true);

            if (isUserId(colName))
            {
                UserIdForeignKey.initColumn(extensionCol);
            }
            else if (colName.equalsIgnoreCase("AssignedTo"))
            {
                IssuesTable.AssignedToForeignKey.initColumn(extensionCol);
            }
            else if (colName.equalsIgnoreCase("NotifyList"))
            {
                extensionCol.setDisplayColumnFactory(colInfo -> new NotifyListDisplayColumn(colInfo, getUserSchema().getUser()));
            }
            cols.add(extensionCol);

            if (!baseProps.contains(colName))
            {
                _extraDefaultColumns.add(FieldKey.fromParts(colName));
            }
        }

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
                        col.setFk(new IssuesPdLookupForeignKey(schema.getUser(), pd, schema.getContainer()));
                        TableInfo target = col.getFk().getLookupTableInfo();
                        if (null != target && target.getPkColumnNames().size() == 1 && StringUtils.equalsIgnoreCase(target.getTitleColumn(),target.getPkColumnNames().get(0)))
                        {
                            col.setDisplayColumnFactory(ColumnInfo.NOLOOKUP_FACTORY);
                        }
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

        getDomainKind().addAdditionalQueryColumns(this);
    }

    private boolean ignoreColumn(String colName)
    {
        return colName.equalsIgnoreCase("container");
    }

    private boolean isUserId(String colName)
    {
        return colName.equalsIgnoreCase("CreatedBy") ||
                colName.equalsIgnoreCase("ModifiedBy") ||
                colName.equalsIgnoreCase("ClosedBy") ||
                colName.equalsIgnoreCase("ResolvedBy");
    }

    private void setDefaultColumns()
    {
        Set<FieldKey> columns = new LinkedHashSet<>();
        // match the domain kind default columns with the columns in the actual domain instance
        Set<FieldKey> existingColumns = getColumns()
                .stream()
                .map(ColumnInfo::getFieldKey)
                .collect(Collectors.toSet());
        columns.addAll(getDomainKind().getDefaultColumnNames()
                .stream()
                .filter(existingColumns::contains)
                .collect(Collectors.toList()));
        columns.addAll(_extraDefaultColumns);
        setDefaultVisibleColumns(columns);
    }

    @NotNull
    @Override
    public SQLFragment getFromSQL(String alias)
    {
        TableInfo provisioned = _issueDef.createTable(getUserSchema().getUser());

        SQLFragment sql = new SQLFragment();
        sql.append("(SELECT * FROM\n");
        sql.append("(SELECT i.issueid, i.duplicate, i.lastIndexed, i.issueDefId, p.* FROM ");
        sql.append(_rootTable, "i");
        sql.append(" INNER JOIN ").append(provisioned, "p").append(" ON i.entityId = p.entityId");
        sql.append(") x");

        // WHERE
        Map<FieldKey, ColumnInfo> columnMap = Table.createColumnMap(getFromTable(), getFromTable().getColumns());
        SQLFragment filterFrag = getFilter().getSQLFragment(_rootTable.getSqlDialect(), columnMap);
        sql.append("\n").append(filterFrag).append(") ").append(alias);

        return sql;
    }

    @Nullable
    @Override
    public Domain getDomain()
    {
        return _issueDef.getDomain(getUserSchema().getUser());
    }

    @Nullable
    @Override
    public AbstractIssuesListDefDomainKind getDomainKind()
    {
        return (AbstractIssuesListDefDomainKind)super.getDomainKind();
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
        return new IssuesTable.DefaultValuesIteratorBuilder(step0, context, getUserSchema().getUser());
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
        protected int truncateRows(User user, Container container) throws QueryUpdateServiceException, SQLException
        {
            return IssueManager.truncateIssueList(_issueDef, container, user);
        }

        @Override
        public int importRows(User user, Container container, DataIteratorBuilder rows, BatchValidationException errors, @Nullable Map<Enum,Object> configParameters, Map<String, Object> extraScriptContext)
                throws SQLException
        {
            return _importRowsUsingDIB(user, container, rows, null, getDataIteratorContext(errors, InsertOption.IMPORT, configParameters), extraScriptContext);
        }

        @Override
        public List<Map<String, Object>> insertRows(User user, Container container, List<Map<String, Object>> rows, BatchValidationException errors, @Nullable Map<Enum, Object> configParameters, Map<String, Object> extraScriptContext) throws DuplicateKeyException, QueryUpdateServiceException, SQLException
        {
            List<Map<String, Object>> results = super._insertRowsUsingDIB(user, container, rows, getDataIteratorContext(errors, InsertOption.INSERT, configParameters), extraScriptContext);
            return results;
        }

        @Override
        protected Map<String, Object> _update(User user, Container c, Map<String, Object> row, Map<String, Object> oldRow, Object[] keys) throws SQLException, ValidationException
        {
            try (DbScope.Transaction transaction = IssuesSchema.getInstance().getSchema().getScope().ensureTransaction())
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

                // save default values
                Domain domain = _issueDef.getDomain(user);
                Map<DomainProperty, Object> defaultValues = new HashMap<>();
                for (DomainProperty prop : domain.getProperties())
                {
                    if (row.containsKey(prop.getName()) && (row.get(prop.getName()) != null))
                    {
                        defaultValues.put(prop, row.get(prop.getName()));
                    }
                }

                try
                {
                    DefaultValueService.get().setDefaultValues(c, defaultValues, user);
                }
                catch (ExperimentException e)
                {
                    throw new RuntimeException(e);
                }
                transaction.commit();

                return ret;
            }
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

            // Insert into issues.issues then the provisioned table
            DataIteratorBuilder step2 = TableInsertDataIterator.create(DataIteratorBuilder.wrap(step0), IssuesSchema.getInstance().getTableInfoIssues(), c, context);
            DataIteratorBuilder step3 = TableInsertDataIterator.create(step2, _issueDef.createTable(getUserSchema().getUser()), c, context);

            return LoggingDataIterator.wrap(step3.getDataIterator(context));
        }
    }

    /**
     * Data iterator to handle issues default values
     */
    private class DefaultValuesIteratorBuilder implements DataIteratorBuilder
    {
        private DataIteratorContext _context;
        private final DataIteratorBuilder _in;
        private User _user;

        DefaultValuesIteratorBuilder(@NotNull DataIteratorBuilder in, DataIteratorContext context, User user)
        {
            _context = context;
            _in = in;
            _user = user;
        }

        @Override
        public DataIterator getDataIterator(DataIteratorContext context)
        {
            DataIterator pre = _in.getDataIterator(context);
            return LoggingDataIterator.wrap(new IssuesTable.DefaultValuesDataIterator(pre, context, _user));
        }
    }

    private class DefaultValuesDataIterator extends WrapperDataIterator
    {
        final DataIteratorContext _context;
        final User _user;
        Map<DomainProperty, Object> _defaultValues = new HashMap<>();
        Map<String, Integer> _columnMap;
        Domain _domain;

        protected DefaultValuesDataIterator(DataIterator di, DataIteratorContext context, User user)
        {
            super(di);
            _context = context;

            _columnMap = DataIteratorUtil.createColumnNameMap(di);
            _domain = _issueDef.getDomain(user);

            _user = user;
        }

        private BatchValidationException getErrors()
        {
            return _context.getErrors();
        }

        @Override
        public boolean next() throws BatchValidationException
        {
            boolean hasNext = super.next();

            // skip processing if there are errors upstream
            if (getErrors().hasErrors())
                return hasNext;

            // for each iteration collect the default values, only save the single set
            for (DomainProperty prop : _domain.getProperties())
            {
                Integer idx = _columnMap.get(prop.getName());
                if (idx != null)
                {
                    Object value = get(idx);
                    if (value != null)
                    {
                        _defaultValues.put(prop, value);
                    }
                }
            }

            if (!hasNext)
            {
                try
                {
                    DefaultValueService.get().setDefaultValues(getContainer(), _defaultValues, _user);
                }
                catch (ExperimentException e)
                {
                    throw new RuntimeException(e);
                }
            }
            return hasNext;
        }
    }

    /**
     * Lookup FK which preserves the current value of the field regardless of whether it
     * is contained in the lookup.
     */
    static class IssuesPdLookupForeignKey extends PdLookupForeignKey
    {
        private User _user;
        private Container _container;
        private String _propName;

        public IssuesPdLookupForeignKey(User user, PropertyDescriptor pd, Container container)
        {
            super(user, pd, container);

            _user = user;
            _container = container;
            _propName = pd.getName();
        }

        @Override
        public NamedObjectList getSelectList(RenderContext ctx)
        {
            NamedObjectList objectList = super.getSelectList(ctx);
            Integer issueId = ctx.get(FieldKey.fromParts("IssueId"), Integer.class);
            if (issueId != null)
            {
                Issue issue = IssueManager.getIssue(_container, _user, issueId);
                if (issue != null)
                {
                    Object value = issue.getProperties().get(_propName);
                    if (value instanceof String)
                    {
                        NamedObject entry = new SimpleNamedObject(value.toString(), value);
                        if (!objectList.contains(entry))
                        {
                            objectList.put(entry);
                        }
                    }
                }
            }
            return objectList;
        }
    }

    static class AssignedToForeignKey extends UserIdForeignKey
    {
        UserSchema _schema;

        static public ColumnInfo initColumn(ColumnInfo column)
        {
            column.setFk(new IssuesTable.AssignedToForeignKey(column.getParentTable().getUserSchema()));
            column.setDisplayColumnFactory(colInfo -> new UserIdRenderer(colInfo));
            return column;
        }

        public AssignedToForeignKey(UserSchema schema)
        {
            super(schema);
            _schema = schema;
        }

        @Override
        public NamedObjectList getSelectList(RenderContext ctx)
        {
            NamedObjectList objectList = new NamedObjectList();
            Integer issueId = ctx.get(FieldKey.fromParts("IssueId"), Integer.class);
            String issueDefName = ctx.get(FieldKey.fromParts("IssueDefName"), String.class);
            Integer assignedTo = ctx.get(FieldKey.fromParts("AssignedTo"), Integer.class);
            Issue issue = null;
            boolean hasAssignedTo = false;

            if (issueId != null)
            {
                issue = IssueManager.getIssue(_schema.getContainer(), _schema.getUser(), issueId);
            }

            for (User user : IssueManager.getAssignedToList(ctx.getContainer(), issueDefName, issue))
            {
                if (assignedTo != null && !hasAssignedTo && user.getUserId() == assignedTo)
                    hasAssignedTo = true;

                objectList.put(new SimpleNamedObject(String.valueOf(user.getUserId()), user.getDisplayName(_schema.getUser())));
            }

            // make sure an entry is ensured for the current assigned to user
            if (assignedTo != null && !hasAssignedTo)
                objectList.put(new SimpleNamedObject(String.valueOf(assignedTo), UserManager.getDisplayName(assignedTo, _schema.getUser())));

            return objectList;
        }
    }
}

// TODO: Remove this class after adding a 'linkTitle' or 'urlTitle' property to ColumnRenderProperties
class URLTitleDisplayColumnFactory implements DisplayColumnFactory
{
    StringExpressionFactory.FieldKeyStringExpression _urlTitleExpr;

    public URLTitleDisplayColumnFactory(String urlTitleExpr)
    {
        this(new StringExpressionFactory.FieldKeyStringExpression(urlTitleExpr, false, StringExpressionFactory.AbstractStringExpression.NullValueBehavior.NullResult));
    }

    public URLTitleDisplayColumnFactory(StringExpressionFactory.FieldKeyStringExpression urlTitleExpr)
    {
        _urlTitleExpr = urlTitleExpr;
    }

    @Override
    public DisplayColumn createRenderer(ColumnInfo colInfo)
    {
        DisplayColumn displayColumn = new DataColumn(colInfo);
        displayColumn.setURLTitle(_urlTitleExpr);
        return displayColumn;
    }
}

class NotifyListDisplayColumn extends DataColumn
{
    private User _user;
    private static final String DELIM = ", ";

    public NotifyListDisplayColumn(ColumnInfo col, User curUser)
    {
        super(col);
        _user = curUser;
    }

    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
    {
        Object o = getValue(ctx);
        if (o != null)
        {
            List<String> usernames = new ArrayList<>();

            for (String notifyUser : o.toString().split(";"))
            {
                notifyUser = parseUserDisplayName(notifyUser);
                if (notifyUser != null)
                    usernames.add(notifyUser);
            }

            out.write(StringUtils.join(usernames, DELIM));
        }
    }

    @Nullable
    public String parseUserDisplayName(String part)
    {
        part = StringUtils.trimToNull(part);
        if (part != null)
        {
            // Issue 20914
            // NOTE: this doesn't address the bad data in the backend just displaying it
            // TODO: consider update script for fixing this issue...
            try
            {
                User user = UserManager.getUser(Integer.parseInt(part));
                if (user != null)
                    return user.getDisplayName(_user);
            }
            catch (NumberFormatException e)
            {
                return part;
            }
        }
        return null;
    }
}
