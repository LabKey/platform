/*
 * Copyright (c) 2004-2018 Fred Hutchinson Cancer Research Center
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

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.query.FieldKey;
import org.labkey.api.sql.LabKeySql;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.logging.LogHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Descriptor for how to sort a query to a database. May include multiple columns, with separate ascending/descending
 * orders for all of them.
 */
public class Sort
{
    public enum SortDirection
    {
        ASC('+'),
        DESC('-');

        private final char dir;
        SortDirection(char dir)
        {
            this.dir = dir;
        }

        public char getDir()
        {
            return dir;
        }

        public String getSqlDir()
        {
            return dir == '+' ? "ASC" : "DESC";
        }

        public static SortDirection fromString(String s)
        {
            if (s == null || s.isEmpty())
                return ASC;

            if (s.length() == 1)
                return fromChar(s.charAt(0));

            if (DESC.name().equals(s))
                return DESC;
            
            return ASC;
        }

        /** @return {@link SortDirection#DESC} if c is '-', {@link org.labkey.api.data.Sort.SortDirection#ASC} otherwise */
        public static SortDirection fromChar(char c)
        {
            if (DESC.dir == c)
                return DESC;

            return ASC;
        }
     }

    private static final String SORT_KEY = ".sort";
    private final List<SortField> _sortList = new ArrayList<>();

    public static class SortFieldBuilder
    {
        private SortDirection _dir = SortDirection.ASC;
        private FieldKey _fieldKey = null;

        public void setDir(SortDirection dir)
        {
            _dir = dir;
        }

        public void setFieldKey(FieldKey fieldKey)
        {
            _fieldKey = FieldKey.fromParts(fieldKey);
        }

        public SortField create()
        {
            return new SortField(_fieldKey, _dir);
        }
    }

    public static class SortField
    {
        SortDirection _dir = SortDirection.ASC;
        FieldKey _fieldKey;
        boolean _urlClause = false;

        public SortField(FieldKey fieldKey, SortDirection dir)
        {
            if (fieldKey == null)
            {
                throw new NullPointerException("No fieldKey specified");
            }
            _fieldKey = fieldKey;
            if (dir != null)
            {
                _dir = dir;
            }
        }

        @Deprecated // Use FieldKey version instead.
        public SortField(String str)
        {
            String colName;
            if (str.charAt(0) == SortDirection.DESC.dir)
            {
                _dir = SortDirection.DESC;
                colName = str.substring(1);
            }
            else if (str.charAt(0) == SortDirection.ASC.dir)
                colName = str.substring(1);
            else
                colName = str;
            _fieldKey = FieldKey.fromString(colName.trim());
        }

        public String toUrlString()
        {
            return (_dir == SortDirection.ASC ? "+" : "-") + _fieldKey.toString();
        }

        private SQLFragment toOrderByFragment(SqlDialect dialect, DatabaseIdentifier alias)
        {
            SQLFragment sql = new SQLFragment();
            sql.appendIdentifier(alias);
            sql.append(" ");
            sql.append(_dir.getSqlDir());
            return sql;
        }

        public DatabaseIdentifier getSelectName(SqlDialect dialect)
        {
            return dialect.makeDatabaseIdentifier(_fieldKey.getName());
        }

        public FieldKey getFieldKey()
        {
            return _fieldKey;
        }

        @Deprecated // Use .getFieldKey() instead.
        public String getColumnName()
        {
            return _fieldKey.toString();
        }

        public SortDirection getSortDirection()
        {
            return _dir;
        }
        
        public boolean isUrlClause()
        {
            return _urlClause;
        }
    }

    public Sort()
    {
    }

    public Sort(URLHelper urlhelp)
    {
        this(urlhelp, "");
    }

    public Sort(URLHelper urlhelp, String regionName)
    {
        String sortParam = urlhelp.getParameter(regionName + SORT_KEY);
        _init(sortParam, true);
    }

    public Sort(FieldKey sort)
    {
        appendSortColumn(sort, SortDirection.ASC, false);
    }

    public Sort(String sort)
    {
        _init(sort, false);
    }

