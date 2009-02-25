/*
 * Copyright (c) 2004-2009 Fred Hutchinson Cancer Research Center
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

import org.apache.log4j.Logger;
import org.labkey.api.util.URLHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;


public class Sort
{
    public enum SortDirection
    {
        ASC('+'),
        DESC('-');

        private char dir;
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
     }

    private static String SORT_KEY = ".sort";
    private static Logger _log = Logger.getLogger(Sort.class);
    private ArrayList<SortField> _sortList = new ArrayList<SortField>();

    public class SortField
    {
        SortDirection _dir = SortDirection.ASC;
        String _colName = null;
        boolean _urlClause = false;

        public SortField(String str, SortDirection dir)
        {
            _colName = str;
            _dir = dir;
        }

        public SortField(String str)
        {
            if (str.charAt(0) == SortDirection.DESC.dir)
            {
                _dir = SortDirection.DESC;
                _colName = str.substring(1);
            }
            else if (str.charAt(0) == SortDirection.ASC.dir)
                _colName = str.substring(1);
            else
                _colName = str;
        }

        public String toUrlString()
        {
            return (_dir == SortDirection.ASC ? "" : "-") + _colName;
        }

        private String toOrderByString(SqlDialect dialect, String alias)
        {
            return (null != dialect ? dialect.getColumnSelectName(alias) : alias) + " " + _dir.getSqlDir();
        }

        public String getSelectName(SqlDialect dialect)
        {
            return dialect.getColumnSelectName(_colName);
        }

        public String getColumnName()
        {
            return _colName;
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

    public Sort(String sort)
    {
        _init(sort, false);
    }

    private void _init(String sortParam, boolean urlClause)
    {
        String[] sortKeys;

        if (null != sortParam)
        {
            sortKeys = sortParam.split(",");
            for (String sortKey : sortKeys)
            {
                SortField sf = new SortField(sortKey);
                sf._urlClause = urlClause;
                _sortList.add(sf);
            }
        }
    }

    public void applyURLSort(URLHelper urlhelp, String regionName)
    {
        String sortParam = urlhelp.getParameter(regionName + SORT_KEY);
        String[] sortKeys;

        if (null != sortParam)
        {
            sortKeys = sortParam.split(",");
            //Insert keys backwards since we always insert at the front...
            for (int i = sortKeys.length - 1; i >= 0; i--)
                insertSortColumn(sortKeys[i], true);
        }
    }

    /**
     * Add a column to the sort.
     *
     * @param columnName Name of column to sort on. Use -columnName to indicate a descending sort.
     * @param urlClause  Make this column visible on the URL.
     */
    public void insertSortColumn(String columnName, boolean urlClause)
    {
        insertSortColumn(columnName, urlClause, 0);
    }

    /**
     * Add a column to the sort.
     *
     * @param columnName Name of column to sort on. Use -columnName to indicate a descending sort.
     * @param urlClause  Make this column visible on the URL.
     * @param insertionIndex Index at which to insert into the sort
     */
    public void insertSortColumn(String columnName, boolean urlClause, int insertionIndex)
    {
        SortField sfToInsert = new SortField(columnName);
        sfToInsert._urlClause = urlClause;
        replaceSortColumn(sfToInsert, insertionIndex);
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
        int deletedIndex = deleteSortColumn(sortField._colName);
        if (deletedIndex != -1 && insertionIndex > deletedIndex)
        {
//            insertionIndex--;
        }
        _sortList.add(insertionIndex, sortField);
    }

    public int deleteSortColumn(String columnName)
    {
        int index = indexOf(columnName);
        if (-1 != index)
            _sortList.remove(index);
        return index;
    }

    public SortField getSortColumn(String columnName)
    {
        for (SortField sf : _sortList)
            if (sf._colName.equalsIgnoreCase(columnName))
                return sf;
        return null;
    }

    public int indexOf(String columnName)
    {
        for (int i = 0; i < _sortList.size(); i++)
        {
            SortField sf = _sortList.get(i);
            if (sf._colName.equalsIgnoreCase(columnName))
                return i;
        }

        return -1;
    }

    public boolean contains(String columnName)
    {
        return -1 != indexOf(columnName);
    }

    public String getSortParamValue()
    {
        if (null == _sortList)
            return null;

        StringBuffer sb = new StringBuffer();
        String sep = "";
        for (SortField sf : _sortList)
        {
            sb.append(sep);
            sb.append(sf.toUrlString());
            sep = ",";
        }

        return sb.toString();
    }

    public String getOrderByClause(SqlDialect dialect)
    {
        return getOrderByClause(dialect, Collections.<String, ColumnInfo>emptyMap());
    }

    public String getOrderByClause(SqlDialect dialect, Map<String, ? extends ColumnInfo> columns)
    {
        if (null == _sortList || _sortList.size() == 0)
            return "";

        StringBuffer sb = new StringBuffer("ORDER BY ");
        String sep = "";
        for (SortField sf : _sortList)
        {
            sb.append(sep);
            String alias = sf.getColumnName();
            // If we have a qc indicator column, we need to sort on it secondarily
            ColumnInfo qcIndicatorCol = null;
            ColumnInfo colinfo = columns.get(alias);
            if (colinfo != null)
            {
                alias = colinfo.getAlias();
                if (colinfo.isQcEnabled())
                {
                    qcIndicatorCol = columns.get(colinfo.getQcColumnName());
                }
            }

            sb.append(sf.toOrderByString(dialect, alias));
            if (qcIndicatorCol != null)
            {
                SortField qcSortField = new SortField(qcIndicatorCol.getName(), sf.getSortDirection());
                sb.append(", ");
                sb.append(qcSortField.toOrderByString(dialect, qcIndicatorCol.getAlias()));
            }
            sep = ", ";
        }

        return sb.toString();
    }

    // Return an English version of the sort
    public String getSortText()
    {
        String sql = getOrderByClause(null).replaceFirst("ORDER BY ", "");
        return sql.replaceAll(" ,", ",").replaceAll("\"", "");
    }

    public List<SortField> getSortList()
    {
        return _sortList;
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

    static public Sort fromURLParamValue(String str)
    {
        Sort ret = new Sort();
        ret._init(str, true);
        return ret;
    }
}
