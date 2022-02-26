/*
 * Copyright (c) 2009-2019 LabKey Corporation
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
package org.labkey.query.sql;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ColumnLogging;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerFilterable;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.MethodInfo;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.PropertyColumn;
import org.labkey.api.exp.query.ExpTable;
import org.labkey.api.query.AliasManager;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryParseException;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.data.xml.ColumnType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * User: matthewb
 * Date: Feb 17, 2009
 * Time: 2:33:46 PM
 */

public class QueryTable extends QueryRelation
{
    private static final Logger _log = LogManager.getLogger(QueryTable.class);

    private final String _originalAlias;        // as passed in from QuerySelect for error messages
    private final int _uniqueAliasCounter;

    private AliasManager _aliasManager;
    private TableInfo _tableInfo;
    private TreeMap<FieldKey,TableColumn> _selectedColumns = new TreeMap<>();
    private String _innerAlias;
    private Boolean _selectAllColumns = false;

    protected Boolean _generateSelectSQL = null;

    public QueryTable(Query query, QuerySchema schema, TableInfo table, String alias)
    {
        this(query, schema, alias);
        setTableInfo(table);
    }

    public QueryTable(Query query, QuerySchema schema, String alias)
    {
        // For recursive queries we need to create this object before we create the TableInfo
        super(query, schema, alias);
        _originalAlias = alias;

        // call this now so we it doesn't change if _getSql() is called more than once
        _uniqueAliasCounter = _query.incrementAliasCounter();
    }

    public void setTableInfo(TableInfo table)
    {
        _tableInfo = table;
        _aliasManager = new AliasManager(table.getSchema());

        String selectName = table.getSelectName();
        if (null != selectName)
            setSavedName(selectName);
    }

    @Override
    public TableInfo getTableInfo()
    {
        return _tableInfo;
    }


    @Override
    public void declareFields()
    {
        _log.debug("declareFields " + toStringDebug());
    }


    @Override
    protected void resolveFields()
    {
    }


    @Override
    int getSelectedColumnCount()
    {
        return _selectedColumns.size();
    }


    @Override
    protected MethodInfo getMethod(String name)
    {
        MethodInfo m = _tableInfo.getMethod(name);
        if (null != m)
        {
            // TODO: _selectAllColumns is a fix for not knowing which columns the method might depend on
            // TODO: proper fix is to add MethodInfo.getDependantFieldKeys()
            for (var field : _tableInfo.getMethodRequiredFieldKeys())
            {
                if (null != field.getParent())
                    throw new IllegalStateException();
                var tc = getColumn(field.getName());
                if (null != tc)
                    tc.addRef(this);
            }

            // This is a hack for experiment tables that have lineage methods.
            // This allow the methods to work whether or not the table exposes a public "objectid" column.
            if ("expObject".equalsIgnoreCase(name) && _tableInfo instanceof ExpTable)
            {
                ColumnInfo ci = ((ExpTable<?>)_tableInfo).getExpObjectColumn();
                if (null != ci)
                {
                    TableColumn tc = _selectedColumns.get(ci.getFieldKey());
                    if (null == tc)
                    {
                        tc = new TableColumn(ci.getFieldKey(), ci, null);
                        addSelectedColumn(ci.getFieldKey(), tc);
                    }
                    tc.addRef(this);
                }
            }

            _generateSelectSQL = Boolean.TRUE; // don't optimize, see getAlias()
        }
        return m;
    }


    @Override
    RelationColumn getColumn(@NotNull String name)
    {
        FieldKey k = new FieldKey(null,name);
        TableColumn ret = _selectedColumns.get(k);
        if (null != ret)
            return ret;
        ColumnInfo ci = _tableInfo.getColumn(name);
        if (ci == null)
            return null;
        ret = new TableColumn(k, ci, null);
        addSelectedColumn(k, ret);
        return ret;
    }


    /** This method does not mark returned columns as selected */
    @Override
    protected Map<String,RelationColumn> getAllColumns()
    {
        List<ColumnInfo> columns = _tableInfo.getColumns();
        LinkedHashMap<String,RelationColumn> map = new LinkedHashMap<>(columns.size()*2);
        for (ColumnInfo ci : columns)
        {
            if (ci.isUnselectable())
                continue;
            RelationColumn r = getColumn(ci.getName());
            assert null != r;
            map.put(ci.getName(),r);
        }
        return map;
    }