    private void _init(String sortParam, boolean urlClause)
    {
        String[] sortKeys;

        if (StringUtils.isEmpty(sortParam))
            return;
        sortKeys = sortParam.split(",");
        for (String sortKey : sortKeys)
        {
            if (StringUtils.isEmpty(sortKey))
                continue;
            SortField sf = new SortField(sortKey);
            sf._urlClause = urlClause;
            _sortList.add(sf);
        }
    }


    public void addURLSort(URLHelper urlhelp, String regionName)
    {
        String[] sortKeys;

        for (String sortParam : urlhelp.getParameterValues(regionName + SORT_KEY))
        {
            if (null == sortParam) continue;
            sortKeys = sortParam.split(",");
            //Insert keys backwards since we always insert at the front...
            for (int i = sortKeys.length - 1; i >= 0; i--)
            {
                String k = StringUtils.trimToNull(sortKeys[i]);
                if (null == k)
                    continue;
                if (k.length() == 1 && (k.charAt(0)==SortDirection.ASC.dir || k.charAt(0)==SortDirection.DESC.dir))
                    continue;
                insertSortColumn(k, true);
            }
        }
    }


    /**
     * Insert a sort column to the head of the sort list.
     *
     * @param sortField Name of column to sort on. Use -columnName to indicate a descending sort.
     */
    public void insertSortColumn(SortField sortField)
    {
        insertSortColumn(sortField.getFieldKey(), sortField.getSortDirection(), sortField.isUrlClause());
    }

    /**
     * Insert a sort column to the head of the sort list.
     *
     * @param columnName Name of column to sort on. Use -columnName to indicate a descending sort.
     */
    @Deprecated // Use FieldKey version instead.
    public void insertSortColumn(String columnName)
    {
        insertSortColumn(columnName, false);
    }

    /**
     * Insert an ascending sort column to the head of the sort list.
     *
     * @param fieldKey FieldKey of column to sort on.
     */
    public void insertSortColumn(FieldKey fieldKey)
    {
        insertSortColumn(fieldKey, SortDirection.ASC);
    }

    /**
     * Insert a sort column to the head of the sort list.
     *
     * @param fieldKey FieldKey of column to sort on. Use -fieldKey to indicate a descending sort.
     * @param dir sort direction
     */
    public void insertSortColumn(FieldKey fieldKey, SortDirection dir)
    {
        insertSortColumn(fieldKey, dir, false);
    }

    /**
     * Insert a sort column to the head of the sort list.
     *
     * @param columnName Name of column to sort on. Use -columnName to indicate a descending sort.
     * @param urlClause  Make this column visible on the URL.
     */
    @Deprecated // Use FieldKey version instead.
    public void insertSortColumn(String columnName, boolean urlClause)
    {
        insertSortColumn(columnName, urlClause, 0);
    }

    /**
     * Insert a sort column to the head of the sort list.
     *
     * @param fieldKey FieldKey of column to sort on. Use -columnName to indicate a descending sort.
     * @param dir sort direction
     * @param urlClause  Make this column visible on the URL.
     */
    public void insertSortColumn(FieldKey fieldKey, SortDirection dir, boolean urlClause)
    {
        insertSortColumn(fieldKey, dir, urlClause, 0);
    }

    /**
     * Add a column to the sort.
     *
     * @param columnName Name of column to sort on. Use -columnName to indicate a descending sort.
     * @param urlClause  Make this column visible on the URL.
     * @param insertionIndex Index at which to insert into the sort
     */
    @Deprecated // Use FieldKey version instead.
    public void insertSortColumn(String columnName, boolean urlClause, int insertionIndex)
    {
        SortField sfToInsert = new SortField(columnName);
        sfToInsert._urlClause = urlClause;
        replaceSortColumn(sfToInsert, insertionIndex);
    }

    /**
     * Add a column to the sort.
     *
     * @param fieldKey FieldKey of column to sort on. Use -columnName to indicate a descending sort.
     * @param dir sort direction
     * @param urlClause  Make this column visible on the URL.
     * @param insertionIndex Index at which to insert into the sort
     */
    public void insertSortColumn(FieldKey fieldKey, SortDirection dir, boolean urlClause, int insertionIndex)
    {
        SortField sfToInsert = new SortField(fieldKey, dir);
        sfToInsert._urlClause = urlClause;
        replaceSortColumn(sfToInsert, insertionIndex);
    }

