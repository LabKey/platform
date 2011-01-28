package org.labkey.query.reports;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.exp.property.Type;
import org.labkey.api.query.UserSchema;
import org.labkey.api.util.Pair;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
* Copyright (c) 2008-2010 LabKey Corporation
* <p/>
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
* <p/>
* http://www.apache.org/licenses/LICENSE-2.0
* <p/>
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
* <p/>
* <p/>
* User: brittp
* Date: Jan 27, 2011 11:14:20 AM
*/
public class VisualizationSourceQuery
{
    private Container _container;
    private UserSchema _schema;
    private String _queryName;
    private VisualizationSourceColumn _pivot;
    private Set<VisualizationSourceColumn> _selects = new LinkedHashSet<VisualizationSourceColumn>();
    private Set<VisualizationAggregateColumn> _aggregates = new LinkedHashSet<VisualizationAggregateColumn>();
    private Set<VisualizationSourceColumn> _sorts = new LinkedHashSet<VisualizationSourceColumn>();
    private VisualizationSourceQuery _joinTarget;  // query this query must join to when building SQL
    private List<Pair<VisualizationSourceColumn, VisualizationSourceColumn>> _joinConditions;

    VisualizationSourceQuery(Container container, UserSchema schema, String queryName, VisualizationSourceQuery joinTarget)
    {
        _container = container;
        _schema = schema;
        _queryName = queryName;
        _joinTarget = joinTarget;
    }

    private void ensureSameQuery(VisualizationSourceColumn measure)
    {
        if (!measure.getSchemaName().equals(getSchemaName()) || !measure.getQueryName().equals(_queryName))
        {
            throw new IllegalArgumentException("Attempt to add measure from " + measure.getSchemaName() + "." +
                    measure.getQueryName() + " to source query " + getSchemaName() + "." + _queryName);
        }
    }

    public void setJoinConditions(List<Pair<VisualizationSourceColumn, VisualizationSourceColumn>> joinConditions)
    {
        _joinConditions = joinConditions;
    }

    public List<Pair<VisualizationSourceColumn, VisualizationSourceColumn>> getJoinConditions()
    {
        return _joinConditions;
    }

    public Container getContainer()
    {
        return _container;
    }

    public void addSelect(VisualizationSourceColumn select)
    {
        ensureSameQuery(select);
        _selects.add(select);
    }

    public Set<VisualizationSourceColumn> getSelects()
    {
        return _selects;
    }

    public void addAggregate(VisualizationAggregateColumn aggregate)
    {
        ensureSameQuery(aggregate);
        _aggregates.add(aggregate);
    }

    public Set<VisualizationAggregateColumn> getAggregates()
    {
        return _aggregates;
    }

    public VisualizationSourceColumn getPivot()
    {
        return _pivot;
    }

    public void setPivot(VisualizationSourceColumn pivot)
    {
        ensureSameQuery(pivot);
        if (_pivot != null)
        {
            throw new IllegalArgumentException("Can't pivot a single dataset by more than one column.  Attempt to pivot " +
                getSchemaName() + "." + _queryName + " by both " + _pivot.getSelectName() + " and " + pivot.getSelectName());
        }
        _pivot = pivot;
    }

    public void addSort(VisualizationSourceColumn sort)
    {
        ensureSameQuery(sort);
        _sorts.add(sort);
    }

    public Set<VisualizationSourceColumn> getSorts()
    {
        return _sorts;
    }

    public String getDisplayName()
    {
        return getSchemaName() + "." + _queryName;
    }

    public String getAlias()
    {
        return ColumnInfo.legalNameFromName(getSchemaName() + "_" + _queryName);
    }

