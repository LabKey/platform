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
                    return new TimestampDiffInfo(Types.INTEGER);
                }
            },

    ifnull(Types.OTHER),
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
        public TimestampDiffInfo(int sqlType)
        {
            super(sqlType);
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

//    class ConvertInfo extends MethodInfoImpl
//    {
//        public ConvertInfo(int sqlType)
//        {
//            super(sqlType);
//        }
//
//        @Override
//        public ColumnInfo createColumnInfo(TableInfo parentTable, ColumnInfo[] arguments, String alias)
//        {
//            ColumnInfo col = super.createColumnInfo(parentTable, arguments, alias);
//            return col;
//        }
//
//        public SQLFragment getSQL(DbSchema schema, SQLFragment[] argumentsIN)
//        {
//            SQLFragment[] arguments = argumentsIN.clone();
//            if (arguments.length >= 2)
//            {
//                String type = StringUtils.trimToEmpty(arguments[1].getSQL());
//                if (type.length() >= 2 && type.startsWith("'") && type.endsWith("'"))
//                    type = type.substring(1,type.length()-1);
//                if (!type.startsWith("SQL_"))
//                    type = "SQL_" + type;
//                try
//                {
//                    ConvertType t = ConvertType.valueOf(type);
//                    if (t != null)
//                        arguments[1] = new SQLFragment(t.name());
//                }
//                catch (IllegalArgumentException x)
//                {
//                }
//            }
//            return super.getSQL(schema, arguments);
//        }
//    }
//    
//    enum ConvertType
//    {
//        SQL_BIGINT,
//        SQL_BINARY,
//        SQL_BIT,
//        SQL_CHAR,
//        SQL_DECIMAL,
//        SQL_DOUBLE,
//        SQL_FLOAT,
//        SQL_GUID,
//        SQL_INTEGER,
//        SQL_INTERVAL_MONTH,
//        SQL_INTERVAL_YEAR,
//        SQL_INTERVAL_YEAR_TO_MONTH,
//        SQL_INTERVAL_DAY,
//        SQL_INTERVAL_HOUR,
//        SQL_INTERVAL_MINUTE,
//        SQL_INTERVAL_SECOND,
//        SQL_INTERVAL_DAY_TO_HOUR,
//        SQL_INTERVAL_DAY_TO_MINUTE,
//        SQL_INTERVAL_DAY_TO_SECOND,
//        SQL_INTERVAL_HOUR_TO_MINUTE,
//        SQL_INTERVAL_HOUR_TO_SECOND,
//        SQL_INTERVAL_MINUTE_TO_SECOND,
//        SQL_LONGVARBINARY,
//        SQL_LONGVARCHAR,
//        SQL_NUMERIC,
//        SQL_REAL,
//        SQL_SMALLINT,
//        SQL_DATE,
//        SQL_TIME,
//        SQL_TIMESTAMP,
//        SQL_TINYINT,
//        SQL_VARBINARY,
//        SQL_VARCHAR,
//        SQL_WCHAR,
//        SQL_WLONGVARCHAR,
//        SQL_WVARCHAR
//    }
}
