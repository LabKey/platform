/*
 * Copyright (c) 2011 LabKey Corporation
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

import org.apache.commons.beanutils.ConvertUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.collections.CaseInsensitiveMapWrapper;
import org.labkey.api.collections.NamedObjectList;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.NullColumnInfo;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.query.AliasManager;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QueryParseException;
import org.labkey.api.query.QueryService;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.util.StringExpression;
import org.labkey.data.xml.ColumnType;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Jan 12, 2011
 * Time: 11:47:53 AM
 */
public class QueryPivot extends QueryRelation
{
    final boolean usePivotForeignKey = false;
    final QuerySelect _from;
    final AliasManager _manager;
    final Map<FieldKey,String> pivotColumnAliases = new HashMap<FieldKey,String>();

    // all columns in the select except the pivot column, in original order
    LinkedHashMap<String,RelationColumn> _select = new LinkedHashMap<String,RelationColumn>();

    // the grouping keys (except the pivot column)
    final HashMap<String,RelationColumn> _grouping = new HashMap<String,RelationColumn>();

    // pivoted aggregate columms
    final Map<String,QAggregate.Type> _aggregates = new HashMap<String,QAggregate.Type>();

    // the pivot column
    RelationColumn _pivotColumn;
    Map<String,IConstant> _pivotValues;

    QueryRelation _inQuery;


    public QueryPivot(Query query, QuerySelect from, QQuery root)
    {
        super(query);

        // We may need to modify the QuerySelect a little
        // make sure all group by columns are selected
        // grab orderby and limit
        _from = from;
        _manager = new AliasManager(query.getSchema().getDbSchema());

        QPivot pivotClause = root.getChildOfType(QPivot.class);
        QNode aggsList = pivotClause.childList().get(0);
        QIdentifier byId = (QIdentifier)pivotClause.childList().get(1);
        QNode inList = pivotClause.childList().size() > 2 ? pivotClause.childList().get(2) : null;

        // this column is not selected, its values become columns
        _pivotColumn = _from.getColumn(byId.getIdentifier());
        if (null == _pivotColumn)
        {
            parseError("Can not find pivot column: " + byId.getIdentifier(), byId);
            return;
        }
        
        // get all the columns, but delete the pivot column
        Map<String,RelationColumn> allFromColumns = _from.getAllColumns();
        for (RelationColumn r : allFromColumns.values())
            _manager.claimAlias(r.getAlias(), null);
        _select = new LinkedHashMap<String,RelationColumn>();
        _select.putAll(allFromColumns);
        _select.remove(_pivotColumn.getFieldKey().getName());

        // modify QuerySelect _from (after we copy the original select list)
        prepareQuerySelect();
        if (!getParseErrors().isEmpty())
            return;

        // inspect the agg columns
        for (QNode node : aggsList.childList())
        {
            QIdentifier id = (QIdentifier)node;
            QuerySelect.SelectColumn col = _from.getColumn(id.getIdentifier());
            if (null == col)
            {
                parseError("Can not find column in select list: " + id.getIdentifier(), id);
                continue;
            }
            QNode source = col._node;
            if (source instanceof QAs)
                source = source.childList().get(0);
            QAggregate.Type rollupType = null;
            if (source instanceof QAggregate)
            {
                switch (((QAggregate) source).getType())
                {
                    case COUNT:
                    case SUM:
                        rollupType = QAggregate.Type.SUM;
                        break;
                    case MIN:
                        rollupType = QAggregate.Type.MIN;
                        break;
                    case MAX:
                        rollupType = QAggregate.Type.MAX;
                        break;
                    case AVG:
                    case STDDEV:
                    case GROUP_CONCAT:
                        // nyi;
                        break;
                }
            }
            _aggregates.put(col.getFieldKey().getName(), rollupType);
        }

        if (inList instanceof QQuery)
        {
            _inQuery = createSubquery((QQuery)inList, "_pivotValues");
            // call getSql() here to generate errors (quick fail)
            _inQuery.getSql();
        }
        else if (inList instanceof QSelect)
        {
            // simple in list
            CaseInsensitiveMapWrapper<IConstant> pivotValues = new CaseInsensitiveMapWrapper<IConstant>(new LinkedHashMap<String,IConstant>());
            for (QNode node : inList.childList())
            {
                IConstant constant;
                String name = null;

                if (node instanceof IConstant)
                    constant = (IConstant) node;
                else
                {
                    assert node instanceof QAs;
                    constant = (IConstant)node.childList().get(0);
                    if (node.childList().size() > 1)
                        name = ((QIdentifier)node.childList().get(1)).getIdentifier();
                }
                if (null == name)
                    name = toName(constant);

                // TODO check duplicate values as well as duplicate names
                if (pivotValues.containsKey(name))
                    parseError("Duplicate pivot column name: " + name, node);
                else
                    pivotValues.put(name, constant);
            }
            _pivotValues = pivotValues;
        }
    }