    private void appendColumnNames(StringBuilder sql, Set<? extends VisualizationSourceColumn> columns, boolean aggregate, boolean aliasInsteadOfName, boolean appendAlias)
    {
        if (columns == null || columns.size() == 0)
            return;
        assert !(aliasInsteadOfName && appendAlias) : "Can't both use only alias and append alias";
        String leadingSep = "";

        for (VisualizationSourceColumn column : columns)
        {
            sql.append(leadingSep);
            if (aggregate && column instanceof VisualizationAggregateColumn)
            {
                VisualizationAggregateColumn agg = (VisualizationAggregateColumn) column;
                sql.append(agg.getAggregate().name()).append("(");
            }

            if (aliasInsteadOfName)
                sql.append(column.getAlias());
            else
                sql.append(column.getSelectName());

            if (aggregate && column instanceof VisualizationAggregateColumn)
                sql.append(")");

            if (appendAlias)
                sql.append(" AS ").append(column.getAlias());
            leadingSep = ", ";
        }
    }


    public String getSelectClause()
    {
        StringBuilder selectList = new StringBuilder("SELECT ");
        Set<VisualizationSourceColumn> selects = new LinkedHashSet<VisualizationSourceColumn>();
        if (_pivot != null)
                selects.add(_pivot);
        selects.addAll(_selects);
        selects.addAll(_sorts);
        selects.addAll(_aggregates);
        appendColumnNames(selectList, selects, true, false, true);
        selectList.append("\n");
        return selectList.toString();
    }

    public String getFromClause()
    {
        String schemaName = getSchemaName();
        if (schemaName.indexOf(' ') >= 0)
            schemaName = "\"" + schemaName + "\"";
        String queryName = _queryName;
        if (queryName.indexOf(' ') >= 0)
            queryName = "\"" + queryName + "\"";
        return "FROM " + schemaName + "." + queryName + "\n";
    }


    public String getGroupByClause()
    {
        if (_aggregates != null && !_aggregates.isEmpty())
        {
            StringBuilder groupBy = new StringBuilder("GROUP BY ");

            Set<VisualizationSourceColumn> groupBys = new LinkedHashSet<VisualizationSourceColumn>();
            if (_pivot != null)
                    groupBys.add(_pivot);
            groupBys.addAll(_selects);
            groupBys.addAll(_sorts);

            appendColumnNames(groupBy, groupBys, false, false, false);
            groupBy.append("\n");
            return groupBy.toString();
        }
        else
            return "";
    }

    private String appendValueList(StringBuilder sql, VisualizationSourceColumn col)
    {
        if (col.getValues() != null && col.getValues().size() > 0)
        {
            sql.append(" IN (");
            String sep = "";
            for (Object value : _pivot.getValues())
            {
                sql.append(sep);
                if (_pivot.getType().isNumeric() || _pivot.getType() == Type.BooleanType)
                    sql.append(value);
                else
                    sql.append("'").append(value).append("'");
                sep = ", ";
            }
            sql.append(")");
        }
        return sql.toString();
    }

    public String getPivotClause()
    {
        if (_pivot != null)
        {
            StringBuilder pivotClause = new StringBuilder("PIVOT ");
            appendColumnNames(pivotClause, _aggregates, false, true, false);
            pivotClause.append(" BY ");
            appendColumnNames(pivotClause, Collections.singleton(_pivot), false, true, false);
            appendValueList(pivotClause, _pivot);
            pivotClause.append("\n");
            return pivotClause.toString();
        }
        else
            return "";
    }

    public String getWhereClause()
    {
        StringBuilder where = new StringBuilder();
        String sep = "WHERE ";
        for (VisualizationSourceColumn select : _selects)
        {
            if (select.getValues() != null && !select.getValues().isEmpty())
            {
                where.append(sep);
                appendColumnNames(where, Collections.singleton(select), false, true, false);
                appendValueList(where, select);
                sep = " AND ";
            }
        }
        where.append("\n");
        return where.toString();
    }

    public String getSQL()
    {
        StringBuilder sql = new StringBuilder();
        sql.append(getSelectClause()).append("\n");
        sql.append(getFromClause()).append("\n");
        sql.append(getWhereClause()).append("\n");
        sql.append(getGroupByClause()).append("\n");
        sql.append(getPivotClause()).append("\n");
        return sql.toString();
    }

    public VisualizationSourceQuery getJoinTarget()
    {
        return _joinTarget;
    }

    public String getSchemaName()
    {
        return _schema.getSchemaName();
    }

    public UserSchema getSchema()
    {
        return _schema;
    }

    public String getQueryName()
    {
        return _queryName;
    }
}
