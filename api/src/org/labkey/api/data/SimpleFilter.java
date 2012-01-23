/*
 * Copyright (c) 2005-2011 Fred Hutchinson Cancer Research Center
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
import org.jetbrains.annotations.Nullable;
import org.junit.Test;
import org.labkey.api.data.CompareType.CompareClause;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.collections.CaseInsensitiveHashSet;

import java.util.*;

/**
 * User: arauch
 * Date: Jan 11, 2005
 * Time: 8:04:37 AM
 */
public class SimpleFilter implements Filter
{
    public static abstract class FilterClause
    {
        private boolean _urlClause = false;
        private Object[] _paramVals = new Object[0];


        boolean isUrlClause()
        {
            return _urlClause;
        }


        void setUrlClause(boolean urlClause)
        {
            _urlClause = urlClause;
        }


        public Object[] getParamVals()
        {
            return _paramVals;
        }


        public void setParamVals(Object[] paramVals)
        {
            _paramVals = paramVals;
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
            SQLFragment sqlf = toSQLFragment(Collections.<String, ColumnInfo>emptyMap(), null);
            if (sqlf.isEmpty())
                sb.append("1=1");
            else
                sb.append(sqlf);
        }

        abstract public List<String> getColumnNames();

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

        protected static String getLabKeySQLColName(String simpleFilterColName)
        {
            FieldKey key = FieldKey.fromString(simpleFilterColName);
            List<String> parts = key.getParts();
            StringBuilder escapedColName = new StringBuilder();
            String sep = "";
            for (String part : parts)
            {
                escapedColName.append(sep).append("\"").append(part).append("\"");
                sep = ".";
            }
            return escapedColName.toString();
        }

        public abstract String getLabKeySQLWhereClause(Map<FieldKey, ? extends ColumnInfo> columnMap);

        public abstract SQLFragment toSQLFragment(Map<String, ? extends ColumnInfo> columnMap, SqlDialect dialect);
    }

    public static class SQLClause extends FilterClause
    {
        String _fragment;
        private List<String> _colNames = new ArrayList<String>();


        public SQLClause(String fragment, Object[] paramVals, String... colNames)
        {
            setUrlClause(false);
            _fragment = fragment;
            if (paramVals == null)
            {
                paramVals = new Object[0];
            }
            setParamVals(paramVals);
            _colNames = Arrays.asList(colNames);
        }

        public SQLFragment toSQLFragment(Map<String, ? extends ColumnInfo> columnMap, SqlDialect dialect)
        {
            return new SQLFragment(_fragment, getParamVals());
        }

