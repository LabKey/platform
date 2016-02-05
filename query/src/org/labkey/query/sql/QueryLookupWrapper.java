/*
 * Copyright (c) 2009-2016 LabKey Corporation
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

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.*;
import org.labkey.api.query.AliasManager;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryParseException;
import org.labkey.data.xml.ColumnType;
import org.labkey.data.xml.TableType;

import java.util.*;

/**
 * User: matthewb
 * Date: Mar 9, 2009
 * Time: 1:56:39 PM
 *
 *
 * If the metadata editor has been used to set additional lookup properties
 * on a query result, we can use this wrapper to handle those additional joins.
 *
 * This lets us avoid having to handle lookups in every QueryRelation implementation.
 *
 * QueryLookupWrapper is actually very similar to QueryTable, the difference being that
 * the input to QueryLookupWrapper is a QueryRelation rather than a TableInfo
 */
public class QueryLookupWrapper extends QueryRelation
{
    private static final Logger _log = Logger.getLogger(QueryLookupWrapper.class);

    final AliasManager _aliasManager;
    QueryRelation _source;
    boolean _hasLookups = false;

    Map<String, ColumnType> _columnMetaDataMap = new CaseInsensitiveHashMap<>();
    Map<String, ColumnType.Fk> _fkMap = new CaseInsensitiveHashMap<>();
    Map<FieldKey, RelationColumn> _selectedColumns = new HashMap<>();

    // shim for creating lookup columns w/o a real TableInfo
    SQLTableInfo _sti = null;


    QueryLookupWrapper(Query query, QueryRelation relation, @Nullable TableType md)
    {
        super(query);
        _aliasManager = new AliasManager(query.getSchema().getDbSchema());
        _alias = "qlw" + relation.getAlias();
        _source = relation;
        _inFromClause = relation._inFromClause;
        relation._parent = this;

        org.labkey.data.xml.TableType.Columns cols = null==md ? null : md.getColumns();
        if (null != cols)
        {
            for (ColumnType col : cols.getColumnArray())
            {
                _columnMetaDataMap.put(col.getColumnName(), col);
                ColumnType.Fk colFK = col.getFk();
                if (null == colFK || null == colFK.getFkTable() || null == colFK.getFkColumnName())
                    continue;
                _fkMap.put(col.getColumnName(), colFK);
            }
        }
    }


    @Override
    void setQuery(Query query)
    {
        super.setQuery(query);
        _source.setQuery(query);
    }
    

    @Override
    protected void setAlias(String alias)
    {
        super.setAlias(alias);
        if (_hasLookups)
            _source.setAlias(getAlias() + "Wrapped");
        else
            _source.setAlias(alias);
    }


    private void setHasLookup()
    {
        _hasLookups = true;
        _source.setAlias(getAlias() + "Wrapped");
    }

    
    void declareFields()
    {
        _log.debug("declareFields " + this.toStringDebug());

        _source.declareFields();
    }


    @Override
    protected void resolveFields()
    {
        _source.resolveFields();
    }


    TableInfo getTableInfo()
    {
        if (!_hasLookups)
        {
            return _source.getTableInfo();
        }
        else
        {
            throw new UnsupportedOperationException();
        }
    }


    @Override
    int getSelectedColumnCount()
    {
        return _selectedColumns.size();
    }
    

    RelationColumn getColumn(String name)
    {
        FieldKey k = new FieldKey(null, name);
        RelationColumn ret = _selectedColumns.get(k);
        if (null != ret)
            return ret;
        RelationColumn c = _source.getColumn(name);
        if (null == c)
            return null;
        ret = new PassThroughColumn(k, c);
        _selectedColumns.put(k,ret);
        return ret;
    }


    protected Map<String,RelationColumn> getAllColumns()
    {
        Map<String,RelationColumn> all = _source.getAllColumns();
        Map<String,RelationColumn> ret = new LinkedHashMap<>(2*all.size());
        for (Map.Entry<String,RelationColumn> e : all.entrySet())
        {
            RelationColumn out = getColumn(e.getKey());
            assert null != out;
            ret.put(e.getKey(), out);
        }
        return ret;
    }


