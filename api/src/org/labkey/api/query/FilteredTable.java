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

package org.labkey.api.query;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.compliance.TableRules;
import org.labkey.api.compliance.TableRulesManager;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ButtonBarConfig;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ColumnLogging;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerFilterable;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.data.PHI;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SchemaTableInfo;
import org.labkey.api.data.SelectQueryAuditProvider;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.WrappedColumnInfo;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.ContainerContext;
import org.labkey.api.util.Pair;
import org.labkey.api.util.StringExpression;
import org.labkey.api.view.ActionURL;
import org.labkey.data.xml.TableCustomizerType;
import org.labkey.data.xml.TableType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A table that filters down to a particular set of rows from an underlying, wrapped table/subquery. A typical example
 * would be filtering to only show rows that are part of a particular container.
 */
public class FilteredTable<SchemaType extends UserSchema> extends AbstractContainerFilterable implements ContainerFilterable
{
    final private SimpleFilter _filter;
    @NotNull protected final TableInfo _rootTable;
    AliasManager _aliasManager = null;
    protected String _publicSchemaName = null;

    private boolean _public = true;

    @NotNull protected SchemaType _userSchema;

    private final @NotNull TableRules _rules;

    public FilteredTable(@NotNull TableInfo table, @NotNull SchemaType userSchema)
    {
        this(table, userSchema, null);
    }

    public FilteredTable(@NotNull TableInfo table, @NotNull SchemaType userSchema, @Nullable ContainerFilter containerFilter)
    {
        super(table.getSchema(), table.getName());
        _filter = new SimpleFilter();
        _rootTable = table;
        //getTitle() reverts to _name, so if the getTitle() matches getName(), we assume it was not explicitly set.
        _title = _rootTable.getName().equals(_rootTable.getTitle()) ? null : _rootTable.getTitle();
        _description = _rootTable.getDescription();
        _importMsg = _rootTable.getImportMessage();
        _importTemplates = _rootTable.getRawImportTemplates();
        // UNDONE: lazy load button bar config????
        _buttonBarConfig = _rootTable.getButtonBarConfig() == null ? null : new ButtonBarConfig(_rootTable.getButtonBarConfig());
        if (_rootTable.supportsAuditTracking())
            _auditBehaviorType = _rootTable.getAuditBehavior();

        // We used to copy the titleColumn from table, but this forced all ColumnInfos to load.  Now, delegate
        // to _rootTable lazily, allowing overrides.
        _userSchema = userSchema;

        // TODO ContainerFilter -- for some subclasses it is too early to call supportsContainerFilter() (e.g. DatasetTableImpl)
        // NOTE: Tables that don't support container filters are required to pass containerFilter == null
//        if (supportsContainerFilter())
        {
            if (containerFilter != null)
                setContainerFilter(containerFilter);
            else
                applyContainerFilter(getDefaultContainerFilter());
        }

        _rules = supportTableRules() ? TableRulesManager.get().getTableRules(getContainer(), userSchema.getUser()) : TableRules.NOOP_TABLE_RULES;
    }

    @Override
    protected ContainerFilter getDefaultContainerFilter()
    {
        return _userSchema.getDefaultContainerFilter();
    }

    public boolean supportTableRules()
    {
        return false;
    }

    @Override
    public void afterConstruct()
    {
        checkLocked();
        super.afterConstruct();
        if (getRealTable() instanceof AbstractTableInfo)
            ((AbstractTableInfo)getRealTable()).afterConstruct();
    }

    @Override
    public void loadFromXML(QuerySchema schema, @Nullable Collection<TableType> xmlTable, Collection<QueryException> errors)
    {
        if (_rootTable instanceof SchemaTableInfo)
        {
            TableCustomizerType parentJavaCustomizer = ((SchemaTableInfo)_rootTable).getJavaCustomizer();
            if (parentJavaCustomizer != null)
            {
                // Before we do our own customization, apply customization from the SchemaTableInfo
                configureViaTableCustomizer(errors, parentJavaCustomizer);
            }
        }

        super.loadFromXML(schema, xmlTable, errors);
    }

    @Override
    public String getTitleColumn()
    {
        lazyLoadTitleColumnProperties();
        return super.getTitleColumn();
    }

