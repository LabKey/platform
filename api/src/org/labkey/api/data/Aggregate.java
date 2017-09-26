/*
 * Copyright (c) 2006-2017 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.stats.BaseAggregatesAnalyticsProvider;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.Formats;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.Format;
import java.util.Date;
import java.util.Map;

/**
 * Configuration to track that a column should be displayed with summary information about its content, such as
 * its maximum value or the sum of all of its values.
 * User: brittp
 * Date: Aug 6, 2006
 */
public class Aggregate
{
    public static String STAR = "*";
    private static FieldKey STAR_FIELDKEY = FieldKey.fromParts(STAR);
    private static double EPSILON = 0.00001;

    public interface Type
    {
        String getName();
        String getFullLabel();
        String getDisplayValue();

        @Nullable
        default String getDescription()
        {
            return null;
        }

        default String getAliasName()
        {
            return getSQLFunctionName(null);
        }

        default String getSQLFunctionName(@Nullable SqlDialect dialect)
        {
            return getName();
        }

        /**
         * Get the generated SQL for calculating this aggregate (i.e. summary stat) value based on a TableInfo column.
         * @param dialect SQL dialect for the running server
         * @param columnName The name of the column to use in the SQL select
         * @param asName The alias to use in the SQL select
         * @param jdbcType The column type
         * @param distinct Boolean indicating if this SQL should use 'DISTINCT' in the generated query
         * @param tableInnerSql SQLFragment for the FROM clause in the case where the aggregate needs to do a "subselect"
         * @return String generated SQL
         */
        default String getSQLColumnFragment(SqlDialect dialect, String columnName, String asName, @Nullable JdbcType jdbcType, boolean distinct, SQLFragment tableInnerSql)
        {
            if (jdbcType != null && !isLegal(jdbcType))
                return null;

            StringBuilder sb = new StringBuilder();
            sb.append(getSQLFunctionName(dialect)).append("(");
            if (distinct)
                sb.append("DISTINCT ");
            sb.append(dialect.getColumnSelectName(columnName));
            sb.append(") AS ").append(asName);
            return sb.toString();
        }

        /**
         * Return true if the jdbcType is a valid input type to this aggregate (i.e. summary stat) function.
         * @param jdbcType input type.
         * @return true if valid type.
         */
        default boolean isLegal(JdbcType jdbcType)
        {
            return returnType(jdbcType) != null;
        }

        static JdbcType returnTypeDecimal(JdbcType jdbcType)
        {
            switch (jdbcType)
            {
                case BIGINT:   return JdbcType.DECIMAL;
                case DECIMAL:  return JdbcType.DECIMAL;
                case DOUBLE:   return JdbcType.DECIMAL;
                case REAL:     return JdbcType.DECIMAL;

                case INTEGER:  return JdbcType.DECIMAL;
                case SMALLINT: return JdbcType.DECIMAL;

                default:       return null;
            }
        }

        /**
         * Get the return type of the aggregate (i.e. summary stat) function for the given
         * JdbcType or null if the type is not applicable (e.g. SUM of a date column).
         */
        JdbcType returnType(JdbcType jdbcType);

        /**
         * If aggregates use subqueries, we'll need to add the set of all tableInnerSql parameters for each subquery
         * @param dialect the use of a subquery may be dialect-specific
         * @return the number of subqueries the aggregate uses
         */
        default int subQueryCount(SqlDialect dialect) { return 0; }
    }

