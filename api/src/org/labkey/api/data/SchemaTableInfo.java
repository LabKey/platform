/*
 * Copyright (c) 2004-2017 Fred Hutchinson Cancer Research Center
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.DbCache;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.collections.NamedObjectList;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.dataiterator.TableInsertDataIterator;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.gwt.client.AuditBehaviorType;
import org.labkey.api.query.AggregateRowConfig;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUrls;
import org.labkey.api.query.SchemaTreeVisitor;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.util.ContainerContext;
import org.labkey.api.util.MinorConfigurationException;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;
import org.labkey.api.util.SimpleNamedObject;
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.data.xml.AuditType;
import org.labkey.data.xml.ImportTemplateType;
import org.labkey.data.xml.TableCustomizerType;
import org.labkey.data.xml.TableType;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * A thin wrapper over a table in the real underlying database. Includes JDBC-provided metadata and configuration
 * performed in schema-based XML file.
 */
public class SchemaTableInfo implements TableInfo, UpdateableTableInfo, AuditConfigurable
{
    // Table properties
    private final DbSchema _parentSchema;
    private final SQLFragment _selectName;
    private final String _metaDataName;
    private final DatabaseTableType _tableType;
    private final Path _notificationKey;

    private String _name;
    private String _description;
    private String _title = null;
    private int _cacheSize = DbCache.DEFAULT_CACHE_SIZE;
    private DetailsURL _gridURL;
    private DetailsURL _insertURL;
    private DetailsURL _importURL;
    private DetailsURL _deleteURL;
    private DetailsURL _updateURL;
    private DetailsURL _detailsURL;
    private ButtonBarConfig _buttonBarConfig;
    private AggregateRowConfig _aggregateRowConfig;
    private boolean _hidden;
    private String _importMsg;
    private List<Pair<String, StringExpression>> _importTemplates;
    private Boolean _hasDbTriggers;

    // Column-related
    private TableType _xmlTable = null;
    private SchemaColumnMetaData _columnMetaData = null;
    private final Object _columnLock = new Object();
    private String _versionColumnName = null;
    private List<FieldKey> _defaultVisibleColumns = null;
    private AuditBehaviorType _auditBehaviorType = AuditBehaviorType.NONE;
    private FieldKey _auditRowPk;

    protected boolean _autoLoadMetaData = true;      // TODO: Remove this? DatasetSchemaTableInfo is the only user of this.

    public SchemaTableInfo(DbSchema parentSchema, DatabaseTableType tableType, String tableName, String metaDataName, String selectName)
    {
        this(parentSchema, tableType, tableName, metaDataName, selectName, null);
    }

    public SchemaTableInfo(DbSchema parentSchema, DatabaseTableType tableType, String tableName, String metaDataName, String selectName, @Nullable String title)
    {
        _parentSchema = parentSchema;
        _name = tableName;
        _metaDataName = metaDataName;
        _selectName = new SQLFragment(selectName);
        _tableType = tableType;
        _notificationKey = new Path(parentSchema.getClass().getName(), parentSchema.getName(), getClass().getName(), getName());
        _title = title;
    }


    public SchemaTableInfo(DbSchema parentSchema, DatabaseTableType tableType, String tableMetaDataName)
    {
        this(parentSchema, tableType, tableMetaDataName, tableMetaDataName, parentSchema.getSqlDialect().getSelectNameFromMetaDataName(parentSchema.getName()) + "." + parentSchema.getSqlDialect().getSelectNameFromMetaDataName(tableMetaDataName));
    }

