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

package org.labkey.api.data;

import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;
import org.labkey.api.data.SimpleFilter.ColumnNameFormatter;
import org.labkey.api.data.SimpleFilter.FilterClause;
import org.labkey.api.util.DateUtil;

import java.sql.Types;
import java.util.*;

/**
 * User: brittp
 * Date: Oct 10, 2006
 * Time: 4:44:45 PM
 */
public enum CompareType
{
    EQUAL("Equals", "eq", true, " = ?", "EQUALS"),
    DATE_EQUAL("Equals", "dateeq", true, null, "DATE_EQUAL")
        {
            public CompareClause createFilterClause(String colName, Object value)
            {
                return getDateCompareClause(colName, value);
            }
        },
    DATE_NOT_EQUAL("Does Not Equal", "dateneq", true, " <> ?", "DATE_NOT_EQUAL")
        {
            public CompareClause createFilterClause(String colName, Object value)
            {
                return getDateCompareClause(colName, value);
            }
        },
    NEQ_OR_NULL("Does Not Equal", "neqornull", true, " <> ?", "NOT_EQUAL_OR_NULL")
        {
            public CompareClause createFilterClause(String colName, Object value)
            {
                return new NotEqualOrNullClause(colName, value);
            }
        },
    NEQ("Does Not Equal", "neq", true, " <> ?", "NOT_EQUALS"),
    ISBLANK("Is Blank", "isblank", false, " IS NULL", "IS_NULL")
        {
            public FilterClause createFilterClause(String colName, Object value)
            {
                return super.createFilterClause(colName, null);
            }
        },
    NONBLANK("Is Not Blank", "isnonblank", false, " IS NOT NULL", "IS_NOT_NULL")
        {
            public FilterClause createFilterClause(String colName, Object value)
            {
                return super.createFilterClause(colName, null);
            }
        },
    GT("Is Greater Than", "gt", true, " > ?", "GREATER_THAN"),
    LT("Is Less Than", "lt", true, " < ?", "LESS_THAN"),
    GTE("Is Greater Than or Equal To", "gte", true, " >= ?", "GREATER_THAN_OR_EQUAL_TO"),
    LTE("Is Less Than or Equal To", "lte", true, " <= ?", "LESS_THAN_OR_EQUAL_TO"),
    CONTAINS("Contains", "contains", true, null, "CONTAINS")
            {
                public CompareClause createFilterClause(String colName, Object value)
                {
                    return new ContainsClause(colName, value);
                }
            },
    DOES_NOT_CONTAIN("Does Not Contain", "doesnotcontain", true, null, "DOES_NOT_CONTAIN")
            {
                public CompareClause createFilterClause(String colName, Object value)
                {
                    return new DoesNotContainClause(colName, value);
                }
            },
    DOES_NOT_START_WITH("Does Not Start With", "doesnotstartwith", true, null, "DOES_NOT_START_WITH")
            {
                public CompareClause createFilterClause(String colName, Object value)
                {
                    return new DoesNotStartWithClause(colName, value);
                }
            },
    STARTS_WITH("Starts With", "startswith", true, null, "STARTS_WITH")
            {
                public CompareClause createFilterClause(String colName, Object value)
                {
                    return new StartsWithClause(colName, value);
                }
            },
    IN("Equals One Of (e.g. 'a;b;c')", "in", true, null, "EQUALS_ONE_OF")
            {
                // Each compare type uses CompareClause by default
                FilterClause createFilterClause(String colName, Object value)
                {
                    List<String> values = new ArrayList<String>();
                    if (value != null && !value.toString().trim().equals(""))
                    {
                        StringTokenizer st = new StringTokenizer(value.toString(), ";", false);
                        while (st.hasMoreTokens())
                        {
                            String token = st.nextToken().trim();
                            values.add(token);
                        }
                    }
                    return new SimpleFilter.InClause(colName, values, true);
                }
            };


