/*
 * Copyright (c) 2006-2008 LabKey Corporation
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

import org.apache.commons.lang.StringUtils;
import org.labkey.api.data.*;
import org.labkey.api.query.AbstractMethodInfo;
import org.labkey.api.query.ExprColumn;

import java.sql.Types;

public enum Method
{
    abs(Types.DOUBLE),
    acos(Types.DOUBLE),
    atan(Types.DOUBLE),
    atan2(Types.DOUBLE),
    ceiling(Types.DOUBLE),
    cos(Types.DOUBLE),
    cot(Types.DOUBLE),
    degrees(Types.DOUBLE),
    exp(Types.DOUBLE),
    floor(Types.DOUBLE),
    log(Types.DOUBLE),
    log10(Types.DOUBLE),
    mod(Types.DOUBLE),
    pi(Types.DOUBLE),
    power(Types.DOUBLE),
    radians(Types.DOUBLE),
    rand(Types.DOUBLE),
    round(Types.DOUBLE),
    sign(Types.DOUBLE),
    sin(Types.DOUBLE),
    sqrt(Types.DOUBLE),
    tan(Types.DOUBLE),

    truncate(Types.DOUBLE),
    lcase(Types.VARCHAR),
    // left(Types.VARCHAR), // disabled for now since "left" is a reserved word, so identifier must be quoted.
    locate(Types.INTEGER)
            {
                public MethodInfo getMethodInfo()
                {
                    return new MethodInfoImpl(_sqlType)
                    {
                        public SQLFragment getSQL(DbSchema schema, SQLFragment[] arguments)
                        {
                            if (arguments.length == 2)
                            {
                                return schema.getSqlDialect().sqlLocate(arguments[0], arguments[1]);
                            }
                            else if (arguments.length == 3)
                            {
                                return schema.getSqlDialect().sqlLocate(arguments[0], arguments[1], arguments[2]);
                            }
                            return new SQLFragment("'Method \"locate\" requires 2 or 3 arguments, found " + arguments.length + "'");
                        }
                    };
                }
            },
    ltrim(Types.VARCHAR),
    repeat(Types.VARCHAR),
    rtrim(Types.VARCHAR),
    substring(Types.VARCHAR),
    ucase(Types.VARCHAR),
    length(Types.INTEGER),

    curdate(Types.DATE),
    curtime(Types.DATE),
    dayofmonth(Types.INTEGER),
    dayofweek(Types.INTEGER),
    dayofyear(Types.INTEGER),
    hour(Types.INTEGER),
    minute(Types.INTEGER),
    month(Types.INTEGER),
    monthname(Types.VARCHAR),
    now(Types.TIMESTAMP),
    quarter(Types.INTEGER),
    second(Types.INTEGER),
    week(Types.INTEGER),
    year(Types.INTEGER),
    timestampdiff(Types.INTEGER)
            {
                @Override
                public MethodInfo getMethodInfo()
                {
                    return new TimestampDiffInfo();
                }
            },

    ifnull(Types.OTHER),
    convert(Types.OTHER)
            {
                @Override
                public MethodInfo getMethodInfo()
                {
                    return new ConvertInfo();
                }
            },
    coalesce(Types.OTHER)
            {
                @Override
                public MethodInfo getMethodInfo()
                {
                    return new PassthroughInfo(this);
                }
            }

    ;

    int _sqlType;
    String _fnEscapeName;
    
    Method(int sqlType, String fnEscapeName)
    {
        _sqlType = sqlType;
        _fnEscapeName = fnEscapeName;
    }

    Method(int sqlType)
    {
        this(sqlType, null);
        _fnEscapeName = this.name();
    }

    public MethodInfo getMethodInfo()
    {
        return new MethodInfoImpl(_sqlType);
    }

    class MethodInfoImpl extends AbstractMethodInfo
    {
        public MethodInfoImpl(int sqlType)
        {
            super(sqlType);
        }

        public SQLFragment getSQL(DbSchema schema, SQLFragment[] arguments)
        {
            SQLFragment ret = new SQLFragment();
            ret.append("{fn ");
            ret.append(_fnEscapeName);
            ret.append("(");
            String comma = "";
            for (SQLFragment argument : arguments)
            {
                ret.append(comma);
                comma = ",";
                ret.append(argument);
            }
            ret.append(")}");
            return ret;
        }
    }


    class TimestampDiffInfo extends MethodInfoImpl
    {
        public TimestampDiffInfo()
        {
            super(Types.INTEGER);
        }

        public SQLFragment getSQL(DbSchema schema, SQLFragment[] argumentsIN)
        {
            SQLFragment[] arguments = argumentsIN.clone();
            if (arguments.length >= 1)
            {
                String interval = StringUtils.trimToEmpty(arguments[0].getSQL());
                if (interval.length() >= 2 && interval.startsWith("'") && interval.endsWith("'"))
                    interval = interval.substring(1,interval.length()-1);
                if (!interval.startsWith("SQL_TSI"))
                    interval = "SQL_TSI_" + interval;
                try
                {
                    TimestampDiffInterval i = TimestampDiffInterval.valueOf(interval);
                    if (i != null)
                        arguments[0] = new SQLFragment(i.name());
                }
                catch (IllegalArgumentException x)
                {
                }
            }
            return super.getSQL(schema, arguments);
        }
    }

    enum TimestampDiffInterval
    {
        SQL_TSI_FRAC_SECOND,
        SQL_TSI_SECOND,
        SQL_TSI_MINUTE,
        SQL_TSI_HOUR,
        SQL_TSI_DAY,
        SQL_TSI_WEEK,
        SQL_TSI_MONTH,
        SQL_TSI_QUARTER,
        SQL_TSI_YEAR
    }


    class ConvertInfo extends MethodInfoImpl
    {
        public ConvertInfo()
        {
            super(Types.OTHER);
        }

        @Override
        public ColumnInfo createColumnInfo(TableInfo parentTable, ColumnInfo[] arguments, String alias)
        {
            SQLFragment[] fragments = getSQLFragments(arguments);
            if (fragments.length >= 2)
            {
                String sqlEscapeTypeName = getTypeArgument(fragments);
                String typeName = sqlEscapeTypeName;
                try
                {
                    _sqlType = ConvertType.valueOf(sqlEscapeTypeName).type;
                    typeName = ColumnInfo.sqlTypeNameFromSqlType(_sqlType,parentTable.getSchema().getSqlDialect());
                }
                catch (IllegalArgumentException x)
                {
                    /* */
                }
                fragments[1] = new SQLFragment(typeName);
            }
            return new ExprColumn(parentTable, alias, getSQL(parentTable.getSchema(), fragments), _sqlType);
        }

        public SQLFragment getSQL(DbSchema schema, SQLFragment[] arguments)
        {
            SQLFragment ret = new SQLFragment();
            ret.append("CAST(");
            if (arguments.length > 0)
                ret.append(arguments[0]);
            if (arguments.length > 1)
            {
                ret.append(" AS ");
                ret.append(arguments[1]);
            }
            ret.append(")");
            return ret;
        }

        String getTypeArgument(SQLFragment[] argumentsIN) throws IllegalArgumentException
        {
            if (argumentsIN.length < 2)
                return "SQL_VARCHAR";
            String typeName = StringUtils.trimToEmpty(argumentsIN[1].getSQL());
            if (typeName.length() >= 2 && typeName.startsWith("'") && typeName.endsWith("'"))
                typeName = typeName.substring(1,typeName.length()-1);
            if (typeName.startsWith("SQL_"))
                typeName = typeName.substring(4);
            return typeName;
        }
    }

    class PassthroughInfo extends MethodInfoImpl
    {
        Method _method;

        public PassthroughInfo(Method method)
        {
            super(Types.OTHER);
            _method = method;
        }

        @Override
        public ColumnInfo createColumnInfo(TableInfo parentTable, ColumnInfo[] arguments, String alias)
        {
            _sqlType = arguments.length > 0 ? arguments[0].getSqlTypeInt() : Types.VARCHAR;
            return super.createColumnInfo(parentTable, arguments, alias);
        }

        public SQLFragment getSQL(DbSchema schema, SQLFragment[] arguments)
        {
            SQLFragment ret = new SQLFragment();
            ret.append(_method.name()).append("(");
            String comma = "";
            for (SQLFragment arg : arguments)
            {
                ret.append(comma);
                ret.append(arg);
                comma = ",";
            }
            ret.append(")");
            return ret;
        }
    }


    enum ConvertType
    {
        BIGINT(Types.BIGINT),
        BINARY(Types.BINARY),
        BIT(Types.BIT),
        CHAR(Types.CHAR),
        DECIMAL(Types.DECIMAL),
        DOUBLE(Types.DOUBLE),
        FLOAT(Types.FLOAT),
        GUID(Types.VARCHAR),
        INTEGER(Types.INTEGER),
        INTERVAL_MONTH(Types.INTEGER),
        INTERVAL_YEAR(Types.INTEGER),
        INTERVAL_YEAR_TO_MONTH(Types.INTEGER),
        INTERVAL_DAY(Types.INTEGER),
        INTERVAL_HOUR(Types.INTEGER),
        INTERVAL_MINUTE(Types.INTEGER),
        INTERVAL_SECOND(Types.INTEGER),
//        INTERVAL_DAY_TO_HOUR,
//        INTERVAL_DAY_TO_MINUTE,
//        INTERVAL_DAY_TO_SECOND,
//        INTERVAL_HOUR_TO_MINUTE,
//        INTERVAL_HOUR_TO_SECOND,
//        INTERVAL_MINUTE_TO_SECOND,
        LONGVARBINARY(Types.LONGVARBINARY),
        LONGVARCHAR(Types.LONGVARCHAR),
        NUMERIC(Types.NUMERIC),
        REAL(Types.REAL),
        SMALLINT(Types.SMALLINT),
        DATE(Types.DATE),
        TIME(Types.TIME),
        TIMESTAMP(Types.TIMESTAMP),
        TINYINT(Types.TINYINT),
        VARBINARY(Types.VARBINARY),
        VARCHAR(Types.VARCHAR)
//        WCHAR(Types.CHAR),
//        WLONGVARCHAR(Types.LONGVARCHAR),
//        WVARCHAR(Types.LONGVARCHAR)
         ;

        int type;
        ConvertType(int type)
        {
            this.type = type;
        }
    }
}