    @Override
    public boolean hasDefaultTitleColumn()
    {
        lazyLoadTitleColumnProperties();
        return super.hasDefaultTitleColumn();
    }


    private void lazyLoadTitleColumnProperties()
    {
        // If _titleColumn has not been set, take the settings from _rootTable
        if (null == _titleColumn)
        {
            _titleColumn = _rootTable.getTitleColumn();
            _hasDefaultTitleColumn = _rootTable.hasDefaultTitleColumn();
        }
    }

    public void wrapAllColumns(boolean preserveHidden)
    {
        for (ColumnInfo col : getRealTable().getColumns())
        {
            var newCol = addWrapColumn(col);
            if (preserveHidden && col.isHidden())
            {
                newCol.setHidden(col.isHidden());
            }
        }
    }

    public TableInfo getRealTable()
    {
        return _rootTable;
    }

    @Override
    public ActionURL getGridURL(Container container)
    {
        ActionURL url = super.getGridURL(container);
        return url != null ? url : getRealTable().getGridURL(container);
    }

    @Override
    public ActionURL getInsertURL(Container container)
    {
        ActionURL url = super.getInsertURL(container);
        return url != null ? url : getRealTable().getInsertURL(container);
    }

    @Override
    public ActionURL getImportDataURL(Container container)
    {
        ActionURL url = super.getImportDataURL(container);
        return url != null ? url : getRealTable().getImportDataURL(container);
    }

    @Override
    public ActionURL getDeleteURL(Container container)
    {
        ActionURL url = super.getDeleteURL(container);
        return url != null ? url : getRealTable().getDeleteURL(container);
    }

    @Override
    public StringExpression getUpdateURL(@Nullable Set<FieldKey> columns, Container container)
    {
        StringExpression expr = super.getUpdateURL(columns, container);
        return expr != null ? expr : getRealTable().getUpdateURL(columns, container);
    }

    @Override
    public StringExpression getDetailsURL(@Nullable Set<FieldKey> columns, Container container)
    {
        StringExpression expr = super.getDetailsURL(columns, container);
        return expr != null ? expr : getRealTable().getDetailsURL(columns, container);
    }

    @Override
    public boolean hasDetailsURL()
    {
        return super.hasDetailsURL() || getRealTable().hasDetailsURL();
    }


    @Override
    public ContainerContext getContainerContext()
    {
        ContainerContext cc = super.getContainerContext();
        if (cc != null)
            return cc;

        cc = getRealTable().getContainerContext();
        if (cc != null)
            return cc;

        // NOTE: This is a last resort -- your query table should override .getContainerContext() or .getContainerFieldKey() instead.
        return getContainer();
    }


    @Override
    public FieldKey getContainerFieldKey()
    {
        FieldKey fk = super.getContainerFieldKey();
        if (fk != null)
            return fk;

        TableInfo t = getRealTable();
        if (t instanceof AbstractTableInfo)
            return t.getContainerFieldKey();

        return null;
    }


    private String filterName(ColumnInfo c)
	{
		return c.getAlias();
	}


    final public void addCondition(SQLFragment condition, FieldKey... fieldKeys)
    {
        checkLocked();
        if (condition.isEmpty())
            return;
        SQLFragment tmp = new SQLFragment();
        tmp.append("(").append(condition.getSQL()).append(")");
        _filter.addWhereClause(condition, fieldKeys);
    }

    public void addCondition(SimpleFilter filter)
    {
        checkLocked();
        _filter.addAllClauses(filter);
    }

    public MutableColumnInfo wrapColumnFromJoinedTable(String alias, ColumnInfo underlyingColumn, String tableAlias)
    {
        return wrapExprColumnFromJoinedTable(alias, underlyingColumn, tableAlias);
    }

    // identical to wrapColumnFromJoinedTable, but see FilteredTableDelegating where this is != wrapColumnFromJoinedTable */
    public ExprColumn wrapExprColumnFromJoinedTable(String alias, ColumnInfo underlyingColumn, String tableAlias)
    {
        var ret = WrappedColumnInfo.wrapAsExprColumn(this, tableAlias, new FieldKey(null,alias), underlyingColumn, null, null);
        if (underlyingColumn.isKeyField() && getColumn(underlyingColumn.getName()) != null)
        {
            ret.setKeyField(false);
        }
        if (!getPHIDataLoggingColumns().isEmpty() && PHI.NotPHI != underlyingColumn.getPHI() && underlyingColumn.isShouldLog())
        {
            ret.setColumnLogging(new ColumnLogging(true, underlyingColumn.getFieldKey(), underlyingColumn.getParentTable(), getPHIDataLoggingColumns(), getPHILoggingComment(), getSelectQueryAuditProvider()));
        }
        assert ret.getParentTable() == this;
        return ret;
    }