    RelationColumn getLookupColumn(RelationColumn parentRelCol, String name)
    {
        assert parentRelCol instanceof _WrapperColumn;
        assert parentRelCol.getTable() == this;

        _WrapperColumn parent = (_WrapperColumn)parentRelCol;
        FieldKey k = new FieldKey(parent._key, name);
        RelationColumn ret = _selectedColumns.get(k);
        if (null != ret)
            return ret;

        // parent is one part passthrough with extended FK
        // parent is one part passthrough with underlying FK
        // parent is a lookup (extended FK)
        // parent is a lookup (underlying FK)

        ColumnType.Fk fk = parent instanceof PassThroughColumn ? ((PassThroughColumn)parent)._columnFK : null;
        return getLookupColumn(parent, fk, name);
    }


    RelationColumn getLookupColumn(RelationColumn parentRelCol, ColumnType.Fk fk, String name)
    {
        _WrapperColumn parent = (_WrapperColumn)parentRelCol;
        FieldKey k = new FieldKey(parent._key, name);

        if (parent instanceof PassThroughColumn)
        {
            // push down to input relation if we can, otherwise wrap it and do it ourselves
            RelationColumn sourceCol;
            if (null != fk)
                sourceCol = _source.getLookupColumn(((PassThroughColumn)parent)._wrapped, fk, name);
            else
                sourceCol = _source.getLookupColumn(((PassThroughColumn)parent)._wrapped, name);
            if (null != sourceCol)
            {
                PassThroughColumn pt = new PassThroughColumn(k, sourceCol);
                _selectedColumns.put(k,pt);
                return pt;
            }
            // fall through
        }

        if (parentRelCol.getFk() == null)
            return null;

        setHasLookup();

        QueryLookupColumn lc = createQueryLookupColumn(k, parentRelCol, parentRelCol.getFk());
        if (null == lc)
            return null;
        _selectedColumns.put(k,lc);
        return lc;
    }


    @Override
    Collection<String> getKeyColumns()
    {
        return _source.getKeyColumns();
    }


    @Override
    public SQLFragment getFromSql()
    {
        if (!_hasLookups)
            return _source.getFromSql();
        else
            return super.getFromSql();
    }


    public SQLFragment getSql()
    {
        if (!_hasLookups)
        {
            return _source.getSql();
        }

        SQLFragment sourceFromSql = _source.getFromSql();
        if (null == sourceFromSql || !_query.getParseErrors().isEmpty())
            return null;

        Map<String, SQLFragment> joins = new LinkedHashMap<>();
        SqlBuilder sql = new SqlBuilder(getSchema().getDbSchema());
        assert sql.appendComment("<QueryLookupWrapper>");

        sql.append("SELECT ");
        String comma = "";
        for (RelationColumn col : _selectedColumns.values())
        {
            if (0 == col.ref.count())
                continue;
            sql.append(comma);
            if (col instanceof QueryLookupColumn)
                col.declareJoins(_source.getAlias(), joins);
            SQLFragment f = col.getInternalSql();
            if (f.getSQL().length() > 40)
                sql.append("\n").append(f).append("\n");
            else
                sql.append(f);
            sql.append(" AS ");
            sql.append(col.getAlias());
            comma = ", ";
        }
        sql.append("\nFROM ");
        sql.append(sourceFromSql);

        for (SQLFragment j : joins.values())
            sql.append(j);

        assert sql.appendComment("</QueryLookupWrapper>");
        return sql;
    }


    String getQueryText()
    {
        return _source.getQueryText();
    }

    @Override
    public void setContainerFilter(ContainerFilter containerFilter)
    {
        getSqlTableInfo().setContainerFilter(containerFilter);
        _source.setContainerFilter(containerFilter);
    }


    @Override
    protected Set<RelationColumn> getSuggestedColumns(Set<RelationColumn> selected)
    {
        // TODO handle lookup columns
        HashSet<RelationColumn> unwrapped = new HashSet<>();
        for (RelationColumn rc : selected)
        {
            if (rc instanceof PassThroughColumn)
                unwrapped.add(((PassThroughColumn)rc)._wrapped);
        }
        return _source.getOrderedSuggestedColumns(unwrapped);
    }


    private static abstract class _WrapperColumn extends RelationColumn
    {
        final QueryRelation _table;
        final FieldKey _key;
        final String _alias;
        
        _WrapperColumn(QueryRelation table, FieldKey key, String alias)
        {
            _table = table;
            _key = key;
            _alias = alias;
        }

        @Override
        public FieldKey getFieldKey()
        {
            return _key;
        }

        QueryRelation getTable()
        {
            return _table;
        }

        String getAlias()
        {
            return _alias;
        }
    }