    @Override
    Collection<String> getKeyColumns()
    {
        return _tableInfo.getPkColumnNames();
    }


    @Override
    RelationColumn getLookupColumn(@NotNull RelationColumn parentRelCol, @NotNull String name)
    {
        assert parentRelCol instanceof TableColumn;
        assert parentRelCol.getTable() == this;

        TableColumn parent = (TableColumn)parentRelCol;
        FieldKey k = new FieldKey(parent._key, name);
        TableColumn ret = _selectedColumns.get(k);
        if (null != ret)
            return ret;
        ForeignKey foreignKey = parent._col.getFk();
        if (null == foreignKey)
            return null;
        ColumnInfo lk = foreignKey.createLookupColumn(parent._col, name);
        if (lk == null)
            return null;
        ret = new TableColumn(k, lk, parent);
        addSelectedColumn(k, ret);
        return ret;
    }


    @Override
    RelationColumn getLookupColumn(@NotNull RelationColumn parentRelCol, @NotNull ColumnType.Fk fk, @NotNull String name)
    {
        assert parentRelCol instanceof TableColumn;
        assert parentRelCol.getTable() == this;

        TableColumn parent = (TableColumn)parentRelCol;
        // reject if column has foreign key
        // we can make this work, but we have to be careful about the aliases
        // we don't want collisions in declareJoins()
        if (parent._col.getFk() != null)
            return null;

        FieldKey k = new FieldKey(parent._key, name);
        TableColumn ret = _selectedColumns.get(k);
        if (null != ret)
            return ret;

        ForeignKey qfk = AbstractTableInfo.makeForeignKey(_schema, _query.getContainerFilter(), fk);
        if (null == qfk)
            return null;
        ColumnInfo lk = qfk.createLookupColumn(parent._col, name);
        ret = new TableColumn(k, lk, parent);
        addSelectedColumn(k,ret);
        return ret;
    }


    String makeRelationName(String name)
    {
        SqlDialect d = getSchema().getDbSchema().getSqlDialect();
        String gttp = null;
        try { gttp = d.getGlobalTempTablePrefix(); } catch (UnsupportedOperationException x) { /* */ }

        if (StringUtils.isEmpty(name))
        {
            name = "table";
        }
        else if (!StringUtils.isEmpty(gttp))
        {
            if (name.startsWith(gttp) && 10 < name.indexOf('$'))
                name = name.substring(gttp.length(), name.indexOf("$"));
        }
        String r = AliasManager.makeLegalName(name, getSchema().getDbSchema().getSqlDialect(), true, false);
        r += "_" + _uniqueAliasCounter;
        return r;
    }

    @Override
    public SQLFragment getFromSql()
    {
        SQLFragment ret = new SQLFragment();
        String comment = "<QueryTable";
        if (!StringUtils.isEmpty(_savedName))
            comment += " savedName='" + _savedName + "'";
        comment += " name='" + _tableInfo.getName() + "' class='" + _tableInfo.getClass().getSimpleName() + "'>";
        assert ret.appendComment(comment, _schema.getDbSchema().getSqlDialect());

        SQLFragment sql = _getSql();
        if (_generateSelectSQL)
            ret.append("(");
        ret.append(sql);
        if (_generateSelectSQL)
            ret.append(") ").append(getAlias());

        assert ret.appendComment("</QueryTable>", _schema.getDbSchema().getSqlDialect());
        return ret;
    }


    @Override
    public String getAlias()
    {
        // TODO : note this doesn't reflect the actual alias when _generateSelectSQL==FALSE
        // this only seems to affect queries that have table methods (see getMethod()) which wrongly assume that they know the column aliases
        return super.getAlias();
    }


    @Override
    public SQLFragment getSql()
    {
        SQLFragment ret = new SQLFragment();
        String comment = "<QueryTable";
        if (!StringUtils.isEmpty(_savedName))
            comment += " savedName='" + _savedName + "'";
        comment += " name='" + _tableInfo.getName() + "' class='" + _tableInfo.getClass().getSimpleName() + "'>";
        assert ret.appendComment(comment, _schema.getDbSchema().getSqlDialect());

        ret.append(_getSql());

        ret.appendComment("</QueryTable>", _schema.getDbSchema().getSqlDialect());
        return ret;
    }


