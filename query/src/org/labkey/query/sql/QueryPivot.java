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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.collections.CaseInsensitiveMapWrapper;
import org.labkey.api.collections.NamedObjectList;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.query.AliasManager;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.StringExpression;
import org.labkey.data.xml.ColumnType;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Jan 12, 2011
 * Time: 11:47:53 AM
 */
public class QueryPivot extends QueryRelation
{
    QuerySelect _from;

    // all columns in the select except the pivot column, in original order
    LinkedHashMap<String,RelationColumn> _select = new LinkedHashMap<String,RelationColumn>();

    // the grouping keys (except the pivot column)
    final HashMap<String,RelationColumn> _grouping = new HashMap<String,RelationColumn>();

    // pivoted aggregate columms
    final Map<String,QAggregate.Type> _aggregates = new HashMap<String,QAggregate.Type>();

    // the pivot column
    RelationColumn _pivotColumn;
    Map<String,IConstant> _pivotValues;


    public QueryPivot(Query query, QuerySelect from, QQuery root)
    {
        super(query);

        // We may need to modify the QuerySelect a little
        // make sure all group by columns are selected
        // grab orderby and limit
        _from = from;

        QPivot pivotClause = root.getChildOfType(QPivot.class);
        QNode aggsList = pivotClause.childList().get(0);
        QIdentifier byId = (QIdentifier)pivotClause.childList().get(1);
        QNode inList = pivotClause.childList().get(2);

        // this column is not selected, its values become columns
        _pivotColumn = _from.getColumn(byId.getIdentifier());
        if (null == _pivotColumn)
        {
            parseError("Can not find pivot column: " + byId.getIdentifier(), byId);
            return;
        }
        
        // get all the columns, but delete the pivot column
        _select = new LinkedHashMap<String,RelationColumn>();
        _select.putAll(_from.getAllColumns());
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

        // in list
        _pivotValues = new CaseInsensitiveMapWrapper<IConstant>(new LinkedHashMap<String,IConstant>());
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
            {
                if (constant instanceof QNull)
                    name = "NULL";
                else
                    name = constant.getValue().toString();
            }
            // TODO check duplicate values as well as duplicate names
            if (_pivotValues.containsKey(name))
                parseError("Duplicate pivot column name: " + name, node);
            else
                _pivotValues.put(name, constant);
        }
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
            parseError("Could not find pivot column in group by list: " + _pivotColumn.getAlias(), null);
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
        }
    }


    Map<String,RelationColumn> _columns = null;

    @Override
    protected Map<String, RelationColumn> getAllColumns()
    {
        if (null == _columns)
        {
            _columns = new CaseInsensitiveMapWrapper<RelationColumn>(new LinkedHashMap<String, RelationColumn>(_select.size()*2));
            for (Map.Entry<String,RelationColumn> entry : _select.entrySet())
            {
                String name = entry.getKey();
                RelationColumn s = entry.getValue();
                RelationColumn p = new PivotColumn(s);
                _columns.put(name, p);
            }
        }

        return _columns;
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
    RelationColumn getLookupColumn(@NotNull RelationColumn parent, @NotNull String name)
    {
        return null;
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

            // add aggregate expressions
            for (Map.Entry<String,IConstant> pivotValues : _pivotValues.entrySet())
            {
                QNode value = (QNode)pivotValues.getValue();
                String alias = makePivotColumnAlias(col.getAlias(), pivotValues.getKey());
                sql.append(comma).append("MAX(CASE WHEN (").append(_pivotColumn.getValueSql(tableAlias));
                if (value instanceof QNull)
                    sql.append(" IS NULL");
                else
                    sql.append("=").append(value.getSourceText());
                sql.append(") THEN (").append(col.getValueSql(tableAlias)).append(") ELSE NULL END) AS ").append(alias);
                comma = ",\n";
            }
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


    class PivotTableInfo extends QueryTableInfo
    {

        PivotTableInfo()
        {
            super(QueryPivot.this, "_pivot");

            for (RelationColumn col : _select.values())
            {
                String name = col.getFieldKey().getName();
                ColumnInfo columnInfo = new RelationColumnInfo(this, col);
                if (_aggregates.containsKey(name))
                {
                    if (null == _aggregates.get(name))
                        columnInfo.setIsUnselectable(true);
                    columnInfo.setFk(new PivotForeignKey(col));
                }
                addColumn(columnInfo);
            }
        }

        @NotNull
        @Override
        public SQLFragment getFromSQL(String pivotTableAlias)
        {
            SQLFragment f = new SQLFragment();
            f.append("(").append(getSql()).append(") ").append(pivotTableAlias);
            return f;
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
        FieldKey key = FieldKey.fromParts(aggAlias, "_p_", pivotValueName);
        String alias =  AliasManager.makeLegalName(key.toString().toLowerCase(), getSqlDialect());
        return alias;
    }
    

    class PivotForeignKey implements ForeignKey
    {
        RelationColumn _agg;

        PivotForeignKey(RelationColumn agg)
        {
            _agg = agg;
        }
        
        @Override
        public ColumnInfo createLookupColumn(ColumnInfo parent, String displayField)
        {
            IConstant c = _pivotValues.get(displayField);
            if (null == c)
            {
                // if dynamic columns return new NullColumnInfo
                return null;
            }
            FieldKey key = new FieldKey(_agg.getFieldKey(), displayField.toLowerCase());
            String alias = makePivotColumnAlias(parent.getAlias(), displayField);
            return new ExprColumn(parent.getParentTable(), key, new SQLFragment(ExprColumn.STR_TABLE_ALIAS + "." + alias), parent.getJdbcType());
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
            for (String displayField : _pivotValues.keySet())
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
  dynamic pivot column list (IN not specified)
  QuerySelect.getGroupByColumns() after resolveFields() would give better expression matching
  NOTE bugs with postgres < 8.3.7?
***/