    QueryRelation createSubquery(QQuery qquery, String alias)
    {
        QueryRelation sub = Query.createQueryRelation(this._query, qquery);
        sub._parent = this;
        sub._inFromClause = true;
        if (null != alias)
            sub.setAlias(alias);
        return sub;
    }


    void prepareQuerySelect()
    {
        // undone copy order by
        // undone copy limit
        _from._limit = null;
        _from._orderBy = null;

        // make sure all group by columns are selected
        Map<String, QuerySelect.SelectColumn> map = _from.getGroupByColumns();
        boolean pivotFound = false;
        for (Map.Entry<String, QuerySelect.SelectColumn> entry : map.entrySet())
        {
            if (entry.getValue() == _pivotColumn)
            {
                pivotFound = true;
                continue;
            }
            _grouping.put(entry.getKey(), entry.getValue());
            // find/verify
        }
        if (!pivotFound)
            parseError("Could not find pivot column in group by list, expression must match exactly: " + _pivotColumn.getAlias(), null);
    }



    Map<String,IConstant> getPivotValues() throws SQLException
    {
        if (null != _pivotValues)
            return _pivotValues;

        ResultSet rs = null;
        try
        {
            _pivotValues = new CaseInsensitiveMapWrapper<IConstant>(new LinkedHashMap<String,IConstant>());
            SQLFragment sqlPivotValues;

            if (null != _inQuery)
            {
                // explicit subquery case
                sqlPivotValues = _inQuery.getSql();
            }
            else
            {
                // implicit sub query
                // CONSIDER: _from.clone() and then simplify the select
                // execute query
                sqlPivotValues = new SQLFragment();
                sqlPivotValues.append("SELECT DISTINCT ").append(_pivotColumn.getValueSql("_pivotValues"));
                sqlPivotValues.append("\nFROM (").append(_from.getSql()).append(") _pivotValues");
                sqlPivotValues.append("\nORDER BY 1 ASC");
            }
            rs = Table.executeQuery(getSchema().getDbSchema(), sqlPivotValues);
            JdbcType type = JdbcType.valueOf(rs.getMetaData().getColumnType(1));
            while (rs.next())
            {
                Object value = rs.getObject(1);
                IConstant wrap = wrapConstant(value, type, rs.wasNull());
                String name = toName(wrap);
                _pivotValues.put(name, wrap);
            }
        }
        finally
        {
            ResultSetUtil.close(rs);
        }

        return _pivotValues;
    }


    String toName(IConstant c)
    {
        if (c instanceof QNull)
            return "NULL";
        Object v = c.getValue();
        if (v instanceof String)
            return (String)v;
        return ConvertUtils.convert(v);
    }


    IConstant wrapConstant(Object v, JdbcType t, boolean wasNull)
    {
        if (wasNull)
            return new QNull();
        if (t == JdbcType.BOOLEAN)
        {
            assert v instanceof Boolean;
            return new QBoolean(v==Boolean.TRUE);
        }
        if (v instanceof Number)
        {
            return new QNumber((Number)v);
        }
        if (!(v instanceof String))
            v = ConvertUtils.convert(v);
        return new QString((String)v);
    }


    @Override
    void declareFields()
    {
        _from.declareFields();
    }


    @Override
    TableInfo getTableInfo()
    {
        QueryTableInfo qti = new PivotTableInfo();
        try
        {
            getPivotValues();
        }
        catch (QueryService.NamedParameterNotProvided x)
        {
            parseError("When used with parameterized query, PIVOT requires an explicit values list", null);
            return null;
        }
        catch (SQLException x)
        {
            parseError("Could not compute pivot column list", null);
            return null;
        }
        return qti;
    }


    class PivotColumn extends RelationColumn
    {
        RelationColumn _s;

        PivotColumn(RelationColumn s)
        {
            _s = s;
        }