    private SQLFragment _getSql()
    {
        // set of non-lookup columns
        Set<FieldKey> tableColumns = new TreeSet<>();

        Map<String, SQLFragment> joins = new LinkedHashMap<>();
        SQLFragment sql = new SQLFragment();

        String selectName = _tableInfo.getSelectName();
        boolean simpleTable = null != selectName;

        if (simpleTable)
            _innerAlias = makeRelationName(selectName);
        else
            _innerAlias = makeRelationName(_tableInfo.getName());

        for (ColumnInfo c : _tableInfo.getColumns())
        {
            if (c.isKeyField() || _selectAllColumns)
            {
                RelationColumn keyField = getColumn(c.getName());
                if (null != keyField)
                    keyField.addRef(this);
            }
        }
        for (var c : new ArrayList<>(_selectedColumns.values()))
        {
            if (c.ref.count() > 0)
            {
                var mvName = c._col.getMvColumnName();
                if (null != mvName)
                {
                    var mvCol = getColumn(mvName.getName());
                    if (null != mvCol)
                        mvCol.addRef(c);
                }
            }
        }

        if (null == _generateSelectSQL)
            _generateSelectSQL = false;

        sql.append("SELECT ");
        String comma = "";
        int columnCount = 0;
        for (TableColumn col : _selectedColumns.values())
        {
            if (col.ref.count() == 0)
                continue;
            columnCount++;
            if (null != col.getFieldKey().getParent())
                _generateSelectSQL = true;
            else
                tableColumns.add(col.getFieldKey());
            if (col._col instanceof PropertyColumn)
                _generateSelectSQL = true;
            col.declareJoins(_innerAlias, joins);
            sql.append(comma);
            SQLFragment f = col.getInternalSql();
            if (f.getSQL().length() > 80)
                sql.append("\n").append(f).append("\n");
            else
                sql.append(f);
            sql.append(" AS ");
            sql.append(col.getAlias());
            comma = ", ";
        }
        if (0 == columnCount)
            sql.append("1 AS \"_ONE_\"");
        sql.append("\nFROM ");

        SQLFragment tableFromSql = _tableInfo.getFromSQL(_innerAlias, tableColumns);

        sql.append(tableFromSql);
        for (SQLFragment j : joins.values())
            sql.append(j);
        if (!joins.isEmpty())
            _generateSelectSQL = true;

        if (null == _parent || _parent instanceof QuerySelect && ((QuerySelect)_parent).isAggregate())
            _generateSelectSQL = true;

        if (!_generateSelectSQL)
            return tableFromSql;
        else
            return sql;
    }


    @Override
    String getQueryText()
    {
        return _tableInfo.getSelectName();
    }

    
    class TableColumn extends RelationColumn
    {
        final FieldKey _key;
        ColumnInfo _col;
        final String _alias;
        final TableColumn _parent;

        TableColumn(@NotNull FieldKey key, @NotNull ColumnInfo col, @Nullable TableColumn parent)
        {
            if (null == key)
                throw new NullPointerException("key is null");
            if (null == col)
                throw new NullPointerException("col is null");
            _key = key;
            _col = col;
            _alias = _aliasManager.decideAlias(col.getAlias());
            _parent = parent;
        }

        @Override
        String getAlias()
        {
            return _alias;
        }

        @Override
        public ForeignKey getFk()
        {
            if (_suggestedColumn)
                return null;
            return _col.getFk();
        }

        @Override
        void declareJoins(String parentAlias, Map<String, SQLFragment> map)
        {
            _col.declareJoins(parentAlias, map);
        }

        @Override
        public SQLFragment getValueSql()
        {
            assert ref.count() > 0;

            // _generateSelectSQL indicates whether to wrap the _tableInfo.getFromSql() in another SELECT statement.
            // If _generateSelectSQL==null, that means _getSql() has not been called, which probably means there
            // is an error in the user's FROM clause.
            if (_generateSelectSQL == null)
            {
                _query.getParseErrors().add(new QueryParseException("Missing from-clause entry: " + _originalAlias, null, 0, 0));
                return new SQLFragment("NULL");
            }
            else if (_generateSelectSQL)
                return super.getValueSql();
            else
                return _col.getValueSql(_innerAlias);
        }

        @Override
        SQLFragment getInternalSql()
        {
            return _col.getValueSql(_innerAlias);
        }

        @Override
        public FieldKey getFieldKey()
        {
            return _key;
        }

        @Override
        QueryRelation getTable()
        {
            return QueryTable.this;
        }

        @Override
        public @NotNull JdbcType getJdbcType()
        {
            return _col.getJdbcType();
        }

