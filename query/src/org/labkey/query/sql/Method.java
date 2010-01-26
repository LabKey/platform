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

package org.labkey.query.sql;

import org.apache.commons.lang.StringUtils;
import org.labkey.api.data.*;
import org.labkey.api.query.AbstractMethodInfo;
import org.labkey.api.query.ExprColumn;

import java.sql.Types;

public enum Method
{
    abs(Types.DOUBLE, 1, 1),
    acos(Types.DOUBLE, 1, 1),
    atan(Types.DOUBLE, 1, 1),
    atan2(Types.DOUBLE, 2, 2),
    ceiling(Types.DOUBLE, 1, 1),
    cos(Types.DOUBLE, 1, 1),
    cot(Types.DOUBLE, 1, 1),
    degrees(Types.DOUBLE, 1, 1),
    exp(Types.DOUBLE, 1, 1),
    floor(Types.DOUBLE, 1, 1),
    log(Types.DOUBLE, 1, 1),
    log10(Types.DOUBLE, 1, 1),
    mod(Types.DOUBLE, 2, 2),
    pi(Types.DOUBLE, 0, 0),
    power(Types.DOUBLE, 2, 2),
    radians(Types.DOUBLE, 1, 1),
    rand(Types.DOUBLE, 0, 1),
    round(Types.DOUBLE, 1, 2)
			{
				@Override
				public MethodInfo getMethodInfo()
				{
					return new RoundInfo();
				}
			},
    sign(Types.DOUBLE, 1, 1),
    sin(Types.DOUBLE, 1, 1),
    sqrt(Types.DOUBLE, 1, 1),
    tan(Types.DOUBLE, 1, 1),
    truncate(Types.DOUBLE, 2, 2),


    lcase(Types.VARCHAR, 1, 1),
    // left(Types.VARCHAR), // disabled for now since "left" is a reserved word, so identifier must be quoted.
    locate(Types.INTEGER, 2, 3)
            {
                public MethodInfo getMethodInfo()
                {
                    return new MethodInfoImpl(_sqlType)
                    {
                        public SQLFragment getSQL(DbSchema schema, SQLFragment[] arguments)
                        {
                            assert arguments.length == 2 || arguments.length == 3;
                            if (arguments.length == 2)
                                return schema.getSqlDialect().sqlLocate(arguments[0], arguments[1]);
                            else
                                return schema.getSqlDialect().sqlLocate(arguments[0], arguments[1], arguments[2]);
                        }
                    };
                }
            },
    ltrim(Types.VARCHAR, 1, 1),
    repeat(Types.VARCHAR, 2, 2),
    rtrim(Types.VARCHAR, 1, 1),
    substring(Types.VARCHAR, 2, 3),
    ucase(Types.VARCHAR, 1, 1),
    length(Types.INTEGER, 1, 1),


    curdate(Types.DATE, 0, 0),
    curtime(Types.DATE, 0, 0),
    dayofmonth(Types.INTEGER, 1, 1),
    dayofweek(Types.INTEGER, 1, 1),
    dayofyear(Types.INTEGER, 1, 1),
    hour(Types.INTEGER, 1, 1),
    minute(Types.INTEGER, 1, 1),
    month(Types.INTEGER, 1, 1),
    monthname(Types.VARCHAR, 1, 1),
    now(Types.TIMESTAMP, 0, 0),
    quarter(Types.INTEGER, 1, 1),
    second(Types.INTEGER, 1, 1),
    week(Types.INTEGER, 1, 1),
    year(Types.INTEGER, 1, 1),
	timestampadd(Types.TIMESTAMP, 3, 3)
			{
				@Override
				public MethodInfo getMethodInfo()
				{
					return new TimestampInfo(this);
				}
			},
    timestampdiff(Types.INTEGER, 3, 3)
            {
                @Override
                public MethodInfo getMethodInfo()
                {
                    return new TimestampInfo(this);
                }
            },
    age_in_months(Types.INTEGER, 2, 2)
            {
                @Override
                public MethodInfo getMethodInfo()
                {
                    return new AgeInMonthsMethodInfo();
                }
            },
    age(Types.INTEGER, 3, 3)
            {
                @Override
                public MethodInfo getMethodInfo()
                {
                    return new AgeMethodInfo();
                }
            },
    age_in_years(Types.INTEGER, 2, 2)
            {
                @Override
                public MethodInfo getMethodInfo()
                {
                    return new AgeInYearsMethodInfo();
                }
            },