    public MutableColumnInfo wrapColumn(String alias, ColumnInfo underlyingColumn)
    {
        assert underlyingColumn.getParentTable() == _rootTable;
        return wrapColumnFromJoinedTable(alias, underlyingColumn, ExprColumn.STR_TABLE_ALIAS);
    }

    public MutableColumnInfo wrapColumn(ColumnInfo underlyingColumn)
    {
        return wrapColumn(underlyingColumn.getName(), underlyingColumn);
    }

    public void clearConditions(FieldKey fieldKey)
    {
        checkLocked();
        _filter.deleteConditions(fieldKey);
    }

    public void addCondition(ColumnInfo col, Container container)
    {
        checkLocked();
        assertCorrectParentTable(col);
        // This CAST improves performance on Postgres for some queries by choosing a more efficient query plan
        SQLFragment frag = new SQLFragment();
        frag.append(filterName(col));
        frag.append(" = CAST(");
        frag.appendStringLiteral(container.getId());
        frag.append(" AS UniqueIdentifier)");
        addCondition(frag, col.getFieldKey());
    }

    public void addCondition(ColumnInfo col, String value)
    {
        checkLocked();
        assertCorrectParentTable(col);
        SQLFragment frag = new SQLFragment();
        frag.append(col.getSelectName());
        frag.append(" = ");
        frag.appendStringLiteral(value);
        addCondition(frag, col.getFieldKey());
    }

    private void assertCorrectParentTable(ColumnInfo col)
    {
        assert col.getParentTable() == _rootTable : "Column '" + col.getName() + "' isn't from the expected table. Should be '" +
                _rootTable + "' but was '" + col.getParentTable() + "'";
    }

    public void addCondition(ColumnInfo col, int value)
    {
        checkLocked();
        assertCorrectParentTable(col);
        SQLFragment frag = new SQLFragment();
        frag.append(filterName(col));
        frag.append(" = ");
        frag.append(Integer.toString(value));
        addCondition(frag);
    }

    public void addCondition(ColumnInfo col, float value)
    {
        checkLocked();
        assertCorrectParentTable(col);
        SQLFragment frag = new SQLFragment();
        frag.append(filterName(col));
        frag.append(" = ");
        frag.append(Float.toString(value));
        addCondition(frag);
    }

    public void addCondition(ColumnInfo col1, ColumnInfo col2)
    {
        checkLocked();
        assert col1.getParentTable() == col2.getParentTable() : "Column is from the wrong table";
        assertCorrectParentTable(col1);
        SQLFragment frag = new SQLFragment();
        frag.append(filterName(col1));
        frag.append(" = ");
        frag.append(filterName(col2));
        addCondition(frag);
    }


    public void addInClause(ColumnInfo col, Collection<?> params)
    {
        checkLocked();
        assertCorrectParentTable(col);
        SimpleFilter.InClause clause = new SimpleFilter.InClause(col.getFieldKey(), params);
        SQLFragment frag = clause.toSQLFragment(Collections.emptyMap(), _schema.getSqlDialect());
        addCondition(frag, col.getFieldKey());
    }


    @Override
    public String getSelectName()
    {
        if (_filter.getWhereSQL(_rootTable).length() == 0)
            return getFromTable().getSelectName();
        return null;
    }
    

    @NotNull
    public final SQLFragment getFromSQL()
    {
        throw new IllegalStateException();
    }

    @NotNull
    @Override
    public SQLFragment getFromSQL(String alias)
    {
        return getFromSQL(alias, false);
    }