    // TODO: Better exception handling?
    protected DateEqCompareClause getDateCompareClause(String colName, Object value)
    {
        //If only date (no time) is specified in a DATE_EQUAL clause
        //return anything that happened during that day.
        Date dt = (Date) ConvertUtils.convert((String)value, Date.class);

        return new DateEqCompareClause(colName, this, dt);
    }

    private String _urlKey;
    private String _displayValue;
    private boolean _dataValueRequired;
    private String _sql;
    private String _rName;

    CompareType(String displayValue, String urlKey, boolean dataValueRequired, String sql, String rName)
    {
        _urlKey = urlKey;
        _displayValue = displayValue;
        _dataValueRequired = dataValueRequired;
        _sql = sql;
        _rName = rName;
    }

    public static List<CompareType> getValidCompareSet(ColumnInfo info)
    {
        List<CompareType> types = new ArrayList<CompareType>();
        if (!info.isLongTextType())
        {
            types.add(info.isDateTimeType() ? DATE_EQUAL : EQUAL);
            types.add(info.isDateTimeType() ? DATE_NOT_EQUAL : NEQ);
        }
        if (info.isNullable())
        {
            types.add(ISBLANK);
            types.add(NONBLANK);
        }
        if (!info.isLongTextType() && !info.isBooleanType())
        {
            types.add(GT);
            types.add(LT);
            types.add(GTE);
            types.add(LTE);
        }
        if (!info.isBooleanType() && !info.isDateTimeType())
        {
            types.add(STARTS_WITH);
            types.add(CONTAINS);
        }
        return types;
    }

    public static CompareType getByURLKey(String urlKey)
    {
        for (CompareType type : values())
        {
            if (type.getUrlKey().equals(urlKey))
                return type;
        }
        return null;
    }

    public String getDisplayValue()
    {
        return _displayValue;
    }

    public String getUrlKey()
    {
        return _urlKey;
    }

    public boolean isDataValueRequired()
    {
        return _dataValueRequired;
    }

    public String getSql()
    {
        return _sql;
    }

    public String getRName()
    {
        return _rName;
    }

    // Each compare type uses CompareClause by default
    FilterClause createFilterClause(String colName, Object value)
    {
        return new CompareClause(colName, this, value);
    }

    public static class CompareClause extends FilterClause
    {
        String _colName;
        CompareType _comparison;


        public CompareClause(String colName, CompareType comparison, Object value)
        {
            _colName = colName;
            _comparison = comparison;

            if (null == value)
                setParamVals(null);
            else
                setParamVals(new Object[]{value});
        }

        String toWhereClause(SqlDialect dialect, String alias)
        {
            return dialect.getColumnSelectName(alias) + _comparison.getSql();
        }

        @Override
        protected void appendSqlText(StringBuilder sb, ColumnNameFormatter formatter)
        {
            appendColumnName(sb, formatter);
            sb.append(_comparison.getSql());
        }

        protected void appendColumnName(StringBuilder sb, ColumnNameFormatter formatter)
        {
            sb.append(formatter.format(_colName));
        }

        public List<String> getColumnNames()
        {
            return Arrays.asList(_colName);
        }


        public SQLFragment toSQLFragment(Map<String, ? extends ColumnInfo> columnMap, SqlDialect dialect)
        {
            String alias = dialect.getColumnSelectName(_colName);
            ColumnInfo colInfo = columnMap.get(_colName);
            if (colInfo != null)
            {
                alias = colInfo.getAlias();
            }
            SQLFragment fragment = new SQLFragment(toWhereClause(dialect, alias));
            if (colInfo == null || !isUrlClause() || getParamVals() == null)
            {
                fragment.addAll(getParamVals());
            }
            else
            {
                for (Object paramVal : getParamVals())
                {
                    fragment.add(convertParamValue(colInfo, paramVal));
                }
            }
            return fragment;
        }

        public CompareType getComparison()
        {
            return _comparison;
        }
    }