    ifnull(Types.OTHER, 2, 2),
    convert(Types.OTHER, 2, 2)
            {
                @Override
                public MethodInfo getMethodInfo()
                {
                    return new ConvertInfo();
                }
            },
    coalesce(Types.OTHER, 0, Integer.MAX_VALUE)
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
    int _minArgs = 0;
    int _maxArgs = Integer.MAX_VALUE;
    
    Method(int sqlType, String fnEscapeName, int min, int max)
    {
        _sqlType = sqlType;
        _fnEscapeName = fnEscapeName;
        _minArgs = min;
        _maxArgs = max;
    }

    Method(int sqlType, int min, int max)
    {
        this(sqlType, null, min , max);
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


    class TimestampInfo extends MethodInfoImpl
    {
        public TimestampInfo(Method method)
        {
            super(method._sqlType);
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
                try
                {
                    String sqlEscapeTypeName = getTypeArgument(fragments);
                    _sqlType = ConvertType.valueOf(sqlEscapeTypeName).type;
                }
                catch (IllegalArgumentException x)
                {
                    /* */
                }
            }
            return new ExprColumn(parentTable, alias, getSQL(parentTable.getSchema(), fragments), _sqlType);
        }

        public SQLFragment getSQL(DbSchema schema, SQLFragment[] fragments)
        {
            if (fragments.length >= 2)
            {
                String sqlEscapeTypeName = getTypeArgument(fragments);
                String typeName = sqlEscapeTypeName;
                try
                {
                    _sqlType = ConvertType.valueOf(sqlEscapeTypeName).type;
                    typeName = schema.getSqlDialect().sqlTypeNameFromSqlType(_sqlType);
                    fragments = new SQLFragment[] {fragments[0], new SQLFragment(typeName)};
                }
                catch (IllegalArgumentException x)
                {
                    /* */
                }
            }

            SQLFragment ret = new SQLFragment();
            ret.append("CAST(");
            if (fragments.length > 0)
                ret.append(fragments[0]);
            if (fragments.length > 1)
            {
                ret.append(" AS ");
                ret.append(fragments[1]);
            }
            ret.append(")");
            return ret;
        }

        String getTypeArgument(SQLFragment[] argumentsIN) throws IllegalArgumentException
        {
            if (argumentsIN.length < 2)
                return "VARCHAR";
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

	class RoundInfo extends MethodInfoImpl
	{
		RoundInfo()
		{
			super(Types.DOUBLE);
		}

		// https://www.labkey.org/issues/home/Developer/issues/details.view?issueId=7078
        // Even though we are generationg {fn ROUND()}, SQL Server requires 2 arguments
        // while Postgres requires 1 argument (for doubles)
		public SQLFragment getSQL(DbSchema schema, SQLFragment[] arguments)
		{
            boolean supportsRoundDouble = schema.getSqlDialect().supportsRoundDouble();
            boolean unitRound = arguments.length == 1 || (arguments.length==2 && arguments[1].getSQL().equals("0"));
            if (unitRound)
            {
                if (supportsRoundDouble)
                    return super.getSQL(schema, new SQLFragment[] {arguments[0], new SQLFragment("0")});
                else
                    return super.getSQL(schema, new SQLFragment[] {arguments[0]});
            }

            int i = Integer.MIN_VALUE;
            try
            {
                i = Integer.parseInt(arguments[1].getSQL());
            }
            catch (NumberFormatException x)
            {
            }

            if (supportsRoundDouble || i == Integer.MIN_VALUE)
                return super.getSQL(schema, arguments);

            // fall back, only supports simple integer
            SQLFragment scaled = new SQLFragment();
            scaled.append("(");
            scaled.append(arguments[0]);
            scaled.append(")*").append(Math.pow(10,i));
            SQLFragment ret = super.getSQL(schema, new SQLFragment[] {scaled});
            ret.append("/");
            ret.append(Math.pow(10,i));
            return ret;
		}
	}


    class AgeMethodInfo extends MethodInfoImpl
    {
        AgeMethodInfo()
        {
            super(Types.INTEGER);
        }

        public SQLFragment getSQL(DbSchema schema, SQLFragment[] arguments)
        {
            String unit = arguments[2].getSQL().toUpperCase();
            if (unit.equals("YEAR") || unit.equals("SQL_TSI_YEAR"))
                return new AgeInYearsMethodInfo().getSQL(schema, arguments);
            if (unit.equals("MONTH") || unit.equals("SQL_TSI_MONTH"))
                return new AgeInYearsMethodInfo().getSQL(schema, arguments);
            throw new IllegalArgumentException("AGE(" + unit + ")");
        }
    }


    class AgeInYearsMethodInfo extends MethodInfoImpl
    {
        AgeInYearsMethodInfo()
        {
            super(Types.INTEGER);
        }

        public SQLFragment getSQL(DbSchema schema, SQLFragment[] arguments)
        {
            MethodInfo year = Method.year.getMethodInfo();
            MethodInfo month = Method.month.getMethodInfo();
            MethodInfo dayofmonth = Method.dayofmonth.getMethodInfo();

            SQLFragment ret = new SQLFragment();
            SQLFragment yearA = year.getSQL(schema, new SQLFragment[] {arguments[0]});
            SQLFragment monthA = month.getSQL(schema, new SQLFragment[] {arguments[0]});
            SQLFragment dayA = dayofmonth.getSQL(schema, new SQLFragment[] {arguments[0]});
            SQLFragment yearB = year.getSQL(schema, new SQLFragment[] {arguments[1]});
            SQLFragment monthB = month.getSQL(schema, new SQLFragment[] {arguments[1]});
            SQLFragment dayB = dayofmonth.getSQL(schema, new SQLFragment[] {arguments[1]});

            ret.append("CASE WHEN (")
                    .append(monthA).append(">").append(monthB).append(" OR ")
                    .append(monthA).append("=").append(monthB).append(" AND ")
                    .append(dayA).append(">").append(dayB)
                    .append(") THEN (")
                    .append(yearB).append("-").append(yearA).append("-1")
                    .append(") ELSE (")
                    .append(yearB).append("-").append(yearA)
                    .append(") END");
            return ret;
        }
    }


    class AgeInMonthsMethodInfo extends MethodInfoImpl
    {
        AgeInMonthsMethodInfo()
        {
            super(Types.INTEGER);
        }

        public SQLFragment getSQL(DbSchema schema, SQLFragment[] arguments)
        {
            MethodInfo year = Method.year.getMethodInfo();
            MethodInfo month = Method.month.getMethodInfo();
            MethodInfo dayofmonth = Method.dayofmonth.getMethodInfo();

            SQLFragment ret = new SQLFragment();
            SQLFragment yearA = year.getSQL(schema, new SQLFragment[] {arguments[0]});
            SQLFragment monthA = month.getSQL(schema, new SQLFragment[] {arguments[0]});
            SQLFragment dayA = dayofmonth.getSQL(schema, new SQLFragment[] {arguments[0]});
            SQLFragment yearB = year.getSQL(schema, new SQLFragment[] {arguments[1]});
            SQLFragment monthB = month.getSQL(schema, new SQLFragment[] {arguments[1]});
            SQLFragment dayB = dayofmonth.getSQL(schema, new SQLFragment[] {arguments[1]});

            ret.append("CASE WHEN (")
                    .append(dayA).append(">").append(dayB)
                    .append(") THEN (")
                    .append("12*(").append(yearB).append("-").append(yearA).append(")")
                    .append("+")
                    .append(monthB).append("-").append(monthA).append("-1")
                    .append(") ELSE (")
                    .append("12*(").append(yearB).append("-").append(yearA).append(")")
                    .append("+")
                    .append(monthB).append("-").append(monthA)
                    .append(") END");
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
