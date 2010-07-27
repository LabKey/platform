/*
 * Copyright (c) 2009-2010 LabKey Corporation
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

import org.apache.commons.lang.StringUtils;
import org.labkey.api.data.*;
import org.labkey.api.query.AliasManager;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QuerySchema;
import org.labkey.data.xml.ColumnType;

import java.util.*;

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
    final TreeMap<FieldKey,RelationColumn> _selectedColumns = new TreeMap<FieldKey,RelationColumn>();
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

    
    RelationColumn getColumn(String name)
    {
        FieldKey k = new FieldKey(null, name);
        RelationColumn ret = _selectedColumns.get(k);
        if (null != ret)
            return ret;
        ColumnInfo ci = _tableInfo.getColumn(name);
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
            RelationColumn r = getColumn(ci.getName());
            assert null != r;
            map.put(ci.getName(),r);
        }
        return map;
    }
    

    RelationColumn getLookupColumn(RelationColumn parentRelCol, String name)
    {
        assert parentRelCol instanceof TableColumn;
        assert parentRelCol.getTable() == this;

        TableColumn parent = (TableColumn)parentRelCol;
        FieldKey k = new FieldKey(parent._key, name);
        RelationColumn ret = _selectedColumns.get(k);
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


    RelationColumn getLookupColumn(RelationColumn parentRelCol, ColumnType.Fk fk, String name)
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
        RelationColumn ret = _selectedColumns.get(k);
        if (null != ret)
            return ret;

        ForeignKey qfk = AbstractTableInfo.makeForeignKey(_schema, fk);
        ColumnInfo lk = qfk.createLookupColumn(parent._col, name);
        ret = new TableColumn(k,lk);
        _selectedColumns.put(k,ret);
        return ret;
    }


    String makeRelationName(String name)
    {
        SqlDialect d = getSchema().getDbSchema().getSqlDialect();
        if (StringUtils.isEmpty(name))
            name = "table";
        else if (name.startsWith(d.getGlobalTempTablePrefix()) && 10 < name.indexOf('$'))
            name = name.substring(d.getGlobalTempTablePrefix().length(), name.indexOf("$"));
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

        String comment = "<QueryTable@" + System.identityHashCode(this);
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

        if (simpleTable)
        {
            sql.append(selectName);
            if (!selectName.equals(_innerAlias))
                sql.append(" AS ").append(_innerAlias);
        }
        else
        {
            sql.append("(\n");
            sql.append(_tableInfo.getFromSQL());
            sql.append(") ");
            sql.append(_innerAlias);
        }
        for (SQLFragment j : joins.values())
            sql.append(j);
        assert sql.appendComment("</QueryTable@" + System.identityHashCode(this) + ">", _schema.getDbSchema().getSqlDialect());
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

        public int getSqlTypeInt()
        {
            return _col.getSqlTypeInt();
        }

        void copyColumnAttributesTo(ColumnInfo to)
        {
            to.copyAttributesFrom(_col);
            to.copyURLFrom(_col, null, null);
        }
    }

    public boolean setContainerFilter(ContainerFilter containerFilter)
    {
        if (_tableInfo instanceof ContainerFilterable)
        {
            ((ContainerFilterable) _tableInfo).setContainerFilter(containerFilter);
            return true;
        }
        else
            return false;
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
