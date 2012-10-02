/*
 * Copyright (c) 2005-2012 Fred Hutchinson Cancer Research Center
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

import junit.framework.Assert;
import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;
import org.labkey.api.data.CompareType.CompareClause;
import org.labkey.api.data.dialect.MockSqlDialect;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: arauch
 * Date: Jan 11, 2005
 * Time: 8:04:37 AM
 */
public class SimpleFilter implements Filter
{
    public static final String SEPARATOR_CHAR = "~";

    public static abstract class FilterClause
    {
        protected boolean _urlClause = false;
        protected Object[] _paramVals = new Object[0];   // TODO: _paramVals, getter, and callers should all be @NotNull. Consider List<?> as well
        protected boolean _includeNull = false;
        protected boolean _negated = false;

        public boolean isUrlClause()
        {
            return _urlClause;
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
            int fromIndex = sb.length();
            appendSqlText(sb, formatter);
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
                if (o instanceof Calendar)
                    o = ((Calendar)o).getTime();
                String param = StringUtils.defaultString(ConvertUtils.convert(o), "NULL");
                int i = sb.indexOf("?", fromIndex);
                fromIndex += param.length();         // Protects against previous param values in this clause containing '?'
                sb.replace(i, i + 1, param);
            }
        }


        protected void appendSqlText(StringBuilder sb, ColumnNameFormatter formatter)
        {
            SQLFragment sqlf = toSQLFragment(Collections.<FieldKey, ColumnInfo>emptyMap(), null);
            if (sqlf.isEmpty())
                sb.append("1=1");
            else
                sb.append(sqlf);
        }

        @Deprecated // Use getFieldKeys() instead
        abstract public List<String> getColumnNames();

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
            if (_urlClause != that._urlClause) return false;
            // Probably incorrect - comparing Object[] arrays with Arrays.equals
            if (!Arrays.equals(_paramVals, that._paramVals)) return false;
            if (!getFieldKeys().equals(that.getFieldKeys())) return false;

