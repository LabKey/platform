/*
 * Copyright (c) 2006-2009 LabKey Corporation
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

import org.apache.beehive.netui.pageflow.Forward;
import org.labkey.api.data.collections.CaseInsensitiveMap;
import org.labkey.api.query.*;
import org.labkey.api.security.User;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.NamedObjectList;
import org.labkey.api.util.SimpleNamedObject;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.view.ActionURL;
import org.labkey.data.xml.ColumnType;
import org.labkey.data.xml.TableType;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

abstract public class AbstractTableInfo implements TableInfo
{
    protected Iterable<FieldKey> _defaultVisibleColumns;
    protected DbSchema _schema;
    private String _titleColumn;

    public ColumnInfo getColumnFromPropertyURI(String propertyURI)
    {
        return null;
    }

    protected final Map<String, ColumnInfo> _columnMap;
    private Map<String, MethodInfo> _methodMap;
    protected String _name;
    private List<DetailsURL> _detailsURLs = new ArrayList<DetailsURL>(0);

    public List<ColumnInfo> getPkColumns()
    {
        List<ColumnInfo> ret = new ArrayList<ColumnInfo>();
        for (ColumnInfo column : getColumns())
        {
            if (column.isKeyField())
            {
                ret.add(column);
            }
        }
        return Collections.unmodifiableList(ret);
    }

    public String getRowTitle(Object pk) throws SQLException
    {
        return null;
    }

    public AbstractTableInfo(DbSchema schema)
    {
        _schema = schema;
        _columnMap = constructColumnMap();
        MemTracker.put(this);
    }

    protected Map<String, ColumnInfo> constructColumnMap()
    {
        if (isCaseSensitive())
        {
            return new LinkedHashMap<String, ColumnInfo>();
        }
        return new CaseInsensitiveMap<ColumnInfo>(new LinkedHashMap<String, ColumnInfo>());
    }

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
        List<String> ret = new ArrayList<String>();
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

    public int getTableType()
    {
        return TABLE_TYPE_NOT_IN_DB;
    }

    public NamedObjectList getSelectList()
    {
        NamedObjectList ret = new NamedObjectList();
        List<ColumnInfo> pkColumns = getPkColumns();
        if (pkColumns.size() != 1)
            return ret;
        ColumnInfo titleColumn = getColumn(getTitleColumn());
        if (titleColumn == null)
            return ret;
        try
        {
            ColumnInfo firstColumn = pkColumns.get(0);
            List<ColumnInfo> cols;
            int titleIndex;
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

            ResultSet rs = Table.select(this, cols, null, null);
            while (rs.next())
            {
                ret.put(new SimpleNamedObject(rs.getString(1), rs.getString(titleIndex)));
            }
            rs.close();
        }
        catch (SQLException e)
        {
            
        }
        return ret;
    }

    public List<ColumnInfo> getUserEditableColumns()
    {
        List<ColumnInfo> ret = new ArrayList<ColumnInfo>();
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
        List<ColumnInfo> ret = new ArrayList<ColumnInfo>(colNameArray.length);
        for (String name : colNameArray)
        {
            ret.add(getColumn(name.trim()));
        }
        return Collections.unmodifiableList(ret);
    }

    public String getSequence()
    {
        return null;
    }

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
            if (null == _titleColumn)
                _titleColumn = getColumns().get(0).getName();
        }

        return _titleColumn;
    }

    public void setTitleColumn(String titleColumn)
    {
        _titleColumn = titleColumn;
    }

    public ColumnInfo getColumn(String name)
    {
        ColumnInfo ret = _columnMap.get(name);
        if (ret != null)
            return ret;
        return resolveColumn(name);
    }

    /**
     * If a column wasn't found in the standard column list, give the table a final chance to locate it.
     * Useful for preserving backwards compatibility with saved queries when a column is renamed.
     */
    protected ColumnInfo resolveColumn(String name)
    {
        return null;
    }

    public List<ColumnInfo> getColumns()
    {
        return Collections.unmodifiableList(new ArrayList<ColumnInfo>(_columnMap.values()));
    }

    public Set<String> getColumnNameSet()
    {
        return Collections.unmodifiableSet(_columnMap.keySet());
    }

    public String getName()
    {
        return _name;
    }

    public boolean removeColumn(ColumnInfo column)
    {
        return _columnMap.remove(column.getName()) != null;
    }

    public ColumnInfo addColumn(ColumnInfo column)
    {
        // Not true if this is a VirtualTableInfo
        // assert column.getParentTable() == this;
        if (_columnMap.containsKey(column.getName()))
        {
            throw new IllegalArgumentException("Column " + column.getName() + " already exists.");
        }
        _columnMap.put(column.getName(), column);
        return column;
    }

    public void addMethod(String name, MethodInfo method)
    {
        if (_methodMap == null)
        {
            _methodMap = new HashMap<String, MethodInfo>();
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
        _name = name;
    }

    public StringExpressionFactory.StringExpression getDetailsURL(Map<String, ColumnInfo> columns)
    {
        for (DetailsURL dUrl : _detailsURLs)
        {
            StringExpressionFactory.StringExpression ret = dUrl.getURL(columns);
            if (ret != null)
                return ret;
        }
        return null;
    }

    public ActionURL delete(User user, ActionURL srcURL, QueryUpdateForm form) throws Exception
    {
        throw new UnsupportedOperationException();
    }

    public Forward insert(User user, QueryUpdateForm form) throws Exception
    {
        throw new UnsupportedOperationException();
    }

    public Forward update(User user, QueryUpdateForm form) throws Exception
    {
        throw new UnsupportedOperationException();
    }

    public boolean hasPermission(User user, int perm)
    {
        return false;
    }

    public void setDetailsURL(DetailsURL detailsURL)
    {
        _detailsURLs.clear();
        _detailsURLs.add(detailsURL);
    }

    public void addDetailsURL(DetailsURL detailsURL)
    {
        _detailsURLs.add(detailsURL);
    }

    public void setDefaultVisibleColumns(Iterable<FieldKey> list)
    {
        _defaultVisibleColumns = list;
    }

    public List<FieldKey> getDefaultVisibleColumns()
    {
        if (_defaultVisibleColumns instanceof List)
        {
            return Collections.unmodifiableList((List<FieldKey>) _defaultVisibleColumns);
        }
        if (_defaultVisibleColumns != null)
        {
            List<FieldKey> ret = new ArrayList<FieldKey>();
            for (FieldKey key : _defaultVisibleColumns)
            {
                ret.add(key);
            }
            return Collections.unmodifiableList(ret);
        }
        return Collections.unmodifiableList(QueryService.get().getDefaultVisibleColumns(getColumns()));
    }

    public boolean safeAddColumn(ColumnInfo column)
    {
        if (getColumn(column.getName()) != null)
            return false;
        addColumn(column);
        return true;

    }


    public static ForeignKey makeForeignKey(QuerySchema fromSchema, ColumnType.Fk fk)
    {
        QuerySchema fkSchema = fromSchema;
        if (fk.getFkDbSchema() != null)
        {
            fkSchema = QueryService.get().getUserSchema(fromSchema.getUser(), fromSchema.getContainer(), fk.getFkDbSchema());
            if (fkSchema == null)
                return null;
        }
        QueryForeignKey qfk = new QueryForeignKey(fkSchema, fk.getFkTable(), fk.getFkColumnName(), null);
        return qfk;
    }


    protected void initColumnFromXml(QuerySchema schema, ColumnInfo column, ColumnType xbColumn, Collection<QueryException> qpe)
    {
        column.setName(xbColumn.getColumnName());
        if (xbColumn.getColumnTitle() != null)
        {
            column.setCaption(xbColumn.getColumnTitle());
        }
        if (xbColumn.isSetDescription())
        {
            column.setDescription(xbColumn.getDescription());
        }
        if (xbColumn.getFk() != null)
        {
            ForeignKey qfk = makeForeignKey(schema, xbColumn.getFk());
            if (qfk == null)
            {
                //noinspection ThrowableInstanceNeverThrown
                qpe.add(new MetadataException("Schema " + xbColumn.getFk().getFkDbSchema() + " not found."));
                return;
            }
            column.setFk(qfk);
        }
        if (xbColumn.getFormatString() != null)
        {
            column.setFormatString(xbColumn.getFormatString());
        }
        if (xbColumn.getDatatype() != null)
        {
            column.setSqlTypeName(xbColumn.getDatatype());
        }
        if (xbColumn.isSetIsHidden())
        {
            column.setIsHidden(xbColumn.getIsHidden());
        }
        if (xbColumn.isSetIsUnselectable())
        {
            column.setIsUnselectable(xbColumn.getIsUnselectable());
        }
        if (xbColumn.isSetIsKeyField())
        {
            column.setKeyField(xbColumn.getIsKeyField());
        }
    }

    public void loadFromXML(QuerySchema schema, TableType xbTable, Collection<QueryException> qpe)
    {
        if (xbTable.getTitleColumn() != null)
        {
            setTitleColumn(xbTable.getTitleColumn());
        }
        if (xbTable.getTableUrl() != null)
        {
            try
            {
                setDetailsURL(DetailsURL.fromString(schema.getContainer(), xbTable.getTableUrl()));
            }
            catch (MetadataException me)
            {
                qpe.add(me);
            }
        }
        if (xbTable.getColumns() != null)
        {
            List<ColumnType> wrappedColumns = new ArrayList<ColumnType>();
            for (ColumnType xbColumn : xbTable.getColumns().getColumnArray())
            {
                if (xbColumn.getWrappedColumnName() != null)
                {
                    wrappedColumns.add(xbColumn);
                }
                else
                {
                    ColumnInfo column = getColumn(xbColumn.getColumnName());
                    if (column != null)
                    {
                        initColumnFromXml(schema, column, xbColumn, qpe);
                    }
                }
            }
            for (ColumnType wrappedColumnXb : wrappedColumns)
            {
                ColumnInfo column = getColumn(wrappedColumnXb.getWrappedColumnName());
                if (column != null && getColumn(wrappedColumnXb.getColumnName()) == null)
                {
                    ColumnInfo wrappedColumn = new WrappedColumn(column, wrappedColumnXb.getColumnName());
                    initColumnFromXml(schema, wrappedColumn, wrappedColumnXb, qpe);
                    addColumn(wrappedColumn);
                }
            }
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
        return true;
    }

    public String getPublicName()
    {
        return getName();
    }

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

    /**
     * @return whether this table allows its metadata to be overriden. User-defined tables typically allow
     * direct editing of their metadata, so they don't need the override functionality.
     */
    public boolean isMetadataOverrideable()
    {
        return true;
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
}