    public enum BaseType implements Type
    {
        SUM("Sum")
                {
                    public String getSQLColumnFragment(SqlDialect dialect, String columnName, String asName, @Nullable JdbcType jdbcType, boolean distinct, SQLFragment tableInnerSql)
                    {
                        if (jdbcType != null && !isLegal(jdbcType))
                            return null;

                        if (jdbcType != null)
                        {
                            StringBuilder sb = new StringBuilder();
                            sb.append("SUM(");
                            if (distinct)
                                sb.append("DISTINCT ");
                            if (dialect.isSqlServer() && castType(jdbcType) != null)
                            {
                                sb.append("CAST(").append(dialect.getColumnSelectName(columnName)).append(" AS ")
                                        .append(castType(jdbcType)).append(")");
                            }
                            else
                            {
                                sb.append(dialect.getColumnSelectName(columnName));
                            }
                            sb.append(") AS ").append(asName);
                            return sb.toString();
                        }
                        else
                        {
                            return super.getSQLColumnFragment(dialect, columnName, asName, null, distinct, tableInnerSql);
                        }
                    }

                    /**
                     * Upcast to the wider datatypes to avoid overflows in the database. Postgres does this implicitly, but SQL Server does not
                     * @param jdbcType type of column
                     * @return cast datatype, if a wider datatype is available
                     */
                    private String castType(JdbcType jdbcType)
                    {
                        switch (jdbcType)
                        {
                            case BIGINT:
                            case INTEGER:
                            case SMALLINT:
                                return JdbcType.BIGINT.toString();
                            case DECIMAL:
                            case DOUBLE:
                            case REAL:
                                return "FLOAT";
                            default:
                                return null;
                        }
                    }

                    @Override
                    public JdbcType returnType(JdbcType jdbcType)
                    {
                        switch (jdbcType)
                        {
                            case BIGINT:   return JdbcType.DECIMAL;
                            case DECIMAL:  return JdbcType.DECIMAL;
                            case DOUBLE:   return JdbcType.DECIMAL;
                            case REAL:     return JdbcType.DECIMAL;

                            case INTEGER:  return JdbcType.BIGINT;
                            case SMALLINT: return JdbcType.BIGINT;

                            default:       return null;
                        }
                    }
                },
        MEAN("Mean")
                {
                    @Override
                    public String getSQLFunctionName(@Nullable SqlDialect dialect)
                    {
                        return "AVG";
                    }

                    @Override
                    public String getSQLColumnFragment(SqlDialect dialect, String columnName, String asName, @Nullable JdbcType jdbcType, boolean distinct, SQLFragment tableInnerSql)
                    {
                        if (jdbcType != null && !isLegal(jdbcType))
                            return null;

                        // special case for casting INTEGER to FLOAT
                        if (jdbcType != null && (jdbcType.equals(JdbcType.INTEGER) || jdbcType.equals(JdbcType.BIGINT) || jdbcType.equals(JdbcType.SMALLINT)))
                        {
                            StringBuilder sb = new StringBuilder();
                            sb.append(getSQLFunctionName(dialect)).append("(");
                            if (distinct)
                                sb.append("DISTINCT ");
                            sb.append("CAST(").append(dialect.getColumnSelectName(columnName)).append(" AS FLOAT)");
                            sb.append(") AS ").append(asName);
                            return sb.toString();
                        }
                        else
                        {
                            return super.getSQLColumnFragment(dialect, columnName, asName, jdbcType, distinct, tableInnerSql);
                        }
                    }

                    @Override
                    public JdbcType returnType(JdbcType jdbcType)
                    {
                        return Type.returnTypeDecimal(jdbcType);
                    }
                },
        COUNT("Count (non-blank)")
                {
                    @Nullable
                    @Override
                    public String getDescription()
                    {
                        return "Count of the number of values in the set that have a non-blank value (i.e. value is not NULL).";
                    }

                    @Override
                    public JdbcType returnType(JdbcType jdbcType)
                    {
                        return JdbcType.BIGINT;
                    }
                },
        MIN("Minimum", "Min")
                {
                    @Override
                    public JdbcType returnType(JdbcType jdbcType)
                    {
                        if (jdbcType.isNumeric() || jdbcType.isDateOrTime() || jdbcType.isText() || jdbcType.equals(JdbcType.BINARY))
                            return jdbcType;

                        return null;
                    }
                },
        MAX("Maximum", "Max")
                {
                    @Override
                    public JdbcType returnType(JdbcType jdbcType)
                    {
                        if (jdbcType.isNumeric() || jdbcType.isDateOrTime() || jdbcType.isText() || jdbcType.equals(JdbcType.BINARY))
                            return jdbcType;

                        return null;
                    }
                };

        private String _fullLabel;
        private String _displayValue;

        BaseType(String fullLabel)
        {
            this(fullLabel, fullLabel);
        }

        BaseType(String fullLabel, String displayValue)
        {
            _fullLabel = fullLabel;
            _displayValue = displayValue;
        }

        public String getName()
        {
            return  name();
        }

        public String getFullLabel()
        {
            return _fullLabel;
        }

        public String getDisplayValue()
        {
            return _displayValue;
        }
    }

    public static class Result
    {
        private final Aggregate _aggregate;
        private final Object _value;

        public Result(Aggregate aggregate, Object value)
        {
            _aggregate = aggregate;
            _value = value;
        }

        public Aggregate getAggregate()
        {
            return _aggregate;
        }

        /** Returns null for invalid aggregates or valid aggregates with no data (e.g., SUM over no rows). */
        @Nullable
        public Object getValue()
        {
            return _value;
        }

