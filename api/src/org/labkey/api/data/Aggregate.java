/*
 * Copyright (c) 2006-2009 LabKey Corporation
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

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * User: brittp
 * Date: Aug 6, 2006
 * Time: 3:36:42 PM
 */
public class Aggregate
{
    public static String STAR = "*";
    public static String QS_PREFIX = ".agg."; //query string param is "<dataregion>.agg.<column>=<type>"

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

        public String getSQLColumnFragment(String columnName, String asName)
        {
            return name() + "(" + CoreSchema.getInstance().getSqlDialect().getColumnSelectName(columnName) + ") AS " + asName;
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
    private String _aggregateColumnName;

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
        _aggregateColumnName = type.name() + _columnName;
    }

    public static Aggregate createCountStar()
    {
        Aggregate agg = new Aggregate();
        agg._columnName = STAR;
        agg._type = Type.COUNT;
        agg._aggregateColumnName = "COUNT_STAR";
        return agg;
    }

    public boolean isCountStar()
    {
        return _columnName.equals(STAR) && _type == Type.COUNT;
    }

    public String getSQL()
    {
        return _type.getSQLColumnFragment(_columnName, _aggregateColumnName);
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
        double resultValue = rs.getDouble(_aggregateColumnName);
        if (resultValue == Math.floor(resultValue))
            return new Result(this, new Long((long) resultValue));
        else
            return new Result(this, new Double(resultValue));
    }
}
