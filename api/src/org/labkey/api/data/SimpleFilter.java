/*
 * Copyright (c) 2005-2017 Fred Hutchinson Cancer Research Center
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

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.CompareType.CompareClause;
import org.labkey.api.data.dialect.MockSqlDialect;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.TestContext;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.data.xml.queryCustomView.FilterType;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Representation of zero or more filters to be used with a database query after being translated to a WHERE clause.
 * User: arauch
 * Date: Jan 11, 2005
 */
public class SimpleFilter implements Filter
{
    public static final String SEPARATOR_CHAR = "~";

    public static SimpleFilter createContainerFilter(Container c)
    {
        return createContainerFilter(c, "Container");
    }

    public static SimpleFilter createContainerFilter(Container c, String columnName)
    {
        return new SimpleFilter(new FieldKey(null, columnName), c);
    }

    /**
     * Creates a SimpleFilter from a queryParameter.
     * @param queryParameter A query parameter, as would be found on a URL. e.g. 'query.Column~gt=value'
     * @return The SimpleFilter that was created. If the string is not formatted correctly, will return null.
     */
    @Nullable
    public static SimpleFilter createFilterFromParameter(@NotNull String queryParameter)
    {
        SimpleFilter filter = null;
        String[] parts = queryParameter.split("\\.");

        if (parts.length > 1)
        {
            String regionName = parts[0];
            ActionURL url = new ActionURL();
            url.setRawQuery(queryParameter);
            filter = new SimpleFilter(url, regionName);
        }

        return filter;
    }

    @Nullable
    public static String getColumnFromParameter(@NotNull String queryParameter)
    {
        String column = null;
        String[] parts = queryParameter.split("\\.");

        if (parts.length > 1)
        {
            String columnPart = String.copyValueOf(parts[1].toCharArray());
            column = columnPart.split(SEPARATOR_CHAR)[0];
        }

        return column;
    }

    /**
     * An expression used to filter a query. Used to generate WHERE clauses in the SQL.
     * Could operate on a single column's value, or represent a compound statement.
     */
    public static abstract class FilterClause
    {
        protected boolean _needsTypeConversion = false;
        protected Object[] _paramVals = new Object[0];   // TODO: _paramVals, getter, and callers should all be @NotNull. Consider List<?> as well
        protected boolean _includeNull = false;
        /** Whether this is the "flip" version of a clause. For example, Does Not Contain, instead of Contains */
        protected boolean _negated = false;
        protected List<ColumnInfo> _selectColumns;

        /**
         * Explicit override for a clause to display filter text. When false, the clause's filter text
         * will only be shown depending on constraints in displayFilterText().
         */
        protected boolean _displayFilterText = false;

        public boolean displayFilterText()
        {
            if (_displayFilterText)
                return true;

            List<FieldKey> keys = this.getFieldKeys();
            return keys != null && !keys.isEmpty();
        }

        /**
         * Whether or not the value needs to be type converted before being sent to the database. This is important
         * for things like URL-supplied filters, where the value is always going to be a string, while the database
         * column may be an INT or DATE.
         */
        public boolean needsTypeConversion()
        {
            return _needsTypeConversion;
        }

        public boolean isIncludeNull()
        {
            return _includeNull;
        }

        public boolean isNegated()
        {
            return _negated;
        }

        public void setIncludeNull(boolean includeNull)
        {
            _includeNull = includeNull;
        }

        public Object[] getParamVals()
        {
            return _paramVals;
        }

        protected void appendFilterText(StringBuilder sb, ColumnNameFormatter formatter)
        {
            int fromIndex = appendFilterValueText(sb, formatter);
            replaceParamValues(sb, fromIndex);
        }


        // FromIndex ensures we're replacing '?'s in this clause only (not earlier clauses with param values containing '?')
        protected void replaceParamValues(StringBuilder sb, int fromIndex)
        {
            Object[] paramVals = getParamVals();

            if (null == paramVals)
                return;

            for (Object o : paramVals)
            {
                String param = formattedParamValue(o);
                int i = sb.indexOf("?", fromIndex);
                fromIndex += param.length();         // Protects against previous param values in this clause containing '?'
                sb.replace(i, i + 1, param);
            }
        }

        // Format param value for filter description display.
        protected String formattedParamValue(Object val)
        {
            if (val instanceof Calendar)
                val = ((Calendar)val).getTime();

            String param = StringUtils.defaultString(ConvertUtils.convert(val), "NULL");

            // Surround value with quotes if it contains whitespace
            if (param.contains(" "))
                param = "'" + param + "'";

            return param;
        }


        /** @return non-URL encoded name/value pair. Value may be null if there's none to be used (for IS BLANK or similar clauses).
         * The whole return value may be null if this clause can't be represented on the URL */
        @Nullable
        public  Map.Entry<String, String> toURLParam(String dataRegionPrefix)
        {
            return null;
        }

        /** @return index into the StringBuilder at which the substitutions should begin to swap parameter values for the placeholder */
        protected int appendFilterValueText(StringBuilder sb, ColumnNameFormatter formatter)
        {
            int result = sb.length();
            SQLFragment sqlf = toSQLFragment(Collections.emptyMap(), null);
            if (sqlf.isEmpty())
                sb.append("1=1");
            else
                sb.append(sqlf);
            return result;
        }

        /**
         * @deprecated Use {@link #getFieldKeys()}
         */
        @Deprecated
        public List<String> getColumnNames()
        {
            return getFieldKeys().stream()
                .map(fk->fk.toString())
                .collect(Collectors.toList());
        }

        abstract public List<FieldKey> getFieldKeys();

        /** @return whether the value meets the criteria of this filter */
        public boolean meetsCriteria(Object value)
        {
            return false;
        }

        protected String escapeLabKeySqlValue(Object value, JdbcType type)
        {
            return escapeLabKeySqlValue(value, type, false);
        }

        protected String escapeLabKeySqlValue(Object value, JdbcType type, boolean suppressQuotes)
        {
            if (type == null)
                throw new IllegalArgumentException("Column type must be provided.");
            StringBuilder sql = new StringBuilder();
            if (type.isNumeric() || type == JdbcType.BOOLEAN)
                sql.append(value);
            else if (type.isDateOrTime())
                sql.append("CAST('").append(value).append("' AS TIMESTAMP)");
            else
            {
                if (!suppressQuotes)
                    sql.append("'");
                if (value instanceof String)
                    value = ((String) value).replace("'", "''");
                sql.append(value);
                if (!suppressQuotes)
                    sql.append("'");
            }
            return sql.toString();
        }

        protected static String getLabKeySQLColName(FieldKey key)
        {
            return key.toSQLString();
        }

        public abstract String getLabKeySQLWhereClause(Map<FieldKey, ? extends ColumnInfo> columnMap);

        public abstract SQLFragment toSQLFragment(Map<FieldKey, ? extends ColumnInfo> columnMap, SqlDialect dialect);

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            FilterClause that = (FilterClause) o;