    class PassThroughColumn extends _WrapperColumn
    {
        QueryRelation.RelationColumn _wrapped;
        ColumnType.Fk _columnFK;
        ForeignKey _fk;

        PassThroughColumn(FieldKey key, RelationColumn c)
        {
            super(QueryLookupWrapper.this, key, _aliasManager.decideAlias(c.getAlias()));
            _wrapped = c;

            // extra metadata only for selected columns
            if (key.getParent() != null)
                return;
            _columnFK = _fkMap.get(key.getName());
        }

        public ForeignKey getFk()
        {
            if (null != _fk)
            {
                return _fk;
            }
            if (null != _columnFK)
            {
                _fk = AbstractTableInfo.makeForeignKey(_schema, _columnFK);
                if (_fk == null)
                {
                    //noinspection ThrowableInstanceNeverThrown
                    _table._query.getParseErrors().add(new QueryParseException("Could not resolve ForeignKey on column '" + getFieldKey().getName() + "'", null, 0, 0));
                }
                return _fk;
            }
            return this._wrapped.getFk();
        }

        public JdbcType getJdbcType()
        {
            return _wrapped.getJdbcType();
        }

        @Override
        boolean isHidden()
        {
            return _wrapped.isHidden();
        }

        @Override
        void copyColumnAttributesTo(ColumnInfo to)
        {
            _wrapped.copyColumnAttributesTo(to);
            if (to.getFieldKey().getParent() == null)
            {
                ColumnType columnMetaData = _columnMetaDataMap.get(to.getName());
                if (null != columnMetaData)
                    to.loadFromXml(columnMetaData, true);
            }
        }

        public SQLFragment getValueSql()
        {
            if (!_hasLookups)
                return _wrapped.getValueSql();
            else
                return super.getValueSql();
        }

        SQLFragment getInternalSql()
        {
            return _wrapped.getValueSql();
        }

        @Override
        public int addRef(@NotNull Object refer)
        {
            if (0 == ref.count())
                _wrapped.addRef(this);
            return super.addRef(refer);
        }

        @Override
        public int releaseRef(@NotNull Object refer)
        {
            if (0 == ref.count())
                return 0;
            int count = super.releaseRef(refer);
            if (count == 0)
                _wrapped.releaseRef(this);
            return count;
        }
    }


    private SQLTableInfo getSqlTableInfo()
    {
        if (null == _sti)
            _sti = new SQLTableInfo(getSchema().getDbSchema(), getAlias());
        return _sti;
    }


    public QueryLookupColumn createQueryLookupColumn(FieldKey key, RelationColumn parent, @NotNull ForeignKey fk)
    {
        ColumnInfo fkCol;
        if (!(parent instanceof QueryLookupColumn))
        {
            fkCol = new RelationColumnInfo(getSqlTableInfo(), parent);
        }
        else
        {
            fkCol = ((QueryLookupColumn)parent)._lkCol;
        }
        ColumnInfo lkCol = fk.createLookupColumn(fkCol, key.getName());
        if (null == lkCol)
            return null;
        return new QueryLookupColumn(key, parent, fk, lkCol);
    }


    // almost the same as TableColumn
    class QueryLookupColumn extends _WrapperColumn
    {
        final RelationColumn _foreignKey;
        final ForeignKey _fk;
        final ColumnInfo _lkCol;

        protected QueryLookupColumn(FieldKey key, RelationColumn parent, @NotNull ForeignKey fk, ColumnInfo lkCol)
        {
            super(parent.getTable(), key, _aliasManager.decideAlias((parent.getAlias() + "$" + key.getName()).toLowerCase()));
            lkCol.setAlias(getAlias());
            _foreignKey = parent;
            _fk = fk;
            _lkCol = lkCol;
        }

        @Override
        public ForeignKey getFk()
        {
            return _lkCol.getFk();
        }

        public JdbcType getJdbcType()
        {
            return _lkCol.getJdbcType();
        }

        @Override
        boolean isHidden()
        {
            return _lkCol.isHidden();
        }

        void copyColumnAttributesTo(ColumnInfo to)
        {
            to.copyAttributesFrom(_lkCol);
        }

        public void declareJoins(String parentAlias, Map<String, SQLFragment> map)
        {
            _lkCol.declareJoins(parentAlias, map);
        }

        SQLFragment getInternalSql()
        {
            return _lkCol.getValueSql(_source.getAlias());
        }
    }
}