    public SQLFragment getFromSQL(String alias, boolean skipTransform)
    {
        SimpleFilter filter = getFilter();
        SQLFragment where = filter.getSQLFragment(_rootTable.getSqlDialect());
        if (where.isEmpty())
            return getFromTable().getFromSQL(alias);

        // SELECT
        SQLFragment ret = new SQLFragment("(SELECT ").append(getInnerFromColumns()).append(" FROM ");

        // FROM
        //   NOTE some filters depend on knowing the name of this table in the simple case, so don't alias it
        String selectName = _rootTable.getSelectName();
        if (null != selectName)
            ret.append(selectName);
        else
            ret.append(getFromTable().getFromSQL("x"));

        // WHERE
        Map<FieldKey, ColumnInfo> columnMap = Table.createColumnMap(getFromTable(), getFromTable().getColumns());
        SQLFragment filterFrag = filter.getSQLFragment(_rootTable.getSqlDialect(), columnMap);
        ret.append("\n").append(filterFrag).append(") ").append(alias);
        return skipTransform ? ret : getTransformedFromSQL(ret);
    }

    public SQLFragment getTransformedFromSQL(SQLFragment sqlFrom)
    {
        return _rules.getSqlTransformer().apply(sqlFrom);
    }

    /**
     * TODO: It would be nice to be able override getFromSQL(String alias, Set<FieldKey> cols), but the chaining of
     * getFromSQL overrides in DatasetTableImpl makes this difficult & risky. In lieu of that, overriding
     * this method at least allows plugging a discrete column list back into the inner SELECT
     */
    protected String getInnerFromColumns()
    {
        return "*";
    }


    protected AliasManager getAliasManager()
    {
        if (null == _aliasManager)
            _aliasManager = new AliasManager(getSchema());
        return _aliasManager;
    }

    @Override
    public MutableColumnInfo addColumn(MutableColumnInfo column)
    {
        checkLocked();
        MutableColumnInfo ret = column;

        // Choke point for handling all column filtering and transforming, e.g., respecting PHI annotations
        if (_rules.getColumnInfoFilter().test(column))
        {
            ColumnInfo transformed = _rules.getColumnInfoTransformer().apply(column);
            getAliasManager().ensureAlias(column);
            ret = super.addColumn((MutableColumnInfo)transformed);
        }

        return ret;
    }

    @Override
    public boolean removeColumn(ColumnInfo column)
    {
        boolean result = super.removeColumn(column);
        if (result && _aliasManager != null)
        {
            _aliasManager.unclaimAlias(column);
        }
        return result;
    }

    public MutableColumnInfo addWrapColumn(String name, ColumnInfo column)
    {
        assert column.getParentTable() == getRealTable() : "Column is not from the same \"real\" table";
        BaseColumnInfo ret = new AliasedColumn(this, name, column);

        if (!getPHIDataLoggingColumns().isEmpty() && PHI.NotPHI != column.getPHI() && column.isShouldLog())
        {
            ret.setColumnLogging(new ColumnLogging(true, column.getFieldKey(), column.getParentTable(), getPHIDataLoggingColumns(), getPHILoggingComment(), getSelectQueryAuditProvider()));
        }
        propagateKeyField(column, ret);
        addColumn(ret);
        return ret;
    }

    public Set<FieldKey> getPHIDataLoggingColumns()
    {
        return Collections.emptySet();
    }

    public String getPHILoggingComment()
    {
        return "";
    }

    /**
     * provide a way to customize identifieddata values
     */
    public SelectQueryAuditProvider getSelectQueryAuditProvider()
    {
        return null;
    }

    public MutableColumnInfo addWrapColumn(ColumnInfo column)
    {
        return addWrapColumn(column.getName(), column);
    }

    public void propagateKeyField(ColumnInfo orig, MutableColumnInfo wrapped)
    {
        // Use getColumnNameSet() instead of getColumn() because we don't want to go through the resolveColumn()
        // codepath, which is potentially expensive and doesn't reflect the "real" columns that are part of this table
        if (orig.isKeyField() && getColumnNameSet().contains(orig.getName()))
        {
            wrapped.setKeyField(false);
        }
    }

    protected TableInfo getFromTable()
    {
        return getRealTable();
    }

    protected SimpleFilter getFilter()
    {
        return _filter;
    }

    /**
     * ignores supportsContainerFilter(), allows subclasses to set container filter w/o supporting
     * external, "public" setting of filter.
     */
    @Override
    protected void _setContainerFilter(@NotNull ContainerFilter filter)
    {
        checkLocked();
        ContainerFilter.logSetContainerFilter(filter, getClass().getSimpleName(), getName());
        _containerFilter = filter;
        applyContainerFilter(_containerFilter);
    }