    // Converts parameter value to the proper type based on the SQL type of the ColumnInfo
    public static Object convertParamValue(ColumnInfo colInfo, Object paramVal)
    {
        if (!(paramVal instanceof String))
            return paramVal;

        switch (colInfo.getSqlTypeInt())
        {
            case Types.INTEGER:
            case Types.TINYINT:
            case Types.SMALLINT:
            {
                try
                {
                    return new Integer((String) paramVal);
                }
                catch (NumberFormatException e)
                {
                    // fall through
                }
            }

            case Types.BIGINT:
            {
                try
                {
                    return new Long((String) paramVal);
                }
                catch (NumberFormatException e)
                {
                    //fall through
                }
            }

            case Types.BOOLEAN:
            case Types.BIT:
            {
                try
                {
                    return ConvertUtils.convert((String) paramVal, Boolean.class);
                }
                catch (Exception e)
                {
                    //Just leave as string...
                }
            }
            break;

            case Types.TIMESTAMP:
            case Types.DATE:
            case Types.TIME:
                return ConvertUtils.convert((String) paramVal, Date.class);

            //FALL THROUGH! (Decimal is better than nothing)
            case Types.DECIMAL:
            case Types.NUMERIC:
            case Types.REAL:
            case Types.FLOAT:
            case Types.DOUBLE:
            {
                try
                {
                    return new Double((String) paramVal);
                }
                catch (NumberFormatException e)
                {
                    // Just keep it as a string
                }
            }
            break;
        }

        return paramVal;
    }


    /**
     * Compare clause for dateeq.
     * If supplied date has no time component return anything during that day
     */
    private static class DateEqCompareClause extends CompareClause
    {
        DateEqCompareClause(String colName, CompareType compareClause, Date value)
        {
            super(colName, compareClause, value);
            Date endDate;
            Calendar cal = Calendar.getInstance();
            cal.setTime(value);
            cal.clear(Calendar.SECOND);
            cal.clear(Calendar.MILLISECOND);
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.clear(Calendar.MINUTE);
            if (cal.getTime().equals(value))
            {
                cal.add(Calendar.DAY_OF_MONTH, 1);
                endDate = cal.getTime();
                setParamVals(new Object[]{value, endDate});
            }
            else
            {
                _comparison = compareClause == CompareType.DATE_EQUAL ? CompareType.EQUAL : CompareType.NEQ;
                setParamVals(new Object[]{value});
            }
        }

        @Override
        String toWhereClause(SqlDialect dialect, String alias)
        {
            String selectName = dialect.getColumnSelectName(alias);
            if (_comparison == CompareType.DATE_EQUAL)
                return selectName + " >= ? AND " + selectName + " < ?";
            else if (_comparison == CompareType.DATE_NOT_EQUAL)
                return selectName + " < ? OR " + selectName + " >= ?";
            else
                return super.toWhereClause(dialect, alias);
        }

        @Override
        protected void appendFilterText(StringBuilder sb, ColumnNameFormatter formatter)
        {
            appendColumnName(sb, formatter);
            sb.append(_comparison == CompareType.DATE_EQUAL ? " = " : " <> ");
            sb.append(DateUtil.formatDate((Date)getParamVals()[0]));
        }
    }

    abstract private static class LikeClause extends CompareClause
    {
        static final private char[] charsToBeEscaped = new char[] { '%', '_', '[' };
        static final private char escapeChar = '!';
        protected LikeClause(String colName, CompareType compareType, Object value)
        {
            super(colName, compareType, escapeLikePattern(ObjectUtils.toString(value)));
        }

        static private String escapeLikePattern(String value)
        {
            String strEscape = new String(new char[] { escapeChar } );
            value = StringUtils.replace(value, strEscape, strEscape + strEscape);
            for (char ch : charsToBeEscaped)
            {
                if (ch == escapeChar)
                    continue;
                String strCh = new String(new char[] { ch});
                value = StringUtils.replace(value, strCh, strEscape + strCh);
            }
            return value;
        }

