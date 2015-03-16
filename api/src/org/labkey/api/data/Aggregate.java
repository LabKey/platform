/*
 * Copyright (c) 2006-2015 LabKey Corporation
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
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.query.CustomViewInfo;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.URLHelper;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.PropertyValues;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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
    private static FieldKey STAR_FIELDKEY = FieldKey.fromParts(STAR);

    public enum Type
    {
        SUM("Total")
                {
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
        AVG("Average")
                {
                    @Override
                    public JdbcType returnType(JdbcType jdbcType)
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
                },
        COUNT("Count")
                {
                    @Override
                    public JdbcType returnType(JdbcType jdbcType)
                    {
                        return JdbcType.BIGINT;
                    }
                },
        MIN("Minimum")
                {
                    @Override
                    public JdbcType returnType(JdbcType jdbcType)
                    {
                        if (jdbcType.isNumeric() || jdbcType.isDateOrTime() || jdbcType.isText() || jdbcType.equals(JdbcType.BINARY))
                            return jdbcType;

                        return null;
                    }
                },
        MAX("Maximum")
                {
                    @Override
                    public JdbcType returnType(JdbcType jdbcType)
                    {
                        if (jdbcType.isNumeric() || jdbcType.isDateOrTime() || jdbcType.isText() || jdbcType.equals(JdbcType.BINARY))
                            return jdbcType;

                        return null;
                    }
                };

        private String _friendlyName;

        private Type(String friendlyName)
        {
            _friendlyName = friendlyName;
        }

        public String getSQLColumnFragment(SqlDialect dialect, String columnName, String asName, @Nullable JdbcType jdbcType, boolean distinct)
        {
            if (jdbcType != null && !isLegal(jdbcType))
                return null;

            StringBuilder sb = new StringBuilder();
            sb.append(name()).append("(");
            if (distinct)
                sb.append("DISTINCT ");
            sb.append(dialect.getColumnSelectName(columnName));
            sb.append(") AS ").append(asName);
            return sb.toString();
        }

        public String getFriendlyName()
        {
            return _friendlyName;
        }

        /**
         * Return true if the jdbcType is a valid input type to this aggregate function.
         * @param jdbcType input type.
         * @return true if valid type.
         */
        public boolean isLegal(JdbcType jdbcType)
        {
            return returnType(jdbcType) != null;
        }

        /**
         * Get the return type of the aggregate function for the given
         * JdbcType or null if the type is not applicable (e.g. SUM of a date column).
         */
        public abstract JdbcType returnType(JdbcType jdbcType);
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
        Aggregate agg = new Aggregate();
        agg._fieldKey = STAR_FIELDKEY;
        agg._type = Type.COUNT;
        agg._distinct = false;
        return agg;
    }

    public boolean isCountStar()
    {
        return _fieldKey == STAR_FIELDKEY && _type == Type.COUNT;
    }

    public boolean isDistinct()
    {
        return _distinct;
    }

    public String toLabKeySQL()
    {
        StringBuilder sb = new StringBuilder();
        sb.append(_type.name()).append("(");
        if (_distinct)
            sb.append("DISTINCT ");
        sb.append(getFieldKey().toSQLString());
        String alias = _label == null ? getAggregateName(getFieldKey().toString()) : _label;
        sb.append(") AS \"").append(alias.replace("\"", "\\\"")).append("\"");
        return sb.toString();

    }

    @Nullable
    public String getSQL(SqlDialect dialect, Map<FieldKey, ? extends ColumnInfo> columns)
    {
        ColumnInfo col = columns.get(getFieldKey());
        String alias = getAliasName(col);
        String aggregateColumnName = getAggregateName(alias);
        JdbcType jdbcType = col == null ? null : col.getJdbcType();

        return _type.getSQLColumnFragment(dialect, alias, aggregateColumnName, jdbcType, _distinct);
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
            return _type.name() + (_distinct ? "Distinct" : "") + alias;
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
        sb.append(getType().getFriendlyName());
        return sb.toString();
    }

    // CONSIDER: Use Results instead of ResultSet -- it includes the selected column map
    public Result getResult(ResultSet rs, Map<FieldKey, ? extends ColumnInfo> columns) throws SQLException
    {
        ColumnInfo col = columns.get(getFieldKey());
        if (col != null && !_type.isLegal(col.getJdbcType()))
            return new Result(this, null);

        String aggregateColumnName = getAggregateName(getAliasName(col));

        Object o;
        JdbcType returnType = col == null ? null : _type.returnType(col.getJdbcType());
        Table.Getter getter = returnType == null ? null : Table.Getter.forClass(returnType.getJavaClass());
        if (getter != null)
            o = getter.getObject(rs, aggregateColumnName);
        else
        {
            o = rs.getObject(aggregateColumnName);
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
        List<Aggregate> aggregates = new LinkedList<>();
        String prefix = regionName + "." + CustomViewInfo.AGGREGATE_PARAM_PREFIX + ".";

        for (PropertyValue val : pvs.getPropertyValues())
        {
            if (val.getName().startsWith(prefix))
            {
                FieldKey fieldKey = FieldKey.fromString(val.getName().substring(prefix.length()));

                List<String> values = new ArrayList<>();

                if (val.getValue() instanceof String)
                    values.add((String) val.getValue());
                else
                    Collections.addAll(values, (String[]) val.getValue());

                for(String s : values)
                {
                    Aggregate a = decodeAggregate(fieldKey, s);
                    if(a != null)
                        aggregates.add(a);
                }
            }
        }

        return aggregates;
    }

    private static Aggregate decodeAggregate(FieldKey fieldKey, String value)
    {
        try
        {
            value = PageFlowUtil.decode(value);

            Map<String, String> properties = new HashMap<>();
            //allow aggregates either in the basic form, ie. query.agg.columnName=MAX, or more complex, ie:
            //query.agg.columnName=type%3BMAX
            if(!value.contains("="))
            {
                properties.put("type", value);
            }
            else
            {
                for (Pair<String, String> entry : PageFlowUtil.fromQueryString(PageFlowUtil.decode(value)))
                {
                    properties.put(entry.getKey().toLowerCase(), entry.getValue());
                }
            }

            Aggregate.Type type = Aggregate.Type.valueOf(properties.get("type").toUpperCase());

            String label = null;
            if (properties.containsKey("label"))
                label = properties.get("label");

            return new Aggregate(fieldKey, type, label);
        }
        catch (IllegalArgumentException e)
        {
            throw new IllegalArgumentException("'" + value + "' is not a valid aggregate type.");
        }
    }

    public String getValueForUrl()
    {
        if (getLabel() == null)
            return getType().name();

        StringBuilder ret = new StringBuilder();

        ret.append(PageFlowUtil.encode("label=" + getLabel()));
        ret.append(PageFlowUtil.encode("&type=" + getType().name()));

        return ret.toString();
    }

    /**
     * Add the aggregate parameter on the url
     * @param url The url to be modified.
     * @param regionName The dataRegion used to scope the sort.
     * @param fieldKey The fieldKey to use in the url parameter
     */
    public void applyToURL(URLHelper url, String regionName, FieldKey fieldKey)
    {
        url.addParameter(regionName + "." + CustomViewInfo.AGGREGATE_PARAM_PREFIX + "." + fieldKey.toString(), getValueForUrl());
    }

    public static final class TestCase extends Assert
    {
        @Test
        public void validAggregateTypes()
        {
            assertAggregate(false, Type.SUM, JdbcType.DATE);
            assertAggregate(false, Type.SUM, JdbcType.VARCHAR);
            assertAggregate(false, Type.SUM, JdbcType.BOOLEAN);
            assertAggregate(true,  Type.SUM, JdbcType.INTEGER);

            assertAggregate(false, Type.AVG, JdbcType.DATE);
            assertAggregate(false, Type.AVG, JdbcType.VARCHAR);
            assertAggregate(false, Type.AVG, JdbcType.BOOLEAN);
            assertAggregate(true,  Type.AVG, JdbcType.INTEGER);

            assertAggregate(true,  Type.COUNT, JdbcType.DATE);
            assertAggregate(true,  Type.COUNT, JdbcType.VARCHAR);
            assertAggregate(true,  Type.COUNT, JdbcType.BOOLEAN);
            assertAggregate(true,  Type.COUNT, JdbcType.INTEGER);

            assertAggregate(true,  Type.MIN, JdbcType.DATE);
            assertAggregate(true,  Type.MIN, JdbcType.VARCHAR);
            assertAggregate(false, Type.MIN, JdbcType.BOOLEAN);
            assertAggregate(true,  Type.MIN, JdbcType.INTEGER);

            assertAggregate(true,  Type.MAX, JdbcType.DATE);
            assertAggregate(true,  Type.MAX, JdbcType.VARCHAR);
            assertAggregate(false, Type.MAX, JdbcType.BOOLEAN);
            assertAggregate(true,  Type.MAX, JdbcType.INTEGER);

        }

        private void assertAggregate(boolean legal, Type type, JdbcType jdbcType)
        {
            assertEquals("Expected " + type.getFriendlyName() + " to be " + (legal ? "legal" : "illegal") + " for JDBC type " + jdbcType.name(),
                    legal, type.isLegal(jdbcType));
        }

    }
}
