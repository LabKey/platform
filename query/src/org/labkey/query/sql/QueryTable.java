/*
 * Copyright (c) 2009-2012 LabKey Corporation
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
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerFilterable;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.query.AliasManager;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.data.xml.ColumnType;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Feb 17, 2009
 * Time: 2:33:46 PM
 */

public class QueryTable extends QueryRelation
{
    final AliasManager _aliasManager;
    final TableInfo _tableInfo;
    final TreeMap<FieldKey,TableColumn> _selectedColumns = new TreeMap<FieldKey,TableColumn>();
    String _innerAlias;

    public QueryTable(Query query, QuerySchema schema, TableInfo table, String alias)
    {
        super(query,schema,alias);
        _tableInfo = table;
        _aliasManager = new AliasManager(table.getSchema());

        String selectName = table.getSelectName();
        if (null != selectName)
            setSavedName(selectName);
    }


    public TableInfo getTableInfo()
    {
        return _tableInfo;
    }


    void declareFields()
    {
    }


    @Override
    int getSelectedColumnCount()
    {
        return _selectedColumns.size();
    }


    @Override
    RelationColumn getColumn(@NotNull String name)
    {
        return getColumn(new FieldKey(null, name));
    }


    /* ONLY for one part names */
    private RelationColumn getColumn(@NotNull FieldKey k)
    {
        TableColumn ret = _selectedColumns.get(k);
        if (null != ret || k.getParent() != null)
            return ret;
        ColumnInfo ci = _tableInfo.getColumn(k.getName());
        if (ci == null)
            return null;
        ret = new TableColumn(k, ci);
        _selectedColumns.put(k,ret);
        return ret;
    }


    protected Map<String,RelationColumn> getAllColumns()
    {
        List<ColumnInfo> columns = _tableInfo.getColumns();
        LinkedHashMap<String,RelationColumn> map = new LinkedHashMap<String,RelationColumn>(columns.size()*2);
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
    

    RelationColumn getLookupColumn(@NotNull RelationColumn parentRelCol, @NotNull String name)
    {
        assert parentRelCol instanceof TableColumn;
        assert parentRelCol.getTable() == this;

        TableColumn parent = (TableColumn)parentRelCol;
        FieldKey k = new FieldKey(parent._key, name);
        TableColumn ret = _selectedColumns.get(k);
        if (null != ret)
            return ret;
        if (parent._col.getFk() == null)
            return null;
        ColumnInfo lk = parent._col.getFk().createLookupColumn(parent._col, name);
        if (lk == null)
            return null;
        ret = new TableColumn(k,lk);
        _selectedColumns.put(k,ret);
        return ret;
    }


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

        ForeignKey qfk = AbstractTableInfo.makeForeignKey(_schema, fk);
        if (null == qfk)
            return null;
        ColumnInfo lk = qfk.createLookupColumn(parent._col, name);
        ret = new TableColumn(k,lk);
        _selectedColumns.put(k,ret);
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
        String r = AliasManager.makeLegalName(name, getSchema().getDbSchema().getSqlDialect(), true);
        r += "_" + _query.incrementAliasCounter();
        return r;
    }


