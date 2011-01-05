/*
 * Copyright (c) 2006-2011 LabKey Corporation
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

import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.query.CustomViewInfo;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.URLHelper;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.PropertyValues;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * User: brittp
 * Date: Aug 6, 2006
 * Time: 3:36:42 PM
 */
public class Aggregate
{
    public static String STAR = "*";

    public enum Type
    {
        SUM("Total"),
        AVG("Average"),
        COUNT("Count"),
        MIN("Minimum"),
        MAX("Maximum");

        private String _friendlyName;

        private Type(String friendlyName)
        {
            _friendlyName = friendlyName;
        }

        public String getSQLColumnFragment(SqlDialect dialect, String columnName, String asName)
        {
            return name() + "(" + dialect.getColumnSelectName(columnName) + ") AS " + asName;
        }

        public String getFriendlyName()
        {
            return _friendlyName;
        }
    }

    public static class Result
    {
        private Aggregate _aggregate;
        private Object _value;
        public Result(Aggregate aggregate, Object value)
        {
            _aggregate = aggregate;
            _value = value;
        }

        public Aggregate getAggregate()
        {
            return _aggregate;
        }

        public Object getValue()
        {
            return _value;
        }
    }

    private String _columnName;
    private Type _type;
    private String _aggregateColumnName = null;

    private Aggregate()
    {
    }

    public Aggregate(ColumnInfo column, Aggregate.Type type)
    {
        this(column.getAlias(), type);
    }

    public Aggregate(String columnAlias, Aggregate.Type type)
    {
        _columnName = columnAlias;
        _type = type;
    }

    public static Aggregate createCountStar()
    {
        Aggregate agg = new Aggregate();
        agg._columnName = STAR;
        agg._type = Type.COUNT;
        return agg;
    }

    public boolean isCountStar()
    {
        return _columnName.equals(STAR) && _type == Type.COUNT;
    }

    public String getSQL(SqlDialect dialect, Map<String, ? extends ColumnInfo> columns)
    {
        String alias = _columnName;
        if (isCountStar())
        {
            _aggregateColumnName = "COUNT_STAR";
        }
        else
        {
            ColumnInfo col = columns.get(alias);
            if (col != null)
                alias = col.getAlias();

            _aggregateColumnName = _type.name() + alias;
        }

        return _type.getSQLColumnFragment(dialect, alias, _aggregateColumnName);
    }

    public String getColumnName()
    {
        return _columnName;
    }

    public Type getType()
    {
        return _type;
    }

    public Result getResult(ResultSet rs) throws SQLException
    {
        assert _aggregateColumnName != null;
        if (_aggregateColumnName == null)
            return new Result(this, 0L);
        
        double resultValue = rs.getDouble(_aggregateColumnName);
        if (resultValue == Math.floor(resultValue))
            return new Result(this, new Long((long) resultValue));
        else
            return new Result(this, new Double(resultValue));
    }

    /** Extracts aggregate URL parameters from a URL. */
    @NotNull
    public static List<Aggregate> fromURL(URLHelper urlHelper, String regionName)
    {
        return fromURL(urlHelper.getPropertyValues(), regionName);
    }

    /** Extracts aggregate URL parameters from a URL. */
    @NotNull
    public static List<Aggregate> fromURL(PropertyValues pvs, String regionName)
    {
        List<Aggregate> aggregates = new LinkedList<Aggregate>();
        String prefix = regionName + "." + CustomViewInfo.AGGREGATE_PARAM_PREFIX + ".";
        for (PropertyValue val : pvs.getPropertyValues())
        {
            if (val.getName().startsWith(prefix))
            {
                FieldKey fieldKey = FieldKey.fromString(val.getName().substring(prefix.length()));
                String columnName = StringUtils.join(fieldKey.getParts(), "/");

                Aggregate.Type type;
                try
                {
                    type = Aggregate.Type.valueOf(((String)val.getValue()).toUpperCase());
                }
                catch (IllegalArgumentException e)
                {
                    throw new IllegalArgumentException("'" + val.getValue() + "' is not a valid aggregate type.");
                }
                aggregates.add(new Aggregate(columnName, type));
            }
        }
        return aggregates;
    }

}
