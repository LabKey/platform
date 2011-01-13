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
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.NamedObjectList;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.AliasManager;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.StringExpression;
import org.labkey.data.xml.ColumnType;

import java.util.ArrayList;
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
    QuerySelect _from;
    
    // all columns in the select except the pivot column
    LinkedHashMap<String,RelationColumn> _select;

    // subset of 'fact' columns to pivot
    Map<String,QAggregate.Type> _aggregates;

    // the pivot column
    RelationColumn _pivotColumn;
    Map<String,IConstant> _pivotValues;


    public QueryPivot(Query query, QuerySelect from, QQuery root)
    {
        super(query);
        _from = from;

        QPivot pivotClause = root.getChildOfType(QPivot.class);
        QExprList aggsList = (QExprList)pivotClause.childList().get(0);
        QIdentifier byId = (QIdentifier)pivotClause.childList().get(1);
        QExprList inList = (QExprList)pivotClause.childList().get(2);

        // this column is not selected, its values become columns
        _pivotColumn = _from.getColumn(byId.getIdentifier());

        // get all the columns, but delete the pivot column
        _select = new LinkedHashMap<String,RelationColumn>();
        _select.putAll(_from.getAllColumns());
        _select.remove(_pivotColumn.getFieldKey().getName());

        // inspect the agg columns
        _aggregates = new HashMap<String,QAggregate.Type>();
        for (QNode node : aggsList.childList())
        {
            QIdentifier id = (QIdentifier)node;
            QuerySelect.SelectColumn col = _from.getColumn(id.getIdentifier());
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
        _pivotValues = new LinkedHashMap<String,IConstant>();
        for (QNode node : inList.childList())
        {
            IConstant constant = (IConstant) node;
            _pivotValues.put(constant.getValue().toString(), constant);
        }
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

    @Override
    protected Map<String, RelationColumn> getAllColumns()
    {
        return _select;
    }

    @Override
    RelationColumn getColumn(@NotNull String name)
    {
        return _select.get(name);
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
        return _from.getSql();
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

        private SQLFragment getSql()
        {
            String tableAlias = "_t";
            SQLFragment sql = new SQLFragment();
            sql.appendComment("<QueryPivot>", getSqlDialect());
            sql.append("SELECT ");
            String comma = "";
            for (RelationColumn col : _select.values())
            {
                String name = col.getFieldKey().getName();
                boolean isAgg = _aggregates.containsKey(name);

                if (!isAgg)
                {
                    sql.append(comma).append(col.getValueSql(tableAlias));
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
                    FieldKey key = new FieldKey(col.getFieldKey(), pivotValues.getKey().toLowerCase());
                    QNode value = (QNode)pivotValues.getValue();
                    String alias = AliasManager.makeLegalName(key.toString(),getSqlDialect());
                    sql.append(comma).append("MAX(CASE WHEN (").append(_pivotColumn.getValueSql(tableAlias)).append("=").append(value.getSourceText());
                    sql.append(") THEN (").append(col.getValueSql(tableAlias)).append(") ELSE NULL END) AS ").append(alias);
                    comma = ",\n";
                }
            }
            sql.append("\n FROM (").append(_from._getSql(true)).append(") ").append(tableAlias).append("\n");

            // UNDONE: separate grouping columns from extra 'fact' columns
            sql.append("GROUP BY ");
            comma = "";
            for (RelationColumn col : _select.values())
            {
                if (_aggregates.containsKey(col.getFieldKey().getName()))
                    continue;
                sql.append(comma);
                sql.append(col.getValueSql(tableAlias));
            }
            sql.appendComment("</QueryPivot>", getSqlDialect());
            return sql;
        }
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
            String alias = AliasManager.makeLegalName(key.toString(),parent.getSqlDialect());
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
}