        protected String sqlEscape()
        {
            assert escapeChar != '\'';
            return " ESCAPE '" + escapeChar + "' ";
        }

        @Override
        protected void appendSqlText(StringBuilder sb, ColumnNameFormatter formatter)
        {
            appendColumnName(sb, formatter);
            sb.append(" LIKE ?");
        }

        abstract String toWhereClause(SqlDialect dialect, String alias);
    }

    private static class StartsWithClause extends LikeClause
    {
        public StartsWithClause(String colName, Object value)
        {
            super(colName, CompareType.STARTS_WITH, value);
        }

        String toWhereClause(SqlDialect dialect, String alias)
        {
            return dialect.getColumnSelectName(alias) + " " + dialect.getCaseInsensitiveLikeOperator() + " ? " + dialect.getConcatenationOperator() + " '%' " + sqlEscape();
        }

        @Override
        protected void appendSqlText(StringBuilder sb, ColumnNameFormatter formatter)
        {
            appendColumnName(sb, formatter);
            sb.append(" STARTS WITH ?");
        }
    }

    private static class DoesNotStartWithClause extends LikeClause
    {
        public DoesNotStartWithClause(String colName, Object value)
        {
            super(colName, CompareType.DOES_NOT_START_WITH, value);
        }

        String toWhereClause(SqlDialect dialect, String alias)
        {
            return dialect.getColumnSelectName(alias) + " NOT " + dialect.getCaseInsensitiveLikeOperator() + " ? " + dialect.getConcatenationOperator() + " '%' " + sqlEscape();
        }

        @Override
        protected void appendSqlText(StringBuilder sb, ColumnNameFormatter formatter)
        {
            appendColumnName(sb, formatter);
            sb.append(" DOES NOT START WITH ?");
        }
    }

    public static class ContainsClause extends LikeClause
    {
        public ContainsClause(String colName, Object value)
        {
            super(colName, CompareType.CONTAINS, value);
        }

        String toWhereClause(SqlDialect dialect, String alias)
        {
            return dialect.getColumnSelectName(alias) + " " + dialect.getCaseInsensitiveLikeOperator() + " '%' " + dialect.getConcatenationOperator() + " ? " + dialect.getConcatenationOperator() + " '%' " + sqlEscape(); 
        }

        @Override
        protected void appendSqlText(StringBuilder sb, ColumnNameFormatter formatter)
        {
            appendColumnName(sb, formatter);
            sb.append(" CONTAINS ?");
        }
    }

    public static class DoesNotContainClause extends LikeClause
    {
        public DoesNotContainClause(String colName, Object value)
        {
            super(colName, CompareType.DOES_NOT_CONTAIN, value);
        }
        String toWhereClause(SqlDialect dialect, String alias)
        {
            return dialect.getColumnSelectName(alias) + " NOT " + dialect.getCaseInsensitiveLikeOperator() + " '%' " + dialect.getConcatenationOperator() + " ? " + dialect.getConcatenationOperator() + " '%' " + sqlEscape(); 
        }

        @Override
        protected void appendSqlText(StringBuilder sb, ColumnNameFormatter formatter)
        {
            appendColumnName(sb, formatter);
            sb.append(" DOES NOT CONTAIN ?");
        }
    }

    private static class NotEqualOrNullClause extends CompareClause
    {
        NotEqualOrNullClause(String colName, Object value)
        {
            super(colName, CompareType.NEQ_OR_NULL, value);
        }

        @Override
        String toWhereClause(SqlDialect dialect, String alias)
        {
            String neq = CompareType.NEQ.getSql();
            String isNull = CompareType.ISBLANK.getSql();
            return "(" + dialect.getColumnSelectName(alias) + neq + " OR " + dialect.getColumnSelectName(alias) + isNull + ")";
        }
    }
}