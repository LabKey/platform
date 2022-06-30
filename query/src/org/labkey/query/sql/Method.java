/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

import org.antlr.runtime.tree.CommonTree;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.MethodInfo;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.module.ModuleProperty;
import org.labkey.api.query.AbstractMethodInfo;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.QueryParseException;
import org.labkey.api.query.QueryParseWarning;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.Queryable;
import org.labkey.api.query.UserIdQueryForeignKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.GUID;
import org.labkey.api.util.Pair;
import org.labkey.query.QueryServiceImpl;
import org.labkey.query.sql.antlr.SqlBaseLexer;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.text.DecimalFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import static org.labkey.query.sql.antlr.SqlBaseParser.IS;
import static org.labkey.query.sql.antlr.SqlBaseParser.IS_NOT;

public abstract class Method
{
    private final static HashMap<String, Method> labkeyMethod = new HashMap<>();

    static
    {
        labkeyMethod.put("abs", new JdbcMethod("abs", JdbcType.DOUBLE, 1, 1));
        labkeyMethod.put("acos", new JdbcMethod("acos", JdbcType.DOUBLE, 1, 1));
        labkeyMethod.put("age", new Method(JdbcType.INTEGER, 2, 3)
            {
                @Override
                public MethodInfo getMethodInfo()
                {
                    return new AgeMethodInfo();
                }

                @Override
                public void validate(CommonTree fn, List<QNode> args, List<Exception> parseErrors, List<QueryParseException> parseWarnings)
                {
                    super.validate(fn, args, parseErrors, parseWarnings);
                    // only YEAR, MONTH supported
                    if (args.size() == 3)
                    {
                        QNode nodeInterval = args.get(2);
                        TimestampDiffInterval i = TimestampDiffInterval.parse(nodeInterval.getTokenText());
                        if (!(i == TimestampDiffInterval.SQL_TSI_MONTH || i == TimestampDiffInterval.SQL_TSI_YEAR))
                        {
                            parseErrors.add(new QueryParseException("AGE function supports SQL_TSI_YEAR or SQL_TSI_MONTH", null,
                                    nodeInterval.getLine(), nodeInterval.getColumn()));
                        }
                    }
                }
            });
        labkeyMethod.put("age_in_months", new Method(JdbcType.INTEGER, 2, 2)
            {
                @Override
                public MethodInfo getMethodInfo()
                {
                    return new AgeInMonthsMethodInfo();
                }
            });
        labkeyMethod.put("age_in_years", new Method(JdbcType.INTEGER, 2, 2)
            {
                @Override
                public MethodInfo getMethodInfo()
                {
                    return new AgeInYearsMethodInfo();
                }
            });
        labkeyMethod.put("asin", new JdbcMethod("asin", JdbcType.DOUBLE, 1, 1));
        labkeyMethod.put("atan", new JdbcMethod("atan", JdbcType.DOUBLE, 1, 1));
        labkeyMethod.put("atan2", new JdbcMethod("atan2", JdbcType.DOUBLE, 2, 2));
        labkeyMethod.put("cast", new Method("convert", JdbcType.OTHER, 2, 3)
            {
                @Override
                public MethodInfo getMethodInfo()
                {
                    return new ConvertInfo();
                }
            });
        labkeyMethod.put("ceiling", new JdbcMethod("ceiling", JdbcType.DOUBLE, 1, 1));
        labkeyMethod.put("coalesce", new Method("coalesce", JdbcType.OTHER, 0, Integer.MAX_VALUE)
            {
                @Override
                public MethodInfo getMethodInfo()
                {
                    return new PassthroughInfo("coalesce", null, JdbcType.OTHER);
                }
            });
        labkeyMethod.put("concat", new JdbcMethod("concat", JdbcType.VARCHAR, 2, 2));
        labkeyMethod.put("contextpath", new Method("contextPath", JdbcType.VARCHAR, 0, 0) {
            @Override
            public MethodInfo getMethodInfo()
            {
                return new ContextPathInfo();
            }
        });
        labkeyMethod.put("convert", new Method("convert", JdbcType.OTHER, 2, 2)
            {
                @Override
                public MethodInfo getMethodInfo()
                {
                    return new ConvertInfo();
                }
            });
        labkeyMethod.put("cos", new JdbcMethod("cos", JdbcType.DOUBLE, 1, 1));
        labkeyMethod.put("cot", new JdbcMethod("cot", JdbcType.DOUBLE, 1, 1));
        labkeyMethod.put("curdate", new JdbcMethod("curdate", JdbcType.DATE, 0, 0));
        labkeyMethod.put("curtime", new JdbcMethod("curtime", JdbcType.DATE, 0, 0));
        labkeyMethod.put("dayofmonth", new JdbcMethod("dayofmonth", JdbcType.INTEGER, 1, 1));
        labkeyMethod.put("dayofweek", new JdbcMethod("dayofweek", JdbcType.INTEGER, 1, 1));
        labkeyMethod.put("dayofyear", new JdbcMethod("dayofyear", JdbcType.INTEGER, 1, 1));
        labkeyMethod.put("degrees", new JdbcMethod("degrees", JdbcType.DOUBLE, 1, 1));
        labkeyMethod.put("exp", new JdbcMethod("exp", JdbcType.DOUBLE, 1, 1));
        labkeyMethod.put("floor", new JdbcMethod("floor", JdbcType.DOUBLE, 1, 1));
        labkeyMethod.put("foldername", new Method("folderName", JdbcType.VARCHAR, 0, 0) {
            @Override
            public MethodInfo getMethodInfo()
            {
                return new FolderInfo(false);
            }
        });
        labkeyMethod.put("folderpath", new Method("folderPath", JdbcType.VARCHAR, 0, 0) {
            @Override
            public MethodInfo getMethodInfo()
            {
                return new FolderInfo(true);
            }
        });
        labkeyMethod.put("greatest", new Method("greatest", JdbcType.OTHER, 1, Integer.MAX_VALUE)
        {
            @Override
            public MethodInfo getMethodInfo()
            {
                return new GreatestAndLeastInfo("greatest");
            }
        });
        labkeyMethod.put("hour", new JdbcMethod("hour", JdbcType.INTEGER, 1, 1));
        labkeyMethod.put("ifnull", new JdbcMethod("ifnull", JdbcType.OTHER, 2, 2){
            @Override
            public MethodInfo getMethodInfo()
            {
                return new JdbcMethodInfoImpl(_name, _jdbcType){
                    @Override
                    public JdbcType getJdbcType(JdbcType[] args)
                    {
                        return JdbcType.promote(args[0],args[1]);
                    }
                };
            }
        }) ;
        labkeyMethod.put("isequal", new Method("isequal", JdbcType.BOOLEAN, 2, 2){
            @Override
            public MethodInfo getMethodInfo()
            {
                return new IsEqualInfo();
            }
        });
        labkeyMethod.put("ismemberof", new Method("ismemberof", JdbcType.BOOLEAN, 1, 2) {
            @Override
            public MethodInfo getMethodInfo()
            {
                return new IsMemberInfo();
            }
        });
        labkeyMethod.put("javaconstant", new Method("javaconstant", JdbcType.VARBINARY, 1, 1){
            @Override
            public void validate(CommonTree fn, List<QNode> args, List<Exception> parseErrors, List<QueryParseException> parseWarnings)
            {
                super.validate(fn, args, parseErrors, parseWarnings);
                if (args.size() != 1)
                    return;
                int line = args.get(0).getLine();
                int column = args.get(0).getColumn();
                if (args.get(0).getTokenType() != SqlBaseLexer.QUOTED_STRING)
                {
                    parseErrors.add(new QueryParseException(_name.toUpperCase() + "() function expects quoted string arguments", null, line, column));
                    return;
                }

                String className = "";
                String propertyName = "";
                try
                {
                    String param = toSimpleString(new SQLFragment(args.get(0).getTokenText()));
                    int dot = param.lastIndexOf('.');
                    if (dot < 0)
                    {
                        parseErrors.add(new QueryParseException(_name.toUpperCase() + "() parameter should be valid class name '.' field: " + param, null, line, column));
                        return;
                    }
                    className = param.substring(0,dot);
                    propertyName = param.substring(dot + 1);
                    Class cls = Class.forName(className);
                    Field f = cls.getField(propertyName);
                    if (!Modifier.isStatic(f.getModifiers()) || !Modifier.isFinal(f.getModifiers()))
                        parseErrors.add(new QueryParseException(_name.toUpperCase() + "() field must be public static final: " + propertyName, null, line, column));
                    else if (null == JdbcType.valueOf(f.getType()))
                        parseErrors.add(new QueryParseException(_name.toUpperCase() + "() field type is not supported: " + f.getType().getName(), null, line, column));
                    else if (!f.isAnnotationPresent(Queryable.class) && cls.getPackage() != java.lang.Object.class.getPackage())
                        parseErrors.add(new QueryParseException(_name.toUpperCase() + "() field is not queryable: " + propertyName, null, line, column));
                }
                catch (ClassNotFoundException e)
                {
                    parseErrors.add(new QueryParseException(_name.toUpperCase() + "() class not found: " + className, null, line, column));
                }
                catch (NoSuchFieldException e)
                {
                    parseErrors.add(new QueryParseException(_name.toUpperCase() + "() field is not accessible: " + propertyName, null, line, column));
                }
            }

            @Override
            public MethodInfo getMethodInfo()
            {
                return new JavaConstantInfo();
            }
        });
        labkeyMethod.put("lcase", new JdbcMethod("lcase", JdbcType.VARCHAR, 1, 1));
        labkeyMethod.put("least", new Method("least", JdbcType.OTHER, 1, Integer.MAX_VALUE)
        {
            @Override
            public MethodInfo getMethodInfo()
            {
                return new GreatestAndLeastInfo("least");
            }
        });
        labkeyMethod.put("left", new JdbcMethod("left", JdbcType.VARCHAR, 2, 2));
        labkeyMethod.put("length", new JdbcMethod("length", JdbcType.INTEGER, 1, 1));
        labkeyMethod.put("log", new JdbcMethod("log", JdbcType.DOUBLE, 1, 1));
        labkeyMethod.put("log10", new JdbcMethod("log10", JdbcType.DOUBLE, 1, 1));
        labkeyMethod.put("locate", new Method("locate", JdbcType.INTEGER, 2, 3)
        {
            @Override
            public MethodInfo getMethodInfo()
            {
                return new AbstractMethodInfo(_jdbcType)
                {
                    @Override
                    public SQLFragment getSQL(SqlDialect dialect, SQLFragment[] arguments)
                    {
                        assert arguments.length == 2 || arguments.length == 3;
                        if (arguments.length == 2)
                            return  dialect.sqlLocate(arguments[0], arguments[1]);
                        else
                            return dialect.sqlLocate(arguments[0], arguments[1], arguments[2]);
                    }
                };
            }
        });
        labkeyMethod.put("lower", new JdbcMethod("lcase", JdbcType.VARCHAR, 1, 1));
        labkeyMethod.put("ltrim", new JdbcMethod("ltrim", JdbcType.VARCHAR, 1, 1));
        labkeyMethod.put("minute", new JdbcMethod("minute", JdbcType.INTEGER, 1, 1));
        labkeyMethod.put("mod", new JdbcMethod("mod", JdbcType.DOUBLE, 2, 2));
        labkeyMethod.put("moduleproperty", new Method("moduleproperty", JdbcType.VARCHAR, 2, 2){
            @Override
            public void validate(CommonTree fn, List<QNode> args, List<Exception> parseErrors, List<QueryParseException> parseWarnings)
            {
                super.validate(fn,args,parseErrors,parseWarnings);
                if (args.size() != 2)
                    return;
                if (args.get(0).getTokenType() != SqlBaseLexer.QUOTED_STRING)
                {
                    parseErrors.add(new QueryParseException(_name.toUpperCase() + "() function expects quoted string arguments", null, args.get(0).getLine(), args.get(0).getColumn()));
                    return;
                }
                if (args.get(1).getTokenType() != SqlBaseLexer.QUOTED_STRING)
                {
                    parseErrors.add(new QueryParseException(_name.toUpperCase() + "() function expects quoted string arguments", null, args.get(1).getLine(), args.get(1).getColumn()));
                    return;
                }

                String moduleName = toSimpleString(new SQLFragment(args.get(0).getTokenText()));
                Module module = ModuleLoader.getInstance().getModule(moduleName);
                if (null == module)
                {
                    parseWarnings.add(new QueryParseWarning(_name.toUpperCase() + "() module not found: " + moduleName, null, args.get(0).getLine(), args.get(0).getColumn()));
                    return;
                }
                String propertyName = toSimpleString(new SQLFragment(args.get(1).getTokenText()));
                ModuleProperty mp = module.getModuleProperties().get(propertyName);
                if (null == mp)
                    parseWarnings.add(new QueryParseWarning(_name.toUpperCase() + "() module property not found: " + propertyName, null, args.get(1).getLine(), args.get(1).getColumn()));
            }

            @Override
            public MethodInfo getMethodInfo()
            {
                return new ModulePropertyInfo();
            }
        });
        labkeyMethod.put("month", new JdbcMethod("month", JdbcType.INTEGER, 1, 1));
        labkeyMethod.put("monthname", new JdbcMethod("monthname", JdbcType.VARCHAR, 1, 1));
        labkeyMethod.put("now", new JdbcMethod("now", JdbcType.TIMESTAMP, 0, 0));
        labkeyMethod.put("nullif", new Method("nullif", JdbcType.OTHER, 2, 2)
        {
            @Override
            public MethodInfo getMethodInfo()
            {
                return new PassthroughInfo("nullif", null, JdbcType.OTHER);
            }
        });
        labkeyMethod.put("pi", new JdbcMethod("pi", JdbcType.DOUBLE, 0, 0));
        labkeyMethod.put("power", new JdbcMethod("power", JdbcType.DOUBLE, 2, 2));
        labkeyMethod.put("quarter", new JdbcMethod("quarter", JdbcType.INTEGER, 1, 1));
        labkeyMethod.put("radians", new JdbcMethod("radians", JdbcType.DOUBLE, 1, 1));
        labkeyMethod.put("rand", new JdbcMethod("rand", JdbcType.DOUBLE, 0, 1));
        labkeyMethod.put("repeat", new JdbcMethod("repeat", JdbcType.VARCHAR, 2, 2));
        labkeyMethod.put("round", new Method("round", JdbcType.DOUBLE, 1, 2)
			{
				@Override
				public MethodInfo getMethodInfo()
				{
					return new RoundInfo();
				}
			});
        labkeyMethod.put("rtrim", new JdbcMethod("rtrim", JdbcType.VARCHAR, 1, 1));
        labkeyMethod.put("second", new JdbcMethod("second", JdbcType.INTEGER, 1, 1));
        labkeyMethod.put("sign", new JdbcMethod("sign", JdbcType.DOUBLE, 1, 1));
        labkeyMethod.put("sin", new JdbcMethod("sin", JdbcType.DOUBLE, 1, 1));
        labkeyMethod.put("sqrt", new JdbcMethod("sqrt", JdbcType.DOUBLE, 1, 1));
        labkeyMethod.put("startswith", new Method("startswith", JdbcType.BOOLEAN, 2, 2)
            {
            @Override
            public MethodInfo getMethodInfo()
            {
                return new StartsWithInfo();
            }
        });
        labkeyMethod.put("substring", new JdbcMethod("substring", JdbcType.VARCHAR, 2, 3){
            @Override
            public MethodInfo getMethodInfo()
            {
                return new JdbcMethodInfoImpl(_name, _jdbcType)
                {
                    @Override
                    public SQLFragment getSQL(SqlDialect dialect, SQLFragment[] arguments)
                    {
                        if (arguments.length == 2)
                        {
                            SQLFragment[] argumentsThree = new SQLFragment[3];
                            argumentsThree[0] = arguments[0];
                            argumentsThree[1] = arguments[1];
                            // 19187: Query error when using substring without 3rd parameter in LabKey SQL
                            argumentsThree[2] = new SQLFragment(String.valueOf(Integer.MAX_VALUE/2));
                            arguments = argumentsThree;
                        }
                        return super.getSQL(dialect, arguments);
                    }
                };
            }
        });
        labkeyMethod.put("tan", new JdbcMethod("tan", JdbcType.DOUBLE, 1, 1));
        labkeyMethod.put("timestampadd", new Method("timestampadd", JdbcType.TIMESTAMP, 3, 3)
            {
                @Override
                public MethodInfo getMethodInfo()
                {
                    return new TimestampInfo(this);
                }
            });
        labkeyMethod.put("timestampdiff", new Method("timestampdiff", JdbcType.INTEGER, 3, 3)
            {
                @Override
                public MethodInfo getMethodInfo()
                {
                    return new TimestampInfo(this);
                }
            });
        labkeyMethod.put("truncate", new JdbcMethod("truncate", JdbcType.DOUBLE, 2, 2));
        labkeyMethod.put("ucase", new JdbcMethod("ucase", JdbcType.VARCHAR, 1, 1));
        labkeyMethod.put("upper", new JdbcMethod("ucase", JdbcType.VARCHAR, 1, 1));
        labkeyMethod.put("userid", new Method("userid", JdbcType.INTEGER, 0, 0)
        {
            @Override
            public MethodInfo getMethodInfo()
            {
                return new UserIdInfo();
            }
        });
        labkeyMethod.put("username", new Method("username", JdbcType.VARCHAR, 0, 0) {
            @Override
            public MethodInfo getMethodInfo()
            {
                return new UserNameInfo();
            }
        });
        labkeyMethod.put("version", new Method("version", JdbcType.DECIMAL, 0, 0){
            @Override
            public MethodInfo getMethodInfo()
            {
                return new VersionMethodInfo(){};
            }
        });
        labkeyMethod.put("week", new JdbcMethod("week", JdbcType.INTEGER, 1, 1));
        labkeyMethod.put("year", new JdbcMethod("year", JdbcType.INTEGER, 1, 1));

        // ========== Methods above this line have been documented ==========
        // Put new methods below this line and move above after they're documented, i.e.,
        // added to https://www.labkey.org/Documentation/wiki-page.view?name=labkeySql


        // ========== Don't document these ==========
        labkeyMethod.put("__cte_two__", new Method(JdbcType.INTEGER, 0, 0) {
            @Override
            public MethodInfo getMethodInfo()
            {
                return new AbstractMethodInfo(JdbcType.INTEGER)
                {
                    @Override
                    public SQLFragment getSQL(SqlDialect dialect, SQLFragment[] arguments)
                    {
                        SQLFragment cte = new SQLFragment("SELECT 2 as x");
                        SQLFragment ret = new SQLFragment();
                        String token = ret.addCommonTableExpression("__test__two__", "_two", cte);
                        ret.append("(SELECT x FROM ").append(token).append(" y)");
                        return ret;
                    }
                };
            }
        });
        labkeyMethod.put("__cte_three__", new Method(JdbcType.INTEGER, 0, 0) {
            @Override
            public MethodInfo getMethodInfo()
            {
                return new AbstractMethodInfo(JdbcType.INTEGER)
                {
                    @Override
                    public SQLFragment getSQL(SqlDialect dialect, SQLFragment[] arguments)
                    {
                        SQLFragment cte = new SQLFragment("SELECT 3 as x");
                        SQLFragment ret = new SQLFragment();
                        String token = ret.addCommonTableExpression("__test_three__", "_three", cte);
                        ret.append("(SELECT x FROM ").append(token).append(" y)");
                        return ret;
                    }
                };
            }
        });
        labkeyMethod.put("__cte_times__", new Method(JdbcType.INTEGER, 2, 2) {
            @Override
            public MethodInfo getMethodInfo()
            {
                return new AbstractMethodInfo(JdbcType.INTEGER)
                {
                    @Override
                    public SQLFragment getSQL(SqlDialect dialect, SQLFragment[] arguments)
                    {
                        SQLFragment cte = new SQLFragment();
                        cte.append("SELECT (").append(arguments[0]).append(")*(").append(arguments[1]).append(") as x");
                        SQLFragment ret = new SQLFragment();
                        String token = ret.addCommonTableExpression(GUID.makeGUID(), "_times", cte);
                        ret.append("(SELECT x FROM ").append(token).append(" y)");
                        return ret;
                    }
                };
            }
        });
    }