    /**
     * Append a sort column to the end of the sort list.
     *
     * @param sortField Name of column to sort on. Use -columnName to indicate a descending sort.
     */
    public void appendSortColumn(SortField sortField)
    {
        appendSortColumn(sortField.getFieldKey(), sortField.getSortDirection(), sortField.isUrlClause());
    }

    /**
     * Append a sort column to the end of the sort list.
     *
     * @param columnName Name of column to sort on. Use -columnName to indicate a descending sort.
     * @param urlClause  Make this column visible on the URL.
     */
    @Deprecated // Use FieldKey version instead.
    public void appendSortColumn(String columnName, boolean urlClause)
    {
        insertSortColumn(columnName, urlClause, _sortList.size());
    }

    /**
     * Append a sort column to the end of the sort list.
     *
     * @param fieldKey FieldKey of column to sort on. Use -columnName to indicate a descending sort.
     * @param dir sort direction
     * @param urlClause  Make this column visible on the URL.
     */
    public void appendSortColumn(FieldKey fieldKey, SortDirection dir, boolean urlClause)
    {
        insertSortColumn(fieldKey, dir, urlClause, _sortList.size());
    }

    // Add all the columns to this sort
    public void insertSort(Sort sort)
    {
        List<SortField> sortList = sort.getSortList();

        for (int i = sortList.size() - 1; i >= 0; i--)
            replaceSortColumn(sortList.get(i), 0);
    }

    private void replaceSortColumn(SortField sortField, int insertionIndex)
    {
        deleteSortColumn(sortField._fieldKey);
        if (insertionIndex > _sortList.size())
            insertionIndex = _sortList.size();
        _sortList.add(insertionIndex, sortField);
    }

    public int deleteSortColumn(FieldKey fieldKey)
    {
        int index = indexOf(fieldKey);
        if (-1 != index)
            _sortList.remove(index);
        return index;
    }

    public SortField deleteSortColumn(int index)
    {
        return _sortList.remove(index);
    }

    public SortField getSortColumn(FieldKey fieldKey)
    {
        for (SortField sf : _sortList)
            if (fieldKey.equals(sf._fieldKey))
                return sf;
        return null;
    }

    public int indexOf(@NotNull FieldKey fieldKey)
    {
        for (SortField sortField : _sortList)
            if (fieldKey.equals(sortField.getFieldKey()))
                return _sortList.indexOf(sortField);

        return -1;
    }

    public boolean contains(FieldKey fieldKey)
    {
        return -1 != indexOf(fieldKey);
    }

    public String getSortParamValue()
    {

        StringBuilder sb = new StringBuilder();
        String sep = "";
        for (SortField sf : _sortList)
        {
            sb.append(sep);
            sb.append(sf.toUrlString());
            sep = ",";
        }

        return sb.toString();
    }

    public Set<FieldKey> getRequiredColumns(Map<FieldKey, ? extends ColumnInfo> columns)
    {
        if (_sortList.isEmpty())
            return Collections.emptySet();

        Set<FieldKey> requiredFieldKeys = new HashSet<>();
        for (SortField sf : _sortList)
        {
            if (null == sf.getFieldKey())
                continue;
            requiredFieldKeys.add(sf.getFieldKey());

            String columnName = sf.getColumnName();
            ColumnInfo col = columns.get(new FieldKey(null,columnName));
            if (col != null && col.isMvEnabled())
            {
                // Note: The columns we're passed won't necessarily contain
                // our mv column at this point -- we need to let the caller
                // know it should be added
                requiredFieldKeys.add(col.getMvColumnName());
            }
        }
        return requiredFieldKeys;
    }

    public SQLFragment getOrderByClause(SqlDialect dialect, @NotNull Map<FieldKey, ? extends ColumnInfo> columns)
    {
        return _getOrderByClause(dialect, columns);
    }