    protected void applyContainerFilter(ContainerFilter filter)
    {
        // Datasets need to determine if they have container column in their root table
        if (_rootTable.hasContainerColumn())
        {
            ColumnInfo containerColumn = _rootTable.getColumn(getContainerFilterColumn());
            if (containerColumn != null && getContainer() != null)
            {
                clearConditions(containerColumn.getFieldKey());
                addCondition(new SimpleFilter(getContainerFilterClause(filter, containerColumn.getFieldKey())));
            }
        }
    }

    /**
     * Subclasses should override this if they need to create a filter clause with an explicit permission (for example,
     * the DefaultAuditTypeTable).  See issue 19515
     */
    protected SimpleFilter.FilterClause getContainerFilterClause(ContainerFilter filter, FieldKey fieldKey)
    {
        return filter.createFilterClause(getSchema(), fieldKey, getContainer());
    }

    public Container getContainer()
    {
        return _userSchema.getContainer();
    }

    public boolean needsContainerClauseAdded()
    {
        return false;
    }

    @Override
    public String toString()
    {
        return getName() + " - FilteredTable over " + _rootTable;
    }

    public boolean isPublic()
    {
        return _public;
    }

    public void setPublic(boolean aPublic)
    {
        _public = aPublic;
    }

    @Override @NotNull
    public Collection<QueryService.ParameterDecl> getNamedParameters()
    {
        return _rootTable.getNamedParameters();
    }

    /** Set the SchemaKey encoded name for this schema. */
    public void setPublicSchemaName(String schemaName)
    {
        assert !SchemaKey.needsEncoding(schemaName) : "schema name must be in SchemaKey encoding: " + schemaName;
        _publicSchemaName = schemaName;
    }

    @Override
    public String getPublicSchemaName()
    {
        return _publicSchemaName == null ? getUserSchema().getSchemaName() : _publicSchemaName;
    }

    /** For FilteredTable, we should always have a UserSchema (it's a @NotNull constructor argument */
    @Override
    @NotNull
    public SchemaType getUserSchema()
    {
        return _userSchema;
    }

    @NotNull
    @Override
    public Map<String, Pair<IndexType, List<ColumnInfo>>> getUniqueIndices()
    {
        return Collections.unmodifiableMap(wrapTableIndices(getRealTable()));
    }

    protected Map<String, Pair<IndexType, List<ColumnInfo>>> wrapTableIndices(TableInfo table)
    {
        Map<String, Pair<IndexType, List<ColumnInfo>>> indices = table.getUniqueIndices();
        return getStringPairMap(indices);
    }

    @NotNull
    @Override
    public Map<String, Pair<IndexType, List<ColumnInfo>>> getAllIndices()
    {
        return Collections.unmodifiableMap(wrapTableAllIndices(getRealTable()));
    }

    protected Map<String, Pair<IndexType, List<ColumnInfo>>> wrapTableAllIndices(TableInfo table)
    {
        Map<String, Pair<IndexType, List<ColumnInfo>>> indices = table.getAllIndices();
        return getStringPairMap(indices);
    }

    @NotNull
    private Map<String, Pair<IndexType, List<ColumnInfo>>> getStringPairMap(Map<String, Pair<IndexType, List<ColumnInfo>>> indices)
    {
        Map<String, Pair<IndexType, List<ColumnInfo>>> ret = new HashMap<>();
        for (Map.Entry<String, Pair<IndexType, List<ColumnInfo>>> entry : indices.entrySet())
        {
            List<ColumnInfo> indexCols = new ArrayList<>(entry.getValue().getValue().size());
            for (ColumnInfo col : entry.getValue().getValue())
            {
                ColumnInfo c = getColumn(col.getFieldKey());
                if (c != null)
                    indexCols.add(c);
            }

            if (!indexCols.isEmpty())
                ret.put(entry.getKey(), Pair.of(entry.getValue().getKey(), indexCols));
        }

        return ret;
    }

    @Override
    public boolean hasDbTriggers()
    {
        return getRealTable().hasDbTriggers();
    }
    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        if (ReadPermission.class.isAssignableFrom(perm))
        {
            return _userSchema.getContainer().hasPermission(user, perm);
        }
        return super.hasPermission(user, perm);
    }
}