    /**
     * Fixup the container context on table and column URLs.
     * This is the same as {@link AbstractTableInfo#afterConstruct()} except it is performed once before being cached in DbSchema.
     */
    /* package */ void afterConstruct()
    {
        checkLocked();

        ContainerContext cc = getContainerContext();
        if (null != cc)
        {
            for (ColumnInfo c : getColumns())
            {
                StringExpression url = c.getURL();
                if (url instanceof DetailsURL && url != AbstractTableInfo.LINK_DISABLER)
                    ((DetailsURL)url).setContainerContext(cc, false);
            }
            if (_detailsURL != null && _detailsURL != AbstractTableInfo.LINK_DISABLER)
            {
                _detailsURL.setContainerContext(cc, false);
            }
            if (_updateURL != null && _updateURL != AbstractTableInfo.LINK_DISABLER)
            {
                _updateURL.setContainerContext(cc, false);
            }
        }
    }

    TableType getXmlTable()
    {
        return _xmlTable;
    }

    public String getName()
    {
        return _name;
    }

    protected void setTitle(String title)
    {
        checkLocked();
        _title = title;
    }

    public String getTitle()
    {
        return _title == null ? _name : _title;
    }

    public String getTitleField()
    {
        return _title;
    }

    @Override
    public String getMetaDataName()
    {
        return _metaDataName;
    }


    @Override
    public String getSelectName()
    {
        return _selectName.getSQL();
    }