        @Override
        boolean isHidden()
        {
            return _col.isHidden();
        }

        @Override
        String getPrincipalConceptCode()
        {
            return _col.getPrincipalConceptCode();
        }

        @Override
        String getConceptURI()
        {
            return _col.getConceptURI();
        }

        @Override
        void copyColumnAttributesTo(BaseColumnInfo to)
        {
            to.copyAttributesFrom(_col);
            to.setKeyField(false);
            to.setCalculated(true);
            // always copy format, we don't care about preserving set/unset-ness
            to.setFormat(_col.getFormat());
            to.copyURLFrom(_col, null, null);
            if (_suggestedColumn)
            {
                to.setHidden(true);
//                to.setDisplayColumnFactory(ColumnInfo.NOLOOKUP_FACTORY);
            }

            if (null == _mapOutputColToTableColumn)
            {
                _mapOutputColToTableColumn = new TreeMap<>();
                _query.qtableColumnMaps.put(QueryTable.this, _mapOutputColToTableColumn);
            }
            _mapOutputColToTableColumn.put(to.getFieldKey(), this);
        }

        @Override
        public int addRef(@NotNull Object refer)
        {
            if (0 == ref.count())
            {
                if (null != _parent)
                    _parent.addRef(this);
            }
            return super.addRef(refer);
        }

        @Override
        public int releaseRef(@NotNull Object refer)
        {
            if (0 == ref.count())
                return 0;
            int count = super.releaseRef(refer);
            if (0 == count)
            {
                if (null != _parent)
                    _parent.releaseRef(this);
            }
            return count;
        }
    }

    Map<FieldKey,RelationColumn> _mapOutputColToTableColumn = null;


    @Override
    public void setContainerFilter(ContainerFilter containerFilter)
    {
        if (_tableInfo.supportsContainerFilter())
        {
            ContainerFilter oldCF = _tableInfo.getContainerFilter();
            ((ContainerFilterable) _tableInfo).setContainerFilter(containerFilter);

            // NOTE: some of our columns maybe holding onto free-floating tableinfo generated by lookup columns
            // and they may never find out output this container filter!

            boolean same = oldCF == null ? oldCF==containerFilter : oldCF.equals(containerFilter);
            if (!same)
            {
                // we want to "rebind" the existing TableColumns (not create/return new ones)
                // however, all the helpers use the _selectedColumns map for caching perf.
                // The easiest thing to do is create a new set of TableColumn objects then copy over the ColumnInfos
                // so that's why we do this _selectedColumns swapping here
                TreeMap<FieldKey,TableColumn> columnsToRebind = _selectedColumns;
                _selectedColumns = new TreeMap<>();
                for (Map.Entry<FieldKey,TableColumn> entry: columnsToRebind.entrySet())
                {
                    _resolve(entry.getKey());
                    TableColumn tc = _selectedColumns.get(entry.getKey());
                    // tc should not be null, that means it resolved in the first pass, but not a second time. that would be... weird
                    if (null == tc)
                        throw new QueryParseException("Could not rebind column: " + entry.getKey().toDisplayString(), new NullPointerException(), -1, -1);
                    entry.getValue()._col = tc._col;
                }
                _selectedColumns = columnsToRebind;
            }
        }
    }


    private void addSuggestedColumns(Set<RelationColumn> suggested, Set<FieldKey> ks, Map<FieldKey, RelationColumn> selectedColumnMap)
    {
        if (ks == null || ks.isEmpty())
            return;

        for (FieldKey k : ks)
            addSuggestedColumn(suggested, k, selectedColumnMap);
    }


    // return column if it is newly added to the suggested list
    private RelationColumn addSuggestedColumn(Set<RelationColumn> suggested, FieldKey k, Map<FieldKey, RelationColumn> selectedColumnMap)
    {
        RelationColumn tc = _resolve(k);
        if (null == tc)
            return null;

        boolean existed = selectedColumnMap.containsKey(tc.getFieldKey());

        if (!existed && tc instanceof TableColumn)
        {
            ((TableColumn)tc)._suggestedColumn = true;
        }

        if (suggested.contains(tc))
            return null;

        suggested.add(tc);
        return tc;
    }


