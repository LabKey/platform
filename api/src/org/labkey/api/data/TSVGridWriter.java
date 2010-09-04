/*
 * Copyright (c) 2004-2010 Fred Hutchinson Cancer Research Center
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

import org.labkey.api.collections.ResultSetRowMapFactory;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.view.HttpView;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.*;

public class TSVGridWriter extends TSVWriter
{
    public enum ColumnHeaderType {caption, propertyName, queryColumnName}

    private ResultSet _rs;
    private Map<FieldKey,ColumnInfo> _fieldMap;
    private List<String> _fileHeader = null;
    private List<DisplayColumn> _displayColumns;
    private boolean _captionRowVisible = true;
    private ColumnHeaderType _columnHeaderType = ColumnHeaderType.caption;


    public TSVGridWriter(RenderContext ctx, TableInfo tinfo, List<DisplayColumn> displayColumns) throws SQLException, IOException
    {
        this(ctx, tinfo, displayColumns, tinfo.getName());
    }

    public TSVGridWriter(RenderContext ctx, TableInfo tinfo, List<DisplayColumn> displayColumns, String name) throws SQLException, IOException
    {
        List<ColumnInfo> selectCols = RenderContext.getSelectColumns(displayColumns, tinfo);
        LinkedHashMap<FieldKey, ColumnInfo> fieldMap = QueryService.get().getColumns(tinfo, Collections.<FieldKey>emptySet(), selectCols);
        ResultSet rs = ctx.getResultSet(fieldMap, tinfo, 0, 0, name, false);
        init(rs, ctx.getFieldMap(), displayColumns);
    }


    @Deprecated
    public TSVGridWriter(ResultSet rs) throws SQLException
    {
        init(rs);
    }


    @Deprecated
    public TSVGridWriter(ResultSet rs, List<DisplayColumn> displayColumns)
    {
        init(rs, null, displayColumns);
    }

    /**
     * Create a TSVGridWriter for a ResultSet, fieldMap, and set of displayColumns.
     * You can use use {@link QueryService#getColumns(TableInfo, Collection<FieldKey>, Collection<ColumnInfo>)}
     * to obtain a fieldMap which will include any extra ColumnInfo required by the selected DisplayColumns.
     *
     * @param rs ResultSet.
     * @param fieldMap map of FieldKey to ColumnInfo.
     * @param displayColumns The DisplayColumns.
     */
    public TSVGridWriter(ResultSet rs, Map<FieldKey, ColumnInfo> fieldMap, List<DisplayColumn> displayColumns)
    {
        init(rs, fieldMap, displayColumns);
    }

    public TSVGridWriter(ResultSet rs, Map<FieldKey, ColumnInfo> fieldMap, Collection<ColumnInfo> columns)
    {
        init(rs, fieldMap, columns);
    }

    protected TSVGridWriter(List<DisplayColumn> displayColumns)
    {
        init(null, null, displayColumns);
    }


    private void init(ResultSet rs) throws SQLException
    {
        ResultSetMetaData md = rs.getMetaData();
        Collection<ColumnInfo> cols = new LinkedList<ColumnInfo>();

        for (int i = 0; i < md.getColumnCount(); i++)
        {
            int sqlColumn = i + 1;
            cols.add(new ColumnInfo(md, sqlColumn));
        }

        init(rs, null, cols);
    }


    private void init(ResultSet rs, Map<FieldKey,ColumnInfo> fieldMap, Collection<ColumnInfo> cols)
    {
        List<DisplayColumn> dataColumns = new LinkedList<DisplayColumn>();

        for (ColumnInfo col : cols)
            dataColumns.add(new DataColumn(col));

        init(rs, fieldMap, dataColumns);
    }


    private void init(ResultSet rs, Map<FieldKey,ColumnInfo> fieldMap, List<DisplayColumn> displayColumns)
    {
        _rs = rs;
        _fieldMap = fieldMap;
        _displayColumns = displayColumns;
    }

    public Map<FieldKey, ColumnInfo> getFieldMap()
    {
        return _fieldMap;
    }

    public void setResultSet(ResultSet rs)
    {
        _rs = rs;
    }


    public boolean isCaptionRowVisible()
    {
        return _captionRowVisible;
    }


    public void setCaptionRowVisible(boolean captionRowVisible)
    {
        _captionRowVisible = captionRowVisible;
    }


    public void setColumnHeaderType(ColumnHeaderType columnHeaderType)
    {
        _columnHeaderType = columnHeaderType;
    }


    public ColumnHeaderType getColumnHeaderType()
    {
        return _columnHeaderType;
    }


    protected void write()
    {
        writeFileHeader();
        writeColumnHeaders();

        try
        {
            writeResultSet(_rs, _fieldMap);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }

        writeFileFooter();
    }


    @Deprecated
    public void writeResultSet(ResultSet rs) throws SQLException
    {
        RenderContext context = new RenderContext(HttpView.currentContext());
        context.setResultSet(rs, null);
        writeResultSet(context, rs);
    }


    public void writeResultSet(ResultSet rs, Map<FieldKey,ColumnInfo> fieldMap) throws SQLException
    {
        RenderContext context = new RenderContext(HttpView.currentContext());
        context.setResultSet(rs, fieldMap);
        writeResultSet(context, rs);
    }


    public void writeResultSet(RenderContext ctx, ResultSet rs) throws SQLException
    {
        // Output all the data cells
        ResultSetRowMapFactory factory = ResultSetRowMapFactory.create(rs);

        while (rs.next())
        {
            ctx.setRow(factory.getRowMap(rs));
            writeRow(_pw, ctx, _displayColumns);
        }
    }

    @Override
    public void close() throws ServletException
    {
        if (_rs != null) try { _rs.close(); } catch (SQLException e) {}
        _rs = null;
        super.close();
    }

    protected void writeRow(PrintWriter out, RenderContext ctx, List<DisplayColumn> displayColumns)
    {
        out.print(getRow(ctx, displayColumns).toString());
        out.write(_rowSeparator);
    }


    protected StringBuilder getRow(RenderContext ctx, List<DisplayColumn> displayColumns)
    {
        StringBuilder row = new StringBuilder();
        for (DisplayColumn dc : displayColumns)
        {
            if (dc.isVisible(ctx))
            {
                // Encode all tab and newline characters; see #8435, #8748
                String value = dc.getTsvFormattedValue(ctx);
                row.append(quoteValue(value));
                row.append(_chDelimiter);
            }
        }

        // displayColumns could be empty
        if (row.length() > 0)
            row.deleteCharAt(row.length() - 1);

        return row;
    }

    public void setFileHeader(List<String> fileHeader)
    {
        _fileHeader = fileHeader;    
    }


    public void writeFileHeader()
    {
        if (null == _fileHeader)
            return;

        for (String line : _fileHeader)
            _pw.println(line);
    }

    protected void writeFileFooter()
    {
    }


    public void writeColumnHeaders()
    {
        // Output the column headers
        if (_captionRowVisible)
        {
            StringBuilder header = getHeader(new RenderContext(HttpView.currentContext()), _displayColumns);
            _pw.print(header.toString());
            _pw.print(_rowSeparator);
        }
    }


    protected StringBuilder getHeader(RenderContext ctx, List<DisplayColumn> displayColumns)
    {
        StringBuilder header = new StringBuilder();

        for (DisplayColumn dc : displayColumns)
        {
            if (dc.isVisible(ctx))
            {
                switch(_columnHeaderType)
                {
                    case caption:
                        header.append(dc.getCaption());
                        break;
                    case propertyName:
                        header.append(dc.getName());
                        break;
                    case queryColumnName:
                    {
                        ColumnInfo columnInfo = dc.getColumnInfo();
                        String name;
                        if (columnInfo != null)
                        {
                            name = FieldKey.fromString(columnInfo.getName()).getDisplayString();
                        }
                        else
                        {
                            name = dc.getName();
                        }
                        header.append(name);

                    }
                    break;
                }
                header.append(_chDelimiter);
            }
        }

        // displayColumns could be empty
        if (header.length() > 0)
            header.deleteCharAt(header.length() - 1);

        return header;
    }
}