    final JdbcType _jdbcType;
    final String _name;
    final int _minArgs;
    final int _maxArgs;
    
    Method(JdbcType jdbcType, int min, int max)
    {
        this("#UNDEF#", jdbcType, min, max);
    }

    protected Method(String name, JdbcType jdbcType, int min, int max)
    {
        _name = name;
        _jdbcType = jdbcType;
        _minArgs = min;
        _maxArgs = max;
    }

    abstract public MethodInfo getMethodInfo();


    public void validate(CommonTree fn, List<QNode> args, List<Exception> parseErrors, List<QueryParseException> parseWarnings)
    {
        int count = args.size();
        if (count < _minArgs || count > _maxArgs)
        {
            if (_minArgs == _maxArgs)
                parseErrors.add(new QueryParseException(_name.toUpperCase() + " function expects " + _minArgs + " argument" + (_minArgs==1?"":"s"), null, fn.getLine(), fn.getCharPositionInLine()));
            else
                parseErrors.add(new QueryParseException(_name.toUpperCase() + " function expects " + _minArgs + " to " + _maxArgs + " arguments", null, fn.getLine(), fn.getCharPositionInLine()));
        }
    }

    public static void addMethod(String name, MethodInfo info, JdbcType returnType, int minArgs, int maxArgs)
    {
        Method m = new Method(name, returnType, minArgs, maxArgs) {
            @Override
            public MethodInfo getMethodInfo()
            {
                return info;
            }
        };
        labkeyMethod.put(name.toLowerCase(), m);
    }