    private SQLFragment _getOrderByClause(SqlDialect dialect, Map<FieldKey, ? extends ColumnInfo> columns)
    {
        if (_sortList.isEmpty())
            return new SQLFragment();

        SQLFragment sql = new SQLFragment();

        //NOTE: we are translating between the raw sort, and the sortFieldKeys() provided by that column
        Set<String>  distinctKeys = new CaseInsensitiveHashSet();
        for (SortField sf : _sortList)
        {
            FieldKey fieldKey = sf.getFieldKey();
            if (null == fieldKey)
                continue;
            ColumnInfo colinfo = null==columns ? null : columns.get(fieldKey);
            if (colinfo == null)
            {
                // if caller provided a columns Map, then ignore columns that are not found
                if (null != columns)
                {
                    LogHelper.getLogger(this.getClass().getPackage(), "Sort").debug("Specified sort column was not found: {}", fieldKey);
                    continue;
                }
                // NOTE Sort.getSortText() passes in dialect==null
                DatabaseIdentifier id;
                if (null != dialect)
                    id = dialect.makeDatabaseIdentifier(fieldKey.getName());
                else
                    id = SqlDialect.makeDatabaseIdentifier(fieldKey.getName(), new SQLFragment(LabKeySql.quoteIdentifier(fieldKey.getName())));
                appendColumnToSort(sf, dialect, id, distinctKeys, sql);
            }
            else
            {
                List<ColumnInfo> sortFields = null;
                List<FieldKey> sortFieldKeys = colinfo.getSortFieldKeys();
                if (null != sortFieldKeys)
                {
                    if (sortFieldKeys.stream().allMatch(sk -> null != columns.get(sk)))
                        sortFields = sortFieldKeys.stream().map(columns::get).collect(Collectors.toList());
                }
                if (null == sortFields || sortFields.isEmpty())
                {
                    if (colinfo.isSortable())
                        sortFields = Collections.singletonList(colinfo);
                }

                if (sortFields != null)
                {
                    for (ColumnInfo sortCol : sortFields)
                    {
                        appendColumnToSort(sf, dialect, sortCol.getAlias(), distinctKeys, sql);

                        // If we have an mv indicator column, we need to sort on it secondarily
                        if (sortCol.isMvEnabled())
                        {
                            ColumnInfo mvIndicatorColumn = columns.get(sortCol.getMvColumnName());

                            if (mvIndicatorColumn != null)
                            {
                                SortField mvSortField = new SortField(mvIndicatorColumn.getFieldKey(), sf.getSortDirection());
                                appendColumnToSort(mvSortField, dialect, mvIndicatorColumn.getAlias(), distinctKeys, sql);
                            }
                        }
                    }
                }
            }
        }

        // Determine if any ORDER BY additions were made
        if (!sql.getSQL().isEmpty())
            return new SQLFragment("ORDER BY ").append(sql);

        return new SQLFragment();
    }

    private void appendColumnToSort(SortField sf, SqlDialect dialect, DatabaseIdentifier alias, Set<String> distinctKeys, SQLFragment sql)
    {
        if (distinctKeys.contains(alias.getId()))
            return;

        if (!distinctKeys.isEmpty())
            sql.append(", ");

        sql.append(sf.toOrderByFragment(dialect, alias));
        distinctKeys.add(alias.getId());
    }

    // Return an English version of the sort
    public String getSortText()
    {
        String sql = getOrderByClause(null, null).getRawSQL().replaceFirst("ORDER BY ", "");
        return sql.replaceAll(" ,", ",").replaceAll("\"", "");
    }

    public List<SortField> getSortList()
    {
        return Collections.unmodifiableList(_sortList);
    }

    public String getURLParamValue()
    {
        StringBuilder ret = new StringBuilder();
        String comma = "";
        for (SortField sf : _sortList)
        {
            ret.append(comma);
            comma = ",";
            ret.append(sf.toUrlString());
        }
        return ret.toString();
    }

    /**
     * Replace the sort parameter on the url with this Sort scoped by the region name prefix.
     * @param url The url to be modified.
     * @param regionName The dataRegion used to scope the sort.
     * @param merge If true, the sort parameters will be added to the end of any existing sort parameter.  If false, any existing sort parameters will be replaced.
     */
    public void applyToURL(URLHelper url, String regionName, boolean merge)
    {
        String key = (regionName == null ? "" : regionName) + SORT_KEY;
        String value = getURLParamValue();
        if (merge)
        {
            String existingSort = url.getParameter(key);
            if (existingSort != null && !existingSort.isEmpty())
                value = existingSort + "," + value;
        }

        url.replaceParameter(key, value);
    }

    static public Sort fromURLParamValue(String str)
    {
        Sort ret = new Sort();
        ret._init(str, true);
        return ret;
    }
}
