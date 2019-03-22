/*
 * Copyright (c) 2008-2015 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;

import java.util.Arrays;

/**
 * Represents a measure for the CrosstabTableInfo.
 * A measure is the pivoted aggregate column and contains a
 * source ColumnInfo and an aggregate function.
 */
public class CrosstabMeasure
{
    public enum AggregateFunction
    {
        COUNT,
        SUM,
        MIN
        {
            @Override
            public boolean retainsForeignKey()
            {
                return true;
            }
        },
        MAX
        {
            @Override
            public boolean retainsForeignKey()
            {
                return true;
            }
        },
        AVG,
        STDDEV
        {
            @Override
            public String getSqlFunction(SqlDialect dialect)
            {
                return dialect.getStdDevFunction();
            }
        },
        STDERR
        {
            @Override
            public String getSqlFunction(SqlDialect dialect)
            {
                return null;
            }
        },
        GROUP_CONCAT
        {
            @Override
            public String getSqlFunction(SqlDialect dialect)
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public SQLFragment getSqlExpression(SqlDialect sqlDialect, SQLFragment sql)
            {
                return sqlDialect.getGroupConcat(sql, false, false);
            }
        };

        public String getCaption()
        {
            return this.name().charAt(0) + this.name().substring(1).toLowerCase();
        }

        public String getSqlFunction(SqlDialect dialect)
        {
            return this.name();
        }

        @NotNull
        public JdbcType getAggregateSqlType(ColumnInfo sourceCol)
        {
            switch(this)
            {
                case COUNT:
                    return JdbcType.BIGINT;
                case AVG:
                case STDDEV:
                    return JdbcType.DECIMAL;
                default:
                    return sourceCol.getJdbcType();
            }
        }

        public SQLFragment getSqlExpression(SqlDialect sqlDialect, SQLFragment columnSQL)
        {
            SQLFragment result = new SQLFragment(getSqlFunction(sqlDialect));
            result.append("(");
            result.append(columnSQL);
            result.append(")");
            return result;
        }

        /** Many transformations don't preserve FK integrity - AVG, for example - while some do - MIN, MAX for example */
        public boolean retainsForeignKey()
        {
            return false;
        }
    }

    private ColumnInfo _sourceColumn = null;
    private AggregateFunction _aggregateFunction = AggregateFunction.COUNT;
    private String _caption;
    private DetailsURL _url;

    public CrosstabMeasure(ColumnInfo sourceColumn, AggregateFunction aggFunction)
    {
        assert null != sourceColumn : "Null source column passed to CrosstabMeasure";

        _sourceColumn = sourceColumn;
        _aggregateFunction = aggFunction;
        if (_aggregateFunction != null)
            _caption = _aggregateFunction.getCaption() + " of " + getSourceColumn().getLabel();
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

    public DetailsURL getUrl()
    {
        return _url;
    }

    /**
     * Returns the Url with the member tokens replaced with values from the specified member
     * @param member The member to use for replacing tokens
     * @return The url with member tokens replaced
     */
    public DetailsURL getUrl(CrosstabMember member)
    {
        return member.replaceTokens(_url);
    }

    public void setUrl(DetailsURL url)
    {
        _url = url;
    }

    /**
     * Returns the entire select expression including the aggregate function
     * @param tableAlias table alias to use
     * @return select expression
     */
    public SQLFragment getSqlExpression(String tableAlias)
    {
        return _aggregateFunction.getSqlExpression(_sourceColumn.getSqlDialect(), new SQLFragment(tableAlias + "." + _sourceColumn.getAlias()));
    }

    @NotNull
    public JdbcType getAggregateSqlType()
    {
        return _aggregateFunction.getAggregateSqlType(_sourceColumn);
    }

    public FieldKey getFieldKey()
    {
        return getFieldKey(null);
    }

    public FieldKey getFieldKey(CrosstabMember member)
    {
        return FieldKey.fromParts(AggregateColumnInfo.getColumnName(member, this));
    }
} //Measure