        @Override
        public String toString()
        {
            return _aggregate.getDisplayString() + ": " + _value;
        }

        public String getFormattedValue(DisplayColumn renderer, Container container)
        {
            // Issue 16570: Formatter is only applicable if the aggregate return type is
            // similar to the input jdbcType.  For example, don't apply a date format
            // to COUNT aggregates but do apply a string or double format to a MIN/MAX aggregate.
            Format formatter = renderer.getFormat();
            ColumnInfo col = renderer.getColumnInfo();
            Aggregate.Type type = getAggregate().getType();

            JdbcType inputType = col.getJdbcType();
            JdbcType returnType = type.returnType(inputType);
            if (type.isLegal(inputType))
            {
                Object value = getValue();

                // Issue 29012: convert very small double values to zero for display
                if (value instanceof Double)
                {
                    double absValue = Math.abs(((Double) value).doubleValue());
                    if (absValue > 0 && absValue < EPSILON)
                        value = 0;
                }

                if (value == null)
                {
                    // no values to aggregate
                    return "n/a";
                }
                else if (formatter != null &&
                        (inputType == returnType ||
                                (inputType.isInteger() && returnType.isInteger()) ||
                                (inputType.isReal() && returnType.isReal())))
                {
                    return formatter.format(value);
                }
                else if (inputType.isNumeric())
                {
                    return Formats.commaf3.format(value);
                }
                else if (returnType.isDateOrTime())
                {
                    return DateUtil.formatDateInfer(container, (Date)value);
                }
                else
                {
                    return value.toString();
                }
            }

            return "<span class='labkey-error'>Not valid for type '" + col.getFriendlyTypeName() + "'</span>";
        }
    }

    private FieldKey _fieldKey;
    private Type _type;
    private @Nullable String _label;
    private boolean _distinct;

    private Aggregate()
    {
    }

    public Aggregate(ColumnInfo column, Aggregate.Type type)
    {
        this(column.getFieldKey(), type, null, false);
    }

    @Deprecated // Use FieldKey version instead.
    public Aggregate(String columnAlias, Aggregate.Type type)
    {
        this(FieldKey.fromString(columnAlias), type, null, false);
    }

    public Aggregate(FieldKey fieldKey, Aggregate.Type type)
    {
        this(fieldKey, type, null, false);
    }

    public Aggregate(FieldKey fieldKey, Aggregate.Type type, @Nullable String label)
    {
        this(fieldKey, type, label, false);
    }

    public Aggregate(FieldKey fieldKey, Aggregate.Type type, @Nullable String label, boolean distinct)
    {
        _fieldKey = fieldKey;
        _type = type;
        _label = label;
        _distinct = distinct;
    }

    public static Aggregate createCountStar()
    {
        return new Aggregate(STAR_FIELDKEY, BaseType.COUNT);
    }

    public boolean isCountStar()
    {
        return _fieldKey == STAR_FIELDKEY && _type == BaseType.COUNT;
    }

    public boolean isDistinct()
    {
        return _distinct;
    }

    public String toLabKeySQL(SQLFragment tableInnerSql)
    {
        String alias = _label == null ? getAggregateName(getFieldKey().toString()) : _label;
        alias = alias.replace("\"", "\\\"");

        // special case for those aggregate (i.e. summary stat) types that don't have a LabKey SQL function
        if (_type.getSQLFunctionName(null) == null)
            return _type.getSQLColumnFragment(null, getFieldKey().toSQLString(), alias, JdbcType.INTEGER, _distinct, tableInnerSql);

        StringBuilder sb = new StringBuilder();
        sb.append(_type.getSQLFunctionName(null)).append("(");
        if (_distinct)
            sb.append("DISTINCT ");
        sb.append(getFieldKey().toSQLString());
        sb.append(") AS \"").append(alias).append("\"");
        return sb.toString();
    }

    @Nullable
    public String getSQL(SqlDialect dialect, Map<FieldKey, ? extends ColumnInfo> columns, SQLFragment tableInnerSql)
    {
        ColumnInfo col = columns.get(getFieldKey());
        String alias = getAliasName(col);
        String aggColName = getAggregateName(alias);
        JdbcType jdbcType = col == null ? null : col.getJdbcType();

        return _type.getSQLColumnFragment(dialect, alias, aggColName, jdbcType, _distinct, tableInnerSql);
    }

    private String getAliasName(ColumnInfo col)
    {
        String alias = getColumnName();
        if (col != null)
            alias = col.getAlias();

        return alias;
    }