            return true;
        }

        @Override
        public int hashCode()
        {
            int result = (_urlClause ? 1 : 0);
            result = 31 * result + (_paramVals != null ? Arrays.hashCode(_paramVals) : 0);
            result = 31 * result + (_includeNull ? 1 : 0);
            result = 31 * result + (_negated ? 1 : 0);
            return result;
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
        String _fragment;
        private List<FieldKey> _fieldKeys = new ArrayList<FieldKey>();

        public SQLClause(String fragment, Object[] paramVals, FieldKey... fieldKeys)
        {
            _urlClause = false;
            _fragment = fragment;
            if (paramVals == null)
            {
                paramVals = new Object[0];
            }
            _paramVals = paramVals;
            _fieldKeys = Arrays.asList(fieldKeys);
        }

        public SQLFragment toSQLFragment(Map<FieldKey, ? extends ColumnInfo> columnMap, SqlDialect dialect)
        {
            return new SQLFragment(_fragment, getParamVals());
        }

        @Deprecated // Use .getFieldKeys() instead.
        public List<String> getColumnNames()
        {
            List<String> colNames = new ArrayList<String>(_fieldKeys.size());
            for (FieldKey fieldKey : _fieldKeys)
                colNames.add(fieldKey.toString());
            return colNames;
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

    public abstract static class OperationClause extends FilterClause
    {
        private List<FilterClause> _clauses;
        private String _operation;

        protected OperationClause(String operation, FilterClause... clauses)
        {
            _operation = operation;
            _clauses = new ArrayList<FilterClause>(Arrays.asList(clauses));
        }

        @Deprecated // Use getFieldKeys() instead.
        public List<String> getColumnNames()
        {
            List<String> result = new ArrayList<String>();
            for (FilterClause clause : _clauses)
            {
                result.addAll(clause.getColumnNames());
            }
            return result;
        }

        public List<FieldKey> getFieldKeys()
        {
            List<FieldKey> result = new ArrayList<FieldKey>();
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
            List<Object> result = new ArrayList<Object>();
            for (FilterClause clause : _clauses)
            {
                if(clause.getParamVals() != null)
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
            SQLFragment labKeySql = new SQLFragment();
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
            for(FilterClause clause : _clauses)
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

        @Deprecated // Use getFieldKeys() instead.
        public List<String> getColumnNames()
        {
            return _clause.getColumnNames();
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
            SQLFragment sqlFragment = new SQLFragment();
            sqlFragment.append(" NOT (");
            sqlFragment.append(_clause.getLabKeySQLWhereClause(columnMap));
            sqlFragment.append(")");
            return sqlFragment.toString();
        }

        @Override
        public boolean meetsCriteria(Object value)
        {
            return !_clause.meetsCriteria(value);
        }
    }

    public static abstract class MultiValuedFilterClause extends FilterClause
    {
        @NotNull protected FieldKey _fieldKey;
        public static final int MAX_FILTER_VALUES_TO_DISPLAY = 10;

        public MultiValuedFilterClause(FieldKey fieldKey, Collection params)
        {
            if (params.contains("")) //params.size() == 0 ||
            {
                _includeNull = true;
                params.remove("");
            }
            _fieldKey = fieldKey;
            _paramVals = params.toArray();
        }

        public FieldKey getFieldKey()
        {
            return _fieldKey;
        }

        public abstract CompareType getCompareType();
    }

    public static class InClause extends MultiValuedFilterClause
    {
        @Deprecated // Use FieldKey version instead.
        public InClause(String colName, Collection params)
        {
            this(colName, params, false);
        }

        public InClause(FieldKey fieldKey, Collection params)
        {
            this(fieldKey, params, false, false);
        }

        @Deprecated // Use FieldKey version instead.
        public InClause(String colName, Collection params, boolean urlClause)
        {
            this(colName, params, urlClause, false);
        }

        public InClause(FieldKey fieldKey, Collection params, boolean urlClause)
        {
            this(fieldKey, params, urlClause, false);
        }

        @Deprecated // Use FieldKey version instead.
        public InClause(String colName, Collection params, boolean urlClause, boolean negated)
        {
            this(FieldKey.fromString(colName), params, urlClause, negated);
        }

        public InClause(FieldKey fieldKey, Collection params, boolean urlClause, boolean negated)
        {
            super(fieldKey, params);

            _urlClause = urlClause;
            _negated = negated;
        }

        @Deprecated // Use getFieldKeys() instead.
        public List<String> getColumnNames()
        {
            return Arrays.asList(getFieldKey().toString());
        }

        public List<FieldKey> getFieldKeys()
        {
            return Arrays.asList(getFieldKey());
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
                sb.append(" has any value");
                return;
            }

            if (isNegated())
                sb.append(" IS NOT ANY OF (");
            else
                sb.append(" IS ONE OF (");

            //TODO: if number of values > 10, don't show each one
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
                    sb.append(sep).append(val.toString());
                    sep = ", ";
                }
            }

            if ("".equals(sep) || isIncludeNull())
            {
                sb.append(sep + "BLANK");
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
                    in.append(alias + " IS " + (isNegated() ? " NOT " : "") + "NULL");
                else if (!isNegated())
                    in.append(alias + " IN (NULL)");  // Empty list case; "WHERE column IN (NULL)" should always be false

                return in.toString();
            }

            in.append("((" + alias);
            in.append(" " + (isNegated() ? "NOT " : "") + "IN (");
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
                    in.append(alias + " IS " + (isNegated() ? " NOT " : "") + "NULL");
                else if (!isNegated())
                    in.append(alias + " IN (NULL)");  // Empty list case; "WHERE column IN (NULL)" should always be false
                else
                    in.append("1=1");

                return in;
            }

            Object[] convertedParams;

            if (null == colInfo || !isUrlClause())
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
            in.append(" ");

            // Dialect may want to generate database-specific SQL, especially for very large IN clauses
            dialect.appendInClauseSql(in, convertedParams);

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
            Set<Object> values = new HashSet<Object>(Arrays.asList(getParamVals()));
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

        public List<String> getColumnNames()
        {
            return Arrays.asList(getFieldKey().toString());
        }

        public List<FieldKey> getFieldKeys()
        {
            return Arrays.asList(getFieldKey());
        }

        public ContainsOneOfClause(FieldKey fieldKey, Collection params, boolean urlClause, boolean negated)
        {
            super(fieldKey, params);

            _urlClause = urlClause;
            _fieldKey = fieldKey;
            _negated = negated;
        }

        @Override
        public CompareType getCompareType()
        {
            return _negated ? CompareType.CONTAINS_ONE_OF : CompareType.CONTAINS_NONE_OF;
        }

        @Override
        protected void appendFilterText(StringBuilder sb, ColumnNameFormatter formatter)
        {
            //TODO: if nmber of values > 10, dont show each one
            sb.append(formatter.format(getFieldKey()));
            sb.append(" " +
                    (isNegated() ? "DOES NOT CONTAIN ANY OF (" : "CONTAINS ONE OF ("));

            //TODO: if number of values > 10, dont show each one
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
                    sb.append(sep).append(val.toString());
                    sep = ", ";
                }
            }

            if(isIncludeNull())
                sb.append(sep + "BLANK");

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
            Object[] params = getParamVals();

            ColumnInfo colInfo = columnMap != null ? columnMap.get(getFieldKey()) : null;
            String alias = colInfo != null ? colInfo.getAlias() : getFieldKey().getName();

            SQLFragment in = new SQLFragment();
            OperationClause oc = getContainsClause(colInfo);
            if(oc.getClauses().size() > 0)
                return in.append(oc.toSQLFragment(columnMap, dialect));

            return in.append(alias + (isNegated() ? " NOT IN " : " IN ") + "(NULL)");  // Empty list case; "WHERE column IN (NULL)" should always be false
        }

        private OperationClause getContainsClause(ColumnInfo colInfo)
        {
            Object[] params = getParamVals();
            OperationClause oc;
            if(isNegated())
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
            if(isIncludeNull())
            {
                OrClause clause = new OrClause();
                if(oc._clauses.size() > 0)
                    clause.addClause(oc);

                if(isNegated())
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
            Set<Object> values = new HashSet<Object>(Arrays.asList(getParamVals()));
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

    private ArrayList<FilterClause> _clauses = new ArrayList<FilterClause>();


    public SimpleFilter()
    {
    }

    public SimpleFilter(FilterClause... clauses)
    {
        _clauses = new ArrayList<FilterClause>(Arrays.asList(clauses));
    }

    public SimpleFilter(Filter filter)
    {
        if (null == filter)
            return;
        SimpleFilter src = (SimpleFilter) filter;
        _clauses = (ArrayList<FilterClause>) src._clauses.clone();
    }

    @Deprecated /** Use FieldKey version instead. */
    public SimpleFilter(String colName, Object value)
    {
        addCondition(colName, value);
    }

    public SimpleFilter(FieldKey fieldKey, Object value)
    {
        addCondition(fieldKey, value);
    }

    @Deprecated /** Use FieldKey version instead. */
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
        String prefix = regionName == null ? "" : regionName + ".";
        String[] filterKeys = urlHelp.getKeysByPrefix(prefix);
        for (String filterKey : filterKeys)
        {
            String colTildeCompare = filterKey.substring(prefix.length());

            String[] params = urlHelp.getParameters(filterKey);
            for (String param : params)
            {
                String[] compareInfo = colTildeCompare.split(SEPARATOR_CHAR);
                CompareType type = null;
                FieldKey fieldKey = FieldKey.fromString(compareInfo[0]);
                if (compareInfo.length == 2)
                    type = CompareType.getByURLKey(compareInfo[1]);

                if (null == type)
                    continue;

                try
                {
                    FilterClause fc = type.createFilterClause(fieldKey, param);
                    fc._urlClause = true;
                    _clauses.add(fc);
                }
                catch (ConversionException e)
                {
                    // rethrow with better error message.  Date CompareTypes convert the parameter when created.
                    throw new ConversionException("Could not convert \"" + param + "\" for column \"" + fieldKey.toDisplayString() + "\"", e);
                }
            }
        }
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
        List<FieldKey> result = new ArrayList<FieldKey>();
        for (FilterClause clause : _clauses)
        {
            result.addAll(clause.getFieldKeys());
        }
        return result;
    }

    @Deprecated // Use getAllFieldKeys() instead.
    public List<String> getAllColumnNames()
    {
        List<String> result = new ArrayList<String>();
        for (FilterClause clause : _clauses)
        {
            result.addAll(clause.getColumnNames());
        }
        return result;
    }

    public SimpleFilter deleteConditions(FieldKey fieldKey)
    {
        for (Iterator<SimpleFilter.FilterClause> it = _clauses.iterator() ; it.hasNext(); )
        {
            SimpleFilter.FilterClause clause = it.next();
            if (clause.getFieldKeys().contains(fieldKey))
                it.remove();
        }
        return this;
    }

    @Deprecated // Use FieldKey version instead
    public SimpleFilter deleteConditions(String colName)
    {
        FieldKey fieldKey = FieldKey.fromString(colName);
        deleteConditions(fieldKey);
        return this;
    }

    @Deprecated // Use FieldKey version instead
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

    @Deprecated // Use FieldKey version insead
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

    @Deprecated // Use FieldKey version instead.
    public SimpleFilter addBetween(String colName, Comparable value1, Comparable value2)
    {
        FieldKey fieldKey = FieldKey.fromString(colName);
        return addBetween(fieldKey, value1, value2);
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

    @Deprecated // Use FieldKey version instead.
    public SimpleFilter addInClause(String colName, Collection paramVals)
    {
        _clauses.add(new InClause(colName, paramVals));
        return this;
    }

    public SimpleFilter addInClause(FieldKey fieldKey, Collection paramVals)
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
            if (fc instanceof CompareClause)
            {
                CompareClause cc = (CompareClause) fc;
                ret.append(and);
                and = "&";
                ret.append(PageFlowUtil.encode(prefix + cc._fieldKey.toString() + SEPARATOR_CHAR + cc._comparison.getPreferredUrlKey()));
                if (cc.getParamVals() != null && cc.getParamVals()[0] != null)
                {
                    ret.append("=");
                    ret.append(PageFlowUtil.encode(cc.getParamVals()[0].toString()));
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
            String urlType = null;
            String value = null;
            if (fc instanceof CompareClause)
            {
                CompareClause cc = (CompareClause) fc;
                urlType = cc._comparison.getPreferredUrlKey();
                value = cc.getParamVals() != null && cc.getParamVals()[0] != null ?
                        cc.getParamVals()[0].toString() : null;
            }
            else if (fc instanceof MultiValuedFilterClause)
            {
                MultiValuedFilterClause clause = (MultiValuedFilterClause)fc;
                urlType = clause.getCompareType().getPreferredUrlKey();
                StringBuilder values = new StringBuilder();
                String separator = "";
                for (Object inValue : clause.getParamVals())
                {
                    values.append(separator);
                    separator = ";";
                    values.append(inValue == null ? "" : inValue.toString());
                }
                value = values.toString();
            }

            if (urlType != null)
            {
                String key = prefix + fc.getColumnNames().get(0) + SEPARATOR_CHAR + urlType;
                url.addParameter(key, value);
            }
        }
    }

    /*
    // UNDONE encode()
    public String getParamString()
    {
        StringBuffer sb = new StringBuffer();
        String and = "";
        for (FilterClause fc : _clauses)
        {
            if (fc.isUrlClause() && fc instanceof CompareClause)
            {
                CompareClause cc = (CompareClause) fc;
                sb.append(and);
                sb.append(_regionName);
                sb.append('.');
                sb.append(cc._colName);
                sb.append(cc._comparison);
                sb.append('=');
                sb.append(cc.getParamVals()[0].toString());
                and = "&";
            }
        }
        return sb.toString();
    }
    */


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
        return getSQLFragment(dialect, Collections.<FieldKey, ColumnInfo>emptyMap());
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
            ret.append(fc.toSQLFragment(columnMap, dialect));
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
        Set<FieldKey> paramNames = new HashSet<FieldKey>(_clauses.size());

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

    public String getWhereSQL(SqlDialect dialect)
    {
        SQLFragment frag = getSQLFragment(dialect);
        return frag.getSQL();
    }

    public String getFilterText()
    {
        return getFilterText(new ColumnNameFormatter());
    }

    // Display pseudo English version of filter
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

    public String toSQLString(SqlDialect dialect)
    {
        SQLFragment fragment = getSQLFragment(dialect);
        return dialect.substituteParameters(fragment);
    }

    public boolean hasContainerEqualClause()
    {
        for (FilterClause clause : _clauses)
        {
            if (clause instanceof CompareClause)
            {
                CompareClause compClause = (CompareClause) clause;
                if (compClause.getComparison() == CompareType.EQUAL &&
                        compClause.getColumnNames().size() == 1 &&
                        "container".equalsIgnoreCase(compClause.getColumnNames().get(0)))
                    return true;
            }
            if (clause instanceof InClause)
            {
                InClause inClause = (InClause)clause;
                if (inClause.getColumnNames().size() == 1 &&
                        "container".equalsIgnoreCase(inClause.getColumnNames().get(0)))
                {
                    return true;
                }
            }
        }
        return false;
    }

    // NOTE: We really should take FieldKey instead of String as map key.
    public boolean meetsCriteria(Map<String, ? extends Object> map)
    {
        if (_clauses == null || _clauses.isEmpty())
            return true;

        for (FilterClause clause : _clauses)
        {
            List<String> columns = clause.getColumnNames();
            if (columns.size() == 0)
                throw new IllegalArgumentException("Expected filter criteria column name");
            if (columns.size() > 1)
                throw new IllegalArgumentException("Can't check filter criteria of multi-column clauses");

            String column = columns.get(0);
            Object value = map.get(column);
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


    public static class FilterTestCase extends Assert
    {
        Calendar asCalendar(Object x)
        {
            Calendar c = new GregorianCalendar();
            c.setTimeInMillis(DateUtil.parseDateTime(x.toString()));
            return c;
        }

        @Test
        public void testMeetsCriteria()
        {
            //And
            //Compare
            //Contains
            //Date*

            Date dJan14 = new Date(DateUtil.parseDateTime("2001-01-14"));
            Date dJan14noon = new Date(DateUtil.parseDateTime("2001-01-14 12:00:00"));
            Date dJan15 = new Date(DateUtil.parseDateTime("2001-01-15"));
            Date dJan15noon = new Date(DateUtil.parseDateTime("2001-01-15 12:00:00"));
            Date dJan16 = new Date(DateUtil.parseDateTime("2001-01-16"));
            Date dJan16noon = new Date(DateUtil.parseDateTime("2001-01-16 12:00:00"));

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
    }


    public static class InClauseTestCase extends org.junit.Assert
    {
        @Test
        public void testInClause()
        {
            SqlDialect mockDialect = new MockSqlDialect();
            FieldKey fieldKey = FieldKey.fromParts("Foo");

            // Empty parameter list
            test("Foo IN (NULL)", new InClause(fieldKey, Collections.emptySet()), mockDialect);
            test("1=1", new InClause(fieldKey, Collections.emptySet(), true, true), mockDialect);

            // Non-null parameters only
            test("((Foo IN (1, 2, 3)))", new InClause(fieldKey, PageFlowUtil.set(1, 2, 3)), mockDialect);
            test("((NOT Foo IN (1, 2, 3)) OR Foo IS NULL)", new InClause(fieldKey, PageFlowUtil.set(1, 2, 3), true, true), mockDialect);

            // Include null parameter
            test("((Foo IN ('Blip', 'Bar')) OR Foo IS NULL)", new InClause(fieldKey, PageFlowUtil.set("Bar", "Blip", "")), mockDialect);
            test("((NOT Foo IN ('Blip', 'Bar')) AND Foo IS NOT NULL)", new InClause(fieldKey, PageFlowUtil.set("Bar", "Blip", ""), true, true), mockDialect);
        }

        private void test(String expected, InClause inClause, SqlDialect dialect)
        {
            assertEquals(expected, inClause.toSQLFragment(Collections.<FieldKey, ColumnInfo>emptyMap(), dialect).toString());
        }
    }
}
