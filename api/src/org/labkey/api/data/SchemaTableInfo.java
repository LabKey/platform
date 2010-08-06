/*
 * Copyright (c) 2004-2010 Fred Hutchinson Cancer Research Center
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

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.DbCache;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.NamedObjectList;
import org.labkey.api.query.*;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.util.SimpleNamedObject;
import org.labkey.api.util.StringExpression;
import org.labkey.api.view.ActionURL;
import org.labkey.data.xml.ColumnType;
import org.labkey.data.xml.TableType;

import java.io.IOException;
import java.io.Writer;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;


public class SchemaTableInfo implements TableInfo
{
    private static final Logger _log = Logger.getLogger(SchemaTableInfo.class);

    String name;
    String title = null;
    String titleColumn = null;
    protected List<String> _pkColumnNames = new ArrayList<String>();
    List<ColumnInfo> _pkColumns;
    protected ArrayList<ColumnInfo> columns = new ArrayList<ColumnInfo>();
    protected Map<String, ColumnInfo> colMap = null;
    DbSchema parentSchema;
    private int _tableType = TABLE_TYPE_NOT_IN_DB;
    private String _versionColumnName = null;
    private String metaDataName = null;
    private List<FieldKey> defaultVisibleColumns = null;
    private String _description;

    protected SQLFragment selectName = null;
    private String _sequence = null;
    private int _cacheSize = DbCache.DEFAULT_CACHE_SIZE;

    private DetailsURL _gridURL;
    private DetailsURL _insertURL;
    private DetailsURL _updateURL;
    private DetailsURL _detailsURL;
    protected ButtonBarConfig _buttonBarConfig;

    protected SchemaTableInfo(DbSchema parentSchema)
    {
        this.parentSchema = parentSchema;
    }


    public SchemaTableInfo(String tableName, DbSchema parentSchema)
    {
        this(parentSchema);

        this.name = tableName;
        String name = getSqlDialect().makeLegalIdentifier(parentSchema.getName())
                + "." + getSqlDialect().makeLegalIdentifier(tableName);
        this.selectName = new SQLFragment(name);
    }

    public String getName()
    {
        return name;
    }

    public String getMetaDataName()
    {
        return metaDataName;
    }


    public void setMetaDataName(String metaDataName)
    {
        this.metaDataName = metaDataName;
    }


    public String getSelectName()
    {
        return selectName.getSQL();
    }


    @NotNull
    public SQLFragment getFromSQL()
    {
        return new SQLFragment().append("SELECT * FROM ").append(selectName);
    }


    public DbSchema getSchema()
    {
        return parentSchema;
    }


    /** getSchema().getSqlDialect() */
    public SqlDialect getSqlDialect()
    {
        return parentSchema.getSqlDialect();
    }


    public List<String> getPkColumnNames()
    {
        return _pkColumnNames;
    }

    public void setPkColumnNames(List<String> pkColumnNames)
    {
        _pkColumnNames = pkColumnNames;
        _pkColumns = null;
    }

    public List<ColumnInfo> getPkColumns()
    {
        if (null == _pkColumnNames)
            return null;

        if (null == _pkColumns)
        {
            List<ColumnInfo> cols = new ArrayList<ColumnInfo>(_pkColumnNames.size());

            for (String name : _pkColumnNames)
            {
                ColumnInfo col = getColumn(name);
                assert null != col;
                cols.add(col);
            }

            _pkColumns = Collections.unmodifiableList(cols);
        }

        return _pkColumns;
    }


    public ColumnInfo getVersionColumn()
    {
        if (null == _versionColumnName)
        {
            if (null != getColumn("_ts"))
                _versionColumnName = "_ts";
            else if (null != getColumn("Modified"))
                _versionColumnName = "Modified";
        }

        return null == _versionColumnName ? null : getColumn(_versionColumnName);
    }


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


    public void setVersionColumnName(String colName)
    {
        _versionColumnName = colName;
    }

    public String getTitleColumn()
    {
        if (null == titleColumn && !columns.isEmpty())
        {
            for (ColumnInfo column : columns)
            {
                if (column.isStringType() && !column.getSqlTypeName().equalsIgnoreCase("entityid"))
                {
                    titleColumn = column.getName();
                    break;
                }
            }
            if (null == titleColumn)
                titleColumn = columns.get(0).getName();
        }

        return titleColumn;
    }

    public int getTableType()
    {
        return _tableType;
    }

    public int getCacheSize()
    {
        return _cacheSize;
    }

    public String toString()
    {
        return selectName.toString();
    }


    void setTableType(String tableType)
    {
        if (tableType.equals("TABLE"))
            _tableType = TABLE_TYPE_TABLE;
        else if (tableType.equals("VIEW"))
            _tableType = TABLE_TYPE_VIEW;
        else
            _tableType = TABLE_TYPE_NOT_IN_DB;
    }

    public void setTableType(int tableType)
    {
        _tableType = tableType;
    }

    public NamedObjectList getSelectList(String columnName)
    {
        if (columnName == null)
            return getSelectList();
        
        ColumnInfo column = getColumn(columnName);
        if (column == null /*|| column.isKeyField()*/)
            return new NamedObjectList();

        return getSelectList(Collections.<String>singletonList(column.getName()));
    }


    public NamedObjectList getSelectList()
    {
        return getSelectList(getPkColumnNames());
    }

    private NamedObjectList getSelectList(List<String> columnNames)
    {
        StringBuffer pkColumnSelect = new StringBuffer();
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

        ResultSet rs = null;
        list = new NamedObjectList();
        String sql = null;

        try
        {
            sql = "SELECT " + pkColumnSelect + " AS VALUE, " + titleColumn + " AS TITLE FROM " + selectName.getSQL() + " ORDER BY " + titleColumn;

            rs = Table.executeQuery(parentSchema, sql, null);

            while (rs.next())
            {
                list.put(new SimpleNamedObject(rs.getString(1), rs.getString(2)));
            }
        }
        catch (SQLException e)
        {
            _log.error(this + "\n" + sql, e);
        }
        finally
        {
            if (null != rs)
                try
                {
                    rs.close();
                }
                catch (SQLException x)
                {
                    _log.error("getSelectList", x);
                }
        }

        DbCache.put(this, cacheKey, list);
        return list;
    }

    /** getSelectList().get(pk) */
    public String getRowTitle(Object pk) throws SQLException
    {
        NamedObjectList selectList = getSelectList();
        Object title = selectList.get(pk.toString());
        return title == null ? "" : title.toString();
    }

    // UNDONE: throwing Exception is not great, why not just return NULL???
    public ColumnInfo getColumn(String colName)
    {
        if (null == colName)
            return null;

        // HACK: need to invalidate in case of addition (doesn't handle mixed add/delete, but I don't think we delete
        if (colMap != null && columns.size() != colMap.size())
            colMap = null;

        if (null == colMap)
        {
            Map<String, ColumnInfo> m = new CaseInsensitiveHashMap<ColumnInfo>();
            for (ColumnInfo colInfo : columns)
            {
                m.put(colInfo.getName(), colInfo);
            }
            colMap = m;
        }

        // TODO: Shouldn't do this -- ":" is a legal character in column names
        int colonIndex;
        if ((colonIndex = colName.indexOf(":")) != -1)
        {
            String first = colName.substring(0, colonIndex);
            String rest = colName.substring(colonIndex + 1);
            ColumnInfo fkColInfo = colMap.get(first);

            // Fall through if this doesn't look like an FK -- : is a legal character
            if (fkColInfo != null && fkColInfo.getFk() != null)
                return fkColInfo.getFkTableInfo().getColumn(rest);
        }

        return colMap.get(colName);
    }


    public void addColumn(ColumnInfo column)
    {
        columns.add(column);
//        assert !column.isAliasSet();       // TODO: Investigate -- had to comment this out since ExprColumn() sets alias
        assert null == column.getFieldKey().getParent();
        assert column.getName().equals(column.getFieldKey().getName());
        assert column.lockName();
        // set alias explicitly, so that getAlias() won't call makeLegalName() and mangle it
        column.setAlias(column.getName());
    }


    public List<ColumnInfo> getColumns()
    {
        return Collections.unmodifiableList(columns);
    }


    public List<ColumnInfo> getUserEditableColumns()
    {
        ArrayList<ColumnInfo> userEditableColumns = new ArrayList<ColumnInfo>(columns.size());
        for (ColumnInfo col : columns)
            if (col.isUserEditable())
                userEditableColumns.add(col);

        return Collections.unmodifiableList(userEditableColumns);
    }


    public List<ColumnInfo> getColumns(String colNames)
    {
        String[] colNameArray = colNames.split(",");
        return getColumns(colNameArray);
    }

    public List<ColumnInfo> getColumns(String... colNameArray)
    {
        List<ColumnInfo> ret = new ArrayList<ColumnInfo>(colNameArray.length);
        for (String name : colNameArray)
        {
            ret.add(getColumn(name.trim()));
        }
        return Collections.unmodifiableList(ret);
    }


    public Set<String> getColumnNameSet()
    {
        Set<String> nameSet = new HashSet<String>();
        for (ColumnInfo aColumnList : columns)
        {
            nameSet.add(aColumnList.getName());
        }

        return Collections.unmodifiableSet(nameSet);
    }


    public void loadFromMetaData(DatabaseMetaData dbmd, String catalogName, String schemaName) throws SQLException
    {
        loadColumnsFromMetaData(dbmd, catalogName, schemaName);
        ResultSet rs = dbmd.getPrimaryKeys(catalogName, schemaName, metaDataName);

        // TODO: Change this to add directly to list

        String[] pkColArray = new String[5]; //Assume no more than 5
        int maxKeySeq = 0;

        SqlDialect.PkMetaDataReader reader = getSqlDialect().getPkMetaDataReader(rs);

        while (rs.next())
        {
            String colName = reader.getName();
            ColumnInfo colInfo = getColumn(colName);

            if (null == colInfo)
            {
                // TODO: Temp hack for PostgreSQL 9.0 bug with renamed columns
                if ("dbuserschemaid".equals(colName))
                    colName = "externalschemaid";

                if ("databaseid".equals(colName))
                    colName = "fastaid";

                colInfo = getColumn(colName);

                assert null != colInfo;
            }

            colInfo.setKeyField(true);
            int keySeq = reader.getKeySeq();

            // SAS doesn't return sequence information -- we could just increment a counter as a backup
            if (0 == keySeq)
                continue;

            pkColArray[keySeq - 1] = colInfo.getName();
            if (keySeq > maxKeySeq)
                maxKeySeq = keySeq;
            //BUG? Assume all non-string key fields are autoInc. (Should use XML instead)
            //if (!ColumnInfo.isStringType(colInfo.getSqlTypeInt()))
            //    colInfo.setAutoIncrement(true);
        }
        rs.close();

        String[] pkColumnNames = new String[maxKeySeq];
        System.arraycopy(pkColArray, 0, pkColumnNames, 0, maxKeySeq);

        _pkColumnNames = Arrays.asList(pkColumnNames);
    }


    private void loadColumnsFromMetaData(DatabaseMetaData dbmd, String catalogName, String schemaName) throws SQLException
    {
        Collection<ColumnInfo> meta = ColumnInfo.createFromDatabaseMetaData(dbmd, catalogName, schemaName, this);
        for (ColumnInfo c : meta)
            addColumn(c);
    }


    void copyToXml(TableType xmlTable, boolean bFull)
    {
        xmlTable.setTableName(name);
        if (_tableType == TABLE_TYPE_TABLE)
            xmlTable.setTableDbType("TABLE");
        else if (_tableType == TABLE_TYPE_VIEW)
            xmlTable.setTableDbType("VIEW");
        else
            xmlTable.setTableDbType("NOT_IN_DB");


        if (bFull)
        {
            // changed to write out the value of property directly, without the
            // default calculation applied by the getter
            if (null != title)
                xmlTable.setTableTitle(title);
            if (null != _pkColumnNames && _pkColumnNames.size() > 0)
                xmlTable.setPkColumnName(StringUtils.join(_pkColumnNames, ','));
            if (null != titleColumn)
                xmlTable.setTitleColumn(titleColumn);
            if (null != _versionColumnName)
                xmlTable.setVersionColumnName(_versionColumnName);
        }

        org.labkey.data.xml.TableType.Columns xmlColumns = xmlTable.addNewColumns();
        org.labkey.data.xml.ColumnType xmlCol;
        for (ColumnInfo columnInfo : columns)
        {
            xmlCol = xmlColumns.addNewColumn();
            columnInfo.copyToXml(xmlCol, bFull);
        }
    }


    void loadFromXml(TableType xmlTable, boolean merge)
    {
        //If merging with DB MetaData, don't overwrite pk
        if (!merge || null == _pkColumnNames || _pkColumnNames.isEmpty())
        {
            String pkColumnName = xmlTable.getPkColumnName();
            if (null != pkColumnName && pkColumnName.length() > 0)
            {
                _pkColumnNames = Arrays.asList(pkColumnName.split(","));
                //Make sure they are lower-cased.
                //REMOVED:  Assume names in xml are correctly formed
/*                for (int i = 0; i < _pkColumnNames.length; i++)
                    if (Character.isUpperCase(_pkColumnNames[i].charAt(0)))
                        _pkColumnNames[i] = Introspector.decapitalize(_pkColumnNames[i]);
*/
            }
        }
        if (!merge)
        {
            setTableType(xmlTable.getTableDbType());
        }

        //Override with the table name from the schema so casing is nice...
        name = xmlTable.getTableName();
        _description = xmlTable.getDescription();
        title = xmlTable.getTableTitle();
        titleColumn = xmlTable.getTitleColumn();
        if (xmlTable.isSetCacheSize())
            _cacheSize = xmlTable.getCacheSize();

        if (xmlTable.getGridUrl() != null)
        {
            _gridURL = DetailsURL.fromString(xmlTable.getGridUrl());
        }
        if (xmlTable.getInsertUrl() != null)
        {
            _insertURL = DetailsURL.fromString(xmlTable.getInsertUrl());
        }
        if (xmlTable.getUpdateUrl() != null)
        {
            _updateURL = DetailsURL.fromString(xmlTable.getUpdateUrl());
        }
        if (xmlTable.getTableUrl() != null)
        {
            _detailsURL = DetailsURL.fromString(xmlTable.getTableUrl());
        }

        ColumnType[] xmlColumnArray = xmlTable.getColumns().getColumnArray();

        if (!merge)
            columns = new ArrayList<ColumnInfo>();

        for (ColumnType xmlColumn : xmlColumnArray)
        {
            ColumnInfo colInfo = null;
            if (merge && getTableType() != TABLE_TYPE_NOT_IN_DB) {
                colInfo = getColumn(xmlColumn.getColumnName());
                if (null != colInfo)
                    colInfo.loadFromXml(xmlColumn, true);
            }

            if (null == colInfo) {
                colInfo = new ColumnInfo(xmlColumn.getColumnName(), this);
                addColumn(colInfo);
                colInfo.loadFromXml(xmlColumn, false);
            }
        }

        if (xmlTable.getButtonBarOptions() != null)
            _buttonBarConfig = new ButtonBarConfig(xmlTable.getButtonBarOptions());
    }


    public void writeCreateTableSql(Writer out) throws IOException
    {
        out.write("CREATE TABLE ");
        out.write(name);
        out.write(" (\n");
        ColumnInfo[] columns = this.columns.toArray(new ColumnInfo[this.columns.size()]);
        for (int i = 0; i < columns.length; i++)
        {
            if (i > 0)
                out.write(",\n");
            out.write(columns[i].toString());
        }
        if (null != _pkColumnNames)
        {
            out.write(",\n PRIMARY KEY (");
            //BUGBUG: This is untested, but we don't use this functionality anyway
            out.write(StringUtils.join(_pkColumnNames, ","));
            out.write(")");
        }
        out.write("\n)\nGO\n\n");
    }


    public void writeCreateConstraintsSql(Writer out) throws IOException
    {
        ColumnInfo[] columns = this.columns.toArray(new ColumnInfo[this.columns.size()]);

        SqlDialect dialect = getSchema().getSqlDialect();
        for (ColumnInfo col : columns) {
            ColumnInfo.SchemaForeignKey fk = (ColumnInfo.SchemaForeignKey) col.getFk();
            if (fk != null) {
                out.write("ALTER TABLE ");
                out.write(name);
                out.write(" ADD CONSTRAINT ");
                out.write("fk_");
                out.write(name);
                out.write("_");
                out.write(col.getName());
                out.write(" FOREIGN KEY (");
                out.write(col.getSelectName());
                if (fk.isJoinWithContainer())
                    out.write(", Container");
                out.write(") REFERENCES ");
                out.write(dialect.makeLegalIdentifier(fk.getLookupTableName()));
                out.write("(");
                out.write(dialect.getColumnSelectName(fk.getLookupColumnName()));
                if (fk.isJoinWithContainer())
                    out.write(", Container");
                out.write(")\nGO\n");
            }
        }
    }


    public void writeBean(Writer out) throws IOException
    {
        out.write("class ");
        out.write(getName());
        out.write("\n\t{\n");

        String[] methNames = new String[columns.size()];
        String[] typeNames = new String[columns.size()];
        String[] memberNames = new String[columns.size()];
        ColumnInfo[] columns = this.columns.toArray(new ColumnInfo[this.columns.size()]);
        for (int i = 0; i < columns.length; i++)
        {
            ColumnInfo col = columns[i];
            memberNames[i] = "_" + col.getPropertyName();
            String methName = col.getLegalName();
            if (!Character.isUnicodeIdentifierPart(methName.charAt(0)))
                methName = Character.toUpperCase(methName.charAt(0)) + methName.substring(1);
            methNames[i] = methName;
            typeNames[i] = ColumnInfo.javaTypeFromSqlType(col.getSqlTypeInt(), col.isNullable());
        }

        for (int i = 0; i < columns.length; i++)
        {
            out.write("\tprivate ");
            out.write(typeNames[i]);
            out.write(" " + memberNames[i] + ";\n");
        }
        out.write("\n\n");
        for (int i = 0; i < columns.length; i++)
        {
            out.write("\tpublic ");
            out.write(typeNames[i]);

            out.write(" get");
            out.write(methNames[i]);
            out.write("()\n\t\t{\n\t\treturn ");
            out.write(memberNames[i]);
            out.write(";\n\t\t}\n\t");

            out.write("public void set");
            out.write(methNames[i]);
            out.write("(");
            out.write(typeNames[i]);
            out.write(" val)\n\t\t{\n\t\t");
            out.write(memberNames[i]);
            out.write(" = val;\n\t\t");
            out.write("}\n\n");

        }
        out.write("\t}\n\n");
    }

    public String getSequence()
    {
        return _sequence;
    }

    public void setSequence(String sequence)
    {
        _sequence = sequence;
    }

    public String decideAlias(String name)
    {
        return name;
    }

    public ActionURL getGridURL(Container container)
    {
        if (_gridURL != null)
            return _gridURL.copy(container).getActionURL();
        return null;
    }

    public ActionURL getInsertURL(Container container)
    {
        if (_insertURL != null)
            return _insertURL.copy(container).getActionURL();
        return null;
    }

    public StringExpression getUpdateURL(Set<FieldKey> columns, Container container)
    {
        if (_updateURL != null && _updateURL.validateFieldKeys(columns))
        {
            return _updateURL.copy(container);
        }
        return null;
    }
    
    public StringExpression getDetailsURL(Set<FieldKey> columns, Container container)
    {
        if (_detailsURL != null && _detailsURL.validateFieldKeys(columns))
        {
            return _detailsURL.copy(container);
        }
        return null;
    }

    public boolean hasPermission(User user, Class<? extends Permission> perm)
    {
        return false;
    }

    public MethodInfo getMethod(String name)
    {
        return null;
    }

    public List<FieldKey> getDefaultVisibleColumns()
    {
        if (defaultVisibleColumns != null)
            return defaultVisibleColumns;
        return Collections.unmodifiableList(QueryService.get().getDefaultVisibleColumns(getColumns()));
    }

    public void setDefaultVisibleColumns(Iterable<FieldKey> keys)
    {
        defaultVisibleColumns = new ArrayList<FieldKey>();
        for (FieldKey key : keys)
            defaultVisibleColumns.add(key);
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
        return true;
    }

    public ButtonBarConfig getButtonBarConfig()
    {
        return _buttonBarConfig;
    }

    public void setButtonBarConfig(ButtonBarConfig config)
    {
        _buttonBarConfig = config;
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
        _description = description;        
    }

    @Nullable
    public QueryUpdateService getUpdateService()
    {
        return null;
    }

    @Override
    public void fireBatchTrigger(TriggerType type, boolean before, ValidationException errors) throws ValidationException
    {
        throw new UnsupportedOperationException("Table triggers not yet supported on schema tables");
    }

    @Override
    public void fireRowTrigger(TriggerType type, boolean before, int rowNumber, Map<String, Object> newRow, Map<String, Object> oldRow) throws ValidationException
    {
        throw new UnsupportedOperationException("Table triggers not yet supported on schema tables");
    }
}