    @NotNull
    public SQLFragment getFromSQL()
    {
        return new SQLFragment().append("SELECT * FROM ").append(_selectName);
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

     public DbSchema getSchema()
    {
        return _parentSchema;
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

    /** getSchema().getSqlDialect() */
    public SqlDialect getSqlDialect()
    {
        return _parentSchema.getSqlDialect();
    }


    @Override
    public List<String> getPkColumnNames()
    {
        return getColumnMetaData().getPkColumnNames();
    }

    @Override @NotNull
    public List<ColumnInfo> getPkColumns()
    {
        return getColumnMetaData().getPkColumns();
    }

    @Override @NotNull
    public Map<String, Pair<IndexType, List<ColumnInfo>>> getUniqueIndices()
    {
        return getColumnMetaData().getUniqueIndices();
    }

    @NotNull
    @Override
    public Map<String, Pair<IndexType, List<ColumnInfo>>> getAllIndices()
    {
        return getColumnMetaData().getAllIndices();
    }

    @NotNull
    @Override
    public List<ColumnInfo> getAlternateKeyColumns()
    {
        return getPkColumns();
    }


    @Override
    public ColumnInfo getVersionColumn()
    {
        String versionColumnName = getVersionColumnName();

        return null == versionColumnName ? null : getColumn(versionColumnName);
    }


    @Override
    public String getVersionColumnName()
    {
        if (null == _versionColumnName)
        {
            if (null != getColumn("_ts"))
                _versionColumnName = "_ts";
            else if (null != getColumn("Modified"))
                _versionColumnName = "Modified";
        }

        return _versionColumnName;
    }

    @Override
    public boolean hasDefaultTitleColumn()
    {
        return getColumnMetaData().hasDefaultTitleColumn();
    }

    @Override
    public String getTitleColumn()
    {
        return getColumnMetaData().getTitleColumn();
    }

    @Override
    public DatabaseTableType getTableType()
    {
        return _tableType;
    }

    public int getCacheSize()
    {
        return _cacheSize;
    }

    public String toString()
    {
        return _selectName.getSQL();
    }


    public NamedObjectList getSelectList(String columnName)
    {
        if (columnName == null)
            return getSelectList();

        ColumnInfo column = getColumn(columnName);
        if (column == null /*|| column.isKeyField()*/)
            return new NamedObjectList();

        return getSelectList(Collections.singletonList(column.getName()));
    }


    public NamedObjectList getSelectList()
    {
        return getSelectList(getPkColumnNames());
    }


    private NamedObjectList getSelectList(List<String> columnNames)
    {
        StringBuilder pkColumnSelect = new StringBuilder();
        String sep = "";

        for (String columnName : columnNames)
        {
            pkColumnSelect.append(sep);
            pkColumnSelect.append(columnName);
            sep = "+','+";
        }

        String cacheKey = "selectArray:" + pkColumnSelect;
        NamedObjectList list = (NamedObjectList) DbCache.get(this, cacheKey);

        if (null != list)
            return list;

        String titleColumn = getTitleColumn();

        final NamedObjectList newList = new NamedObjectList();
        String sql = "SELECT " + pkColumnSelect + " AS VALUE, " + titleColumn + " AS TITLE FROM " + _selectName.getSQL() + " ORDER BY " + titleColumn;

        new SqlSelector(_parentSchema, sql).forEach(rs -> newList.put(new SimpleNamedObject(rs.getString(1), rs.getString(2))));

        DbCache.put(this, cacheKey, newList, getSelectListTimeout());

        return newList;
    }


    private long _selectListTimeout = TimeUnit.MINUTES.toMillis(1);

    private long getSelectListTimeout()
    {
        return _selectListTimeout;
    }

    @Override
    public boolean hasContainerColumn()
    {
        return true;
    }

    @Override
    public ColumnInfo getColumn(@NotNull String colName)
    {
        return getColumnMetaData().getColumn(colName);
    }


    @Override
    public ColumnInfo getColumn(@NotNull FieldKey name)
    {
        if (null != name.getParent())
            return null;
        return getColumn(name.getName());
    }


    private SchemaColumnMetaData getColumnMetaData()
    {
        synchronized (_columnLock)
        {
            if (null == _columnMetaData)
            {
                try
                {
                    _columnMetaData = createSchemaColumnMetaData();
                }
                catch (SQLException e)
                {
                    String message = e.getMessage();

                    // See #14374  TODO: I don't think this will be thrown anymore... will likely be a Spring DAO exception instead
                    if ("com.mysql.jdbc.exceptions.jdbc4.MySQLSyntaxErrorException".equals(e.getClass().getName()) && message.startsWith("SELECT command denied to user"))
                        throw new MinorConfigurationException("The LabKey database user doesn't have permissions to access this table", e);
                    else
                        throw new RuntimeSQLException(e);
                }
            }

            return _columnMetaData;
        }
    }

    protected SchemaColumnMetaData createSchemaColumnMetaData() throws SQLException
    {
        return new SchemaColumnMetaData(this, _autoLoadMetaData);
    }


    public TableCustomizerType getJavaCustomizer()
     {
         if (_xmlTable == null || !_xmlTable.isSetJavaCustomizer())
         {
             return null;
         }
         return _xmlTable.getJavaCustomizer();
     }


    @NotNull
    public List<ColumnInfo> getColumns()
    {
        return Collections.unmodifiableList(getColumnMetaData().getColumns());
    }


    public List<ColumnInfo> getUserEditableColumns()
    {
        return getColumnMetaData().getUserEditableColumns();
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
            ColumnInfo col = getColumn(name.trim());
            if (col != null)
                ret.add(col);
        }

        return Collections.unmodifiableList(ret);
    }


    public Set<String> getColumnNameSet()
    {
        return getColumnMetaData().getColumnNameSet();
    }

    @Override
    public boolean supportsAuditTracking()
    {
        return true;
    }

    @Override
     public void setAuditBehavior(AuditBehaviorType type)
     {
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

     public void copyToXml(TableType xmlTable, boolean bFull)
    {
        xmlTable.setTableName(_name);
        xmlTable.setTableDbType(_tableType.name());

        if (bFull)
        {
            // changed to write out the value of property directly, without the
            // default calculation applied by the getter
            if (null != _title)
                xmlTable.setTableTitle(_title);
            if (_hidden)
                xmlTable.setHidden(true);
            if (null != _versionColumnName)
                xmlTable.setVersionColumnName(_versionColumnName);
        }

        getColumnMetaData().copyToXml(xmlTable, bFull);
    }


    public void loadTablePropertiesFromXml(TableType xmlTable)
    {
        loadTablePropertiesFromXml(xmlTable, false);
    }
    public void loadTablePropertiesFromXml(TableType xmlTable, boolean dontSetName)
    {
        checkLocked();
        //Override with the table name from the schema so casing is nice...
        if (!dontSetName)
            _name = xmlTable.getTableName();
        _description = xmlTable.getDescription();
        _hidden = xmlTable.getHidden();
        _title = xmlTable.getTableTitle();
        if (xmlTable.isSetCacheSize())
            _cacheSize = xmlTable.getCacheSize();

        if (xmlTable.getImportMessage() != null)
            setImportMessage(xmlTable.getImportMessage());

        if (xmlTable.getImportTemplates() != null)
            setImportTemplates(xmlTable.getImportTemplates().getTemplateArray());

        if (xmlTable.isSetGridUrl())
            _gridURL = DetailsURL.fromXML(xmlTable.getGridUrl(), null);

        if (xmlTable.isSetImportUrl())
            _importURL = DetailsURL.fromXML(xmlTable.getImportUrl(), null);

        if (xmlTable.isSetInsertUrl())
            _insertURL = DetailsURL.fromXML(xmlTable.getInsertUrl(), null);

        if (xmlTable.isSetUpdateUrl())
            _updateURL = DetailsURL.fromXML(xmlTable.getUpdateUrl(), null);

        if (xmlTable.isSetDeleteUrl())
            _deleteURL = DetailsURL.fromXML(xmlTable.getDeleteUrl(), null);

        if (xmlTable.isSetTableUrl())
            _detailsURL = DetailsURL.fromXML(xmlTable.getTableUrl(), null);


        if (xmlTable.getButtonBarOptions() != null)
            _buttonBarConfig = new ButtonBarConfig(xmlTable.getButtonBarOptions());

        if (xmlTable.getAuditLogging() != null)
        {
            AuditType.Enum auditBehavior = xmlTable.getAuditLogging();
            setAuditBehavior(AuditBehaviorType.valueOf(auditBehavior.toString()));
        }

        // Stash so we can overlay ColumnInfo properties later
        _xmlTable = xmlTable;
    }


    public ActionURL getGridURL(Container container)
    {
        if (_gridURL != null)
            return _gridURL.copy(container).getActionURL();
        return null;
    }

    public ActionURL getInsertURL(Container container)
    {
        if (_insertURL == null)
            return null;
        if (AbstractTableInfo.LINK_DISABLER == _insertURL)
            return AbstractTableInfo.LINK_DISABLER_ACTION_URL;
        return _insertURL.copy(container).getActionURL();
    }

    @Override
    public ActionURL getImportDataURL(Container container)
    {
        if (null == _importURL)
            return null;
        if (AbstractTableInfo.LINK_DISABLER == _importURL)
            return AbstractTableInfo.LINK_DISABLER_ACTION_URL;
        return _importURL.copy(container).getActionURL();
    }

    public ActionURL getDeleteURL(Container container)
    {
        if (_deleteURL == null)
            return null;
        if (AbstractTableInfo.LINK_DISABLER == _deleteURL)
            return AbstractTableInfo.LINK_DISABLER_ACTION_URL;
        return _deleteURL.copy(container).getActionURL();
    }

    public StringExpression getUpdateURL(@Nullable Set<FieldKey> columns, Container container)
    {
        if (_updateURL == null)
            return null;
        if (AbstractTableInfo.LINK_DISABLER == _updateURL)
            return AbstractTableInfo.LINK_DISABLER;

        ContainerContext containerContext = getContainerContext();
        if (containerContext == null)
            containerContext = container;

        if (columns == null || _updateURL.validateFieldKeys(columns))
            return _updateURL.copy(containerContext);

        return null;
    }

    public StringExpression getDetailsURL(Set<FieldKey> columns, Container container)
    {
        // First null check is critical for server startup... can't initialize LINK_DISABLER until first request
        if (null == _detailsURL || _detailsURL == AbstractTableInfo.LINK_DISABLER)
        {
            return null;
        }

        ContainerContext containerContext = getContainerContext();
        if (containerContext == null)
            containerContext = container;

        if (columns == null || _detailsURL.validateFieldKeys(columns))
            return _detailsURL.copy(containerContext);

        return null;
    }

    @Override
    public boolean hasDetailsURL()
    {
        return _detailsURL != null;
    }

    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        return false;
    }

    public MethodInfo getMethod(String name)
    {
        return null;
    }

    public List<FieldKey> getDefaultVisibleColumns()
    {
        if (_defaultVisibleColumns != null)
            return _defaultVisibleColumns;
        return Collections.unmodifiableList(QueryService.get().getDefaultVisibleColumns(getColumns()));
    }

    public void setDefaultVisibleColumns(@Nullable Iterable<FieldKey> keys)
    {
        checkLocked();
        if (keys == null)
        {
            _defaultVisibleColumns = null;
        }
        else
        {
            _defaultVisibleColumns = new ArrayList<>();
            for (FieldKey key : keys)
                _defaultVisibleColumns.add(key);
        }
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


    /** Used by SimpleUserSchema and external schemas to hide tables from the list of visible tables.  Not the same as isPublic(). */
    public boolean isHidden()
    {
        return _hidden;
    }

    public boolean isPublic()
    {
        //schema table infos are not public (i.e., not accessible from Query)
        return false;
    }

    public String getPublicName()
    {
        return null;
    }

    public String getPublicSchemaName()
    {
        return null;
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
        return false;
    }

    @Override
    public void overlayMetadata(String tableName, UserSchema schema, Collection<QueryException> errors)
    {
        checkLocked();
        // no-op, we don't support metadata overrides
    }

    @Override
    public void overlayMetadata(Collection<TableType> metadata, UserSchema schema, Collection<QueryException> errors)
    {
        checkLocked();
        // no-op, we don't support metadata overrides
    }

    public ButtonBarConfig getButtonBarConfig()
    {
        return _buttonBarConfig;
    }

    public ColumnInfo getLookupColumn(ColumnInfo parent, String name)
    {
        ForeignKey fk = parent.getFk();
        if (fk == null)
            return null;
        return fk.createLookupColumn(parent, name);
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

    @Nullable
    public Domain getDomain()
    {
        return null;
    }

    @Nullable
    public DomainKind getDomainKind()
    {
        return null;
    }

    @Nullable
    public QueryUpdateService getUpdateService()
    {
        return null;
    }

    @Override @NotNull
    public Collection<QueryService.ParameterDecl> getNamedParameters()
    {
        return Collections.emptyList();
    }

    @Override
    public void fireBatchTrigger(Container c, TriggerType type, boolean before, BatchValidationException errors, Map<String, Object> extraContext) throws BatchValidationException
    {
        throw new UnsupportedOperationException("Table triggers not yet supported on schema tables");
    }

    @Override
    public void fireRowTrigger(Container c, TriggerType type, boolean before, int rowNumber, Map<String, Object> newRow, Map<String, Object> oldRow, Map<String, Object> extraContext) throws ValidationException
    {
        throw new UnsupportedOperationException("Table triggers not yet supported on schema tables");
    }

    @Override
    public boolean hasTriggers(Container c)
    {
        return false;
    }

    @Override
    public void resetTriggers(Container c)
    {
        throw new UnsupportedOperationException("Table triggers not yet supported on schema tables");
    }

    @Override
    public boolean hasDbTriggers()
    {
        if (_hasDbTriggers == null)
        {
            _hasDbTriggers = getSchema().getSqlDialect().hasTriggers(getSchema(), getSchema().getName(), getName());
        }
        return _hasDbTriggers;
    }

    //
    // UpdateableTableInfo
    //

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
        return true;
    }

    @Override
    public TableInfo getSchemaTableInfo()
    {
        return this;
    }

    @Override
    public ObjectUriType getObjectUriType()
    {
        return ObjectUriType.schemaColumn;
    }

    @Override
    public String getObjectURIColumnName()
    {
        return null;
    }

    @Override
    public String getObjectIdColumnName()
    {
        return null;
    }

    @Override
    public CaseInsensitiveHashMap<String> remapSchemaColumns()
    {
        return null;
    }

    @Override
    public CaseInsensitiveHashSet skipProperties()
    {
        return null;
    }

    @Override
    public DataIteratorBuilder persistRows(DataIteratorBuilder data, DataIteratorContext context)
    {
        return TableInsertDataIterator.create(data, this, null, context);
    }

    @Override
    public Parameter.ParameterMap insertStatement(Connection conn, User user) throws SQLException
    {
        return StatementUtils.insertStatement(conn, this, null, user, false, true);
    }

    @Override
    public Parameter.ParameterMap updateStatement(Connection conn, User user, Set<String> columns) throws SQLException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Parameter.ParameterMap deleteStatement(Connection conn) throws SQLException
    {
        return Table.deleteStatement(conn, this);
    }

    // TODO: Eliminate these setters/modifiers, making SchemaTableInfo effectively immutable... which would improve
    // concurrency, caching, performance, and maintainability

    protected void addColumn(ColumnInfo c)
    {
        checkLocked();
        getColumnMetaData().addColumn(c);
    }

    // Move an existing column to a different spot in the ordered list
    public void setColumnIndex(ColumnInfo c, int i)
    {
        checkLocked();
        getColumnMetaData().setColumnIndex(c, i);
    }

    public void setPkColumnNames(@NotNull List<String> pkColumnNames)
    {
        checkLocked();
        getColumnMetaData().setPkColumnNames(pkColumnNames);
    }

    @Override
    public Path getNotificationKey()
    {
        return _notificationKey;
    }

    private void checkLocked()
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
        List<ColumnInfo> cols = getColumns(); // may have side-effects
        _locked = b;
        for (ColumnInfo c : cols)
            c.setLocked(b);
    }

    @Override
    public boolean isLocked()
    {
        return _locked;
    }

    @Override
    public boolean supportsContainerFilter()
    {
        return this instanceof ContainerFilterable;
    }

    @Override
    public boolean hasUnionTable()
    {
        return false;
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

        if (_importTemplates != null)
        {
            for (Pair<String, StringExpression> pair : _importTemplates)
            {
                if (pair.second instanceof DetailsURL)
                    templates.add(Pair.of(pair.first, ((DetailsURL)pair.second).copy(ctx.getContainer()).getActionURL().toString()));
                else if (pair.second instanceof StringExpressionFactory.URLStringExpression)
                    templates.add(Pair.of(pair.first, pair.second.eval(renderCtx)));
            }
        }

        if (templates.size() == 0)
        {
            URLHelper url = PageFlowUtil.urlProvider(QueryUrls.class).urlCreateExcelTemplate(ctx.getContainer(), getPublicSchemaName(), getName());
            url.addParameter("headerType", ColumnHeaderType.Name.name()); // CONSIDER: Use DisplayFieldKey instead
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
    public ContainerContext getContainerContext()
    {
        FieldKey fieldKey = getContainerFieldKey();
        if (fieldKey != null)
            return new ContainerContext.FieldKeyContext(fieldKey);

        return null;
    }

    public FieldKey getContainerFieldKey()
    {
        ColumnInfo col = getColumn("container");
        return null==col ? null : col.getFieldKey();
    }

    @Override
    public <R, P> R accept(SchemaTreeVisitor<R, P> visitor, SchemaTreeVisitor.Path path, P param)
    {
        // Skip visiting
        return null;
    }

    @Override
    public UserSchema getUserSchema()
    {
        return null;
    }

    @Override
    public Set<ColumnInfo> getAllInvolvedColumns(Collection<ColumnInfo> selectColumns)
    {
        Set<ColumnInfo> allInvolvedColumns = new HashSet<>();
        for (ColumnInfo column : selectColumns)
            allInvolvedColumns.add(column);
        return allInvolvedColumns;
    }
}