    public static void addPassthroughMethod(String name, @Nullable String declaringSchemaName, JdbcType returnType, int minArguments, int maxArguments, SqlDialect dialect)
    {
        PassthroughMethod m = new PassthroughMethod(name, declaringSchemaName, returnType, minArguments, maxArguments);
        if (dialect.isPostgreSQL())
        {
            postgresMethods.put(name, m);
        }
        if (dialect.isSqlServer())
        {
            mssqlMethods.put(name, m);
        }
        if (dialect.isOracle())
        {
            oracleMethods.put(name, m);
        }
    }


    static class JdbcMethodInfoImpl extends AbstractMethodInfo
    {
        String _name;

        public JdbcMethodInfoImpl(String name, JdbcType jdbcType)
        {
            super(jdbcType);
            _name = name;
        }

        @Override
        public SQLFragment getSQL(SqlDialect dialect, SQLFragment[] arguments)
        {
            return dialect.formatJdbcFunction(_name, arguments);
        }
    }


    class TimestampInfo extends JdbcMethodInfoImpl
    {
        public TimestampInfo(Method method)
        {
            super(method._name, method._jdbcType);
        }

        @Override
        public SQLFragment getSQL(SqlDialect dialect, SQLFragment[] argumentsIN)
        {
            SQLFragment[] arguments = argumentsIN.clone();
            if (arguments.length >= 1)
            {
                TimestampDiffInterval i = TimestampDiffInterval.parse(arguments[0].getSQL());
                if (i != null)
                    arguments[0] = new SQLFragment(i.name());
            }
            return super.getSQL(dialect, arguments);
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
        SQL_TSI_YEAR;


        static TimestampDiffInterval parse(String s)
        {
            String interval = StringUtils.trimToEmpty(s).toUpperCase();
            if (interval.length() >= 2 && interval.startsWith("'") && interval.endsWith("'"))
                interval = interval.substring(1,interval.length()-1);
            if (!interval.startsWith("SQL_TSI"))
                interval = "SQL_TSI_" + interval;
            try
            {
                return TimestampDiffInterval.valueOf(interval);
            }
            catch (IllegalArgumentException x)
            {
                return null;
            }
        }
    }


    class ConvertInfo extends AbstractMethodInfo
    {
        public ConvertInfo()
        {
            super(JdbcType.OTHER);
        }

        @Override
        public BaseColumnInfo createColumnInfo(TableInfo parentTable, ColumnInfo[] arguments, String alias)
        {
            JdbcType jdbcType = _jdbcType;
            if (arguments.length >= 2)
            {
                try
                {
                    SQLFragment[] frags = getSQLFragments(new ColumnInfo[] {arguments[1]}); // only convert the type arg
                    String sqlEscapeTypeName = getTypeArgument(frags[0]);
                    jdbcType = ConvertType.valueOf(sqlEscapeTypeName).jdbcType;
                }
                catch (IllegalArgumentException x)
                {
                    /* */
                }
            }

            return new ExprColumn(parentTable, alias, null, jdbcType)
            {
                @Override
                public SQLFragment getValueSql(String tableAlias)
                {
                    return getSQL(parentTable.getSchema().getSqlDialect(), getSQLFragments(arguments));
                }
            };
        }