            if (_includeNull != that._includeNull) return false;
            if (_negated != that._negated) return false;
            if (_needsTypeConversion != that._needsTypeConversion) return false;
            // Probably incorrect - comparing Object[] arrays with Arrays.equals
            if (!Arrays.equals(_paramVals, that._paramVals)) return false;
            if (!getFieldKeys().equals(that.getFieldKeys())) return false;

            return true;
        }

        @Override
        public int hashCode()
        {
            int result = (_needsTypeConversion ? 1 : 0);
            result = 31 * result + (_paramVals != null ? Arrays.hashCode(_paramVals) : 0);
            result = 31 * result + (_includeNull ? 1 : 0);
            result = 31 * result + (_negated ? 1 : 0);
            return result;
        }

        public static FilterClause fromXml(FilterType xmlFilter)
        {
            CompareType compareType = CompareType.getByURLKey(xmlFilter.getOperator().toString());
            if (compareType == null)
                throw new IllegalArgumentException("Unsupported operator type: " + xmlFilter.getOperator());

            Object value = xmlFilter.getValue();
            FieldKey fieldKey = FieldKey.fromString(xmlFilter.getColumn());
            FilterClause filterClause = compareType.createFilterClause(fieldKey, value);
            // Because the value is coming in as a string in the XML, remember that we need to do type
            // conversion on it later when we know the actual column type
            filterClause._needsTypeConversion = true;
            return filterClause;
        }

        public void setSelectColumns(List<ColumnInfo> selectColumns)
        {
            this._selectColumns = selectColumns;
        }
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SimpleFilter filter = (SimpleFilter) o;

        if (_clauses != null ? !_clauses.equals(filter._clauses) : filter._clauses != null) return false;