        @Override
        SQLFragment getInternalSql()
        {
            SQLFragment ret = _s.getInternalSql();
            return ret;
        }

        // wrapper methods

        @Override
        public FieldKey getFieldKey()
        {
            return _s.getFieldKey();
        }

        @Override
        String getAlias()
        {
            return _s.getAlias();
        }

        @Override
        QueryRelation getTable()
        {
            return QueryPivot.this;
        }

        @NotNull
        @Override
        public JdbcType getJdbcType()
        {
            return _s.getJdbcType();
        }

        @Override
        void copyColumnAttributesTo(ColumnInfo to)
        {
            _s.copyColumnAttributesTo(to);
            if (_aggregates.containsKey(_s.getFieldKey().getName()))
            {
                if (usePivotForeignKey)
                    to.setFk(new PivotForeignKey(_s));
                else
                    to.setHidden(true);
            }
        }
    }


    Map<String,RelationColumn> _columns = null;

    @Override
    protected Map<String, RelationColumn> getAllColumns()
    {
        if (null == _columns)
        {
            Map<String, IConstant> pivotValues;
            try
            {
                pivotValues = getPivotValues();
                // UNDONE: _from.getSql() has side-effect that seems to be important for declareFields()
                _from.getSql();
            }
            catch (SQLException x)
            {
                assert(!getParseErrors().isEmpty());
                if (getParseErrors().isEmpty())
                    getParseErrors().add(new QueryException(getSqlDialect().sanitizeException(x), x));
                pivotValues = Collections.EMPTY_MAP;
            }

            _columns = new CaseInsensitiveMapWrapper<RelationColumn>(new LinkedHashMap<String, RelationColumn>(_select.size()*2));
            for (Map.Entry<String,RelationColumn> entry : _select.entrySet())
            {
                String name = entry.getKey();
                RelationColumn s = entry.getValue();
                RelationColumn p = new PivotColumn(s);
                _columns.put(name, p);

                if (!usePivotForeignKey && _aggregates.containsKey(name))
                {
                    for (String pivotValue : pivotValues.keySet())
                    {
                        String pivotName = makePivotAggName(name, pivotValue);
                        RelationColumn pvt = _makePivotedAggColumn(s, new FieldKey(null, pivotName), pivotValue);
                        _columns.put(pivotName, pvt);
                    }
                }
            }
        }
        return _columns;
    }


    final static String PIVOT_SEPARATOR = "::";
    
    String makePivotAggName(String aggName, String pivotValue)
    {
        return pivotValue + PIVOT_SEPARATOR + aggName;
    }
    

    @Override
    RelationColumn getColumn(@NotNull String name)
    {
        return getAllColumns().get(name);
    }

    @Override
    int getSelectedColumnCount()
    {
        return _select.size();
    }

    @Override
    RelationColumn getLookupColumn(@NotNull RelationColumn parent, @NotNull final String name)
    {
        if (!(parent instanceof PivotColumn))
            return null;
        RelationColumn agg = ((PivotColumn)parent)._s;
        return _makePivotedAggColumn(agg, new FieldKey(parent.getFieldKey(), name), name);
    }


    private RelationColumn _makePivotedAggColumn(@NotNull final RelationColumn agg, final FieldKey key, @NotNull final String name)
    {
        if (!_aggregates.containsKey(agg.getFieldKey().getName()))
            return null;

        Map<String, IConstant> pivotValues;
        try
        {
            pivotValues = getPivotValues();
        }
        catch (SQLException x)
        {
            return null;
        }

        if (null == pivotValues)
        {
            assert(!getParseErrors().isEmpty());
            return null;
        }

        final IConstant c = pivotValues.get(name);
        final String alias = makePivotColumnAlias(agg.getAlias(), name);

        return new RelationColumn()
        {
            @Override
            public FieldKey getFieldKey()
            {
                return key;
            }

            @Override
            String getAlias()
            {
                return alias;
            }

            @Override
            QueryRelation getTable()
            {
                return QueryPivot.this;
            }

            @NotNull
            @Override
            public JdbcType getJdbcType()
            {
                return agg.getJdbcType();
            }

            @Override
            void copyColumnAttributesTo(ColumnInfo to)
            {
                agg.copyColumnAttributesTo(to);
                if (usePivotForeignKey)
                    to.setLabel(null);
                else if (_aggregates.size() == 1)
                    to.setLabel(name);
                else
                {
                    String aggLabel = to.getLabel();
                    to.setLabel(name + " " + aggLabel);
                }
            }

            @Override
            public SQLFragment getValueSql(String tableAlias)
            {
                if (null == c)
                    return new SQLFragment(NullColumnInfo.nullValue(getSqlDialect().sqlTypeNameFromSqlType(getJdbcType().sqlType)));
                else
                    return new SQLFragment(tableAlias + "." + alias);
            }
        };
    }