        @Override
        public SQLFragment getSQL(SqlDialect dialect, SQLFragment[] fragments)
        {
            JdbcType jdbcType = null;
            SQLFragment length = null;
            if (fragments.length >= 2)
            {
                String sqlEscapeTypeName = getTypeArgument(fragments[1]);
                try
                {
                    jdbcType = ConvertType.valueOf(sqlEscapeTypeName).jdbcType;
                    String typeName = dialect.getSqlTypeName(jdbcType);
                    if (null == typeName)
                        throw new NullPointerException("No sql type name found for '" + jdbcType.name() + "' in " + dialect.getProductName() + " database");
                    if (fragments.length > 2)
                        length = fragments[2];
                    fragments = new SQLFragment[] {fragments[0], new SQLFragment(typeName)};

                    if (jdbcType == JdbcType.DOUBLE || jdbcType == JdbcType.REAL)
                    {
                        String s = fragments[0].getRawSQL().toLowerCase();
                        if ("'infinity'".equals(s) || "'+infinity'".equals(s))
                            return new SQLFragment("?", jdbcType==JdbcType.DOUBLE ? Double.POSITIVE_INFINITY : Float.POSITIVE_INFINITY);
                        if ("'-infinity'".equals(s))
                            return new SQLFragment("?", jdbcType==JdbcType.DOUBLE ? Double.NEGATIVE_INFINITY : Float.NEGATIVE_INFINITY);
                        if ("'nan'".equals(s))
                            return new SQLFragment("?", jdbcType==JdbcType.DOUBLE ? Double.NaN : Float.NaN);
                    }
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
                if (null != length)
                {
                    ret.append("(").append(length).append(")");
                }
            }
            ret.append(")");                            
            return ret;
        }
        String getTypeArgument(SQLFragment typeSqlFragment) throws IllegalArgumentException
        {
            String typeName = StringUtils.trimToEmpty(typeSqlFragment.getSQL());
            if (typeName.length() >= 2 && typeName.startsWith("'") && typeName.endsWith("'"))
                typeName = typeName.substring(1,typeName.length()-1);
            if (typeName.startsWith("SQL_"))
                typeName = typeName.substring(4);
            return typeName;
        }

        @Override
        public JdbcType getJdbcType(JdbcType[] args)
        {
            throw new IllegalStateException("CONVERT/CAST can't figure out type using this method");
        }

        public JdbcType getTypeFromArgs(QNode args)
        {
            List<QNode> children = args.childList();
            if (children.size() < 2)
                return JdbcType.VARCHAR;

            String sqlEscapeTypeName = ((QString)children.get(1)).getValue();
            if (sqlEscapeTypeName.startsWith("SQL_"))
                sqlEscapeTypeName = sqlEscapeTypeName.substring(4);

            JdbcType jdbcType = JdbcType.OTHER;
            try { jdbcType = ConvertType.valueOf(sqlEscapeTypeName).jdbcType; }catch (IllegalArgumentException x) {/* */}
            return jdbcType;
        }
    }

    static class PassthroughInfo extends AbstractMethodInfo
    {
        private final String _name;
        private final String _declaringSchemaName;

        public PassthroughInfo(String method, @Nullable String declaringSchemaName, JdbcType jdbcType)
        {
            super(jdbcType);
            _name = method;
            _declaringSchemaName = declaringSchemaName;
        }

        @Override
        protected JdbcType getSqlType(ColumnInfo[] arguments)
        {
            JdbcType jdbcType = _jdbcType;
            if (jdbcType == JdbcType.OTHER)
                jdbcType = arguments.length > 0 ? arguments[0].getJdbcType() : JdbcType.VARCHAR;
            return jdbcType;
        }

        @Override
        public JdbcType getJdbcType(JdbcType[] args)
        {
            JdbcType jdbcType = _jdbcType;
            if (jdbcType == JdbcType.OTHER)
                jdbcType = args.length > 0 ? args[0] : JdbcType.VARCHAR;
            return jdbcType;
        }

        @Override
        public SQLFragment getSQL(SqlDialect dialect, SQLFragment[] arguments)
        {
            SQLFragment ret = new SQLFragment();
            if (_declaringSchemaName != null)
            {
                ret.append(_declaringSchemaName);
                ret.append(".");
            }
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
			super("round", JdbcType.DOUBLE);
		}

		// https://www.labkey.org/issues/home/Developer/issues/details.view?issueId=7078
        // Even though we are generating {fn ROUND()}, SQL Server requires 2 arguments
        // while Postgres requires 1 argument (for doubles)
		@Override
        public SQLFragment getSQL(SqlDialect dialect, SQLFragment[] arguments)
		{
            boolean supportsRoundDouble = dialect.supportsRoundDouble();
            boolean unitRound = arguments.length == 1 || (arguments.length==2 && arguments[1].getSQL().equals("0"));
            if (unitRound)
            {
                if (supportsRoundDouble)
                    return super.getSQL(dialect, new SQLFragment[] {arguments[0], new SQLFragment("0")});
                else
                    return super.getSQL(dialect, new SQLFragment[] {arguments[0]});
            }

            int i = Integer.MIN_VALUE;
            try
            {
                i = Integer.parseInt(arguments[1].getSQL());
            }
            catch (NumberFormatException x)
            {
                /* fall through */
            }

            if (supportsRoundDouble || i == Integer.MIN_VALUE)
                return super.getSQL(dialect, arguments);

            // fall back, only supports simple integer
            SQLFragment scaled = new SQLFragment();
            scaled.append("(");
            scaled.append(arguments[0]);
            scaled.append(")*").append(Math.pow(10,i));
            SQLFragment ret = super.getSQL(dialect, new SQLFragment[] {scaled});
            ret.append("/");
            ret.append(Math.pow(10,i));
            return ret;
		}
	}


    class AgeMethodInfo extends AbstractMethodInfo
    {
        AgeMethodInfo()
        {
            super(JdbcType.INTEGER);
        }

        @Override
        public SQLFragment getSQL(SqlDialect dialect, SQLFragment[] arguments)
        {
            if (arguments.length == 2)
                return new AgeInYearsMethodInfo().getSQL(dialect, arguments);
            TimestampDiffInterval i = TimestampDiffInterval.parse(arguments[2].getSQL());
            if (i == TimestampDiffInterval.SQL_TSI_YEAR)
                return new AgeInYearsMethodInfo().getSQL(dialect, arguments);
            if (i == TimestampDiffInterval.SQL_TSI_MONTH)
                return new AgeInMonthsMethodInfo().getSQL(dialect, arguments);
            if (null == i)
                throw new IllegalArgumentException("AGE(" + arguments[2].getSQL() + ")");
            else
                throw new IllegalArgumentException("AGE only supports YEAR and MONTH");
        }
    }


    class AgeInYearsMethodInfo extends AbstractMethodInfo
    {
        AgeInYearsMethodInfo()
        {
            super(JdbcType.INTEGER);
        }

        @Override
        public SQLFragment getSQL(SqlDialect dialect, SQLFragment[] arguments)
        {
            MethodInfo year = labkeyMethod.get("year").getMethodInfo();
            MethodInfo month = labkeyMethod.get("month").getMethodInfo();
            MethodInfo dayofmonth = labkeyMethod.get("dayofmonth").getMethodInfo();

            SQLFragment ret = new SQLFragment();
            SQLFragment yearA = year.getSQL(dialect, new SQLFragment[] {arguments[0]});
            SQLFragment monthA = month.getSQL(dialect, new SQLFragment[] {arguments[0]});
            SQLFragment dayA = dayofmonth.getSQL(dialect, new SQLFragment[] {arguments[0]});
            SQLFragment yearB = year.getSQL(dialect, new SQLFragment[] {arguments[1]});
            SQLFragment monthB = month.getSQL(dialect, new SQLFragment[] {arguments[1]});
            SQLFragment dayB = dayofmonth.getSQL(dialect, new SQLFragment[] {arguments[1]});

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
            super(JdbcType.INTEGER);
        }


