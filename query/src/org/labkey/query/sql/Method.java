/*
 * Copyright (c) 2006-2010 LabKey Corporation
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
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveTreeSet;
import org.labkey.api.data.*;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.query.AbstractMethodInfo;
import org.labkey.api.query.ExprColumn;

import java.sql.Types;
import java.util.HashMap;

public abstract class Method
{
    static HashMap<String,Method> labkeyMethod = new HashMap<String,Method>();

    static
    {
        labkeyMethod.put("abs", new JdbcMethod("abs", Types.DOUBLE, 1, 1));
        labkeyMethod.put("acos", new JdbcMethod("acos", Types.DOUBLE, 1, 1));
        labkeyMethod.put("atan", new JdbcMethod("atan", Types.DOUBLE, 1, 1));
        labkeyMethod.put("atan2", new JdbcMethod("atan2", Types.DOUBLE, 2, 2));
        labkeyMethod.put("ceiling", new JdbcMethod("ceiling", Types.DOUBLE, 1, 1));
        labkeyMethod.put("cos", new JdbcMethod("cos", Types.DOUBLE, 1, 1));
        labkeyMethod.put("cot", new JdbcMethod("cot", Types.DOUBLE, 1, 1));
        labkeyMethod.put("degrees", new JdbcMethod("degrees", Types.DOUBLE, 1, 1));
        labkeyMethod.put("exp", new JdbcMethod("exp", Types.DOUBLE, 1, 1));
        labkeyMethod.put("floor", new JdbcMethod("floor", Types.DOUBLE, 1, 1));
        labkeyMethod.put("log", new JdbcMethod("log", Types.DOUBLE, 1, 1));
        labkeyMethod.put("log10", new JdbcMethod("log10", Types.DOUBLE, 1, 1));
        labkeyMethod.put("mod", new JdbcMethod("mod", Types.DOUBLE, 2, 2));
        labkeyMethod.put("pi", new JdbcMethod("pi", Types.DOUBLE, 0, 0));
        labkeyMethod.put("power", new JdbcMethod("power", Types.DOUBLE, 2, 2));
        labkeyMethod.put("radians", new JdbcMethod("radians", Types.DOUBLE, 1, 1));
        labkeyMethod.put("rand", new JdbcMethod("rand", Types.DOUBLE, 0, 1));
        labkeyMethod.put("round", new Method("round", Types.DOUBLE, 1, 2)
			{
				@Override
				public MethodInfo getMethodInfo()
				{
					return new RoundInfo();
				}
			});
        labkeyMethod.put("sign", new JdbcMethod("sign", Types.DOUBLE, 1, 1));
        labkeyMethod.put("sin", new JdbcMethod("sin", Types.DOUBLE, 1, 1));
        labkeyMethod.put("sqrt", new JdbcMethod("sqrt", Types.DOUBLE, 1, 1));
        labkeyMethod.put("tan", new JdbcMethod("tan", Types.DOUBLE, 1, 1));
        labkeyMethod.put("truncate", new JdbcMethod("truncate", Types.DOUBLE, 2, 2));


        labkeyMethod.put("lcase", new JdbcMethod("lcase", Types.VARCHAR, 1, 1));
        labkeyMethod.put("lower", new JdbcMethod("lcase", Types.VARCHAR, 1, 1));
        labkeyMethod.put("left", new JdbcMethod("left", Types.VARCHAR, 2, 2));
        labkeyMethod.put("locate", new Method("locate", Types.INTEGER, 2, 3)
            {
                public MethodInfo getMethodInfo()
                {
                    return new AbstractMethodInfo(_sqlType)
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
            });
        labkeyMethod.put("ltrim", new JdbcMethod("ltrim", Types.VARCHAR, 1, 1));
        labkeyMethod.put("repeat", new JdbcMethod("repeat", Types.VARCHAR, 2, 2));
        labkeyMethod.put("rtrim", new JdbcMethod("rtrim", Types.VARCHAR, 1, 1));
        labkeyMethod.put("substring", new JdbcMethod("substring", Types.VARCHAR, 2, 3));
        labkeyMethod.put("ucase", new JdbcMethod("ucase", Types.VARCHAR, 1, 1));
        labkeyMethod.put("upper", new JdbcMethod("ucase", Types.VARCHAR, 1, 1));
        labkeyMethod.put("length", new JdbcMethod("length", Types.INTEGER, 1, 1));


        labkeyMethod.put("curdate", new JdbcMethod("curdate", Types.DATE, 0, 0));
        labkeyMethod.put("curtime", new JdbcMethod("curtime", Types.DATE, 0, 0));
        labkeyMethod.put("dayofmonth", new JdbcMethod("dayofmonth", Types.INTEGER, 1, 1));
        labkeyMethod.put("dayofweek", new JdbcMethod("dayofweek", Types.INTEGER, 1, 1));
        labkeyMethod.put("dayofyear", new JdbcMethod("dayofyear", Types.INTEGER, 1, 1));
        labkeyMethod.put("hour", new JdbcMethod("hour", Types.INTEGER, 1, 1));
        labkeyMethod.put("minute", new JdbcMethod("minute", Types.INTEGER, 1, 1));
        labkeyMethod.put("month", new JdbcMethod("month", Types.INTEGER, 1, 1));
        labkeyMethod.put("monthname", new JdbcMethod("monthname", Types.VARCHAR, 1, 1));
        labkeyMethod.put("now", new JdbcMethod("now", Types.TIMESTAMP, 0, 0));
        labkeyMethod.put("quarter", new JdbcMethod("quarter", Types.INTEGER, 1, 1));
        labkeyMethod.put("second", new JdbcMethod("second", Types.INTEGER, 1, 1));
        labkeyMethod.put("week", new JdbcMethod("week", Types.INTEGER, 1, 1));
        labkeyMethod.put("year", new JdbcMethod("year", Types.INTEGER, 1, 1));
	    labkeyMethod.put("timestampadd", new Method("timestampadd", Types.TIMESTAMP, 3, 3)
			{
				@Override
				public MethodInfo getMethodInfo()
				{
					return new TimestampInfo(this);
				}
			});
        labkeyMethod.put("timestampdiff", new Method("timestampdiff", Types.INTEGER, 3, 3)
            {
                @Override
                public MethodInfo getMethodInfo()
                {
                    return new TimestampInfo(this);
                }
            });
        labkeyMethod.put("age_in_months", new Method(Types.INTEGER, 2, 2)
            {
                @Override
                public MethodInfo getMethodInfo()
                {
                    return new AgeInMonthsMethodInfo();
                }
            });
        labkeyMethod.put("age", new Method(Types.INTEGER, 2, 3)
            {
                @Override
                public MethodInfo getMethodInfo()
                {
                    return new AgeMethodInfo();
                }
            });
        labkeyMethod.put("age_in_years", new Method(Types.INTEGER, 2, 2)
            {
                @Override
                public MethodInfo getMethodInfo()
                {
                    return new AgeInYearsMethodInfo();
                }
            });


        labkeyMethod.put("ifnull", new JdbcMethod("ifnull", Types.OTHER, 2, 2));
        labkeyMethod.put("cast", new Method("convert", Types.OTHER, 2, 2)
            {
                @Override
                public MethodInfo getMethodInfo()
                {
                    return new ConvertInfo();
                }
            });
        labkeyMethod.put("convert", new Method("convert", Types.OTHER, 2, 2)
            {
                @Override
                public MethodInfo getMethodInfo()
                {
                    return new ConvertInfo();
                }
            });
        labkeyMethod.put("coalesce", new Method("coalesce", Types.OTHER, 0, Integer.MAX_VALUE)
            {
                @Override
                public MethodInfo getMethodInfo()
                {
                    return new PassthroughInfo("coalesce", Types.OTHER);
                }
            });
    }


    final int _sqlType;
    final String _name;
    final int _minArgs;
    final int _maxArgs;
    
    Method(int sqlType, int min, int max)
    {
        this("#UNDEF#", sqlType, min, max);
    }

    Method(String name, int sqlType, int min, int max)
    {
        _name = name;
        _sqlType = sqlType;
        _minArgs = min;
        _maxArgs = max;
    }

    abstract public MethodInfo getMethodInfo();

    
    class JdbcMethodInfoImpl extends AbstractMethodInfo
    {
        String _name;

        public JdbcMethodInfoImpl(String name, int sqlType)
        {
            super(sqlType);
            _name = name;
        }

        public SQLFragment getSQL(DbSchema schema, SQLFragment[] arguments)
        {
            SQLFragment ret = new SQLFragment();
            ret.append("{fn ");
            ret.append(_name);
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


    class TimestampInfo extends JdbcMethodInfoImpl
    {
        public TimestampInfo(Method method)
        {
            super(method._name, method._sqlType);
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


    class ConvertInfo extends AbstractMethodInfo
    {
        public ConvertInfo()
        {
            super(Types.OTHER);
        }

        @Override
        public ColumnInfo createColumnInfo(TableInfo parentTable, ColumnInfo[] arguments, String alias)
        {
            SQLFragment[] fragments = getSQLFragments(arguments);
            int sqlType = _sqlType;
            if (fragments.length >= 2)
            {
                try
                {
                    String sqlEscapeTypeName = getTypeArgument(fragments);
                    sqlType = ConvertType.valueOf(sqlEscapeTypeName).type;
                }
                catch (IllegalArgumentException x)
                {
                    /* */
                }
            }
            return new ExprColumn(parentTable, alias, getSQL(parentTable.getSchema(), fragments), sqlType);
        }

        public SQLFragment getSQL(DbSchema schema, SQLFragment[] fragments)
        {
            int sqlType = _sqlType;
            if (fragments.length >= 2)
            {
                String sqlEscapeTypeName = getTypeArgument(fragments);
                String typeName = sqlEscapeTypeName;
                try
                {
                    sqlType = ConvertType.valueOf(sqlEscapeTypeName).type;
                    typeName = schema.getSqlDialect().sqlTypeNameFromSqlType(sqlType);
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

    static class PassthroughInfo extends AbstractMethodInfo
    {
        String _name;

        public PassthroughInfo(String method, int sqlType)
        {
            super(sqlType);
            _name = method;
        }

        @Override
        protected int getSqlType(ColumnInfo[] arguments)
        {
            int sqlType = _sqlType;
            if (sqlType == Types.OTHER)
                sqlType = arguments.length > 0 ? arguments[0].getSqlTypeInt() : Types.VARCHAR;
            return sqlType;
        }

        public SQLFragment getSQL(DbSchema schema, SQLFragment[] arguments)
        {
            SQLFragment ret = new SQLFragment();
            ret.append(_name).append("(");
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

	class RoundInfo extends JdbcMethodInfoImpl
	{
		RoundInfo()
		{
			super("round", Types.DOUBLE);
		}

		// https://www.labkey.org/issues/home/Developer/issues/details.view?issueId=7078
        // Even though we are generating {fn ROUND()}, SQL Server requires 2 arguments
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


    class AgeMethodInfo extends AbstractMethodInfo
    {
        AgeMethodInfo()
        {
            super(Types.INTEGER);
        }

        public SQLFragment getSQL(DbSchema schema, SQLFragment[] arguments)
        {
            if (arguments.length == 2)
                return new AgeInYearsMethodInfo().getSQL(schema, arguments);
            String unit = StringUtils.strip(arguments[2].getSQL().toUpperCase(),"'");
            if (unit.equals("YEAR") || unit.equals("SQL_TSI_YEAR"))
                return new AgeInYearsMethodInfo().getSQL(schema, arguments);
            if (unit.equals("MONTH") || unit.equals("SQL_TSI_MONTH"))
                return new AgeInMonthsMethodInfo().getSQL(schema, arguments);
            throw new IllegalArgumentException("AGE(" + unit + ")");
        }
    }


    class AgeInYearsMethodInfo extends AbstractMethodInfo
    {
        AgeInYearsMethodInfo()
        {
            super(Types.INTEGER);
        }

        public SQLFragment getSQL(DbSchema schema, SQLFragment[] arguments)
        {
            MethodInfo year = labkeyMethod.get("year").getMethodInfo();
            MethodInfo month = labkeyMethod.get("month").getMethodInfo();
            MethodInfo dayofmonth = labkeyMethod.get("dayofmonth").getMethodInfo();

            SQLFragment ret = new SQLFragment();
            SQLFragment yearA = year.getSQL(schema, new SQLFragment[] {arguments[0]});
            SQLFragment monthA = month.getSQL(schema, new SQLFragment[] {arguments[0]});
            SQLFragment dayA = dayofmonth.getSQL(schema, new SQLFragment[] {arguments[0]});
            SQLFragment yearB = year.getSQL(schema, new SQLFragment[] {arguments[1]});
            SQLFragment monthB = month.getSQL(schema, new SQLFragment[] {arguments[1]});
            SQLFragment dayB = dayofmonth.getSQL(schema, new SQLFragment[] {arguments[1]});

            ret.append("(CASE WHEN (")
                    .append(monthA).append(">").append(monthB).append(" OR ")
                    .append(monthA).append("=").append(monthB).append(" AND ")
                    .append(dayA).append(">").append(dayB)
                    .append(") THEN (")
                    .append(yearB).append("-").append(yearA).append("-1")
                    .append(") ELSE (")
                    .append(yearB).append("-").append(yearA)
                    .append(") END)");
            return ret;
        }
    }


    class AgeInMonthsMethodInfo extends AbstractMethodInfo
    {
        AgeInMonthsMethodInfo()
        {
            super(Types.INTEGER);
        }

        public SQLFragment getSQL(DbSchema schema, SQLFragment[] arguments)
        {
            MethodInfo year = labkeyMethod.get("year").getMethodInfo();
            MethodInfo month = labkeyMethod.get("month").getMethodInfo();
            MethodInfo dayofmonth = labkeyMethod.get("dayofmonth").getMethodInfo();

            SQLFragment ret = new SQLFragment();
            SQLFragment yearA = year.getSQL(schema, new SQLFragment[] {arguments[0]});
            SQLFragment monthA = month.getSQL(schema, new SQLFragment[] {arguments[0]});
            SQLFragment dayA = dayofmonth.getSQL(schema, new SQLFragment[] {arguments[0]});
            SQLFragment yearB = year.getSQL(schema, new SQLFragment[] {arguments[1]});
            SQLFragment monthB = month.getSQL(schema, new SQLFragment[] {arguments[1]});
            SQLFragment dayB = dayofmonth.getSQL(schema, new SQLFragment[] {arguments[1]});

            ret.append("(CASE WHEN (")
                    .append(dayA).append(">").append(dayB)
                    .append(") THEN (")
                    .append("12*(").append(yearB).append("-").append(yearA).append(")")
                    .append("+")
                    .append(monthB).append("-").append(monthA).append("-1")
                    .append(") ELSE (")
                    .append("12*(").append(yearB).append("-").append(yearA).append(")")
                    .append("+")
                    .append(monthB).append("-").append(monthA)
                    .append(") END)");
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

    private static class JdbcMethod extends Method
    {
        JdbcMethod(String name, int sqlType, int min, int max)
        {
            super(name, sqlType, min, max);
        }

        @Override
        public MethodInfo getMethodInfo()
        {
            return new JdbcMethodInfoImpl(_name, _sqlType);
        }
    }

    private static class PassthroughMethod extends Method
    {
        PassthroughMethod(String name, int sqlType, int min, int max)
        {
            super(name, sqlType, min, max);
        }

        @Override
        public MethodInfo getMethodInfo()
        {
            return new PassthroughInfo(_name, _sqlType);
        }
    }


    public static Method valueOf(String name)
    {
        return resolve(null, name);
    }
    
    public static Method resolve(SqlDialect d, String name)
    {
        name = name.toLowerCase();
        // UNDONE
        Method m = labkeyMethod.get(name);
        if (null != m)
            return m;
        if (null != d )
        {
            if (name.startsWith("::"))
                name = name.substring(2);
            if (d.isPostgreSQL())
                m = postgresMethods.get(name);
            else if (d.isSqlServer())
                m = mssqlMethods.get(name);
            if (null != m)
                return m;
        }
        throw new IllegalArgumentException(name);
    }


    static CaseInsensitiveHashMap<Method> postgresMethods = new CaseInsensitiveHashMap<Method>();
    static
    {
        postgresMethods.put("ascii",new PassthroughMethod("ascii",Types.INTEGER,1,1));
        postgresMethods.put("btrim",new PassthroughMethod("btrim",Types.VARCHAR,1,2));
        postgresMethods.put("char_length",new PassthroughMethod("char_length",Types.INTEGER,1,1));
        postgresMethods.put("character_length",new PassthroughMethod("character_length",Types.INTEGER,1,1));
        postgresMethods.put("chr",new PassthroughMethod("chr",Types.VARCHAR,1,1));
        postgresMethods.put("decode",new PassthroughMethod("decode",Types.VARCHAR,2,2));
        postgresMethods.put("encode",new PassthroughMethod("encode",Types.VARCHAR,2,2));
        postgresMethods.put("initcap",new PassthroughMethod("initcap",Types.VARCHAR,1,1));
        postgresMethods.put("lpad",new PassthroughMethod("lpad",Types.VARCHAR,2,3));
        postgresMethods.put("md5",new PassthroughMethod("md5",Types.VARCHAR,1,1));
        postgresMethods.put("octet_length",new PassthroughMethod("octet_length",Types.INTEGER,1,1));
        postgresMethods.put("quote_ident",new PassthroughMethod("quote_ident",Types.VARCHAR,1,1));
        postgresMethods.put("quote_literal",new PassthroughMethod("quote_literal",Types.VARCHAR,1,1));
        postgresMethods.put("regexp_replace",new PassthroughMethod("regexp_replace",Types.VARCHAR,3,4));
        postgresMethods.put("repeat",new PassthroughMethod("repeat",Types.VARCHAR,2,2));
        postgresMethods.put("replace",new PassthroughMethod("replace",Types.VARCHAR,3,3));
        postgresMethods.put("rpad",new PassthroughMethod("rpad",Types.VARCHAR,2,3));
        postgresMethods.put("split_part",new PassthroughMethod("split_part",Types.VARCHAR,3,3));
        postgresMethods.put("strpos",new PassthroughMethod("strpos",Types.VARCHAR,2,2));
        postgresMethods.put("substr",new PassthroughMethod("substr",Types.VARCHAR,2,3));
        postgresMethods.put("to_ascii",new PassthroughMethod("to_ascii",Types.VARCHAR,1,2));
        postgresMethods.put("to_hex",new PassthroughMethod("to_hex",Types.VARCHAR,1,1));
        postgresMethods.put("translate",new PassthroughMethod("translate",Types.VARCHAR,3,3));
        postgresMethods.put("to_char",new PassthroughMethod("to_char",Types.VARCHAR,2,2));
        postgresMethods.put("to_date",new PassthroughMethod("to_date",Types.DATE,2,2));
        postgresMethods.put("to_timestamp",new PassthroughMethod("to_timestamp",Types.TIMESTAMP,2,2));
        postgresMethods.put("to_number",new PassthroughMethod("to_number",Types.NUMERIC,2,2));
    }

    static CaseInsensitiveHashMap<Method> mssqlMethods = new CaseInsensitiveHashMap<Method>();
    static
    {
        mssqlMethods.put("ascii",new PassthroughMethod("ascii",Types.INTEGER,1,1));
        mssqlMethods.put("char",new PassthroughMethod("char",Types.VARCHAR,1,1));
        mssqlMethods.put("charindex",new PassthroughMethod("charindex",Types.INTEGER,2,3));
        mssqlMethods.put("difference",new PassthroughMethod("difference",Types.INTEGER,2,2));
        mssqlMethods.put("len",new PassthroughMethod("len",Types.INTEGER,1,1));
        mssqlMethods.put("patindex",new PassthroughMethod("patindex",Types.INTEGER,2,2));
        mssqlMethods.put("quotename",new PassthroughMethod("quotename",Types.VARCHAR,1,2));
        mssqlMethods.put("replace",new PassthroughMethod("replace",Types.VARCHAR,3,3));
        mssqlMethods.put("replicate",new PassthroughMethod("replicate",Types.VARCHAR,2,2));
        mssqlMethods.put("reverse",new PassthroughMethod("reverse",Types.VARCHAR,1,1));
        mssqlMethods.put("right",new PassthroughMethod("right",Types.VARCHAR,2,2));
        mssqlMethods.put("soundex",new PassthroughMethod("soundex",Types.VARCHAR,1,1));
        mssqlMethods.put("space",new PassthroughMethod("space",Types.VARCHAR,1,1));
        mssqlMethods.put("str",new PassthroughMethod("str",Types.VARCHAR,1,3));
        mssqlMethods.put("stuff",new PassthroughMethod("stuff",Types.VARCHAR,4,4));
    }
}