    private String getAggregateName(String alias)
    {
        if (isCountStar())
        {
            return "COUNT_STAR";
        }
        else
        {
            return _type.getAliasName() + (_distinct ? "Distinct" : "") + alias;
        }
    }

    public FieldKey getFieldKey()
    {
        return _fieldKey;
    }

    @Deprecated // Use getFieldKey() instead.
    public String getColumnName()
    {
        return _fieldKey.toString();
    }

    public String getName()
    {
        return BaseAggregatesAnalyticsProvider.PREFIX + getType().getName();
    }

    public Type getType()
    {
        return _type;
    }

    public @Nullable String getLabel()
    {
        return _label;
    }

    public void setLabel(@Nullable String label)
    {
        _label = label;
    }

    public String getDisplayString()
    {
        if (_label != null)
            return _label;

        StringBuilder sb = new StringBuilder();
        if (isDistinct())
            sb.append("Distinct ");
        sb.append(getType().getDisplayValue());
        return sb.toString();
    }

    // CONSIDER: Use Results instead of ResultSet -- it includes the selected column map
    public Result getResult(ResultSet rs, Map<FieldKey, ? extends ColumnInfo> columns) throws SQLException
    {
        ColumnInfo col = columns.get(getFieldKey());
        if (col != null && !_type.isLegal(col.getJdbcType()))
            return new Result(this, null);

        String aggColName = getAggregateName(getAliasName(col));

        Object o;
        JdbcType returnType = col == null ? null : _type.returnType(col.getJdbcType());
        Table.Getter getter = returnType == null ? null : Table.Getter.forClass(returnType.getJavaClass());
        if (getter != null)
            o = getter.getObject(rs, aggColName);
        else
        {
            o = rs.getObject(aggColName);
            // TODO: Handle BigDecimal values
            if (o instanceof Number)
            {
                double resultValue = ((Number)o).doubleValue();
                if (resultValue == Math.floor(resultValue))
                    o = new Long((long) resultValue);
                else
                    o = new Double(resultValue);
            }
        }

        return new Result(this, o);
    }

    public static final class TestCase extends Assert
    {
        @Test
        public void validAggregateTypes()
        {
            assertAggregate(false, BaseType.SUM, JdbcType.DATE);
            assertAggregate(false, BaseType.SUM, JdbcType.VARCHAR);
            assertAggregate(false, BaseType.SUM, JdbcType.BOOLEAN);
            assertAggregate(true,  BaseType.SUM, JdbcType.INTEGER);
            assertAggregate(true,  BaseType.SUM, JdbcType.DOUBLE);

            assertAggregate(false, BaseType.MEAN, JdbcType.DATE);
            assertAggregate(false, BaseType.MEAN, JdbcType.VARCHAR);
            assertAggregate(false, BaseType.MEAN, JdbcType.BOOLEAN);
            assertAggregate(true,  BaseType.MEAN, JdbcType.INTEGER);
            assertAggregate(true,  BaseType.MEAN, JdbcType.DOUBLE);

            assertAggregate(true,  BaseType.COUNT, JdbcType.DATE);
            assertAggregate(true,  BaseType.COUNT, JdbcType.VARCHAR);
            assertAggregate(true,  BaseType.COUNT, JdbcType.BOOLEAN);
            assertAggregate(true,  BaseType.COUNT, JdbcType.INTEGER);
            assertAggregate(true,  BaseType.COUNT, JdbcType.DOUBLE);

            assertAggregate(true,  BaseType.MIN, JdbcType.DATE);
            assertAggregate(true,  BaseType.MIN, JdbcType.VARCHAR);
            assertAggregate(false, BaseType.MIN, JdbcType.BOOLEAN);
            assertAggregate(true,  BaseType.MIN, JdbcType.INTEGER);
            assertAggregate(true,  BaseType.MIN, JdbcType.DOUBLE);

            assertAggregate(true,  BaseType.MAX, JdbcType.DATE);
            assertAggregate(true,  BaseType.MAX, JdbcType.VARCHAR);
            assertAggregate(false, BaseType.MAX, JdbcType.BOOLEAN);
            assertAggregate(true,  BaseType.MAX, JdbcType.INTEGER);
            assertAggregate(true,  BaseType.MAX, JdbcType.DOUBLE);

        }

        private void assertAggregate(boolean legal, Type type, JdbcType jdbcType)
        {
            assertEquals("Expected " + type.getDisplayValue() + " to be " + (legal ? "legal" : "illegal") + " for JDBC type " + jdbcType.name(),
                    legal, type.isLegal(jdbcType));
        }

    }
}