    public SQLFragment getSql()
    {
        Map<String, SQLFragment> joins = new LinkedHashMap<String, SQLFragment>();
        SQLFragment sql = new SQLFragment();

        String selectName = _tableInfo.getSelectName();
        boolean simpleTable = null != selectName;

        if (simpleTable)
            _innerAlias = makeRelationName(selectName);
        else
            _innerAlias = makeRelationName(_tableInfo.getName());

        String comment = "<QueryTable";
        if (!StringUtils.isEmpty(_savedName))
            comment += " savedName='" + _savedName + "'";
        comment += " name='" + this._tableInfo.getName() + "' class='" + this._tableInfo.getClass().getSimpleName() + "'>";
        assert sql.appendComment(comment, _schema.getDbSchema().getSqlDialect());

        for (ColumnInfo c : _tableInfo.getColumns())
            if (c.isKeyField())
                getColumn(c.getName());

        sql.append("SELECT ");
        String comma = "";
        for (RelationColumn col : _selectedColumns.values())
        {
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
        sql.append("\nFROM ");
        sql.append(_tableInfo.getFromSQL(_innerAlias));
        for (SQLFragment j : joins.values())
            sql.append(j);
        assert sql.appendComment("</QueryTable>", _schema.getDbSchema().getSqlDialect());
        return sql;
    }


    String getQueryText()
    {
        return _tableInfo.getSelectName();
    }

    
    class TableColumn extends RelationColumn
    {
        FieldKey _key;
        ColumnInfo _col;
        String _alias;
        boolean _setHidden = false;

        TableColumn(FieldKey key, ColumnInfo col)
        {
            _key = key;
            _col = col;
            _alias = _aliasManager.decideAlias(col.getAlias());
        }

        String getAlias()
        {
            return _alias;
        }

        @Override
        public ForeignKey getFk()
        {
            return _col.getFk();
        }

        @Override
        void declareJoins(String parentAlias, Map<String, SQLFragment> map)
        {
            _col.declareJoins(parentAlias, map);
        }

        SQLFragment getInternalSql()
        {
            return _col.getValueSql(_innerAlias);
        }

        public FieldKey getFieldKey()
        {
            return _key;
        }

        QueryRelation getTable()
        {
            return QueryTable.this;
        }

        public @NotNull JdbcType getJdbcType()
        {
            return _col.getJdbcType();
        }

        void copyColumnAttributesTo(ColumnInfo to)
        {
            to.copyAttributesFrom(_col);
            // always copy format, we don't care about preserving set/unset-ness
            to.setFormat(_col.getFormat());
            to.copyURLFrom(_col, null, null);
            if (this._setHidden)
                to.setHidden(true);

            if (null == _mapOutputColToTableColumn)
            {
                _mapOutputColToTableColumn = new TreeMap<FieldKey, RelationColumn>();
                _query.qtableColumnMaps.put(QueryTable.this, _mapOutputColToTableColumn);
            }
            _mapOutputColToTableColumn.put(to.getFieldKey(), this);
        }
    }

    Map<FieldKey,RelationColumn> _mapOutputColToTableColumn = null;


    public void setContainerFilter(ContainerFilter containerFilter)
    {
        if (_tableInfo.supportsContainerFilter())
        {
            ((ContainerFilterable) _tableInfo).setContainerFilter(containerFilter);
        }
    }


    private void addSuggestedColumn(Set<RelationColumn> suggested, FieldKey k)
    {
        boolean existed = _selectedColumns.containsKey(k);
        TableColumn tc = (TableColumn)getColumn(k);
        if (null == tc)
            return;
        if (!existed)
            tc._setHidden = true;
        suggested.add(tc);
    }


    // CONSIDER: should we autoAdd the suggested columns?
    // we are currently because of the getColumn() call
    @Override
    public Set<RelationColumn> getSuggestedColumns(Set<RelationColumn> selected)
    {
        Set<FieldKey> fks = new HashSet<FieldKey>();
        Set<RelationColumn> suggested = new HashSet<RelationColumn>();
//        for (RelationColumn rc : selected)
//        {
//            if (!(rc instanceof TableColumn) || ((TableColumn)rc).getTable()  != this)
//                throw new IllegalArgumentException("Wrong column passed to getSuggestedColumn(): " + rc.getAlias());
//            TableColumn tc = (TableColumn)rc;
//            fks.add(tc._key);
//        }
        for (RelationColumn rc : selected)
        {
            TableColumn tc = (TableColumn)rc;
            FieldKey fk = tc._col.getFieldKey();
            // TODO not handling lookup columns yet
            if (fk.getParent()  != null)
                continue;
            if (null != tc._col.getMvColumnName())
                addSuggestedColumn(suggested, new FieldKey(null, tc._col.getMvColumnName()));
            StringExpression se = tc._col.getURL();
            if (se instanceof StringExpressionFactory.FieldKeyStringExpression)
            {
                Set<FieldKey> keys = ((StringExpressionFactory.FieldKeyStringExpression) se).getFieldKeys();
                for (FieldKey key : keys)
                {
                    if (key.getParent() != null)
                        continue;
                    addSuggestedColumn(suggested, key);
                }
            }
        }
        suggested.removeAll(selected);
        return suggested;
    }


//    // an unwrapped lookup column
//    class LookupColumnInfoColumn extends TableColumn
//    {
//        LookupColumnInfoColumn(FieldKey key, ColumnInfo col)
//        {
//            super(key, col);
//        }
//
//        public SQLFragment getValueSql(String tableAlias)
//        {
//            return _col.getValueSql(_innerAlias);
//        }
//    }
}