        @Override
        public SQLFragment getSQL(SqlDialect dialect, SQLFragment[] arguments)
        {
            MethodInfo year = labkeyMethod.get("year").getMethodInfo();
            MethodInfo month = labkeyMethod.get("month").getMethodInfo();
            MethodInfo dayofmonth = labkeyMethod.get("dayofmonth").getMethodInfo();

            SQLFragment ret = new SQLFragment();
            SQLFragment yearA = year.getSQL(dialect, new SQLFragment[] {arguments[0]});
            SQLFragment monthA = month.getSQL(dialect, new SQLFragment[] {arguments[0]});
            SQLFragment dayA = dayofmonth.getSQL(dialect, new SQLFragment[] {arguments[0]});
            SQLFragment yearB = year.getSQL(dialect, new SQLFragment[] {arguments[1]});
            SQLFragment monthB = month.getSQL(dialect, new SQLFragment[] {arguments[1]});
            SQLFragment dayB = dayofmonth.getSQL(dialect, new SQLFragment[] {arguments[1]});

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


    class StartsWithInfo extends AbstractMethodInfo
    {
        StartsWithInfo()
        {
            super(JdbcType.BOOLEAN);
        }

        @Override
        public SQLFragment getSQL(SqlDialect dialect, SQLFragment[] arguments)
        {
            // try to turn second argument into pattern
            SQLFragment pattern = escapeLikePattern(arguments[1], '!', null, "%");
            if (null != pattern)
            {
                String like = dialect.getCaseInsensitiveLikeOperator();
                SQLFragment ret = new SQLFragment();
                ret.append("((").append(arguments[0]).append(") ").append(like).append(" ").append(pattern).append(" ESCAPE '!')");
                return ret;
            }
            else if (dialect.isCaseSensitive())
            {
                SQLFragment ret = new SQLFragment();
                ret.append("{fn lcase({fn left(").append(arguments[0]).append(",").append("{fn length(").append(arguments[1]).append(")})})}");
                ret.append("={fn lcase(").append(arguments[1]).append(")}");
                return ret;
            }
            else
            {
                SQLFragment ret = new SQLFragment();
                ret.append("{fn left(").append(arguments[0]).append(",").append("{fn length(").append(arguments[1]).append(")})}");
                ret.append("=(").append(arguments[1]).append(")");
                return ret;
            }
        }
    }




    class IsEqualInfo extends AbstractMethodInfo
    {
        IsEqualInfo()
        {
            super(JdbcType.BOOLEAN);
        }

        @Override
        public SQLFragment getSQL(SqlDialect dialect, SQLFragment[] arguments)
        {
            SQLFragment ret = new SQLFragment();
            SQLFragment a = arguments[0];
            SQLFragment b = arguments[1];

            ret.append("(");
            ret.append("(").append(a).append(")=(").append(b).append(")");
            ret.append(" OR (");
            ret.append("(").append(a).append(") IS NULL AND (").append(b).append(") IS NULL");
            ret.append("))");

            return ret;
        }
    }

    class VersionMethodInfo extends AbstractMethodInfo
    {
        VersionMethodInfo()
        {
            super(JdbcType.DECIMAL);
        }

        @Override
        public SQLFragment getSQL(SqlDialect dialect, SQLFragment[] arguments)
        {
            return new SQLFragment("CAST(" + (new DecimalFormat("0.000#")).format(AppProps.getInstance().getSchemaVersion()) + " AS NUMERIC(15,4))");
        }
    }

    class UserIdInfo extends AbstractMethodInfo
    {
        UserIdInfo()
        {
            super(JdbcType.INTEGER);
        }

        @Override
        public SQLFragment getSQL(SqlDialect dialect, SQLFragment[] arguments)
        {
            SQLFragment ret = new SQLFragment("?");
            ret.add(new Callable(){
                @Override
                public Object call()
                {
                    User user = (User)QueryServiceImpl.get().getEnvironment(QueryService.Environment.USER);
                    return null == user ? null : user.getUserId();
                }
            });
            return ret;
        }

        @Override
        public MutableColumnInfo createColumnInfo(TableInfo parentTable, ColumnInfo[] arguments, String alias)
        {
            var c = super.createColumnInfo(parentTable, arguments, alias);
            UserSchema schema = parentTable.getUserSchema();
            if (null == schema)
                throw new NullPointerException();
            c.setFk(new UserIdQueryForeignKey(schema));
            c.setDisplayColumnFactory(UserIdQueryForeignKey._factoryBlank);
            return c;
        }
    }

    class UserNameInfo extends AbstractMethodInfo
    {
        UserNameInfo()
        {
            super(JdbcType.VARCHAR);
        }

        @Override
        public SQLFragment getSQL(SqlDialect dialect, SQLFragment[] arguments)
        {
            SQLFragment ret = new SQLFragment("?");
            ret.add((Callable) () -> {
                User user = (User)QueryServiceImpl.get().getEnvironment(QueryService.Environment.USER);
                if (null == user)
                    return null;
                return user.getDisplayName(user);
            });
            return ret;
        }
    }


    static class FolderInfo extends AbstractQueryMethodInfo
    {
        final boolean path;

        FolderInfo(boolean path)
        {
            super(JdbcType.VARCHAR);
            this.path = path;
        }

        @Override
        public SQLFragment getSQL(Query query, SqlDialect dialect, SQLFragment[] arguments)
        {
            String v;
            // NOTE we resolve CONTAINER at compile time because we don't have a good place to set this variable at runtime
            // use of SqlSelector and async complicate that
            Container cCompile = getCompileTimeContainer(query);
            v = null==cCompile ? null : path ? cCompile.getPath() : cCompile.getName();

            if (null == v)
                return new SQLFragment("CAST(NULL AS VARCHAR)");
            else
                return new SQLFragment("CAST(? AS " + dialect.getSqlTypeName(JdbcType.VARCHAR) + ")", v);
        }
    }


    static class ModulePropertyInfo extends AbstractQueryMethodInfo
    {
        ModulePropertyInfo()
        {
            super(JdbcType.VARCHAR);
        }

        @Override
        public SQLFragment getSQL(Query query, SqlDialect dialect, SQLFragment[] arguments)
        {
            String moduleName = toSimpleString(arguments[0]);
            String propertyName = toSimpleString(arguments[1]);

            findProperty:
            {
                if (StringUtils.isEmpty(moduleName) || StringUtils.isEmpty(propertyName))
                    break findProperty;

                Module module = ModuleLoader.getInstance().getModule(moduleName);
                if (null == module)
                    break findProperty;

                ModuleProperty mp = module.getModuleProperties().get(propertyName);
                if (null == mp)
                    break findProperty;

                String value = null;
                Container cCompile = getCompileTimeContainer(query);
                if (null != cCompile)
                    value = mp.getEffectiveValue(cCompile);
                return new SQLFragment("CAST(? AS " + dialect.getSqlTypeName(JdbcType.VARCHAR) + ")", value);
            }

            return new SQLFragment("CAST(NULL AS VARCHAR)");
        }
    }
    class JavaConstantInfo extends AbstractMethodInfo
    {
        JavaConstantInfo()
        {
            super(JdbcType.VARCHAR);
        }

        @Override
        public SQLFragment getSQL(SqlDialect dialect, SQLFragment[] arguments)
        {
            getProperty:
            {
                String param = toSimpleString(arguments[0]);
                int dot = param.lastIndexOf('.');
                if (dot < 0)
                    break getProperty;
                String className = param.substring(0,dot);
                String propertyName = param.substring(dot+1);

                Class cls;
                try
                {
                    cls = Class.forName(className);
                }
                catch (ClassNotFoundException e)
                {
                    break getProperty;
                }

                Field f;
                try
                {
                    f = cls.getField(propertyName);
                    if (!Modifier.isStatic(f.getModifiers()) || !Modifier.isFinal(f.getModifiers()))
                        break getProperty;
                    if (!f.isAnnotationPresent(Queryable.class) && cls.getPackage() != java.lang.Object.class.getPackage())
                        break getProperty;
                }
                catch (NoSuchFieldException e)
                {
                    break getProperty;
                }

                // NOTE: we've already said that this is a String so we can't really return the correct type here
                JdbcType type = JdbcType.valueOf(f.getType());
                if (null == type)
                    break getProperty;

                Object value;
                try
                {
                    value = f.get(null);
                }
                catch (IllegalAccessException x)
                {
                    break getProperty;
                }

                //see issue 19661.  SQLServer defaults to 30 for VARCHAR, unless a length is explicitly specified, so we CAST using the length of this string
                return new SQLFragment("CAST(? AS " + dialect.getSqlTypeName(JdbcType.VARCHAR) + (value == null ? "" : "(" + value.toString().length() + ")") + ")", value);
            }

            // no legal field found
            return new SQLFragment("CAST(NULL AS VARCHAR)");
        }
    }


    class ContextPathInfo extends AbstractMethodInfo
    {
        ContextPathInfo()
        {
            super(JdbcType.VARCHAR);
        }

        @Override
        public SQLFragment getSQL(SqlDialect dialect, SQLFragment[] arguments)
        {
            SQLFragment ret = new SQLFragment("?");
            ret.add(AppProps.getInstance().getContextPath());
            return ret;
        }
    }


    class IsMemberInfo extends AbstractMethodInfo
    {
        IsMemberInfo()
        {
            super(JdbcType.BOOLEAN);
        }

        @Override
        public SQLFragment getSQL(SqlDialect dialect, SQLFragment[] arguments)
        {
            SQLFragment groupArg = arguments[0];
            SQLFragment userArg = arguments.length > 1 ? arguments[1] : null;

            //If current user, which as of 12.3 is the only documented version of this function
            if (arguments.length == 1)
            {
                //Current UserID gets put in QueryService.getEnvironment() by AuthFilter
                // NOTE: ideally this should be calculated at RUN time not compile time. (see UserIdInfo)
                // However, we are generating an IN () clause here, and it's easier to do this way
                User user =  (User)QueryServiceImpl.get().getEnvironment(QueryService.Environment.USER);
                if (null == user)
                    throw new IllegalStateException("Query environment has not been set");
                Object[] groupIds = ArrayUtils.toObject(user.getGroups());
                SQLFragment ret = new SQLFragment();
                ret.append("(").append(groupArg).append(") IN (").append(StringUtils.join(groupIds, ",")).append(")");
                return ret;
            }

            // NOTE: we are not verifying principals.type='g'
            // NOTE: we are not verifying principals.container in (project,site)

            return CompareType.getMemberOfSQL(dialect, userArg, groupArg);
         }
    }


    private static class JdbcMethod extends Method
    {
        JdbcMethod(String name, JdbcType jdbcType, int min, int max)
        {
            super(name, jdbcType, min, max);
        }

        @Override
        public MethodInfo getMethodInfo()
        {
            return new JdbcMethodInfoImpl(_name, _jdbcType);
        }
    }

    private static class PassthroughMethod extends Method
    {
        private final String _declaringSchemaName;

        PassthroughMethod(String name, JdbcType jdbcType, int min, int max)
        {
            this(name, null, jdbcType, min, max);
        }

        PassthroughMethod(String name, @Nullable String declaringSchemaName, JdbcType jdbcType, int min, int max)
        {
            super(name, jdbcType, min, max);
            _declaringSchemaName = declaringSchemaName;
        }

        @Override
        public MethodInfo getMethodInfo()
        {
            return new PassthroughInfo(_name, _declaringSchemaName, _jdbcType);
        }
    }


    public static Method valueOf(String name)
    {
        return resolve(null, name);
    }
    
    public static Method resolve(SqlDialect d, String name)
    {
        Method m = null;
        name = name.toLowerCase();
        if (null != d )
        {
            if (d.isPostgreSQL())
                m = postgresMethods.get(name);
            else if (d.isSqlServer())
                m = mssqlMethods.get(name);
            else if (d.isOracle())
                m = oracleMethods.get(name);

            if (null != m)
                return m;
        }
        m = labkeyMethod.get(name);
        if (null != m)
            return m;
        throw new IllegalArgumentException(name);
    }


    class OverlapsMethodInfo extends AbstractMethodInfo
    {
        OverlapsMethodInfo()
        {
            super(JdbcType.BOOLEAN);
        }
        
        @Override
        public SQLFragment getSQL(SqlDialect dialect, SQLFragment[] arguments)
        {
            SQLFragment ret = new SQLFragment();
            ret.append("(").append(arguments[0]).append(",").append(arguments[1]).append(")");
            ret.append(" overlaps ");
            ret.append("(").append(arguments[2]).append(",").append(arguments[3]).append(")");
            return ret;
        }
    }

    class SimilarToMethodInfo extends AbstractMethodInfo
    {
        SimilarToMethodInfo()
        {
            super(JdbcType.BOOLEAN);
        }

        @Override
        public SQLFragment getSQL(SqlDialect dialect, SQLFragment[] arguments)
        {
            SQLFragment ret = new SQLFragment();
            ret.append("((").append(arguments[0]).append(")");
            ret.append(" SIMILAR TO ");
            ret.append("(").append(arguments[1]).append(")");
            if (arguments.length == 3)
            {
                ret.append(" ESCAPE (").append(arguments[2]).append(")");
            }
            ret.append(")");
            return ret;
        }
    }


    class GreatestAndLeastInfo extends PassthroughInfo
    {
        public GreatestAndLeastInfo(String method)
        {
            super(method, null, JdbcType.OTHER);
        }

        @Override
        public SQLFragment getSQL(SqlDialect dialect, SQLFragment[] arguments)
        {
            if (dialect.supportsNativeGreatestAndLeast())
                return super.getSQL(dialect, arguments);
            else
                return dialect.getGreatestAndLeastSQL(_name, arguments);
        }
    }




    public static SQLFragment escapeLikePattern(SQLFragment f, char escapeChar, @Nullable String prepend, @Nullable String append)
    {
        if (!isSimpleString(f))
            return null;

        String escapeChars = "_%[" + escapeChar;
        SQLFragment esc = new SQLFragment();

        esc.append("'");
        if (null != prepend)
            esc.append(prepend);

        for (char c : f.getSQL().substring(1,f.length()-1).toCharArray())
        {
            if (-1 != escapeChars.indexOf(c))
                esc.append(escapeChar);
            esc.append(c);
        }

        if (null != append)
            esc.append(append);
        esc.append('\'');

        return esc;
    }


    public static boolean isSimpleString(SQLFragment f)
    {
        if (f.getParams().size() > 0)
            return false;
        String s = f.getSQL();
        if (s.length() < 2 || !s.startsWith("'"))
            return false;
        return s.length()-1 == s.indexOf('\'',1);
    }


    public static String toSimpleString(SQLFragment f)
    {
        assert isSimpleString(f);
        String s = f.getSQL();
        if (s.length() < 2 || !s.startsWith("'"))
            return s;
        s = s.substring(1,s.length()-1);
        s = StringUtils.replace(s,"''","'");
        return s;
    }


    static Container getCompileTimeContainer(Query query)
    {
        Container cCompile = (Container)QueryServiceImpl.get().getEnvironment(QueryService.Environment.CONTAINER);
        if (null == cCompile && null != query)
            cCompile = query.getSchema().getContainer();
        return cCompile;
    }


    final static Map<String, Method> postgresMethods = Collections.synchronizedMap(new CaseInsensitiveHashMap<>());
    static
    {
        postgresMethods.put("ascii",new PassthroughMethod("ascii",JdbcType.INTEGER,1,1));
        postgresMethods.put("btrim",new PassthroughMethod("btrim",JdbcType.VARCHAR,1,2));
        postgresMethods.put("char_length",new PassthroughMethod("char_length",JdbcType.INTEGER,1,1));
        postgresMethods.put("character_length",new PassthroughMethod("character_length",JdbcType.INTEGER,1,1));
        postgresMethods.put("chr",new PassthroughMethod("chr",JdbcType.VARCHAR,1,1));
        postgresMethods.put("concat_ws", new PassthroughMethod("concat_ws", JdbcType.VARCHAR, 1, Integer.MAX_VALUE));
        postgresMethods.put("decode",new PassthroughMethod("decode",JdbcType.VARCHAR,2,2));
        postgresMethods.put("encode",new PassthroughMethod("encode",JdbcType.VARCHAR,2,2));
        postgresMethods.put("initcap",new PassthroughMethod("initcap",JdbcType.VARCHAR,1,1));
        postgresMethods.put("lpad",new PassthroughMethod("lpad",JdbcType.VARCHAR,2,3));
        postgresMethods.put("md5",new PassthroughMethod("md5",JdbcType.VARCHAR,1,1));
        postgresMethods.put("octet_length",new PassthroughMethod("octet_length",JdbcType.INTEGER,1,1));
        postgresMethods.put("overlaps",new PassthroughMethod("overlaps",JdbcType.BOOLEAN,4,4) {
            @Override
            public MethodInfo getMethodInfo()
            {
                return new OverlapsMethodInfo();
            }
        });
        postgresMethods.put("quote_ident",new PassthroughMethod("quote_ident",JdbcType.VARCHAR,1,1));
        postgresMethods.put("quote_literal",new PassthroughMethod("quote_literal",JdbcType.VARCHAR,1,1));
        postgresMethods.put("regexp_replace",new PassthroughMethod("regexp_replace",JdbcType.VARCHAR,3,4));
        postgresMethods.put("repeat",new PassthroughMethod("repeat",JdbcType.VARCHAR,2,2));
        postgresMethods.put("replace",new PassthroughMethod("replace",JdbcType.VARCHAR,3,3));
        postgresMethods.put("rpad",new PassthroughMethod("rpad",JdbcType.VARCHAR,2,3));
        postgresMethods.put("similar_to", new PassthroughMethod("similar_to", JdbcType.BOOLEAN, 2, 3) {
            @Override
            public MethodInfo getMethodInfo() { return new SimilarToMethodInfo(); }
        });
        postgresMethods.put("split_part",new PassthroughMethod("split_part",JdbcType.VARCHAR,3,3));
        postgresMethods.put("strpos",new PassthroughMethod("strpos",JdbcType.VARCHAR,2,2));
        postgresMethods.put("substr",new PassthroughMethod("substr",JdbcType.VARCHAR,2,3));
        postgresMethods.put("to_ascii",new PassthroughMethod("to_ascii",JdbcType.VARCHAR,1,2));
        postgresMethods.put("to_hex",new PassthroughMethod("to_hex",JdbcType.VARCHAR,1,1));
        postgresMethods.put("translate",new PassthroughMethod("translate",JdbcType.VARCHAR,3,3));
        postgresMethods.put("to_char",new PassthroughMethod("to_char",JdbcType.VARCHAR,2,2));
        postgresMethods.put("to_date",new PassthroughMethod("to_date",JdbcType.DATE,2,2));
        postgresMethods.put("to_timestamp",new PassthroughMethod("to_timestamp",JdbcType.TIMESTAMP,2,2));
        postgresMethods.put("to_number",new PassthroughMethod("to_number",JdbcType.DECIMAL,2,2));
        postgresMethods.put("string_to_array",new PassthroughMethod("string_to_array",JdbcType.VARCHAR,2,3));
        postgresMethods.put("unnest",new PassthroughMethod("unnest",JdbcType.VARCHAR,1,1));
        postgresMethods.put("row",new PassthroughMethod("row",JdbcType.VARCHAR,1, Integer.MAX_VALUE));

        addPostgresJsonMethods();
    }

    /**
     * Wire up JSON and JSONB data type support for Postgres, as described here:
     * https://www.postgresql.org/docs/9.5/functions-json.html
     */
    private static void addPostgresJsonMethods()
    {
        // Pretend that the JSON operators are a function instead so that we don't need them to be fully supported
        // for query parsing
        postgresMethods.put("json_op", new Method(JdbcType.VARCHAR, 3, 3)
        {
            @Override
            public MethodInfo getMethodInfo()
            {
                return new AbstractMethodInfo(JdbcType.VARCHAR)
                {
                    private final Set<String> ALLOWED_OPERATORS = Set.of("->", "->>", "#>", "#>>", "@>", "<@",
                            "?", "?|", "?&", "||", "-", "#-");

                    @Override
                    public SQLFragment getSQL(SqlDialect dialect, SQLFragment[] arguments)
                    {
                        SQLFragment rawOperator = arguments[1];
                        String operatorRawString = rawOperator.getSQL();
                        if (!rawOperator.getParams().isEmpty() || !operatorRawString.startsWith("'") || !operatorRawString.endsWith("'"))
                        {
                            throw new QueryParseException("Unsupported JSON operator: " + rawOperator, null, 0, 0);
                        }

                        String strippedOperator = operatorRawString.substring(1, operatorRawString.length() - 1);
                        if (!ALLOWED_OPERATORS.contains(strippedOperator))
                        {
                            throw new QueryParseException("Unsupported JSON operator: " + rawOperator, null, 0, 0);
                        }

                        return new SQLFragment("(").append(arguments[0]).append(")").
                                append(strippedOperator).
                                append("(").append(arguments[2]).append(")");
                    }
                };
            }
        });

        // Special functions to cast an argument to the JSON or JSONB data types without needing to support as official datatype in CAST
        postgresMethods.put("parse_json", new ParseJSONMethod("json"));
        postgresMethods.put("parse_jsonb", new ParseJSONMethod("jsonb"));

        postgresMethods.put("to_json", new PassthroughMethod("to_json", JdbcType.OTHER, 1, 1));
        postgresMethods.put("to_jsonb", new PassthroughMethod("to_jsonb", JdbcType.OTHER, 1, 1));
        postgresMethods.put("array_to_json", new PassthroughMethod("array_to_json", JdbcType.OTHER, 1, 2));
        postgresMethods.put("row_to_json", new PassthroughMethod("row_to_json", JdbcType.OTHER, 1, 2));

        addJsonPassthroughMethod("build_array", JdbcType.OTHER, 1, Integer.MAX_VALUE);
        addJsonPassthroughMethod("build_object", JdbcType.OTHER, 1, Integer.MAX_VALUE);
        addJsonPassthroughMethod("object", JdbcType.OTHER, 1, 2);

        addJsonPassthroughMethod("array_length", JdbcType.INTEGER, 1, 1);
        addJsonPassthroughMethod("extract_path", JdbcType.OTHER, 2, Integer.MAX_VALUE);
        addJsonPassthroughMethod("extract_path_text", JdbcType.VARCHAR, 2, Integer.MAX_VALUE);
        addJsonPassthroughMethod("object_keys", JdbcType.OTHER, 1, 1);
        addJsonPassthroughMethod("array_elements", JdbcType.OTHER, 1, 1);
        addJsonPassthroughMethod("array_elements_text", JdbcType.VARCHAR, 1, 1);
        addJsonPassthroughMethod("typeof", JdbcType.VARCHAR, 1, 1);
        addJsonPassthroughMethod("strip_nulls", JdbcType.OTHER, 1, 1);

        // Not fully supported because they can't be used in the FROM clause of a query, and they produce more than
        // one column as an output, but leaving because they work in other contexts
        addJsonPassthroughMethod("each", JdbcType.OTHER, 1, 1);
        addJsonPassthroughMethod("each_text", JdbcType.OTHER, 1, 1);


        // Not supported because they require an AS clause that LabKey SQL doesn't handle
        // Example: select * from json_to_record('{"a":1,"b":[1,2,3],"c":[1,2,3],"e":"bar","r": {"a": 123, "b": "a b c"}}') as x(a int, b text, c int[], d text, r myrowtype)
//        addJsonPassthroughMethod("to_record", JdbcType.OTHER, 1, 1);
//        addJsonPassthroughMethod("to_recordset", JdbcType.OTHER, 1, 1);

        // Not supported because they require a first argument (base/type) that LabKey SQL doesn't handle
        // Example: select * from json_populate_record(null::myrowtype, '{"a": 1, "b": ["2", "a b"], "c": {"d": 4, "e": "a b c"}, "x": "foo"}')
//        addJsonPassthroughMethod("populate_record", JdbcType.OTHER, 2, 2);
//        addJsonPassthroughMethod("populate_recordset", JdbcType.OTHER, 2, 2);


        postgresMethods.put("jsonb_set", new PassthroughMethod("jsonb_set", JdbcType.OTHER, 3, 4));
        postgresMethods.put("jsonb_set_lax", new PassthroughMethod("jsonb_set_lax", JdbcType.OTHER, 3, 5));
        postgresMethods.put("jsonb_insert", new PassthroughMethod("jsonb_insert", JdbcType.OTHER, 3, 4));
        postgresMethods.put("jsonb_pretty", new PassthroughMethod("jsonb_pretty", JdbcType.VARCHAR, 1, 1));

        // New in Postgres 12, 13, and 14
        postgresMethods.put("jsonb_path_exists", new PassthroughMethod("jsonb_path_exists", JdbcType.VARCHAR, 2, 4));
        postgresMethods.put("jsonb_path_match", new PassthroughMethod("jsonb_path_match", JdbcType.VARCHAR, 2, 4));
        postgresMethods.put("jsonb_path_query", new PassthroughMethod("jsonb_path_query", JdbcType.VARCHAR, 2, 4));
        postgresMethods.put("jsonb_path_query_array", new PassthroughMethod("jsonb_path_query_array", JdbcType.VARCHAR, 2, 4));
        postgresMethods.put("jsonb_path_query_first", new PassthroughMethod("jsonb_path_query_first", JdbcType.VARCHAR, 2, 4));
        postgresMethods.put("jsonb_path_exists_tz", new PassthroughMethod("jsonb_path_exists_tz", JdbcType.VARCHAR, 2, 4));
        postgresMethods.put("jsonb_path_match_tz", new PassthroughMethod("jsonb_path_match_tz", JdbcType.VARCHAR, 2, 4));
        postgresMethods.put("jsonb_path_query_tz", new PassthroughMethod("jsonb_path_query_tz", JdbcType.VARCHAR, 2, 4));
        postgresMethods.put("jsonb_path_query_array_tz", new PassthroughMethod("jsonb_path_query_array_tz", JdbcType.VARCHAR, 2, 4));
        postgresMethods.put("jsonb_path_query_first_tz", new PassthroughMethod("jsonb_path_query_first_tz", JdbcType.VARCHAR, 2, 4));

        // "is distinct from" and "is not distinct from" operators in method form
        labkeyMethod.put("is_distinct_from", new Method(JdbcType.BOOLEAN, 2, 2) {
            @Override
            public MethodInfo getMethodInfo()
            {
                return new IsDistinctFromMethodInfo(IS);
            }
        });
        labkeyMethod.put("is_not_distinct_from", new Method(JdbcType.BOOLEAN, 2, 2) {
            @Override
            public MethodInfo getMethodInfo()
            {
                return new IsDistinctFromMethodInfo(IS_NOT);
            }
        });
    }

    private static class IsDistinctFromMethodInfo extends AbstractMethodInfo
    {
        final int token;

        IsDistinctFromMethodInfo(int token)
        {
            super(JdbcType.BOOLEAN);
            this.token = token;
        }
        @Override
        public SQLFragment getSQL(SqlDialect dialect, SQLFragment[] arguments)
        {
            SQLFragment ret = new SQLFragment();
            ret.append(" ((").append(arguments[0]).append(")");
            if (token == IS)
                ret.append(" IS DISTINCT FROM ");
            else
                ret.append(" IS NOT DISTINCT FROM ");
            ret.append("(").append(arguments[1]).append(")) ");
            return ret;
        }
    }

    private static void addJsonPassthroughMethod(String name, JdbcType type, int minArgs, int maxArgs)
    {
        postgresMethods.put("json_" + name, new PassthroughMethod("json_" + name, type, minArgs, maxArgs));
        postgresMethods.put("jsonb_" + name, new PassthroughMethod("jsonb_" + name, type, minArgs, maxArgs));
    }

    final static Map<String, Method> mssqlMethods = Collections.synchronizedMap(new CaseInsensitiveHashMap<>());
    static
    {
        mssqlMethods.put("ascii",new PassthroughMethod("ascii",JdbcType.INTEGER,1,1));
        Method chr = new PassthroughMethod("char",JdbcType.VARCHAR,1,1);
        mssqlMethods.put("char", chr);
        mssqlMethods.put("chr", chr);   // postgres and oracle use 'chr' (see 15473)
        mssqlMethods.put("charindex",new PassthroughMethod("charindex",JdbcType.INTEGER,2,3));
        mssqlMethods.put("concat_ws", new PassthroughMethod("concat_ws", JdbcType.VARCHAR, 1, Integer.MAX_VALUE));
        mssqlMethods.put("difference",new PassthroughMethod("difference",JdbcType.INTEGER,2,2));
        mssqlMethods.put("isnumeric",new PassthroughMethod("isnumeric",JdbcType.BOOLEAN,1,1));
        mssqlMethods.put("len",new PassthroughMethod("len",JdbcType.INTEGER,1,1));
        mssqlMethods.put("patindex",new PassthroughMethod("patindex",JdbcType.INTEGER,2,2));
        mssqlMethods.put("quotename",new PassthroughMethod("quotename",JdbcType.VARCHAR,1,2));
        mssqlMethods.put("replace",new PassthroughMethod("replace",JdbcType.VARCHAR,3,3));
        mssqlMethods.put("replicate",new PassthroughMethod("replicate",JdbcType.VARCHAR,2,2));
        mssqlMethods.put("reverse",new PassthroughMethod("reverse",JdbcType.VARCHAR,1,1));
        mssqlMethods.put("right",new PassthroughMethod("right",JdbcType.VARCHAR,2,2));
        mssqlMethods.put("soundex",new PassthroughMethod("soundex",JdbcType.VARCHAR,1,1));
        mssqlMethods.put("space",new PassthroughMethod("space",JdbcType.VARCHAR,1,1));
        mssqlMethods.put("str",new PassthroughMethod("str",JdbcType.VARCHAR,1,3));
        mssqlMethods.put("stuff",new PassthroughMethod("stuff",JdbcType.VARCHAR,4,4));
        mssqlMethods.put("ucase", new PassthroughMethod("upper", JdbcType.VARCHAR, 1, 1));
        mssqlMethods.put("upper", new PassthroughMethod("upper", JdbcType.VARCHAR, 1, 1));
    }

    final static Map<String, Method> oracleMethods = Collections.synchronizedMap(new CaseInsensitiveHashMap<>());
    static
    {
/*  Standard Oracle Functions
    See: http://download.oracle.com/docs/cd/E11882_01/server.112/e17118/functions002.htm#CJAIBHGG*/

        // Numeric Functions - Haven't put advanced mathematical functions in. Can add in later if the demand is there.

        oracleMethods.put("to_number", new PassthroughMethod("to_number", JdbcType.DECIMAL, 1,3));

        // Character Functions returning Character Values

        oracleMethods.put("to_char", new PassthroughMethod("to_char", JdbcType.VARCHAR, 1,3));
        oracleMethods.put("substr", new PassthroughMethod("substr", JdbcType.VARCHAR, 2,3));
        oracleMethods.put("trim", new PassthroughMethod("trim", JdbcType.VARCHAR, 1,1));
        oracleMethods.put("instr", new PassthroughMethod("instr", JdbcType.VARCHAR, 2,4));
        oracleMethods.put("replace", new PassthroughMethod("replace", JdbcType.VARCHAR, 2,3));
        oracleMethods.put("translate", new PassthroughMethod("translate", JdbcType.VARCHAR, 3,3));
        oracleMethods.put("rpad", new PassthroughMethod("rpad", JdbcType.VARCHAR, 2,3));
        oracleMethods.put("lpad", new PassthroughMethod("lpad", JdbcType.VARCHAR, 2,3));
        oracleMethods.put("ascii", new PassthroughMethod("ascii", JdbcType.INTEGER, 1,1));
        oracleMethods.put("initcap", new PassthroughMethod("initcap", JdbcType.VARCHAR, 1,1));
        oracleMethods.put("chr", new PassthroughMethod("chr", JdbcType.VARCHAR, 1,1));
        oracleMethods.put("regexp_like", new PassthroughMethod("regexp_like", JdbcType.VARCHAR, 2,2));

            // Date Functions

        oracleMethods.put("to_date", new PassthroughMethod("to_date", JdbcType.DATE, 1,3));
        oracleMethods.put("sysdate", new PassthroughMethod("sysdate", JdbcType.DATE, 0,0));
    }

    private static class ParseJSONMethod extends Method
    {
        private final String _targetType;

        ParseJSONMethod(String targetType)
        {
            super(JdbcType.OTHER, 1, 1);
            _targetType = targetType;
        }

        @Override
        public MethodInfo getMethodInfo()
        {
            return new AbstractMethodInfo(JdbcType.OTHER)
            {
                @Override
                public SQLFragment getSQL(SqlDialect dialect, SQLFragment[] arguments)
                {
                    return new SQLFragment("(").append(arguments[0]).append(")::").append(_targetType);
                }
            };
        }
    }
}