        return true;
    }

    @Override
    public int hashCode()
    {
        return _clauses != null ? _clauses.hashCode() : 0;
    }

    public static class SQLClause extends FilterClause
    {
        private final SQLFragment _fragment;
        private final List<FieldKey> _fieldKeys;

        public SQLClause(String fragment, @Nullable Object[] paramVals, FieldKey... fieldKeys)
        {
            _needsTypeConversion = false;
            if (null == paramVals)
                _fragment = new SQLFragment(fragment);
            else
                _fragment = new SQLFragment(fragment, paramVals);
            _fieldKeys = Arrays.asList(fieldKeys);
        }

        public SQLClause(SQLFragment fragment, FieldKey... fieldKeys)
        {
            _needsTypeConversion = false;
            _fragment = new SQLFragment(fragment);
            _fieldKeys = Arrays.asList(fieldKeys);
        }

        public SQLFragment toSQLFragment(Map<FieldKey, ? extends ColumnInfo> columnMap, SqlDialect dialect)
        {
            return _fragment;
        }

        public List<FieldKey> getFieldKeys()
        {
            return Collections.unmodifiableList(_fieldKeys);
        }

        @Override
        public String getLabKeySQLWhereClause(Map<FieldKey, ? extends ColumnInfo> columnMap)
        {
            throw new UnsupportedOperationException();
        }
    }


    public static class FalseClause extends SQLClause
    {
        public FalseClause()
        {
            super("0=1", null);
        }
    }


    public abstract static class OperationClause extends FilterClause
    {
        private final String _operation;
        private final List<FilterClause> _clauses;

        protected OperationClause(String operation, FilterClause... clauses)
        {
            _operation = operation;
            _clauses = new ArrayList<>(Arrays.asList(clauses));
        }

        public List<FieldKey> getFieldKeys()
        {
            List<FieldKey> result = new ArrayList<>();
            for (FilterClause clause : _clauses)
            {
                result.addAll(clause.getFieldKeys());
            }
            return result;
        }

        public void addClause(FilterClause clause)
        {
            _clauses.add(clause);
        }

        public Object[] getParamVals()
        {
            List<Object> result = new ArrayList<>();
            for (FilterClause clause : _clauses)
            {
                if (clause.getParamVals() != null)
                    result.addAll(Arrays.asList(clause.getParamVals()));
            }
            return result.toArray(new Object[result.size()]);
        }

        public SQLFragment toSQLFragment(Map<FieldKey, ? extends ColumnInfo> columnMap, SqlDialect dialect)
        {
            SQLFragment sqlFragment = new SQLFragment();
            String separator = "";
            for (FilterClause clause : _clauses)
            {
                sqlFragment.append(separator);
                separator = _operation;
                sqlFragment.append("(");
                sqlFragment.append(clause.toSQLFragment(columnMap, dialect));
                sqlFragment.append(")");
            }
            
            return sqlFragment;
        }


        @Override
        public String getLabKeySQLWhereClause(Map<FieldKey, ? extends ColumnInfo> columnMap)
        {
            StringBuilder labKeySql = new StringBuilder();
            String separator = "";
            for (FilterClause clause : _clauses)
            {
                labKeySql.append(separator);
                separator = _operation;
                labKeySql.append("(");
                labKeySql.append(clause.getLabKeySQLWhereClause(columnMap));
                labKeySql.append(")");
            }
            return labKeySql.toString();
        }

        @Override
        protected void appendFilterText(StringBuilder sb, ColumnNameFormatter formatter)
        {
            String sep = "";
            for (FilterClause clause : _clauses)
            {
                sb.append(sep);
                clause.appendFilterText(sb, formatter);
                sep = _operation;
            }
        }

        protected List<FilterClause> getClauses()
        {
            return _clauses;
        }
    }

    public static class OrClause extends OperationClause
    {
        public OrClause(FilterClause... clauses)
        {
            super(" OR ", clauses);
        }

        @Override
        public boolean meetsCriteria(Object value)
        {
            for (FilterClause clause : getClauses())
            {
                if (clause.meetsCriteria(value))
                {
                    return true;
                }
            }
            return false;
        }
    }

    public static class AndClause extends OperationClause
    {
        public AndClause(FilterClause... clauses)
        {
            super(" AND ", clauses);
        }

        @Override
        public boolean meetsCriteria(Object value)
        {
            for (FilterClause clause : getClauses())
            {
                if (!clause.meetsCriteria(value))
                {
                    return false;
                }
            }
            return !getClauses().isEmpty();
        }
    }

    public static class NotClause extends FilterClause
    {
        private FilterClause _clause;

        public NotClause(FilterClause clause)
        {
            _clause = clause;
        }

        public List<FieldKey> getFieldKeys()
        {
            return _clause.getFieldKeys();
        }

        public Object[] getParamVals()
        {
            return _clause.getParamVals();
        }

        public SQLFragment toSQLFragment(Map<FieldKey, ? extends ColumnInfo> columnMap, SqlDialect dialect)
        {
            SQLFragment sqlFragment = new SQLFragment();
            sqlFragment.append(" NOT (");
            sqlFragment.append(_clause.toSQLFragment(columnMap, dialect));
            sqlFragment.append(")");
            return sqlFragment;
        }

        @Override
        public String getLabKeySQLWhereClause(Map<FieldKey, ? extends ColumnInfo> columnMap)
        {
            return " NOT (" + _clause.getLabKeySQLWhereClause(columnMap) + ")";
        }

        @Override
        public boolean meetsCriteria(Object value)
        {
            return !_clause.meetsCriteria(value);
        }
    }

    public static abstract class MultiValuedFilterClause extends CompareType.AbstractCompareClause
    {
        public static final int MAX_FILTER_VALUES_TO_DISPLAY = 10;

        public MultiValuedFilterClause(@NotNull FieldKey fieldKey, Collection<?> params)
        {
            super(fieldKey);
            params = new ArrayList<>(params); // possibly immutable
            if (params.contains(null)) //params.size() == 0 ||
            {
                _includeNull = true;
                params.remove(null);
            }
            if (params.contains("")) //params.size() == 0 ||
            {
                _includeNull = true;
                params.remove("");
            }

            _paramVals = params.toArray();
        }

        @Override
        protected String toURLParamValue()
        {
            if (getParamVals() != null && getParamVals().length > 0)
            {
                StringBuilder sb = new StringBuilder();
                String separator = "";
                for (Object value : getParamVals())
                {
                    sb.append(separator);
                    separator = ";";
                    sb.append(value == null ? "" : value.toString());
                }
                if (_includeNull)
                {
                    sb.append(separator);
                }
                return sb.toString();
            }
            return null;
        }
    }

    public static class InClause extends MultiValuedFilterClause
    {
        public static final String SEPARATOR = ";";

        public InClause(FieldKey fieldKey, Collection<?> params)
        {
            this(fieldKey, params, false, false);
        }

        public InClause(FieldKey fieldKey, Collection<?> params, boolean urlClause)
        {
            this(fieldKey, params, urlClause, false);
        }

        public InClause(FieldKey fieldKey, Collection<?> params, boolean urlClause, boolean negated)
        {
            super(fieldKey, params);

            _needsTypeConversion = urlClause;
            _negated = negated;
        }

        public InClause(FieldKey fieldKey, String namedSet, boolean urlClause)
        {
            this(fieldKey, QueryService.get().getNamedSet(namedSet), urlClause, false);
        }

        @Override
        public CompareType getCompareType()
        {
            return _negated ? CompareType.NOT_IN : CompareType.IN;
        }

        @Override
        protected void appendFilterText(StringBuilder sb, ColumnNameFormatter formatter)
        {
            sb.append(formatter.format(getFieldKey()));

            if (getParamVals().length == 0 && !isIncludeNull())
            {
                if (isNegated())
                {
                    sb.append(" has any value");
                }
                else
                {
                    sb.append(" never matches");
                }
                return;
            }

            if (isNegated())
                sb.append(" IS NOT ANY OF (");
            else
                sb.append(" IS ONE OF (");

            if (getParamVals().length > MAX_FILTER_VALUES_TO_DISPLAY)
            {
                sb.append("too many values to display)");
                return;
            }

            String sep = "";
            for (Object val : getParamVals())
            {
                if (val != null)
                {
                    String s = formattedParamValue(val);
                    sb.append(sep).append(s);
                    sep = ", ";
                }
            }

            if ("".equals(sep) || isIncludeNull())
            {
                sb.append(sep).append("BLANK");
            }

            sb.append(")");
        }

        @Override
        public String getLabKeySQLWhereClause(Map<FieldKey, ? extends ColumnInfo> columnMap)
        {
            String alias = getLabKeySQLColName(getFieldKey());
            ColumnInfo col = columnMap != null ? columnMap.get(getFieldKey()) : null;
            Object[] params = getParamVals();
            StringBuilder in =  new StringBuilder();

            if (params.length == 0)
            {
                if (isIncludeNull())
                    in.append(alias).append(" IS ").append(isNegated() ? " NOT " : "").append("NULL");
                else if (!isNegated())
                    in.append(alias).append(" IN (NULL)");  // Empty list case; "WHERE column IN (NULL)" should always be false

                return in.toString();
            }

            in.append("((").append(alias);
            in.append(" ").append(isNegated() ? "NOT " : "").append("IN (");
            String sep = "";

            for (Object param : params)
            {
                in.append(sep).append(escapeLabKeySqlValue(param, col.getJdbcType()));
                sep = ", ";
            }

            in.append(")");

            if (isIncludeNull())
            {
                if (isNegated())
                    in.append(") AND " + alias + " IS NOT NULL)");
                else
                    in.append(") OR " + alias + " IS NULL)");
            }
            else
            {
                if (isNegated())
                    in.append(") OR " + alias + " IS NULL)");
                else
                    in.append("))");
            }

            return in.toString();
        }


        public SQLFragment toSQLFragment(Map<FieldKey, ? extends ColumnInfo> columnMap, SqlDialect dialect)
        {
            Object[] params = getParamVals();

            @Nullable ColumnInfo colInfo = columnMap != null ? columnMap.get(getFieldKey()) : null;
            String name = colInfo != null ? colInfo.getAlias() : getFieldKey().getName();
            String alias = dialect.getColumnSelectName(name);

            SQLFragment in = new SQLFragment();

            if (params.length == 0)
            {
                if (isIncludeNull())
                    in.append(alias).append(" IS ").append(isNegated() ? " NOT " : "").append("NULL");
                else if (!isNegated())
                    in.append(alias).append(" IN (NULL)");  // Empty list case; "WHERE column IN (NULL)" should always be false
                else
                    in.append("1=1");

                return in;
            }

            Object[] convertedParams;

            if (null == colInfo || !needsTypeConversion())
            {
                convertedParams = params;
            }
            else
            {
                convertedParams = new Object[params.length];

                for (int i = 0; i < params.length; i++)
                    convertedParams[i] = CompareType.convertParamValue(colInfo, params[i]);
            }

            in.append("((");

            if (isNegated())
                in.append("NOT ");

            in.append(alias);

            // Dialect may want to generate database-specific SQL, especially for very large IN clauses
            dialect.appendInClauseSql(in, Arrays.asList(convertedParams));

            if (isIncludeNull())
            {
                if (isNegated())
                    in.append(") AND " + alias + " IS NOT NULL)");
                else
                    in.append(") OR " + alias + " IS NULL)");
            }
            else
            {
                if (isNegated())
                    in.append(") OR " + alias + " IS NULL)");
                else
                    in.append("))");
            }

            return in;
        }

        public void addInValue(Object... values)
        {
            addInValues(Arrays.asList(values));
        }

        public void addInValues(Collection<?> newValues)
        {
            Set<Object> values = new HashSet<>(Arrays.asList(getParamVals()));
            values.addAll(newValues);
            _paramVals = values.toArray();
        }

        @Override
        public boolean meetsCriteria(Object value)
        {
            for (Object params : getParamVals())
            {
                // Loop through all the values and check if any of them are equals
                FilterClause compareClause = CompareType.EQUAL.createFilterClause(getFieldKeys().get(0), params);
                if (compareClause.meetsCriteria(value))
                {
                    return true;
                }
            }
            return false;
        }
    }

    public static class ContainsOneOfClause extends MultiValuedFilterClause
    {
        public ContainsOneOfClause(FieldKey fieldKey, Collection params, boolean urlClause)
        {
            this(fieldKey, params, urlClause, false);
        }

        public ContainsOneOfClause(FieldKey fieldKey, Collection params, boolean urlClause, boolean negated)
        {
            super(fieldKey, params);

            _needsTypeConversion = urlClause;
            _negated = negated;
        }

        @Override
        public CompareType getCompareType()
        {
            return _negated ? CompareType.CONTAINS_NONE_OF : CompareType.CONTAINS_ONE_OF;
        }

        @Override
        protected void appendFilterText(StringBuilder sb, ColumnNameFormatter formatter)
        {
            sb.append(formatter.format(getFieldKey()));
            sb.append(" ").append(isNegated() ? "DOES NOT CONTAIN ANY OF (" : "CONTAINS ONE OF (");

            if(getParamVals().length > MAX_FILTER_VALUES_TO_DISPLAY)
            {
                sb.append("too many values to display)");
                return;
            }

            String sep = "";
            for (Object val : getParamVals())
            {
                if (val != null)
                {
                    String s = formattedParamValue(val);
                    sb.append(sep).append(s);
                    sep = ", ";
                }
            }

            if(isIncludeNull())
                sb.append(sep).append("BLANK");

            sb.append(")");
        }

        @Override
        public String getLabKeySQLWhereClause(Map<FieldKey, ? extends ColumnInfo> columnMap)
        {
            ColumnInfo col = columnMap.get(getFieldKey());

            Object[] params = getParamVals();
            if (params.length > 0)
            {
                return getContainsClause(col).getLabKeySQLWhereClause(columnMap);
            }

            return col.getName() + (isNegated() ? " NOT IN" : " IN ") + " (NULL)";  // Empty list case; "WHERE column IN (NULL)" should always be false
        }


        public SQLFragment toSQLFragment(Map<FieldKey, ? extends ColumnInfo> columnMap, SqlDialect dialect)
        {
            ColumnInfo colInfo = columnMap != null ? columnMap.get(getFieldKey()) : null;
            String alias = colInfo != null ? colInfo.getAlias() : getFieldKey().getName();

            SQLFragment in = new SQLFragment();
            OperationClause oc = getContainsClause(colInfo);
            if(oc.getClauses().size() > 0)
                return in.append(oc.toSQLFragment(columnMap, dialect));

            return in.append(alias).append(isNegated() ? " NOT IN " : " IN ").append("(NULL)");  // Empty list case; "WHERE column IN (NULL)" should always be false
        }

        private OperationClause getContainsClause(ColumnInfo colInfo)
        {
            Object[] params = getParamVals();
            OperationClause oc;
            if (isNegated())
                oc = new AndClause();
            else
                oc = new OrClause();

            FieldKey fieldKey = colInfo == null ? getFieldKey() : colInfo.getFieldKey();
            if (params.length > 0)
            {
                for(Object param : params)
                {
                    if(isNegated())
                    {
                        oc.addClause(new CompareType.DoesNotContainClause(fieldKey, param));
                    }
                    else
                    {
                        oc.addClause(new CompareType.ContainsClause(fieldKey, param));
                    }
                }
            }

            //account for null
            if (isIncludeNull())
            {
                OrClause clause = new OrClause();
                if (oc._clauses.size() > 0)
                    clause.addClause(oc);

                if (isNegated())
                    clause.addClause(CompareType.NONBLANK.createFilterClause(fieldKey, null));
                else
                    clause.addClause(CompareType.ISBLANK.createFilterClause(fieldKey, null));
                return clause;
            }

            return oc;
        }
        public void addInValue(Object... values)
        {
            addInValues(Arrays.asList(values));
        }

        public void addInValues(Collection<?> newValues)
        {
            Set<Object> values = new HashSet<>(Arrays.asList(getParamVals()));
            values.addAll(newValues);
            _paramVals = values.toArray();
        }

        @Override
        public boolean meetsCriteria(Object value)
        {
            for (Object params : getParamVals())
            {
                // Loop through all the values and check if any of them are equals
                FilterClause compareClause = CompareType.CONTAINS.createFilterClause(getFieldKeys().get(0), params);
                if (compareClause.meetsCriteria(value))
                {
                    return true;
                }
            }
            return false;
        }
    }

    private ArrayList<FilterClause> _clauses = new ArrayList<>();


    public SimpleFilter()
    {
    }

    public SimpleFilter(FilterClause... clauses)
    {
        _clauses = new ArrayList<>(Arrays.asList(clauses));
    }

    public SimpleFilter(Filter filter)
    {
        if (null == filter)
            return;
        SimpleFilter src = (SimpleFilter) filter;
        _clauses = (ArrayList<FilterClause>) src._clauses.clone();
    }

    /**
     * @deprecated Use {@link #SimpleFilter(FieldKey, Object)}
     */
    @Deprecated
    public SimpleFilter(String colName, Object value)
    {
        addCondition(colName, value);
    }

    public SimpleFilter(FieldKey fieldKey, Object value)
    {
        addCondition(fieldKey, value);
    }

    /**
     * @deprecated Use {@link #SimpleFilter(FieldKey, Object, CompareType)}
     */
    @Deprecated
    public SimpleFilter(String colName, Object value, CompareType compare)
    {
        addCondition(colName, value, compare);
    }

    public SimpleFilter(FieldKey fieldKey, Object value, CompareType compare)
    {
        addCondition(fieldKey, value, compare);
    }

    public SimpleFilter(URLHelper urlHelp, String regionName)
    {
        addUrlFilters(urlHelp, regionName);
    }

    public void addUrlFilters(URLHelper urlHelp, @Nullable String regionName)
    {
        addUrlFilters(urlHelp, regionName, Collections.emptyList());
    }

    public void addUrlFilters(URLHelper urlHelp, @Nullable String regionName, @NotNull List<ColumnInfo> selectColumns)
    {
        String prefixDot = regionName == null ? "" : regionName + ".";

        // for "dataRegion.column~op" filters
        urlHelp.getParameterMap().keySet().stream()
            .filter(filterKey -> filterKey.startsWith(prefixDot))
            .forEach(filterKey ->
            {
                String colTildeCompare = filterKey.substring(prefixDot.length());
                for (String param : urlHelp.getParameterValues(filterKey))
                {
                    String[] compareInfo = colTildeCompare.split(SEPARATOR_CHAR);
                    CompareType type = null;
                    FieldKey fieldKey = FieldKey.fromString(compareInfo[0]);
                    if (compareInfo.length == 2)
                        type = CompareType.getByURLKey(compareInfo[1]);

                    if (null == type || fieldKey == null)
                        continue;

                    if ((CompareType.MV_INDICATOR.equals(type) || CompareType.NO_MV_INDICATOR.equals(type)) &&
                            null != fieldKey.getParent())
                    {
                        ColumnInfo column = null;
                        for (ColumnInfo selectColumn : selectColumns)
                        {
                            if (fieldKey.getParent().equals(selectColumn.getFieldKey()))
                            {
                                column = selectColumn;
                                break;
                            }
                        }
                        if (null != column && column.isMvEnabled())
                            fieldKey = column.getFieldKey();
                    }

                    try
                    {
                        FilterClause fc = type.createFilterClause(fieldKey, param);
                        fc.setSelectColumns(selectColumns);
                        fc._needsTypeConversion = true;
                        _clauses.add(fc);
                    }
                    catch (ConversionException e)
                    {
                        // rethrow with better error message.  Date CompareTypes convert the parameter when created.
                        throw new ConversionException("Could not convert filter value \"" + param + "\" for column \"" + fieldKey.toDisplayString() + "\". Please check your input. " + (e.getMessage() != null ? e.getMessage() : ""), e);
                    }
                }
            });

        // for "dataRegion~op" filters (e.g. dataregion level filters)
        String prefixTilde = regionName == null ? "" : regionName + "~";
        urlHelp.getParameterMap().keySet().stream()
                .filter(filterKey -> filterKey.startsWith(prefixTilde))
                .forEach(filterKey ->
                {
                    String colTildeCompare = filterKey.substring(prefixTilde.length());
                    for (String param : urlHelp.getParameterValues(filterKey))
                    {
                        CompareType type = CompareType.getByURLKey(colTildeCompare);

                        if (null == type)
                            continue;

                        try
                        {
                            FilterClause fc = type.createFilterClause(new FieldKey(null, "*"), param);
                            fc.setSelectColumns(selectColumns);
                            fc._needsTypeConversion = true;
                            _clauses.add(fc);
                        }
                        catch (ConversionException e)
                        {
                            // rethrow with better error message.  Date CompareTypes convert the parameter when created.
                            throw new ConversionException("Could not convert filter value \"" + param + "\". Please check your input. " + (e.getMessage() != null ? e.getMessage() : ""), e);
                        }
                    }
                });
    }

    public SimpleFilter addAllClauses(SimpleFilter filter)
    {
        _clauses.addAll((ArrayList<FilterClause>) filter._clauses.clone());
        return this;
    }

    public SimpleFilter addClause(FilterClause clause)
    {
        _clauses.add(clause);
        return this;
    }

    public List<FieldKey> getAllFieldKeys()
    {
        List<FieldKey> result = new ArrayList<>();
        for (FilterClause clause : _clauses)
        {
            result.addAll(clause.getFieldKeys());
        }
        return result;
    }

    public SimpleFilter deleteConditions(FieldKey fieldKey)
    {
        _clauses.removeIf(clause -> clause.getFieldKeys().contains(fieldKey));
        return this;
    }

    /**
     * @deprecated Use {@link #addCondition(FieldKey, Object)}
     */
    @Deprecated
    public SimpleFilter addCondition(String colName, Object value)
    {
        return addCondition(FieldKey.fromString(colName), value);
    }

    public SimpleFilter addCondition(FieldKey fieldKey, Object value)
    {
        return addCondition(fieldKey, value == null ? Parameter.NULL_MARKER : value, CompareType.EQUAL);
    }

    public SimpleFilter addCondition(ColumnInfo column, Object value)
    {
        return addCondition(column.getFieldKey(), value == null ? Parameter.NULL_MARKER : value, CompareType.EQUAL);
    }

    public SimpleFilter addCondition(ColumnInfo column, Object value, CompareType compare)
    {
        return addCondition(column.getFieldKey(), value, compare);
    }

    /**
     * @deprecated Use {@link #addCondition(FieldKey, Object, CompareType)}
     */
    @Deprecated
    public SimpleFilter addCondition(String colName, @Nullable Object value, CompareType compare)
    {
        return addCondition(FieldKey.fromString(colName), value, compare);
    }

    public SimpleFilter addCondition(FieldKey fieldKey, @Nullable Object value, CompareType compare)
    {
        _clauses.add(compare.createFilterClause(fieldKey, value));
        return this;
    }

    public SimpleFilter addCondition(FilterClause clause)
    {
        _clauses.add(clause);
        return this;
    }

    public SimpleFilter addBetween(FieldKey fieldKey, Comparable value1, Comparable value2)
    {
        if (value1 != null && value2 != null && value1.equals(value2))
            addCondition(fieldKey, value1);  // Equal
        else if (value1 != null && value2 != null && value1.compareTo(value2) > 0)
        {
            addCondition(fieldKey, value2, CompareType.GTE);
            addCondition(fieldKey, value1, CompareType.LTE);
        }
        else
        {
            addCondition(fieldKey, value1, CompareType.GTE);
            addCondition(fieldKey, value2, CompareType.LTE);
        }
        return this;
    }

    public SimpleFilter addWhereClause(String fragment, Object[] paramVals, FieldKey... fieldKeys)
    {
        _clauses.add(new SQLClause(fragment, paramVals, fieldKeys));
        return this;
    }

    public SimpleFilter addWhereClause(SQLFragment fragment, FieldKey... fieldKeys)
    {
        _clauses.add(new SQLClause(fragment, fieldKeys));
        return this;
    }

    public SimpleFilter addInClause(FieldKey fieldKey, Collection<?> paramVals)
    {
        _clauses.add(new InClause(fieldKey, paramVals));
        return this;
    }

    public String toQueryString(String regionName)
    {
        StringBuilder ret = new StringBuilder();
        String prefix = regionName == null ? "" : regionName + ".";
        String and = "";
        for (FilterClause fc : _clauses)
        {
            Map.Entry<String, String> entry = fc.toURLParam(prefix);
            if (entry != null)
            {
                ret.append(and);
                and = "&";
                ret.append(PageFlowUtil.encode(entry.getKey()));
                if (entry.getValue() != null)
                {
                    ret.append("=");
                    ret.append(PageFlowUtil.encode(entry.getValue()));
                }
            }
        }
        return ret.toString();
    }

    /**
     * Apply the filters in this SimpleFilter to the url scoped by the regionName prefix.
     * @param url The url to be modified.
     * @param regionName The prefix used to scope the filters.
     */
    public void applyToURL(URLHelper url, String regionName)
    {
        String prefix = regionName == null ? "" : regionName + ".";
        for (FilterClause fc : _clauses)
        {
            Map.Entry<String, String> entry = fc.toURLParam(prefix);

            if (entry != null)
            {
                url.addParameter(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * Determines if SimpleFilter should display filter text from any of it's FilterClauses.
     * If any filter clause chooses to display itself, this will return true.
     */
    public boolean displayFilterText()
    {
        for (FilterClause fc : _clauses)
        {
            if (fc.displayFilterText())
                return true;
        }

        return false;
    }

    public SQLFragment getSQLFragment(TableInfo tableInfo, @Nullable List<ColumnInfo> colInfos)
    {
        if (null == _clauses || 0 == _clauses.size())
        {
            return new SQLFragment();
        }
        return getSQLFragment(tableInfo.getSqlDialect(), Table.createColumnMap(tableInfo, colInfos));
    }

    //
    // Filter
    //


    public SQLFragment getSQLFragment(SqlDialect dialect)
    {
        return getSQLFragment(dialect, Collections.emptyMap());
    }

    public SQLFragment getSQLFragment(SqlDialect dialect, Map<FieldKey, ? extends ColumnInfo> columnMap)
    {
        SQLFragment ret = new SQLFragment();

        if (null == _clauses || 0 == _clauses.size())
            return ret;

        String sAND = "WHERE ";

        for (FilterClause fc : _clauses)
        {
            ret.append(sAND);
            ret.append("(");
            try
            {
                ret.append(fc.toSQLFragment(columnMap, dialect));
            }
            catch (RuntimeSQLException e)
            {
                // Deal with unparseable filter values - see issue 23321
                if (e.getSQLException() instanceof SQLGenerationException)
                {
                    ret.append("0 = 1");
                }
                else
                {
                    throw e;
                }
            }
            ret.append(")");
            sAND = " AND ";
        }

        return ret;
    }


    public List<Object> getWhereParams(TableInfo tableInfo)
    {
        return getWhereParams(tableInfo, tableInfo.getColumns());
    }


    public List<Object> getWhereParams(TableInfo tableInfo, List<ColumnInfo> colInfos)
    {
        SQLFragment frag = getSQLFragment(tableInfo, colInfos);
        return frag.getParams();
    }


    public Set<FieldKey> getWhereParamFieldKeys()
    {
        Set<FieldKey> paramNames = new HashSet<>(_clauses.size());

        for (FilterClause fc : _clauses)
            paramNames.addAll(fc.getFieldKeys());

        return paramNames;
    }

    @Override
    public boolean isEmpty()
    {
        return getWhereParamFieldKeys().isEmpty();
    }

    public List<FilterClause> getClauses()
    {
        return Collections.unmodifiableList(_clauses);
    }

    public String getWhereSQL(TableInfo tableInfo)
    {
        SQLFragment frag = getSQLFragment(tableInfo, null);
        return frag.getSQL();
    }

    @NotNull
    public String getFilterText()
    {
        return getFilterText(new ColumnNameFormatter());
    }

    // Display pseudo English version of filter
    @NotNull
    public String getFilterText(ColumnNameFormatter formatter)
    {
        StringBuilder sb = new StringBuilder();
        String sAND = "";

        for (FilterClause clause : _clauses)
        {
            sb.append(sAND);
            sb.append('(');
            clause.appendFilterText(sb, formatter);
            sb.append(')');
            sAND = " AND ";
        }

        return sb.toString();
    }

    public String toLabKeySQL(Map<FieldKey, ColumnInfo> columns)
    {
        StringBuilder result = new StringBuilder();
        String separator = "(";
        for (SimpleFilter.FilterClause filterClause : getClauses())
        {
            result.append(separator);
            separator = ") AND (";
            result.append(filterClause.getLabKeySQLWhereClause(columns));
        }
        if (result.length() > 0)
        {
            result.append(")");
        }
        return result.toString();
    }

    public String toSQLString(SqlDialect dialect)
    {
        SQLFragment fragment = getSQLFragment(dialect);
        return dialect.substituteParameters(fragment);
    }

    private static final FieldKey CONTAINER_FIELD_KEY = FieldKey.fromParts("container");

    public boolean hasContainerEqualClause()
    {
        for (FilterClause clause : _clauses)
        {
            if (clause instanceof CompareClause)
            {
                CompareClause compClause = (CompareClause) clause;
                if (compClause.getCompareType() == CompareType.EQUAL &&
                        compClause.getFieldKeys().size() == 1 &&
                        CONTAINER_FIELD_KEY.equals(compClause.getFieldKeys().get(0)))
                    return true;
            }
            if (clause instanceof InClause)
            {
                InClause inClause = (InClause)clause;
                if (inClause.getFieldKeys().size() == 1 &&
                        CONTAINER_FIELD_KEY.equals(inClause.getFieldKeys().get(0)))
                {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean meetsCriteria(Map<FieldKey, ?> map)
    {
        if (_clauses == null || _clauses.isEmpty())
            return true;

        for (FilterClause clause : _clauses)
        {
            List<FieldKey> fieldKeys = clause.getFieldKeys();
            if (fieldKeys.size() == 0)
                throw new IllegalArgumentException("Expected filter criteria column name");
            if (fieldKeys.size() > 1)
                throw new IllegalArgumentException("Can't check filter criteria of multi-column clauses");

            FieldKey fieldKey = fieldKeys.get(0);
            Object value = map.get(fieldKey);
            if (!clause.meetsCriteria(value))
                return false;
        }

        return true;
    }

    // Take care of column name artifacts
    public static class ColumnNameFormatter
    {
        public String format(FieldKey fieldKey)
        {
            return fieldKey.toDisplayString();
        }
    }

    @Nullable
    public static SimpleFilter fromXml(FilterType[] xmlFilters)
    {
        if (xmlFilters.length == 0)
        {
            return null;
        }
        SimpleFilter filter = new SimpleFilter();
        for (FilterType xmlFilter : xmlFilters)
        {
            SimpleFilter.FilterClause clause = SimpleFilter.FilterClause.fromXml(xmlFilter);
            if (clause != null)
                filter.addClause(clause);
        }
        return filter;
    }

    public static class FilterTestCase extends Assert
    {
        Calendar asCalendar(Object x)
        {
            Calendar c = new GregorianCalendar();
            c.setTimeInMillis(DateUtil.parseISODateTime(x.toString()));
            return c;
        }

        @Test
        public void testMeetsCriteria()
        {
            //And
            //Compare
            //Contains
            //Date*

            Date dJan14 = new Date(DateUtil.parseISODateTime("2001-01-14"));
            Date dJan14noon = new Date(DateUtil.parseISODateTime("2001-01-14 12:00:00"));
            Date dJan15 = new Date(DateUtil.parseISODateTime("2001-01-15"));
            Date dJan15noon = new Date(DateUtil.parseISODateTime("2001-01-15 12:00:00"));
            Date dJan16 = new Date(DateUtil.parseISODateTime("2001-01-16"));
            Date dJan16noon = new Date(DateUtil.parseISODateTime("2001-01-16 12:00:00"));

            Calendar cJan15 = asCalendar("2001-01-15");

            FieldKey x = FieldKey.fromParts("x");
            CompareClause eqCompareClause = new CompareType.DateEqCompareClause(x, cJan15);
            assertFalse(eqCompareClause.meetsCriteria(dJan14));
            assertFalse(eqCompareClause.meetsCriteria(dJan14noon));
            assertTrue(eqCompareClause.meetsCriteria(dJan15));
            assertTrue(eqCompareClause.meetsCriteria(dJan15noon));
            assertFalse(eqCompareClause.meetsCriteria(dJan16));
            assertFalse(eqCompareClause.meetsCriteria(dJan16noon));

            CompareClause neqCompareClause = new CompareType.DateNeqCompareClause(x, cJan15);
            assertTrue(neqCompareClause.meetsCriteria(dJan14));
            assertTrue(neqCompareClause.meetsCriteria(dJan14noon));
            assertFalse(neqCompareClause.meetsCriteria(dJan15));
            assertFalse(neqCompareClause.meetsCriteria(dJan15noon));
            assertTrue(neqCompareClause.meetsCriteria(dJan16));
            assertTrue(neqCompareClause.meetsCriteria(dJan16noon));

            CompareClause ltCompareClause = new CompareType.DateLtCompareClause(x, cJan15);
            assertTrue(ltCompareClause.meetsCriteria(dJan14));
            assertTrue(ltCompareClause.meetsCriteria(dJan14noon));
            assertFalse(ltCompareClause.meetsCriteria(dJan15));
            assertFalse(ltCompareClause.meetsCriteria(dJan15noon));
            assertFalse(ltCompareClause.meetsCriteria(dJan16));
            assertFalse(ltCompareClause.meetsCriteria(dJan16noon));

            CompareClause lteCompareClause = new CompareType.DateLteCompareClause(x, cJan15);
            assertTrue(lteCompareClause.meetsCriteria(dJan14));
            assertTrue(lteCompareClause.meetsCriteria(dJan14noon));
            assertTrue(lteCompareClause.meetsCriteria(dJan15));
            assertTrue(lteCompareClause.meetsCriteria(dJan15noon));
            assertFalse(lteCompareClause.meetsCriteria(dJan16));
            assertFalse(lteCompareClause.meetsCriteria(dJan16noon));

            CompareClause gtCompareClause = new CompareType.DateGtCompareClause(x, cJan15);
            assertFalse(gtCompareClause.meetsCriteria(dJan14));
            assertFalse(gtCompareClause.meetsCriteria(dJan14noon));
            assertFalse(gtCompareClause.meetsCriteria(dJan15));
            assertFalse(gtCompareClause.meetsCriteria(dJan15noon));
            assertTrue(gtCompareClause.meetsCriteria(dJan16));
            assertTrue(gtCompareClause.meetsCriteria(dJan16noon));

            CompareClause gteCompareClause = new CompareType.DateGteCompareClause(x, cJan15);
            assertFalse(gteCompareClause.meetsCriteria(dJan14));
            assertFalse(gteCompareClause.meetsCriteria(dJan14noon));
            assertTrue(gteCompareClause.meetsCriteria(dJan15));
            assertTrue(gteCompareClause.meetsCriteria(dJan15noon));
            assertTrue(gteCompareClause.meetsCriteria(dJan16));
            assertTrue(gteCompareClause.meetsCriteria(dJan16noon));

            //DoesNotContain
            //DoesNotStartwith
            //Equals
            //In
            //Like
            //Not
            //NotEqualOrNull
            //NotEquals
            //Operation
            //Or
            //StartsWith
        }

        @Test
        public void testURLParams() throws URISyntaxException
        {
            SimpleFilter filter = new SimpleFilter();
            filter.addClause(new CompareClause(FieldKey.fromParts("Field1"), CompareType.EQUAL, 1));
            filter.addClause(new ContainsOneOfClause(FieldKey.fromParts("Field2"), Arrays.asList("x", "y"), true));

            assertEquals("query.Field1%7Eeq=1&query.Field2%7Econtainsoneof=x%3By", filter.toQueryString("query"));
            URLHelper url = new URLHelper("http://labkey.com");
            filter.applyToURL(url, "query");
            assertEquals("query.Field1%7Eeq=1&query.Field2%7Econtainsoneof=x%3By", url.getQueryString());
        }
    }

    public abstract static class ClauseTestCase extends Assert
    {
        protected void test(String expectedSQL, String description, FilterClause clause, SqlDialect dialect)
        {
            assertEquals("Generated SQL did not match", expectedSQL, clause.toSQLFragment(Collections.emptyMap(), dialect).toDebugString());
            StringBuilder sb = new StringBuilder();
            clause.appendFilterText(sb, new ColumnNameFormatter());
            assertEquals("Description did not match", description, sb.toString());
        }
    }

    public static class InClauseTestCase extends ClauseTestCase
    {
        @Test
        public void testInClause()
        {
            SqlDialect mockDialect = new MockSqlDialect();
            FieldKey fieldKey = FieldKey.fromParts("Foo");

            // Empty parameter list
            test("Foo IN (NULL)", "Foo never matches", new InClause(fieldKey, Collections.emptySet()), mockDialect);
            test("1=1", "Foo has any value", new InClause(fieldKey, Collections.emptySet(), true, true), mockDialect);

            // Non-null parameters only
            test("((Foo IN (1, 2, 3)))", "Foo IS ONE OF (1, 2, 3)", new InClause(fieldKey, PageFlowUtil.set(1, 2, 3)), mockDialect);
            test("((NOT Foo IN (1, 2, 3)) OR Foo IS NULL)", "Foo IS NOT ANY OF (1, 2, 3)", new InClause(fieldKey, PageFlowUtil.set(1, 2, 3), true, true), mockDialect);

            // Include null parameter
            test("((Foo IN ('Bar', 'Blip')) OR Foo IS NULL)", "Foo IS ONE OF (Bar, Blip, BLANK)", new InClause(fieldKey, PageFlowUtil.set("Bar", "Blip", "")), mockDialect);
            test("((NOT Foo IN ('Bar', 'Blip')) AND Foo IS NOT NULL)", "Foo IS NOT ANY OF (Bar, Blip, BLANK)", new InClause(fieldKey, PageFlowUtil.set("Bar", "Blip", ""), true, true), mockDialect);
        }

        @Test
        public void testInClauseQueryString()
        {
            FieldKey fieldKey = FieldKey.fromParts("Foo");

            // Empty parameter list
            assertEquals(new Pair<>("query.Foo~in", (String)null), new InClause(fieldKey, Collections.emptySet()).toURLParam("query."));
            assertEquals(new Pair<>("query.Foo~notin", (String)null), new InClause(fieldKey, Collections.emptySet(), true, true).toURLParam("query."));

            // Non-null parameters only
            assertEquals(new Pair<>("query.Foo~in", "1;2;3"), new InClause(fieldKey, Arrays.asList(1, 2, 3)).toURLParam("query."));
            assertEquals(new Pair<>("query.Foo~notin", "1;2;3"), new InClause(fieldKey, Arrays.asList(1, 2, 3), true, true).toURLParam("query."));

            // Include null parameter
            assertEquals(new Pair<>("query.Foo~in", "Bar;Blip;"), new InClause(fieldKey, Arrays.asList("Bar", "Blip", "")).toURLParam("query."));
            assertEquals(new Pair<>("query.Foo~notin", "Bar;Blip;"), new InClause(fieldKey, Arrays.asList("Bar", "Blip", ""), true, true).toURLParam("query."));
        }

        @Test
        public void testInClauseParsing() throws Exception
        {
            validateInClause("http://localhost/labkey/query/executeQuery.view?query.Foo~notin=", true, true);
            validateInClause("http://localhost/labkey/query/executeQuery.view?query.Foo~notin=1", true, false, "1");
            validateInClause("http://localhost/labkey/query/executeQuery.view?query.Foo~notin=1;2;3", true, false, "1", "2", "3");
            validateInClause("http://localhost/labkey/query/executeQuery.view?query.Foo~notin=1;", true, true, "1");
            validateInClause("http://localhost/labkey/query/executeQuery.view?query.Foo~notin=1;;2", true, true, "1", "2");
        }

        private void validateInClause(String url, boolean negated, boolean includeNull, Object... expectedValues) throws URISyntaxException
        {
            SimpleFilter f = new SimpleFilter();
            f.addUrlFilters(new URLHelper(url), "query");
            InClause clause = (InClause)f.getClauses().get(0);
            assertEquals("negated didn't match for IN clause", negated, clause._negated);
            assertEquals("includeNull didn't match for IN clause", includeNull, clause._includeNull);
            // Convert to sets because we don't care about order for IN clauses
            assertEquals("Parameter values didn't match for IN clause", new HashSet<>(Arrays.asList(expectedValues)), new HashSet<>(Arrays.asList(clause._paramVals)));
        }

        private static final int IN_CLAUSE_SIZE = 5000;

        @Test
        public void testLargeInClause() throws Exception
        {
            User user = TestContext.get().getUser();

            testActiveUsersInClause(0, Collections.emptyList());
            testActiveUsersInClause(1, Collections.singleton(user.getUserId()));
            testActiveUsersInClause(1, Collections.singleton(user));

            Sort sort = new Sort();
            sort.appendSortColumn(FieldKey.fromParts("UserId"), Sort.SortDirection.DESC, false);
            TableInfo usersTable = CoreSchema.getInstance().getTableInfoActiveUsers();
            List<Integer> userIdsDesc = new TableSelector(usersTable, Collections.singletonList(usersTable.getColumn("UserId")),
                                                          null, sort).getArrayList(Integer.class);

            Collection<Integer> ids = new LinkedList<>();
            for (int i = 0; i < IN_CLAUSE_SIZE; i++)
                ids.add(i);

            int expected = howManyLessThan(userIdsDesc, IN_CLAUSE_SIZE);
            testActiveUsersInClause(expected, ids);
            testActiveUsersInClause(expected, ids, ids);

            // Test even bigger
            for (int i = IN_CLAUSE_SIZE; i < IN_CLAUSE_SIZE * 2; i++)
                ids.add(i);

            expected = howManyLessThan(userIdsDesc, IN_CLAUSE_SIZE * 2);
            testActiveUsersInClause(expected, ids);
            testActiveUsersInClause(expected, ids, ids, ids);

            // Test a little smaller than the IN_CLAUSE_SIZE
            ids = new LinkedList<>();
            for (int i = 0; i < IN_CLAUSE_SIZE - 10; i++)
                ids.add(i);

            expected = howManyLessThan(userIdsDesc, IN_CLAUSE_SIZE - 10);
            testActiveUsersInClause(expected, ids);
            testActiveUsersInClause(expected, ids, ids);

            // Test even smaller
            ids = new LinkedList<>();
            for (int i = 0; i < 1500; i++)
                ids.add(i);

            expected = howManyLessThan(userIdsDesc, 1500);
            testActiveUsersInClause(expected, ids);
            testActiveUsersInClause(expected, ids, ids);
        }

        private void testActiveUsersInClause(int expectedSize, Collection... users)
        {
            SimpleFilter f = new SimpleFilter();
            OrClause orClause = new OrClause();
            for (Collection userSet : users)
            {
                orClause.addClause(new InClause(FieldKey.fromParts("UserId"), userSet));
            }
            f.addClause(orClause);
            TableSelector userSelector = new TableSelector(CoreSchema.getInstance().getTableInfoActiveUsers(), f, null);
            Collection<User> ret = userSelector.getCollection(User.class);
            assertEquals(expectedSize, ret.size());
        }

        private int howManyLessThan(List<Integer> userIdsDesc, int max)
        {
            int howMany = userIdsDesc.size();
            for (Integer id : userIdsDesc)
            {
                if (max > id)
                    break;
                howMany -= 1;
            }
            return howMany;
        }
    }

    public static class BetweenClauseTestCase extends ClauseTestCase
    {
        @Test(expected=IllegalArgumentException.class)
        public void testBetweenNull()
        {
            FieldKey fieldKey = FieldKey.fromParts("Foo");
            new CompareType.BetweenClause(fieldKey, null, null, false);
        }

        @Test(expected=IllegalArgumentException.class)
        public void testBetweenEmpty()
        {
            FieldKey fieldKey = FieldKey.fromParts("Foo");
            new CompareType.BetweenClause(fieldKey, "", " ", false);
        }

        @Test
        public void testBetweenSpace()
        {
            // Allow space in filters
            FieldKey fieldKey = FieldKey.fromParts("Foo");
            new CompareType.BetweenClause(fieldKey, " ", " ", false);
        }

        @Test
        public void testBetweenGeneratedSql()
        {
            FieldKey fieldKey = FieldKey.fromParts("Foo");
            SqlDialect mockDialect = new MockSqlDialect();

            test("Foo BETWEEN 1 AND 2", "Foo BETWEEN 1 AND 2", new CompareType.BetweenClause(fieldKey, 1, 2, false), mockDialect);
            test("Foo BETWEEN -1 AND 2.2", "Foo BETWEEN -1 AND 2.2", new CompareType.BetweenClause(fieldKey, -1, 2.2, false), mockDialect);
            test("Foo NOT BETWEEN '1' AND '2'", "Foo NOT BETWEEN 1 AND 2", new CompareType.BetweenClause(fieldKey, "1", "2", true), mockDialect);
            test("Foo BETWEEN 'A' AND 'Z'", "Foo BETWEEN A AND Z", new CompareType.BetweenClause(fieldKey, "A", "Z", false), mockDialect);
            test("Foo BETWEEN ' A' AND 'Z '", "Foo BETWEEN ' A' AND 'Z '", new CompareType.BetweenClause(fieldKey, " A", "Z ", false), mockDialect);
        }

        @Test
        public void testBetweenQueryString()
        {
            FieldKey fieldKey = FieldKey.fromParts("Foo");

            assertEquals(Pair.of("query.Foo~between", "1,2"), new CompareType.BetweenClause(fieldKey, 1, 2, false).toURLParam("query."));
            assertEquals(Pair.of("query.Foo~between", "1,2"), new CompareType.BetweenClause(fieldKey, "1", "2", false).toURLParam("query."));
            assertEquals(Pair.of("query.Foo~notbetween", "-1,2.2"), new CompareType.BetweenClause(fieldKey, -1, 2.2, true).toURLParam("query."));
            assertEquals(Pair.of("query.Foo~between", "A,Z"), new CompareType.BetweenClause(fieldKey, "A", "Z", false).toURLParam("query."));
        }
    }
}