    @Override
    RelationColumn getLookupColumn(@NotNull RelationColumn parent, @NotNull ColumnType.Fk fk, @NotNull String name)
    {
        return null;
    }

    
    @Override
    public SQLFragment getSql()
    {
        String tableAlias = "_t";
        SqlBuilder sql = new SqlBuilder(getSqlDialect());
        sql.appendComment("<QueryPivot>", getSqlDialect());
        sql.append("SELECT ");
        String comma = "";

        for (RelationColumn col : _select.values())
        {
            String name = col.getFieldKey().getName();
            boolean isAgg = _aggregates.containsKey(name);
            boolean isKey = _grouping.containsKey(name);

            if (isKey)
            {
                sql.append(comma).append(col.getValueSql(tableAlias)).append(" AS ").append(col.getAlias());
                comma = ",\n";
                continue;
            }

            if (!isAgg)
            {
                sql.append(comma).append("MAX(").append(col.getValueSql(tableAlias)).append(") AS ").append(col.getAlias());
                comma = ",\n";
                continue;
            }

            QAggregate.Type type = _aggregates.get(name);
            if (null == type)
            {
                sql.append(comma).append("NULL AS ").append(col.getAlias());
                comma = ",\n";
            }
            else
            {
                String agg = type.name();
                sql.append(comma).append(agg).append("(").append(col.getValueSql(tableAlias)).append(") AS ").append(col.getAlias());
                comma = ",\n";
            }

            // getSql() does not return
            Map<String,IConstant> pivotValues;
            try
            {
                pivotValues = getPivotValues();
            }
            catch (QueryService.NamedParameterNotProvided x)
            {
                throw new QueryParseException("When used with parameterized query, PIVOT requires an explicit values list", null);
            }
            catch (SQLException x)
            {
                throw new QueryParseException("Could not compute pivot column list", null);
            }
            
            // add aggregate expressions
            for (Map.Entry<String,IConstant> pivotValue : pivotValues.entrySet())
            {
                QNode value = (QNode)pivotValue.getValue();
                String alias = makePivotColumnAlias(col.getAlias(), pivotValue.getKey());
                sql.append(comma).append("MAX(CASE WHEN (").append(_pivotColumn.getValueSql(tableAlias));
                if (value instanceof QNull)
                    sql.append(" IS NULL");
                else
                    sql.append("=").append(value.getSourceText());
                sql.append(") THEN (").append(col.getValueSql(tableAlias)).append(") ELSE NULL END) AS ").append(alias);
                comma = ",\n";
            }
        }
        SQLFragment fromSql = _from._getSql(true);
        if (null == fromSql)
        {
            assert !getParseErrors().isEmpty();
            return null;
        }
        sql.append("\n FROM (").append(_from._getSql(true)).append(") ").append(tableAlias).append("\n");

        // UNDONE: separate grouping columns from extra 'fact' columns
        // sql.append("GROUP BY ");
        sql.pushPrefix("GROUP BY ");
        for (RelationColumn col : _grouping.values())
        {
            if (_aggregates.containsKey(col.getFieldKey().getName()))
                continue;
            sql.append(col.getValueSql(tableAlias));
            sql.nextPrefix(",");
        }
        sql.appendComment("</QueryPivot>", getSqlDialect());
        return sql;
    }

    @Override
    String getQueryText()
    {
        return null;
    }

    @Override
    public void setContainerFilter(ContainerFilter containerFilter)
    {
        _from.setContainerFilter(containerFilter);
    }


    class PivotTableInfo extends QueryTableInfo
    {
        PivotTableInfo()
        {
            super(QueryPivot.this, "_pivot");

            for (RelationColumn col : getAllColumns().values())
            {
                String name = col.getFieldKey().getName();
                ColumnInfo columnInfo = new RelationColumnInfo(this, col);
                addColumn(columnInfo);
            }
        }


