/*
 * Copyright (c) 2006-2014 LabKey Corporation
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

package org.labkey.api.data;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlCursor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.DbCache;
import org.labkey.api.collections.CaseInsensitiveMapWrapper;
import org.labkey.api.collections.CaseInsensitiveTreeSet;
import org.labkey.api.collections.NamedObjectList;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.gwt.client.AuditBehaviorType;
import org.labkey.api.module.Module;
import org.labkey.api.query.AggregateRowConfig;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.MetadataException;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUrls;
import org.labkey.api.query.SchemaTreeVisitor;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.resource.Resource;
import org.labkey.api.script.ScriptReference;
import org.labkey.api.script.ScriptService;
import org.labkey.api.security.SecurityLogger;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.ContainerContext;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.MemTrackable;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;
import org.labkey.api.util.SimpleNamedObject;
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.data.xml.AuditType;
import org.labkey.data.xml.ColumnType;
import org.labkey.data.xml.CustomizerType;
import org.labkey.data.xml.ImportTemplateType;
import org.labkey.data.xml.PositionTypeEnum;
import org.labkey.data.xml.TableType;

import javax.script.ScriptException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

abstract public class AbstractTableInfo implements TableInfo, MemTrackable
{
    private static final Logger LOG = Logger.getLogger(AbstractTableInfo.class);

    /** Used as a marker to indicate that a URL (such as insert or update) has been explicitly disabled. Null values get filled in with default URLs in some cases */
    public static final ActionURL LINK_DISABLER_ACTION_URL;
    static
    {
        boolean assertsEnabled = false;
        assert assertsEnabled = true;
        LINK_DISABLER_ACTION_URL = assertsEnabled ? new ActionURL("~~disabled~link~should~not~render~~", "~~error~~", null) : new ActionURL();
    }

    /** Used as a marker to indicate that a URL (such as insert or update) has been explicitly disabled. Null values get filled in with default URLs in some cases */
    public static final DetailsURL LINK_DISABLER = new DetailsURL(LINK_DISABLER_ACTION_URL);
    protected Iterable<FieldKey> _defaultVisibleColumns;
    protected DbSchema _schema;
    protected String _titleColumn;
    protected boolean _hasDefaultTitleColumn = true;
    private int _cacheSize = DbCache.DEFAULT_CACHE_SIZE;

    protected final Map<String, ColumnInfo> _columnMap;
    private Map<String, MethodInfo> _methodMap;
    protected String _name;
    protected String _title = null;
    protected String _description;
    protected String _importMsg;
    protected List<Pair<String, StringExpression>> _importTemplates;

    protected DetailsURL _gridURL;
    protected DetailsURL _insertURL;
    protected DetailsURL _updateURL;
    protected DetailsURL _deleteURL;
    protected DetailsURL _importURL;
    protected ButtonBarConfig _buttonBarConfig;
    protected AggregateRowConfig _aggregateRowConfig;

    private DetailsURL _detailsURL;
    protected AuditBehaviorType _auditBehaviorType = AuditBehaviorType.NONE;
    private FieldKey _auditRowPk;

    @NotNull
    public List<ColumnInfo> getPkColumns()
    {
        List<ColumnInfo> ret = new ArrayList<>();
        for (ColumnInfo column : getColumns())
        {
            if (column.isKeyField())
            {
                ret.add(column);
            }
        }
        return Collections.unmodifiableList(ret);
    }


    public AbstractTableInfo(DbSchema schema, String name)
    {
        _schema = schema;
        _columnMap = constructColumnMap();
        setName(name);
        MemTracker.getInstance().put(this);
    }


    public void afterConstruct()
    {
        checkLocked();

        ContainerContext cc = getContainerContext();
        if (null != cc)
        {
            for (ColumnInfo c : getColumns())
            {
                StringExpression url = c.getURL();
                if (url instanceof DetailsURL && url != LINK_DISABLER)
                    ((DetailsURL)url).setContainerContext(cc, false);
            }
            if (_detailsURL != null && _detailsURL != LINK_DISABLER)
            {
                _detailsURL.setContainerContext(cc, false);
            }
            if (_updateURL != null && _updateURL != LINK_DISABLER)
            {
                _updateURL.setContainerContext(cc, false);
            }
        }
    }


    protected Map<String, ColumnInfo> constructColumnMap()
    {
        if (isCaseSensitive())
        {
            return new LinkedHashMap<>();
        }
        return new CaseInsensitiveMapWrapper<>(new LinkedHashMap<String, ColumnInfo>());
    }

    // BUGBUG: This is suspect -- all other parts of LabKey expect column names to be case-insensitive including FieldKeys
    protected boolean isCaseSensitive()
    {
        return false;
    }

    public DbSchema getSchema()
    {
        return _schema;
    }

    public SqlDialect getSqlDialect()
    {
        return getSchema().getSqlDialect();
    }

    public List<String> getPkColumnNames()
    {
        List<String> ret = new ArrayList<>();
        for (ColumnInfo col : getPkColumns())
        {
            ret.add(col.getName());
        }
        return Collections.unmodifiableList(ret);
    }

    public ColumnInfo getVersionColumn()
    {
        return null;
    }

    public String getVersionColumnName()
    {
        return null;
    }

    @Override
    public DatabaseTableType getTableType()
    {
        return DatabaseTableType.NOT_IN_DB;
    }

    @NotNull
    public SQLFragment getFromSQL(String alias)
    {
        if (null != getSelectName())
            return new SQLFragment().append(getSelectName()).append(" ").append(alias);
        else
            return new SQLFragment().append("(").append(getFromSQL()).append(") ").append(alias);
    }

    @Override
    public SQLFragment getFromSQL(String alias, Set<FieldKey> cols)
    {
        return getFromSQL(alias);
    }

    // don't have to implement if you override getFromSql(String alias)
    abstract protected SQLFragment getFromSQL();


    public NamedObjectList getSelectList()
    {
        List<ColumnInfo> pkColumns = getPkColumns();
        if (pkColumns.size() != 1)
            return new NamedObjectList();

        return getSelectList(pkColumns.get(0));
    }


    public NamedObjectList getSelectList(String columnName)
    {
        if (columnName == null)
            return getSelectList();
        
        ColumnInfo column = getColumn(columnName);
        return getSelectList(column);
    }


    public NamedObjectList getSelectList(ColumnInfo firstColumn)
    {
        final NamedObjectList ret = new NamedObjectList();
        if (firstColumn == null)
            return ret;
        ColumnInfo titleColumn = getColumn(getTitleColumn());
        if (titleColumn == null)
            return ret;

        List<ColumnInfo> cols;
        final int titleIndex;
        if (firstColumn == titleColumn)
        {
            cols = Arrays.asList(firstColumn);
            titleIndex = 1;
        }
        else
        {
            cols = Arrays.asList(firstColumn, titleColumn);
            titleIndex = 2;
        }

        String sortStr = (titleColumn.getSortDirection() != null ? titleColumn.getSortDirection().getDir() : "") + titleColumn.getName();

        new TableSelector(this, cols, null, new Sort(sortStr)).forEach(new Selector.ForEachBlock<ResultSet>()
        {
            @Override
            public void exec(ResultSet rs) throws SQLException
            {
                ret.put(new SimpleNamedObject(rs.getString(1), rs.getString(titleIndex)));
            }
        });

        return ret;
    }

    public List<ColumnInfo> getUserEditableColumns()
    {
        List<ColumnInfo> ret = new ArrayList<>();
        for (ColumnInfo col : getColumns())
        {
            if (col.isUserEditable())
            {
                ret.add(col);
            }
        }
        return Collections.unmodifiableList(ret);
    }

    public List<ColumnInfo> getColumns(String colNames)
    {
        String[] colNameArray = colNames.split(",");
        return getColumns(colNameArray);
    }

    public List<ColumnInfo> getColumns(String... colNameArray)
    {
        List<ColumnInfo> ret = new ArrayList<>(colNameArray.length);
        for (String name : colNameArray)
        {
            ret.add(getColumn(name.trim()));
        }
        return Collections.unmodifiableList(ret);
    }

    @Override
    public boolean hasDefaultTitleColumn()
    {
        return _hasDefaultTitleColumn;
    }

    @Override
    public String getTitleColumn()
    {
        if (null == _titleColumn)
        {
            for (ColumnInfo column : getColumns())
            {
                if (column.isStringType() && !column.getSqlTypeName().equalsIgnoreCase("entityid"))
                {
                    _titleColumn = column.getName();
                    break;
                }
            }
            if (null == _titleColumn && getColumns().size() > 0)
                _titleColumn = getColumns().get(0).getName();
        }

        return _titleColumn;
    }

    public void setTitleColumn(String titleColumn)
    {
        checkLocked();
        setTitleColumn(titleColumn, null == titleColumn);
    }

    // Passing in defaultTitleColumn helps with export & serialization
    public void setTitleColumn(String titleColumn, boolean defaultTitleColumn)
    {
        checkLocked();
        _titleColumn = titleColumn;
        _hasDefaultTitleColumn = defaultTitleColumn;
    }

    public ColumnInfo getColumn(@NotNull String name)
    {
        ColumnInfo ret = _columnMap.get(name);
        if (ret != null)
            return ret;
        return resolveColumn(name);
    }

    @Override
    public ColumnInfo getColumn(@NotNull FieldKey name)
    {
        if (null != name.getParent())
            return null;
        return getColumn(name.getName());
    }


    /**
     * If a column wasn't found in the standard column list, give the table a final chance to locate it.
     * Useful for preserving backwards compatibility with saved queries when a column is renamed.
     */
    protected ColumnInfo resolveColumn(String name)
    {
        for (ColumnInfo col : getColumns())
        {
            if (null != col)        // #19358
            {
                String propName = col.getPropertyName();
                if (null != propName && propName.equalsIgnoreCase(name))
                    return col;
            }
        }
        return null;
    }

    public List<ColumnInfo> getColumns()
    {
        return Collections.unmodifiableList(new ArrayList<>(_columnMap.values()));
    }

    public Set<String> getColumnNameSet()
    {
        // Make the set case-insensitive
        return Collections.unmodifiableSet(new CaseInsensitiveTreeSet(_columnMap.keySet()));
    }

    public String getName()
    {
        return _name;
    }

    public String getTitle()
    {
        return _title == null ? _name : _title;
    }

    public String getTitleField()
    {
        return _title;
    }

    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        checkLocked();
        _description = description;
    }

    public boolean removeColumn(ColumnInfo column)
    {
        checkLocked();
        return _columnMap.remove(column.getName()) != null;
    }

    public ColumnInfo addColumn(ColumnInfo column)
    {
        checkLocked();
        // Not true if this is a VirtualTableInfo
        // assert column.getParentTable() == this;
        if (_columnMap.containsKey(column.getName()))
        {
            throw new IllegalArgumentException("Column " + column.getName() + " already exists for table " + getName());
        }
        _columnMap.put(column.getName(), column);
        assert column.lockName();
        return column;
    }

    public void addMethod(String name, MethodInfo method)
    {
        checkLocked();
        if (_methodMap == null)
        {
            _methodMap = new HashMap<>();
        }
        _methodMap.put(name, method);
    }

    public MethodInfo getMethod(String name)
    {
        if (_methodMap == null)
            return null;
        return _methodMap.get(name);
    }

    public void setName(String name)
    {
        checkLocked();
        _name = name;
    }

    public void setTitle(String title)
    {
        checkLocked();
        _title = title;
    }

    public ActionURL getGridURL(Container container)
    {
        if (_gridURL == LINK_DISABLER)
        {
            return LINK_DISABLER_ACTION_URL;
        }
        if (_gridURL != null)
        {
            return _gridURL.copy(container).getActionURL();
        }
        return null;
    }

    public ActionURL getInsertURL(Container container)
    {
        if (_insertURL == LINK_DISABLER)
        {
            return LINK_DISABLER_ACTION_URL;
        }
        if (_insertURL != null)
        {
            return _insertURL.copy(container).getActionURL();
        }
        return null;
    }

    @Override
    public ActionURL getImportDataURL(Container container)
    {
        if (_importURL == LINK_DISABLER)
        {
            return LINK_DISABLER_ACTION_URL;
        }
        if (_importURL != null)
        {
            return _importURL.copy(container).getActionURL();
        }
        return null;
    }

    public ActionURL getDeleteURL(Container container)
    {
        if (_deleteURL == LINK_DISABLER)
        {
            return LINK_DISABLER_ACTION_URL;
        }
        if (_deleteURL != null)
        {
            return _deleteURL.copy(container).getActionURL();
        }
        return null;
    }

    public StringExpression getUpdateURL(@Nullable Set<FieldKey> columns, Container container)
    {
        if (_updateURL == LINK_DISABLER)
            return LINK_DISABLER;

        if (_updateURL == null)
            return null;

        ContainerContext containerContext = getContainerContext();
        if (containerContext == null)
            containerContext = container;

        // Include the ContainerContext FieldKey if it hasn't already been included.
        if (columns != null && containerContext instanceof ContainerContext.FieldKeyContext)
        {
            ContainerContext.FieldKeyContext fieldKeyContext = (ContainerContext.FieldKeyContext) containerContext;
            Set<FieldKey> s = new HashSet<>(columns);
            s.add(fieldKeyContext.getFieldKey());
            columns = s;
        }

        if (columns == null || _updateURL.validateFieldKeys(columns))
            return _updateURL.copy(containerContext);

        return null;
    }

    public StringExpression getDetailsURL(@Nullable Set<FieldKey> columns, Container container)
    {
        if (_detailsURL == AbstractTableInfo.LINK_DISABLER)
            return LINK_DISABLER;

        if (_detailsURL == null)
            return null;

        ContainerContext containerContext = getContainerContext();
        if (containerContext == null)
            containerContext = container;

        // Include the ContainerContext FieldKey if it hasn't already been included.
        if (columns != null && containerContext instanceof ContainerContext.FieldKeyContext)
        {
            ContainerContext.FieldKeyContext fieldKeyContext = (ContainerContext.FieldKeyContext) containerContext;
            Set<FieldKey> s = new HashSet<>(columns);
            s.add(fieldKeyContext.getFieldKey());
            columns = s;
        }

        if (columns == null || _detailsURL.validateFieldKeys(columns))
            return _detailsURL.copy(containerContext);

        return null;
    }

    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        SecurityLogger.log("AbstractTableInfo.hasPermission " + getName(), user, null, false);
        return false;
    }

    public void setDetailsURL(DetailsURL detailsURL)
    {
        _detailsURL = detailsURL;
    }

    @Override
    public boolean hasDetailsURL()
    {
        return _detailsURL != null && _detailsURL != AbstractTableInfo.LINK_DISABLER;
    }

    public void setGridURL(DetailsURL gridURL)
    {
        checkLocked();
        _gridURL = gridURL;
    }

    public void setInsertURL(DetailsURL insertURL)
    {
        checkLocked();
        _insertURL = insertURL;
    }

    public void setImportURL(DetailsURL importURL)
    {
        checkLocked();
        _importURL = importURL;
    }

    public void setDeleteURL(DetailsURL deleteURL)
    {
        checkLocked();
        _deleteURL = deleteURL;
    }

    public void setUpdateURL(DetailsURL updateURL)
    {
        checkLocked();
        _updateURL = updateURL;
    }

    public ButtonBarConfig getButtonBarConfig()
    {
        return _buttonBarConfig;
    }

    public void setButtonBarConfig(ButtonBarConfig buttonBarConfig)
    {
        checkLocked();
        _buttonBarConfig = buttonBarConfig;
    }

    public AggregateRowConfig getAggregateRowConfig()
    {
        return _aggregateRowConfig;
    }

    public void setAggregateRowConfig(AggregateRowConfig config)
    {
        checkLocked();
        _aggregateRowConfig = config;
    }

    public void setDefaultVisibleColumns(Iterable<FieldKey> list)
    {
        checkLocked();
        _defaultVisibleColumns = list;
    }

    /** @return unmodifiable list of the columns that should be shown by default for this table */
    public List<FieldKey> getDefaultVisibleColumns()
    {
        if (_defaultVisibleColumns instanceof List)
        {
            return Collections.unmodifiableList((List<FieldKey>) _defaultVisibleColumns);
        }
        if (_defaultVisibleColumns != null)
        {
            List<FieldKey> ret = new ArrayList<>();
            for (FieldKey key : _defaultVisibleColumns)
            {
                ret.add(key);
            }
            return Collections.unmodifiableList(ret);
        }
        return Collections.unmodifiableList(QueryService.get().getDefaultVisibleColumns(getColumns()));
    }

    @Override
    public Map<FieldKey, ColumnInfo> getExtendedColumns(boolean includeHidden)
    {
        List<ColumnInfo> columns = getColumns();
        LinkedHashMap<FieldKey, ColumnInfo> ret = new LinkedHashMap<>(columns.size());
        if (includeHidden)
        {
            for (ColumnInfo col : columns)
                ret.put(col.getFieldKey(), col);
        }

        // Include any extra columns named by the default visible set
        ret.putAll(QueryService.get().getColumns(this, getDefaultVisibleColumns()));

        return Collections.unmodifiableMap(ret);
    }

    public boolean safeAddColumn(ColumnInfo column)
    {
        checkLocked();
        if (getColumn(column.getName()) != null)
            return false;
        addColumn(column);
        return true;
    }


    public static ForeignKey makeForeignKey(QuerySchema fromSchema, ColumnType.Fk fk)
    {
        ForeignKey ret = null;

        String displayColumnName = null;
        boolean useRawFKValue = false;
        if (fk.isSetFkDisplayColumnName())
        {
            displayColumnName = fk.getFkDisplayColumnName().getStringValue();
            useRawFKValue = fk.getFkDisplayColumnName().getUseRawValue();
        }

        Container lookupContainer;
        if (fk.getFkDbSchema() != null)
        {
            Container effectiveTargetContainer;
            if (fk.getFkFolderPath() != null)
            {
                effectiveTargetContainer = ContainerManager.getForPath(fk.getFkFolderPath());
                if (effectiveTargetContainer == null || !effectiveTargetContainer.hasPermission(fromSchema.getUser(), ReadPermission.class))
                    return null;
                lookupContainer = effectiveTargetContainer;
            }
            else
            {
                effectiveTargetContainer = fromSchema.getContainer();
                lookupContainer = null;
            }
            if (!fromSchema.getSchemaName().equals(fk.getFkDbSchema()) || !effectiveTargetContainer.equals(fromSchema.getContainer()))
            {
                // Let the QueryForeignKey lazily create the schema on demand
                ret = new QueryForeignKey(fk.getFkDbSchema(), effectiveTargetContainer, lookupContainer, fromSchema.getUser(), fk.getFkTable(), fk.getFkColumnName(), displayColumnName, useRawFKValue);
            }
        }
        else
        {
            lookupContainer = null;
        }

        if (ret == null)
        {
            // We can reuse the same schema object
            ret = new QueryForeignKey(fromSchema, lookupContainer, fk.getFkTable(), fk.getFkColumnName(), displayColumnName, useRawFKValue);
        }

        if (fk.isSetFkMultiValued())
        {
            String type = fk.getFkMultiValued();
            if ("junction".equals(type))
                ret = new MultiValuedForeignKey(ret, fk.getFkJunctionLookup());
            else
                throw new UnsupportedOperationException("Non-junction multi-value columns NYI");
        }

        return ret;
    }


    protected void initColumnFromXml(QuerySchema schema, ColumnInfo column, ColumnType xbColumn, Collection<QueryException> qpe)
    {
        checkLocked();
        column.loadFromXml(xbColumn, true);
        
        if (xbColumn.getFk() != null)
        {
            ForeignKey qfk = makeForeignKey(schema, xbColumn.getFk());
            if (qfk == null)
            {
                //noinspection ThrowableInstanceNeverThrown
                String msgColumnName =
                        (column.getParentTable()==null?"":column.getParentTable().getName()) +
                        column.getName();
                qpe.add(new MetadataException("Schema " + xbColumn.getFk().getFkDbSchema() + " not found, in foreign key definition: " + msgColumnName));
                return;
            }
            column.setFk(qfk);
        }
    }


    public void loadFromXML(QuerySchema schema, @Nullable Collection<TableType> xmlTables, Collection<QueryException> errors)
    {
        checkLocked();

        if (xmlTables != null)
            for (TableType xmlTable : xmlTables)
                loadFromXML(schema, xmlTable, errors);
    }

    public void loadFromXML(QuerySchema schema, @Nullable TableType xmlTable, Collection<QueryException> errors)
    {
        checkLocked();

        loadAllButCustomizerFromXML(schema, xmlTable, errors);

        // This needs to happen AFTER all of the other XML-based config has been applied, so it should always
        // be at the end of this method
        if (xmlTable != null && xmlTable.isSetJavaCustomizer())
        {
            configureViaTableCustomizer(errors, xmlTable.getJavaCustomizer());
        }
    }

    /** Applies XML metadata for everything, except for invoking any Java TableInfo customizer */
    protected void loadAllButCustomizerFromXML(QuerySchema schema, @Nullable TableType xmlTable, Collection<QueryException> errors)
    {
        if (xmlTable == null)
            return;

        //See issue 18592
        if (!StringUtils.isEmpty(xmlTable.getTableName()))
        {
            if (xmlTable.getTableName().equalsIgnoreCase(getName()))
            {
                setName(xmlTable.getTableName());
            }
            else
            {
                LOG.debug("Query name in XML metadata in schema '" + schema.getSchemaName() + "' did not match expected. Was: [" + xmlTable.getTableName() + "], expected: [" + getName() + "] in container " + schema.getContainer().getPath());
            }
        }
        if (xmlTable.getTitleColumn() != null)
            setTitleColumn(xmlTable.getTitleColumn());
        if (xmlTable.getDescription() != null)
            setDescription(xmlTable.getDescription());
        if (xmlTable.getTableTitle() != null)
            setTitle(xmlTable.getTableTitle());

        if (xmlTable.isSetGridUrl())
        {
            if (StringUtils.isBlank(xmlTable.getGridUrl()))
                _gridURL = LINK_DISABLER;
            else
                _gridURL = DetailsURL.fromString(xmlTable.getGridUrl(), null, errors);
        }

        if (xmlTable.isSetImportUrl())
        {
            if (StringUtils.isBlank(xmlTable.getImportUrl()))
                _importURL = LINK_DISABLER;
            else
                _importURL = DetailsURL.fromString(xmlTable.getImportUrl(), null, errors);
        }
        if (xmlTable.isSetInsertUrl())
        {
            if (StringUtils.isBlank(xmlTable.getInsertUrl()))
                _insertURL = LINK_DISABLER;
            else
                _insertURL = DetailsURL.fromString(xmlTable.getInsertUrl(), null, errors);
        }
        if (xmlTable.isSetUpdateUrl())
        {
            if (StringUtils.isBlank(xmlTable.getUpdateUrl()))
                _updateURL = LINK_DISABLER;
            else
                _updateURL = DetailsURL.fromString(xmlTable.getUpdateUrl(), null, errors);
        }
        if (xmlTable.isSetDeleteUrl())
        {
            if (StringUtils.isBlank(xmlTable.getDeleteUrl()))
                _deleteURL = LINK_DISABLER;
            else
                _deleteURL = DetailsURL.fromString(xmlTable.getDeleteUrl(), null, errors);
        }

        if (xmlTable.isSetTableUrl())
        {
            if (StringUtils.isBlank(xmlTable.getTableUrl()))
                _detailsURL = LINK_DISABLER;
            else
                _detailsURL = DetailsURL.fromString(xmlTable.getTableUrl(), null, errors);
        }

        if (xmlTable.isSetCacheSize())
            _cacheSize = xmlTable.getCacheSize();

        if(xmlTable.getImportMessage() != null)
            setImportMessage(xmlTable.getImportMessage());

        if(xmlTable.getImportTemplates() != null)
            setImportTemplates(xmlTable.getImportTemplates().getTemplateArray());

        // Optimization - only check for wrapped columns based on the "real" set of columns, not columns that are
        // resolved by resolveColumn() for backwards compatibility
        Set<String> columnNames = getColumnNameSet();

        if (xmlTable.getColumns() != null)
        {
            List<ColumnType> wrappedColumns = new ArrayList<>();

            for (ColumnType xmlColumn : xmlTable.getColumns().getColumnArray())
            {
                // this shouldn't happen since columnName is required
                if (null == xmlColumn.getColumnName())
                    throw new ConfigurationException("Table schema has column with empty columnName attribute: ", xmlTable.getTableName());

                if (xmlColumn.getWrappedColumnName() != null)
                {
                    wrappedColumns.add(xmlColumn);
                }
                else
                {
                    if (xmlColumn.getColumnName() != null && columnNames.contains(xmlColumn.getColumnName()))
                    {
                        ColumnInfo column = getColumn(xmlColumn.getColumnName());

                        if (column != null)
                        {
                            initColumnFromXml(schema, column, xmlColumn, errors);
                        }
                    }
                }
            }

            for (ColumnType wrappedColumnXml : wrappedColumns)
            {
                ColumnInfo column = getColumn(wrappedColumnXml.getWrappedColumnName());

                if (column != null && !getColumnNameSet().contains(wrappedColumnXml.getColumnName()))
                {
                    ColumnInfo wrappedColumn = new WrappedColumn(column, wrappedColumnXml.getColumnName());
                    initColumnFromXml(schema, wrappedColumn, wrappedColumnXml, errors);
                    addColumn(wrappedColumn);
                }
            }

            if (xmlTable.getUseColumnOrder())
            {
                // Reorder based on the sequence of columns in XML
                Map<String, ColumnInfo> originalColumns = constructColumnMap();
                originalColumns.putAll(_columnMap);
                for (ColumnInfo columnInfo : originalColumns.values())
                {
                    // Remove all the existing columns
                    removeColumn(columnInfo);
                }
                for (ColumnType xmlColumn : xmlTable.getColumns().getColumnArray())
                {
                    // Iterate through the ones in the XML and add them in the right order
                    ColumnInfo column = originalColumns.remove(xmlColumn.getColumnName());
                    if (column != null)
                    {
                        addColumn(column);
                    }
                }
                for (ColumnInfo column : originalColumns.values())
                {
                    // Readd the rest of the columns that weren't in the XML. It's backed by a LinkedHashMap, so they'll
                    // be in the same order they were in originally
                    addColumn(column);
                }
            }
        }

        if (xmlTable.getButtonBarOptions() != null)
            _buttonBarConfig = new ButtonBarConfig(xmlTable.getButtonBarOptions());

        if(xmlTable.getAggregateRowOptions() != null && xmlTable.getAggregateRowOptions().getPosition() != null)
        {
            setAggregateRowConfig(xmlTable);
        }

        if (xmlTable.getAuditLogging() != null)
        {
            AuditType.Enum auditBehavior = xmlTable.getAuditLogging();
            setAuditBehavior(AuditBehaviorType.valueOf(auditBehavior.toString()));
        }
    }

    protected void configureViaTableCustomizer(Collection<QueryException> errors, CustomizerType xmlCustomizer)
    {
        String className = xmlCustomizer.getClass1();
        if (className == null)
        {
            // For backwards compatibility with <13.2, check the text contents.
            XmlCursor cur = xmlCustomizer.newCursor();
            try
            {
                XmlCursor.TokenType tok = cur.toFirstContentToken();
                if (tok == XmlCursor.TokenType.TEXT)
                    className = cur.getTextValue();
            }
            finally
            {
                if (cur != null) cur.dispose();
            }
        }

        if (className == null)
            return;

        className = className.trim();
        if (className.isEmpty())
            return;

        try
        {
            Class c = Class.forName(className);
            if (!(TableCustomizer.class.isAssignableFrom(c)))
            {
                addAndLogError(errors, "Class " + c.getName() + " is not an implementation of " + TableCustomizer.class.getName() + " to configure table " + this, null);
            }
            else
            {
                Class<TableCustomizer> customizerClass = (Class<TableCustomizer>)c;
                try
                {
                    TableCustomizer customizer = customizerClass.newInstance();
                    customizer.customize(this);
                }
                catch (InstantiationException | IllegalAccessException e)
                {
                    addAndLogError(errors, "Unable to create instance of class '" + className + "'" + " to configure table " + this, e);
                }
            }
        }
        catch (ClassNotFoundException e)
        {
            addAndLogError(errors, "Unable to load class '" + className + "'" + " to configure table " + this, e);
        }
    }

    private static void addAndLogError(Collection<QueryException> errors, String message, Exception e)
    {
        errors.add(new QueryException(message, e));
        LOG.warn(message + ((e == null || e.getMessage() == null) ? "" : e.getMessage()));
    }

    private void setAggregateRowConfig(TableType xmlTable)
    {
        checkLocked();
        _aggregateRowConfig = new AggregateRowConfig(false, false);

        PositionTypeEnum.Enum position = xmlTable.getAggregateRowOptions().getPosition();
        if(position.equals(PositionTypeEnum.BOTH) || position.equals(PositionTypeEnum.TOP))
        {
            _aggregateRowConfig.setAggregateRowFirst(true);
        }
        if(position.equals(PositionTypeEnum.BOTH) || position.equals(PositionTypeEnum.BOTTOM))
        {
            _aggregateRowConfig.setAggregateRowLast(true);
        }
    }

    /**
     * Returns true by default. Override if your derived class is not accessible through Query
     * @return Whether this table is public (i.e., accessible via Query)
     */
    public boolean isPublic()
    {
        //by default, all subclasses are public (i.e., accessible through Query)
        //override to change this
        return getPublicName() != null && getPublicSchemaName() != null;
    }

    public String getPublicName()
    {
        return getName();
    }

    // XXX: Change to SchemaKey
    public String getPublicSchemaName()
    {
        return getSchema().getName();
    }

    public boolean needsContainerClauseAdded()
    {
        return true;
    }

    public ContainerFilter getContainerFilter()
    {
        return null;
    }

    public boolean isMetadataOverrideable()
    {
        return true;
    }

    public void overlayMetadata(String tableName, UserSchema schema, Collection<QueryException> errors)
    {
        checkLocked();
        if (isMetadataOverrideable())
        {
            Collection<TableType> metadata = QueryService.get().findMetadataOverride(schema, tableName, false, false, errors, null);
            overlayMetadata(metadata, schema, errors);
        }
    }

    public void overlayMetadata(Collection<TableType> metadata, UserSchema schema, Collection<QueryException> errors)
    {
        checkLocked();
        if (isMetadataOverrideable())
        {
            loadFromXML(schema, metadata, errors);
        }
    }

    public String getSelectName()
    {
        return null;
    }


    public ColumnInfo getLookupColumn(ColumnInfo parent, String name)
    {
        ForeignKey fk = parent.getFk();
        if (fk == null)
            return null;
        return fk.createLookupColumn(parent, name);
    }

    public int getCacheSize()
    {
        return _cacheSize;
    }

    @Nullable
    public Domain getDomain()
    {
        return null;
    }

    @Nullable
    public DomainKind getDomainKind()
    {
        Domain domain = getDomain();
        if (domain != null)
            return domain.getDomainKind();
        return null;
    }

    @Nullable
    public QueryUpdateService getUpdateService()
    {
        // UNDONE: consider allowing all query tables to be updated via update service
        //if (getTableType() == TableInfo.TABLE_TYPE_TABLE)
        //    return new DefaultQueryUpdateService(this);
        return null;
    }

    @Override @NotNull
    public Collection<QueryService.ParameterDecl> getNamedParameters()
    {
        return Collections.emptyList();
    }

    @Override
    public ContainerContext getContainerContext()
    {
        FieldKey fieldKey = getContainerFieldKey();
        if (fieldKey != null)
            return new ContainerContext.FieldKeyContext(fieldKey);

        return null;
    }

    /**
     * Return the FieldKey of the Container column for this table.
     * If the value is non-null then getContainerContext() will
     * return a FieldKeyContext using the container FieldKey.
     *
     * @return FieldKey of the Container column.
     */
    @Nullable
    public FieldKey getContainerFieldKey()
    {
        ColumnInfo col = getColumn("container");
        if (col == null)
            col = getColumn("folder");

        if (col != null && col.getJdbcType() == JdbcType.GUID)
            return col.getFieldKey();

        return null;
    }

    public boolean hasTriggers(Container c)
    {
        try
        {
            return null != getTableScript(c);
        }
        catch (ScriptException x)
        {
            return true;
        }
    }


    boolean scriptLoaded = false;
    ScriptReference tableScript = null;

    protected ScriptReference getTableScript(Container c) throws ScriptException
    {
        if (!scriptLoaded)
        {
            ScriptReference script = loadScript(c);
            tableScript = script;
            scriptLoaded = true;
        }
        return tableScript;
    }


    private ScriptReference loadScript(Container c) throws ScriptException
    {
        ScriptService svc = ServiceRegistry.get().getService(ScriptService.class);
        assert svc != null;
        if (svc == null)
            return null;

        if (getPublicSchemaName() == null || getName() == null)
            return null;

        // Create legal path name
        Path pathNew = new Path(QueryService.MODULE_QUERIES_DIRECTORY,
                FileUtil.makeLegalName(getPublicSchemaName()),
                FileUtil.makeLegalName(getName()) + ".js");

        // For backwards compat with 10.2
        Path pathOld = new Path(QueryService.MODULE_QUERIES_DIRECTORY,
                getPublicSchemaName().replaceAll("\\W", "_"),
                getName().replaceAll("\\W", "_") + ".js");

        Set<Path> paths = new HashSet<>();
        paths.add(pathNew);
        paths.add(pathOld);

        if (null != getTitle() && !getName().equals(getTitle()))
        {
            Path pathLabel = new Path(QueryService.MODULE_QUERIES_DIRECTORY,
                    FileUtil.makeLegalName(getPublicSchemaName()),
                    FileUtil.makeLegalName(getTitle()) + ".js");
            paths.add(pathLabel);
        }

        // UNDONE: get all table scripts instead of just first found
        Resource r = null;
        OUTER: for (Module m : c.getActiveModules())
        {
            for (Path p : paths)
            {
                r = m.getModuleResource(p);
                if (r != null && r.isFile())
                    break OUTER;
            }
        }
        if (r == null || !r.isFile())
            return null;

        return svc.compile(r);
    }


    protected <T> T invokeTableScript(Container c, Class<T> resultType, String methodName, Map<String, Object> extraContext, Object... args)
    {
        try
        {
            ScriptReference script = getTableScript(c);
            if (script == null)
                return null;

            if (!script.evaluated())
            {
                Map<String, Object> bindings = new HashMap<>();
                if (extraContext == null)
                    extraContext = new HashMap<>();
                bindings.put("extraContext", extraContext);
                bindings.put("schemaName", getPublicSchemaName());
                bindings.put("tableName", getPublicName());

                script.eval(bindings);
            }

            if (script.hasFn(methodName))
            {
                return script.invokeFn(resultType, methodName, args);
            }
        }
        catch (NoSuchMethodException e)
        {
            throw new UnexpectedException(e);
        }
        catch (ScriptException e)
        {
            throw new UnexpectedException(e);
        }

        return null;
    }

    private Object[] EMPTY_ARGS = new Object[0];

    @Override
    public void fireBatchTrigger(Container c, TriggerType type, boolean before, BatchValidationException batchErrors, Map<String, Object> extraContext)
            throws BatchValidationException
    {
        assert batchErrors != null;

        String triggerMethod = (before ? "init" : "complete");
        Boolean success = invokeTableScript(c, Boolean.class, triggerMethod, extraContext, type.name().toLowerCase(), batchErrors);
        if (success != null && !success)
            batchErrors.addRowError(new ValidationException(triggerMethod + " validation failed"));

        if (batchErrors.hasErrors())
            throw batchErrors;
    }

    @Override
    public void fireRowTrigger(Container c, TriggerType type, boolean before, int rowNumber,
                                  Map<String, Object> newRow, Map<String, Object> oldRow, Map<String, Object> extraContext)
            throws ValidationException
    {
        ValidationException errors = new ValidationException();
        errors.setSchemaName(getPublicSchemaName());
        errors.setQueryName(getName());
        errors.setRow(newRow);
        errors.setRowNumber(rowNumber);

        String triggerMethod = (before ? "before" : "after") + type.getMethodName();

        Object[] args = EMPTY_ARGS;
        if (before)
        {
            switch (type)
            {
                case SELECT: args = new Object[] { oldRow, errors };         break;
                case INSERT: args = new Object[] { newRow, errors };         break;
                case UPDATE: args = new Object[] { newRow, oldRow, errors }; break;
                case DELETE: args = new Object[] { oldRow, errors };         break;
            }
        }
        else
        {
            switch (type)
            {
                case SELECT: args = new Object[] { newRow, errors };         break;
                case INSERT: args = new Object[] { newRow, errors };         break;
                case UPDATE: args = new Object[] { newRow, oldRow, errors }; break;
                case DELETE: args = new Object[] { oldRow, errors };         break;
            }
        }
        Boolean success = invokeTableScript(c, Boolean.class, triggerMethod, extraContext, args);
        if (success != null && !success)
            errors.addGlobalError(triggerMethod + " validation failed");

        if (errors.hasErrors())
            throw errors;
    }


    /** TableInfo does not support DbCache by default */
    @Override
    public Path getNotificationKey()
    {
        return null;
    }

    protected void checkLocked()
    {
        if (_locked)
            throw new IllegalStateException("TableInfo is locked: " + getName());
    }

    boolean _locked;

    @Override
    public void setLocked(boolean b)
    {
        if (_locked && !b)
            throw new IllegalStateException("Can't unlock table: " + getName());
        _locked = b;
        // set columns in the column list as locked, lookup columns created later are not locked
        for (ColumnInfo c : getColumns())
            c.setLocked(b);
    }

    @Override
    public boolean isLocked()
    {
        return _locked;
    }

    @Override
    public void setAuditBehavior(AuditBehaviorType type)
    {
        checkLocked();
        _auditBehaviorType = type;
    }

    @Override
    public AuditBehaviorType getAuditBehavior()
    {
        return _auditBehaviorType;
    }

    @Override
    public FieldKey getAuditRowPk()
    {
        if (_auditRowPk == null)
        {
            List<String> pks = getPkColumnNames();
            if (pks.size() == 1)
                _auditRowPk = FieldKey.fromParts(pks.get(0));
            else if (getColumn(FieldKey.fromParts("EntityId")) != null)
                _auditRowPk = FieldKey.fromParts("EntityId");
            else if (getColumn(FieldKey.fromParts("RowId")) != null)
                _auditRowPk = FieldKey.fromParts("RowId");
        }
        return _auditRowPk;
    }

    @Override
    public boolean supportsContainerFilter()
    {
        return this instanceof ContainerFilterable;
    }

    @Override
    public String getImportMessage()
    {
        return _importMsg;
    }

    public void setImportMessage(String msg)
    {
        checkLocked();
        _importMsg = msg;
    }

    @Override
    public List<Pair<String, String>> getImportTemplates(ViewContext ctx)
    {
        List<Pair<String, String>> templates = new ArrayList<>();
        //NOTE: should this create a RenderContext from viewContext instead?
        //RenderContext rc = new RenderContext(ctx);
        Map<String, Container> renderCtx = Collections.singletonMap("container", ctx.getContainer());

        if(_importTemplates != null)
        {
            for (Pair<String, StringExpression> pair : _importTemplates)
            {
                if(pair.second instanceof DetailsURL)
                    templates.add(Pair.of(pair.first, ((DetailsURL)pair.second).copy(ctx.getContainer()).getActionURL().toString()));
                else if (pair.second instanceof StringExpressionFactory.URLStringExpression)
                    templates.add(Pair.of(pair.first, ((StringExpressionFactory.URLStringExpression)pair.second).eval(renderCtx)));
            }
        }

        if (templates.size() == 0)
        {
            ActionURL url = PageFlowUtil.urlProvider(QueryUrls.class).urlCreateExcelTemplate(ctx.getContainer(), getPublicSchemaName(), getName());
            url.addParameter("captionType", ExcelWriter.CaptionType.Name.name());
            if(url != null)
                templates.add(Pair.of("Download Template", url.toString()));
        }

        return templates;
    }

    @Override
    public List<Pair<String, StringExpression>> getRawImportTemplates()
    {
        return _importTemplates;
    }

    public void setImportTemplates(ImportTemplateType[] templates)
    {
        checkLocked();
        List<Pair<String, StringExpression>> list = new ArrayList<>();
        for (ImportTemplateType t : templates)
        {
            StringExpression url = StringExpressionFactory.createURL(t.getUrl());
            list.add(Pair.of(t.getLabel(), url));
        }


        _importTemplates = list;
    }

    @Override
    public <R, P> R accept(SchemaTreeVisitor<R, P> visitor, SchemaTreeVisitor.Path path, P param)
    {
        return visitor.visitTable(this, path, param);
    }

    @Override
    public String toMemTrackerString()
    {
        return getClass().getName() + ": " + getSchema().getName() + "." + getName();
    }
}