    private void addSuggestedContainerColumn(Set<RelationColumn> suggested, TableColumn sibling, Map<FieldKey, RelationColumn> selectedColumnMap)
    {
        // UNDONE: let tableinfo specify the container column in some way
        // foreignKey().createLookupContainerColumn()
        // or foreignKey.getTable().getContainerColumn()
        // or column.getContainerColumnFieldKey()

        // if this isn't a lookup, then let's just _tableInfo what the container column is
        if (null == sibling.getFieldKey().getParent() && _tableInfo instanceof AbstractTableInfo)
        {
            FieldKey k = _tableInfo.getContainerFieldKey();
            if (null != k)
            {
                addSuggestedColumn(suggested, k, selectedColumnMap);
                return;
            }
        }

        FieldKey fkContainer = new FieldKey(sibling.getFieldKey().getParent(), "container");
        FieldKey fkFolder = new FieldKey(sibling.getFieldKey().getParent(), "folder");

        if (null == addSuggestedColumn(suggested, fkFolder, selectedColumnMap))
            addSuggestedColumn(suggested, fkContainer, selectedColumnMap);
    }


    private RelationColumn _resolve(FieldKey k)
    {
        TableColumn tc = _selectedColumns.get(k);
        if (null != tc)
            return tc;

        if (null == k.getParent())
            return getColumn(k.getName());

        RelationColumn parent = _resolve(k.getParent());
        if (null == parent)
            return null;
        return getLookupColumn(parent, k.getName());
    }


    // CONSIDER: should we autoAdd the suggested columns?
    // we are currently because of the getColumn() call
    // NOTE: Every type of suggested column should have a corresponding fixup in ColumnInfo.remapFieldKeys().
    @Override
    protected Set<RelationColumn> getSuggestedColumns(Set<RelationColumn> selected)
    {
        if (_query._strictColumnList)
            return Collections.emptySet();

        Map<FieldKey, RelationColumn> selectedColumnMap = new HashMap<>(selected.size());
        for (RelationColumn column : selected)
            selectedColumnMap.put(column.getFieldKey(), column);

        Set<RelationColumn> suggested = new LinkedHashSet<>();
        Set<FieldKey> suggestedContainerColumns = new HashSet<>();

        for (RelationColumn rc : selected)
        {
            TableColumn tc = (TableColumn)rc;
            ColumnInfo col = tc._col;
            FieldKey fk = col.getFieldKey();

            if (suggestedContainerColumns.add(fk.getParent()))
                addSuggestedContainerColumn(suggested, tc, selectedColumnMap);

            if (null != col.getMvColumnName())
                addSuggestedColumn(suggested, col.getMvColumnName(), selectedColumnMap);

            addSuggestedColumns(col.getURL(), suggested, selectedColumnMap);
            addSuggestedColumns(col.getTextExpression(), suggested, selectedColumnMap);

            if (col.getFk() != null)
                addSuggestedColumns(suggested, col.getFk().getSuggestedColumns(), selectedColumnMap);

            if (col.getSortFieldKeys() != null)
            {
                for (FieldKey key : col.getSortFieldKeys())
                {
                    addSuggestedColumn(suggested, key, selectedColumnMap);
                }
            }

            ColumnInfo column = getTableInfo().getColumn(tc.getFieldKey());
            if (null != column)
            {
                ColumnLogging columnLogging = column.getColumnLogging();
                for (FieldKey fieldKey : columnLogging.getDataLoggingColumns())
                {
                    addSuggestedColumn(suggested, fieldKey, selectedColumnMap);
                }
            }

            ColumnLogging columnLogging = col.getColumnLogging();
            for (FieldKey fieldKey : columnLogging.getDataLoggingColumns())
            {
                FieldKey fixedUp = fieldKey;
                if (null != tc.getFieldKey().getParent())
                    fixedUp = new FieldKey(tc.getFieldKey().getParent(), fieldKey.getName());
                addSuggestedColumn(suggested, fixedUp, selectedColumnMap);
            }
        }
        suggested.removeAll(selected);
        return suggested;
    }

    private void addSuggestedColumns(StringExpression se, Set<RelationColumn> suggested, Map<FieldKey, RelationColumn> selectedColumnMap)
    {
        if (se instanceof StringExpressionFactory.FieldKeyStringExpression)
        {
            Set<FieldKey> keys = ((StringExpressionFactory.FieldKeyStringExpression) se).getFieldKeys();
            for (FieldKey key : keys)
                addSuggestedColumn(suggested, key, selectedColumnMap);
        }
    }

    private void addSelectedColumn(FieldKey key, TableColumn column)
    {
        _selectedColumns.put(key, column);
        _query.addInvolvedTableColumn(column);
    }
}