        @Override
        public SQLFragment getFromSQL(String pivotTableAlias)
        {
            SQLFragment f = new SQLFragment();
            SQLFragment fromSql = getSql();
            if (null == fromSql)
                return null;
            f.append("(").append(fromSql).append(") ").append(pivotTableAlias);
            return f;
        }


        @Override
        public List<FieldKey> getDefaultVisibleColumns()
        {
            Map<String, IConstant> pivotValues;
            try
            {
                pivotValues = getPivotValues();
            }
            catch (SQLException x)
            {
                pivotValues = Collections.EMPTY_MAP;
            }

            ArrayList<FieldKey> list = new ArrayList<FieldKey>();

            if (usePivotForeignKey)
            {
                for (RelationColumn col : _select.values())
                {
                    if (_aggregates.containsKey(col.getFieldKey().getName()))
                    {
                        for (String displayField : pivotValues.keySet())
                        {
                            list.add(new FieldKey(col.getFieldKey(), displayField));
                        }
                    }
                    else
                    {
                        list.add(col.getFieldKey());
                    }
                }
            }
            else
            {
                for (RelationColumn col : getAllColumns().values())
                {
                    if (_aggregates.containsKey(col.getFieldKey().getName()))
                        continue;
                    list.add(col.getFieldKey());
                }
            }
            return list;
        }
    }



    SqlDialect _dialect;
    
    SqlDialect getSqlDialect()
    {
        if (null == _dialect)
            _dialect = _query.getSchema().getDbSchema().getSqlDialect();
        return _dialect;
    }


    
    // We could use aliasManager and remember these
    // but its easier if we can generate names we expect to be unique
    String makePivotColumnAlias(String aggAlias, String pivotValueName)
    {
        FieldKey key = FieldKey.fromParts(aggAlias, pivotValueName.toLowerCase());
        String alias =  pivotColumnAliases.get(key);
        if (null != alias)
            return alias;
        alias =  _manager.decideAlias("__pvt_" + _query.incrementAliasCounter() + "_" + key.toString());
        pivotColumnAliases.put(key,alias);
        return alias;
    }


    // marker interface
    // ForeignKey whose lookup columns are carried along with the fk
    interface ExpandoForeignKey {}


    class PivotForeignKey implements ForeignKey, ExpandoForeignKey
    {
        RelationColumn _agg;

        PivotForeignKey(RelationColumn agg)
        {
            _agg = agg;
        }
        
        @Override
        public ColumnInfo createLookupColumn(ColumnInfo parent, String displayField)
        {
            if (null == displayField)
                return null;
            RelationColumn lk = _makePivotedAggColumn(_agg, new FieldKey(parent.getFieldKey(), displayField), displayField);
            ColumnInfo col = new RelationColumnInfo(parent.getParentTable(), lk);
            return col;
        }

        @Override
        public TableInfo getLookupTableInfo()
        {
            AbstractTableInfo t = new AbstractTableInfo(_query.getSchema().getDbSchema())
            {
                @Override
                protected SQLFragment getFromSQL()
                {
                    return null;
                }
            };
            Map<String, IConstant> pivotValues;
            try
            {
                pivotValues = getPivotValues();
            }
            catch (SQLException x)
            {
                return null;
            }
            if (null == pivotValues)
            {
                assert(!getParseErrors().isEmpty());
                return null;
            }
            for (String displayField : pivotValues.keySet())
            {
                ColumnInfo c = new RelationColumnInfo(t, _agg);
                c.setName(displayField);
                c.setLabel(displayField);
                t.addColumn(c);
            }
            return t;
        }

        @Override
        public StringExpression getURL(ColumnInfo parent)
        {
            return null;
        }

        @Override
        public NamedObjectList getSelectList()
        {
            return null;
        }

        @Override
        public String getLookupContainerId()
        {
            return null;
        }

        @Override
        public String getLookupTableName()
        {
            return null;
        }

        @Override
        public String getLookupSchemaName()
        {
            return null;
        }

        @Override
        public String getLookupColumnName()
        {
            return null;
        }
    }

    private void parseError(String message, QNode node)
    {
        Query.parseError(getParseErrors(), message, node);
    }
}


/***  TODO
  case-sensitive grouping in Postgres
  non-string pivot column (allow?)
  QuerySelect.getGroupByColumns() after resolveFields() would give better expression matching
  NOTE bugs with postgres < 8.3.7?
***/
