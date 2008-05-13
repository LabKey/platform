/*
 * Copyright (c) 2008 LabKey Corporation
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

import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;

import java.util.Arrays;
import java.sql.Types;

/**
 * Represents a measure for the CrosstabTableInfo. A measure contains a
 * source ColumnInfo and an aggregate function.
 */
public class CrosstabMeasure
{
    public enum AggregateFunction
    {
        COUNT,
        SUM,
        MIN,
        MAX,
        AVG,
        STDDEV;

        public String getCaption()
        {
            return this.name().charAt(0) + this.name().substring(1).toLowerCase();
        }

        public String getSqlFunction(SqlDialect dialect)
        {
            if(STDDEV == this)
                return dialect.getStdDevFunction();
            else
                return this.name();
        }

        public int getAggregateSqlType(ColumnInfo sourceCol)
        {
            switch(this)
            {
                case COUNT:
                    return Types.BIGINT;
                case AVG:
                case STDDEV:
                    return Types.NUMERIC;
                default:
                    return sourceCol.getSqlTypeInt();
            }
        }
    }

    private ColumnInfo _sourceColumn = null;
    private AggregateFunction _aggregateFunction = AggregateFunction.COUNT;
    private String _name;
    private String _caption;
    private String _url;

    public CrosstabMeasure(ColumnInfo sourceColumn, AggregateFunction aggFunction)
    {
        assert null != sourceColumn : "Null source column passed to CrosstabMeasure";

        _sourceColumn = sourceColumn;
        _aggregateFunction = aggFunction;
        _name = _aggregateFunction.name() + "_" + sourceColumn.getName();
        _caption = _aggregateFunction.getCaption() + " of " + getSourceColumn().getCaption();
    }

    public CrosstabMeasure(TableInfo table, FieldKey fieldKey, AggregateFunction aggFunction)
    {
        this(QueryService.get().getColumns(table, Arrays.asList(fieldKey)).get(fieldKey), aggFunction);
    }

    public ColumnInfo getSourceColumn()
    {
        return _sourceColumn;
    }

    public AggregateFunction getAggregateFunction()
    {
        return _aggregateFunction;
    }

    public void setAggregateFunction(AggregateFunction aggregateFunction)
    {
        _aggregateFunction = aggregateFunction;
    }

    public String getName()
    {
        return AggregateColumnInfo.getColumnName(null, this);
    }

    public String getCaption()
    {
        return _caption;
    }

    public void setCaption(String caption)
    {
        _caption = caption;
    }

    public String getUrl()
    {
        return _url;
    }

    /**
     * Returns the Url with the member tokens replaced with values from the specified member
     * @param member The member to use for replacing tokens
     * @return The url with member tokens replaced
     */
    public String getUrl(CrosstabMember member)
    {
        return member.replaceTokens(_url);
    }

    public void setUrl(String url)
    {
        _url = url;
    }

    /**
     * Returns the entire select expression including the aggregate function
     * @param tableAlias table alias to use
     * @return select expression
     */
    public String getSqlExpression(String tableAlias)
    {
        return _aggregateFunction.getSqlFunction(_sourceColumn.getSqlDialect())
                + "(" + tableAlias + "." + _sourceColumn.getAlias() + ")";
    }

    public int getAggregateSqlType()
    {
        return _aggregateFunction.getAggregateSqlType(_sourceColumn);
    }

    public FieldKey getFieldKey()
    {
        return FieldKey.fromParts(AggregateColumnInfo.getColumnName(null, this));
    }

    public FieldKey getFieldKey(CrosstabMember member)
    {
        return FieldKey.fromParts(AggregateColumnInfo.getColumnName(member, this));
    }
} //Measure
