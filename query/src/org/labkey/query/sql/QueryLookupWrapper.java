package org.labkey.query.sql;

import org.labkey.api.data.*;
import org.labkey.api.query.*;
import org.labkey.data.xml.TableType;
import org.labkey.data.xml.ColumnType;
import static org.apache.commons.lang.StringUtils.defaultString;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
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
    final AliasManager _aliasManager;
    Query _query;
    QueryRelation _source;
    TableType _tableType;
    boolean _hasLookups = false;
    
    LinkedHashMap<FieldKey, String> _joins = new LinkedHashMap<FieldKey, String>();
    Map<String, ColumnType.Fk> _fkMap = new HashMap<String, ColumnType.Fk>();
    Map<FieldKey, RelationColumn> _selectedColumns = new HashMap<FieldKey, RelationColumn>();


    QueryLookupWrapper(Query query, QueryRelation relation, TableType md)
    {
        super(query);
        _aliasManager = new AliasManager(query.getSchema().getDbSchema());
        _alias = relation.getAlias();
        _source = relation;
        _tableType = md;

        for (ColumnType col :  _tableType.getColumns().getColumnArray())
        {
            ColumnType.Fk colFK = col.getFk();
            if (null == colFK || null == colFK.getFkTable() || null == colFK.getFkColumnName())
                continue;
            _fkMap.put(col.getColumnName(), colFK);
        }
    }


    @Override
    void setQuery(Query query)
    {
        super.setQuery(query);    //To change body of overridden methods use File | Settings | File Templates.
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


    protected List<RelationColumn> getAllColumns()
    {
        List<RelationColumn> all = _source.getAllColumns();
        List<RelationColumn> ret = new ArrayList<RelationColumn>(all.size());
        for (RelationColumn c : all)
        {
            RelationColumn out = getColumn(c.getName());
            assert null != out;
            ret.add(out);
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

        return getLookupColumn(parent, ((PassThroughColumn)parent)._columnFK, name);
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


    public SQLFragment getSql()
    {
        if (!_hasLookups)
        {
            return _source.getSql();
        }

        Map<String, SQLFragment> joins = new LinkedHashMap<String,SQLFragment>();
        SQLFragment sql = new SQLFragment();
        assert sql.appendComment("<QueryLookupWrapper@" + System.identityHashCode(this) + ">");

        sql.append("SELECT ");
        String comma = "";
        for (RelationColumn col : _selectedColumns.values())
        {
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
        sql.append("(\n");
        sql.append(_source.getSql());
        sql.append(") ");
        sql.append(_source.getAlias());

        for (SQLFragment j : joins.values())
            sql.append(j);

        assert sql.appendComment("</QueryLookupWrapper@" + System.identityHashCode(this) + ">");
        return sql;
    }


    String getQueryText()
    {
        return _source.getQueryText();
    }


    private static abstract class _WrapperColumn extends RelationColumn
    {
        final QueryRelation _table;
        final FieldKey _key;
        final String _name;
        final String _alias;
        
        _WrapperColumn(QueryRelation table, FieldKey key, String alias)
        {
            _table = table;
            _key = key;
            _name = key.getName();
            _alias = alias;
        }

        public String getName()
        {
            return _name;
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
            super(QueryLookupWrapper.this, key, _aliasManager.decideAlias(key.getName()));
            _wrapped = c;

            // extra metadata only for selected columns
            if (key.getParent() != null)
                return;
            _columnFK = _fkMap.get(key.getName());
        }

        public ForeignKey getFk()
        {
            if (null != _fk)
                return _fk;
            if (null == _columnFK)
                return null;
            _fk = AbstractTableInfo.makeForeignKey(_schema, _columnFK);
            if (_fk == null)
            {
                //noinspection ThrowableInstanceNeverThrown
                _table._query.getParseErrors().add(new QueryParseException("Could not resolve ForeignKey on column '" + getName() + "'", null, 0, 0));
            }
            return _fk;
        }

        public int getSqlTypeInt()
        {
            return _wrapped.getSqlTypeInt();
        }

        @Override
        void copyColumnAttributesTo(ColumnInfo to)
        {
            _wrapped.copyColumnAttributesTo(to);
        }

        public SQLFragment getValueSql(String tableAlias)
        {
            if (!_hasLookups)
                return _wrapped.getValueSql(tableAlias);
            else
                return super.getValueSql(tableAlias);
        }

        SQLFragment getInternalSql()
        {
            return _wrapped.getValueSql(_source.getAlias());
        }
    }


    public QueryLookupColumn createQueryLookupColumn(FieldKey key, RelationColumn parent, @NotNull ForeignKey fk)
    {
        SQLTableInfo qti =  new SQLTableInfo(parent.getTable()._schema.getDbSchema());
        qti.setName(parent.getTable().getAlias());
        ColumnInfo fkCol = new RelationColumnInfo(qti, parent);

        ColumnInfo lkCol = fk.createLookupColumn(fkCol, key.getName());
        if (null == lkCol)
            return null;
        return new QueryLookupColumn(key, parent, fk, lkCol);
    }    


    // almost the same as TableColumn
    class QueryLookupColumn extends _WrapperColumn
    {
        RelationColumn _foreignKey;
        final ForeignKey _fk;

        TableInfo _lookupTable;
        ColumnInfo _lkCol;

        protected QueryLookupColumn(FieldKey key, RelationColumn parent, @NotNull ForeignKey fk, ColumnInfo lkCol)
        {
            super(parent.getTable(), key, parent.getAlias() + "$" + AliasManager.makeLegalName(key.getName(), getDialect(parent)));
            _foreignKey = parent;
            _fk = fk;
            _lkCol = lkCol;
        }


        public int getSqlTypeInt()
        {
            return _lkCol.getSqlTypeInt();
        }

        void copyColumnAttributesTo(ColumnInfo to)
        {
            to.copyAttributesFrom(_lkCol);
        }

        public void declareJoins(String parentAlias, Map<String, SQLFragment> map)
        {
            _lkCol.declareJoins(parentAlias, map);
        }

        TableInfo getLookupTable()
        {
            return _fk.getLookupTableInfo();
        }
        
        SQLFragment getInternalSql()
        {
            return _lkCol.getValueSql(_source.getAlias());
        }
    }
}