        public List<String> getColumnNames()
        {
            return _colNames;
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

        public List<String> getColumnNames()
        {
            List<String> result = new ArrayList<String>();
            for (FilterClause clause : _clauses)
            {
                result.addAll(clause.getColumnNames());
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

        public SQLFragment toSQLFragment(Map<String, ? extends ColumnInfo> columnMap, SqlDialect dialect)
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

        public List<String> getColumnNames()
        {
            return _clause.getColumnNames();
        }

        public Object[] getParamVals()
        {
            return _clause.getParamVals();
        }

        public SQLFragment toSQLFragment(Map<String, ? extends ColumnInfo> columnMap, SqlDialect dialect)
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

    public static class InClause extends FilterClause
    {
        private String _colName;
        private boolean _isNegated = false;

        public InClause(String colName, Collection params)
        {
            this(colName, params, false);
        }

        public InClause(String colName, Collection params, boolean urlClause)
        {
            this(colName, params, urlClause, false);
        }

        public InClause(String colName, Collection params, boolean urlClause, boolean isNegated)
        {
            setUrlClause(urlClause);
            setParamVals(params.toArray());
            _colName = colName;
            _isNegated = isNegated;
        }

        public List<String> getColumnNames()
        {
            return Arrays.asList(_colName);
        }

        @Override
        protected void appendFilterText(StringBuilder sb, ColumnNameFormatter formatter)
        {
            sb.append(formatter.format(_colName));
            if(_isNegated)
                sb.append(" IS NOT ANY OF (");
            else
                sb.append(" IS ONE OF (");

            String sep = "";
            for (Object val : getParamVals())
            {
                if (val != null)
                {
                    sb.append(sep).append(val.toString());
                    sep = ", ";
                }
            }
            sb.append(")");
        }

        @Override
        public String getLabKeySQLWhereClause(Map<FieldKey, ? extends ColumnInfo> columnMap)
        {
            String selectName = getLabKeySQLColName(_colName);
            ColumnInfo col = columnMap.get(FieldKey.fromString(_colName));
            StringBuilder in =  new StringBuilder(selectName);

            in.append(" " + (_isNegated ? "NOT " : "") + "IN (");

            Object[] params = getParamVals();
            if (params.length > 0)
            {
                String sep = "";
                for (Object param : params)
                {
                    in.append(sep).append(escapeLabKeySqlValue(param, col.getJdbcType()));
                    sep = ", ";
                }
            }
            else
            {
                in.append("NULL");  // Empty list case; "WHERE column IN (NULL)" should always be false
            }

            in.append(")");
            return in.toString();
        }


        public SQLFragment toSQLFragment(Map<String, ? extends ColumnInfo> columnMap, SqlDialect dialect)
        {
            Object[] params = getParamVals();

            if (0 == params.length)
                return new SQLFragment("1=0");

            ColumnInfo colInfo = columnMap.get(_colName);
            String alias = _colName;
            if (colInfo != null)
            {
                alias = dialect.getColumnSelectName(colInfo.getAlias());
            }
            SQLFragment in = new SQLFragment(alias);

            in.append(" " + (_isNegated ? "NOT " : "") + "IN (");

            if (params.length > 0)
            {
                String questionMarks = StringUtils.repeat("?, ", params.length);
                in.append(questionMarks.substring(0, questionMarks.length() - 2));
            }
            else
            {
                in.append("NULL");  // Empty list case; "WHERE column IN (NULL)" should always be false
            }

            in.append(")");

            if (colInfo == null || !isUrlClause() || getParamVals() == null)
            {
                in.addAll(getParamVals());
            }
            else
            {
                for (Object paramVal : getParamVals())
                {
                    in.add(CompareType.convertParamValue(colInfo, paramVal));
                }
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
            setParamVals(values.toArray());
        }

        @Override
        public boolean meetsCriteria(Object value)
        {
            for (Object params : getParamVals())
            {
                // Loop through all the values and check if any of them are equals
                FilterClause compareClause = CompareType.EQUAL.createFilterClause(getColumnNames().get(0), params);
                if (compareClause.meetsCriteria(value))
                {
                    return true;
                }
            }
            return false;
        }
    }

    public static class ContainsInClause extends FilterClause
    {
        private String _colName;
        private boolean _isNegated = false;

        public ContainsInClause(String colName, Collection params)
        {
            this(colName, params, false, false);
        }

        public ContainsInClause(String colName, Collection params, boolean urlClause)
        {
            this(colName, params, urlClause, false);
        }

        public ContainsInClause(String colName, Collection params, boolean urlClause, boolean isNegated)
        {
            setUrlClause(urlClause);
            setParamVals(params.toArray());
            _colName = colName;
            _isNegated = isNegated;
        }

        public List<String> getColumnNames()
        {
            return Arrays.asList(_colName);
        }

        @Override
        protected void appendFilterText(StringBuilder sb, ColumnNameFormatter formatter)
        {
            sb.append(formatter.format(_colName));
            sb.append(" " +
                    (_isNegated ? "DOES NOT CONTAIN ANY OF (" : "CONTAINS ONE OF ("));
            String sep = "";
            for (Object val : getParamVals())
            {
                if (val != null)
                {
                    sb.append(sep).append(val.toString());
                    sep = ", ";
                }
            }
            sb.append(")");
        }

        @Override
        public String getLabKeySQLWhereClause(Map<FieldKey, ? extends ColumnInfo> columnMap)
        {
            ColumnInfo col = columnMap.get(FieldKey.fromString(_colName));

            Object[] params = getParamVals();
            if (params.length > 0)
            {
                return getContainsClause(col).toString();
            }

            //TODO: _isNegated
            return col.getName() + (_isNegated ? " NOT IN" : " IN ") + " (NULL)";  // Empty list case; "WHERE column IN (NULL)" should always be false
        }


        public SQLFragment toSQLFragment(Map<String, ? extends ColumnInfo> columnMap, SqlDialect dialect)
        {
            Object[] params = getParamVals();

            if (0 == params.length)
                return new SQLFragment("1=0");

            ColumnInfo colInfo = columnMap.get(_colName);
            String alias = _colName;
            if (colInfo != null)
            {
                alias = dialect.getColumnSelectName(colInfo.getAlias());
            }

            SQLFragment in = new SQLFragment();
            OperationClause oc = getContainsClause(colInfo);
            if(oc.getClauses().size() > 0)
                return in.append(oc.toSQLFragment(columnMap, dialect));

            return in.append(alias + (_isNegated ? " NOT IN" : " IN ") + "(NULL)");  // Empty list case; "WHERE column IN (NULL)" should always be false
        }

        private OperationClause getContainsClause(ColumnInfo colInfo)
        {
            Object[] params = getParamVals();
            OperationClause oc;
            if(_isNegated)
                oc = new AndClause();
            else
                oc = new OrClause();


            if (params.length > 0)
            {
                for(Object param : params)
                {
                    if(_isNegated)
                    {
                        oc.addClause(new CompareType.DoesNotContainClause(colInfo.getName(), param));
                    }
                    else
                    {
                        oc.addClause(new CompareType.ContainsClause(colInfo.getName(), param));
                    }
                }
            }

            //always allow null for NOT IN
            if(_isNegated)
            {
                OrClause clause = new OrClause();
                clause.addClause(oc);
                clause.addClause(CompareType.ISBLANK.createFilterClause(colInfo.getName(), null));
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
            setParamVals(values.toArray());
        }

        @Override
        public boolean meetsCriteria(Object value)
        {
            for (Object params : getParamVals())
            {
                // Loop through all the values and check if any of them are equals
                FilterClause compareClause = CompareType.CONTAINS.createFilterClause(getColumnNames().get(0), params);
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

    public SimpleFilter(String colName, Object value)
    {
        addCondition(colName, value);
    }

    public SimpleFilter(String colName, Object value, CompareType compare)
    {
        addCondition(colName, value, compare);
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
                String[] compareInfo = colTildeCompare.split("~");
                CompareType type = null;
                String columnName = compareInfo[0];
                if (compareInfo.length == 2)
                    type = CompareType.getByURLKey(compareInfo[1]);

                if (null == type)
                    continue;

                try
                {
                    FilterClause fc = type.createFilterClause(columnName, param);
                    fc.setUrlClause(true);
                    _clauses.add(fc);
                }
                catch (ConversionException e)
                {
                    // rethrow with better error message.  Date CompareTypes convert the parameter when created.
                    throw new ConversionException("Could not convert \"" + param + "\" for column \"" + columnName + "\"", e);
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

    public List<String> getAllColumnNames()
    {
        List<String> result = new ArrayList<String>();
        for (FilterClause clause : _clauses)
        {
            result.addAll(clause.getColumnNames());
        }
        return result;
    }

    public SimpleFilter deleteConditions(String colName)
    {
        for (Iterator<SimpleFilter.FilterClause> it = _clauses.iterator() ; it.hasNext(); )
        {
            SimpleFilter.FilterClause clause = it.next();
            CaseInsensitiveHashSet names = new CaseInsensitiveHashSet(clause.getColumnNames());
            if (names.contains(colName))
                it.remove();
        }
        return this;
    }

    public SimpleFilter addCondition(String colName, Object value)
    {
        return addCondition(colName, value == null ? Parameter.NULL_MARKER : value, CompareType.EQUAL);
    }

    public SimpleFilter addCondition(ColumnInfo column, Object value)
    {
        return addCondition(column.getAlias(), value == null ? Parameter.NULL_MARKER : value, CompareType.EQUAL);
    }

    public SimpleFilter addCondition(ColumnInfo column, Object value, CompareType compare)
    {
        return addCondition(column.getAlias(), value, compare);
    }

    public SimpleFilter addCondition(String colName, @Nullable Object value, CompareType compare)
    {
        _clauses.add(compare.createFilterClause(colName, value));
        return this;
    }

    public SimpleFilter addCondition(FilterClause clause)
    {
        _clauses.add(clause);
        return this;
    }

    public SimpleFilter addBetween(String colName, Comparable value1, Comparable value2)
    {
        if (value1 != null && value2 != null && value1.equals(value2))
            addCondition(colName, value1);  // Equal
        else if (value1 != null && value2 != null && value1.compareTo(value2) > 0)
        {
            addCondition(colName, value2, CompareType.GTE);
            addCondition(colName, value1, CompareType.LTE);
        }
        else
        {
            addCondition(colName, value1, CompareType.GTE);
            addCondition(colName, value2, CompareType.LTE);
        }
        return this;
    }

    public SimpleFilter addWhereClause(String fragment, Object[] paramVals, String... colNames)
    {
        _clauses.add(new SQLClause(fragment, paramVals, colNames));
        return this;
    }

    public SimpleFilter addInClause(String colName, Collection paramVals)
    {
        _clauses.add(new InClause(colName, paramVals));
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
                ret.append(PageFlowUtil.encode(prefix + cc._colName + "~" + cc._comparison.getPreferredUrlKey()));
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
            else if (fc instanceof SimpleFilter.InClause)
            {
                SimpleFilter.InClause inClause = (SimpleFilter.InClause)fc;
                urlType = CompareType.IN.getPreferredUrlKey();
                StringBuilder values = new StringBuilder();
                String separator = "";
                for (Object inValue : inClause.getParamVals())
                {
                    values.append(separator);
                    separator = ";";
                    values.append(inValue == null ? "" : inValue.toString());
                }
                value = values.toString();
            }

            if (urlType != null)
            {
                String key = prefix + fc.getColumnNames().get(0) + "~" + urlType;
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
        return getSQLFragment(dialect, new HashMap<String, ColumnInfo>());
    }

    public SQLFragment getSQLFragment(SqlDialect dialect, Map<String, ? extends ColumnInfo> columnMap)
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


    public Set<String> getWhereParamNames()
    {
        Set<String> paramNames = new HashSet<String>(_clauses.size());

        for (FilterClause fc : _clauses)
            paramNames.addAll(fc.getColumnNames());

        return paramNames;
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
        public String format(String columnName)
        {
            return FieldKey.fromString(columnName).getDisplayString();
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

            CompareClause eqCompareClause = new CompareType.DateEqCompareClause("x", cJan15);
            assertFalse(eqCompareClause.meetsCriteria(dJan14));
            assertFalse(eqCompareClause.meetsCriteria(dJan14noon));
            assertTrue(eqCompareClause.meetsCriteria(dJan15));
            assertTrue(eqCompareClause.meetsCriteria(dJan15noon));
            assertFalse(eqCompareClause.meetsCriteria(dJan16));
            assertFalse(eqCompareClause.meetsCriteria(dJan16noon));

            CompareClause neqCompareClause = new CompareType.DateNeqCompareClause("x", cJan15);
            assertTrue(neqCompareClause.meetsCriteria(dJan14));
            assertTrue(neqCompareClause.meetsCriteria(dJan14noon));
            assertFalse(neqCompareClause.meetsCriteria(dJan15));
            assertFalse(neqCompareClause.meetsCriteria(dJan15noon));
            assertTrue(neqCompareClause.meetsCriteria(dJan16));
            assertTrue(neqCompareClause.meetsCriteria(dJan16noon));

            CompareClause ltCompareClause = new CompareType.DateLtCompareClause("x", cJan15);
            assertTrue(ltCompareClause.meetsCriteria(dJan14));
            assertTrue(ltCompareClause.meetsCriteria(dJan14noon));
            assertFalse(ltCompareClause.meetsCriteria(dJan15));
            assertFalse(ltCompareClause.meetsCriteria(dJan15noon));
            assertFalse(ltCompareClause.meetsCriteria(dJan16));
            assertFalse(ltCompareClause.meetsCriteria(dJan16noon));

            CompareClause lteCompareClause = new CompareType.DateLteCompareClause("x", cJan15);
            assertTrue(lteCompareClause.meetsCriteria(dJan14));
            assertTrue(lteCompareClause.meetsCriteria(dJan14noon));
            assertTrue(lteCompareClause.meetsCriteria(dJan15));
            assertTrue(lteCompareClause.meetsCriteria(dJan15noon));
            assertFalse(lteCompareClause.meetsCriteria(dJan16));
            assertFalse(lteCompareClause.meetsCriteria(dJan16noon));

            CompareClause gtCompareClause = new CompareType.DateGtCompareClause("x", cJan15);
            assertFalse(gtCompareClause.meetsCriteria(dJan14));
            assertFalse(gtCompareClause.meetsCriteria(dJan14noon));
            assertFalse(gtCompareClause.meetsCriteria(dJan15));
            assertFalse(gtCompareClause.meetsCriteria(dJan15noon));
            assertTrue(gtCompareClause.meetsCriteria(dJan16));
            assertTrue(gtCompareClause.meetsCriteria(dJan16noon));

            CompareClause gteCompareClause = new CompareType.DateGteCompareClause("x", cJan15);
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
}
