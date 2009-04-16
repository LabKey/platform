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
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.view.HttpView;
import org.labkey.api.query.FieldKey;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TSVGridWriter extends TSVWriter
{
    private static Logger _log = Logger.getLogger(TSVGridWriter.class);

    public enum ColumnHeaderType {caption, propertyName, queryColumnName}

    private ResultSet _rs;
    private List<String> _fileHeader = null;
    private List<DisplayColumn> _displayColumns;
    private boolean _captionRowVisible = true;
    private ColumnHeaderType _columnHeaderType = ColumnHeaderType.caption;


    public TSVGridWriter(RenderContext ctx, TableInfo tinfo, List<DisplayColumn> displayColumns) throws SQLException, IOException
    {
        init(ctx.getResultSet(displayColumns, tinfo, 0, 0, tinfo.getName()), displayColumns);
    }


    public TSVGridWriter(RenderContext ctx, TableInfo tinfo, List<DisplayColumn> displayColumns, String name) throws SQLException, IOException
    {
        init(ctx.getResultSet(displayColumns, tinfo, 0, 0, name), displayColumns);
    }


    public TSVGridWriter(ResultSet rs) throws SQLException
    {
        init(rs);
    }


    public TSVGridWriter(ResultSet rs, List<DisplayColumn> displayColumns)
    {
        init(rs, displayColumns);
    }


    public TSVGridWriter(List<DisplayColumn> displayColumns)
    {
        init(null, displayColumns);
    }


    public TSVGridWriter(ResultSet rs, ColumnInfo[] cols)
    {
        List<DisplayColumn> dataColumns = new ArrayList<DisplayColumn>(cols.length);

        for (ColumnInfo col : cols)
            dataColumns.add(new DataColumn(col));

        init(rs, dataColumns);
    }


    public TSVGridWriter(DbSchema schema, String query) throws SQLException
    {
        init(schema, query);
    }


    private void init(DbSchema schema, String query) throws SQLException
    {
        init(Table.executeQuery(schema, query, null));
    }


    private void init(ResultSet rs) throws SQLException
    {
        ResultSetMetaData md = rs.getMetaData();
        ColumnInfo cols[] = new ColumnInfo[md.getColumnCount()];

        for (int i = 0; i < cols.length; i++)
        {
            int sqlColumn = i + 1;
            cols[i] = new ColumnInfo(md, sqlColumn);
        }

        init(rs, cols);
    }


    private void init(ResultSet rs, ColumnInfo[] cols)
    {
        List<DisplayColumn> dataColumns = new ArrayList<DisplayColumn>(cols.length);

        for (ColumnInfo col : cols)
            dataColumns.add(new DataColumn(col));

        init(rs, dataColumns);
    }


    private void init(ResultSet rs, List<DisplayColumn> displayColumns)
    {
        _rs = rs;
        _displayColumns = displayColumns;
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
            writeResultSet(_rs);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }

        writeFileFooter();
    }


    public void writeResultSet(ResultSet rs) throws SQLException
    {
        RenderContext context = new RenderContext(HttpView.currentContext());
        context.setResultSet(rs);
        writeResultSet(context, rs);
    }


    public void writeResultSet(RenderContext ctx, ResultSet rs) throws SQLException
    {
        Map<String, Object> rowMap = null;

        // Output all the data cells
        boolean first = true;
        while (rs.next())
        {
            ctx.setRow(ResultSetUtil.mapRow(rs, rowMap));
            if (!first)
                _pw.print(getRowSeparator());
            else
                first = false;
            writeRow(_pw, ctx, _displayColumns);
        }
    }

    protected String getRowSeparator()
    {
        return "";
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
        out.println(getRow(ctx, displayColumns).toString());
    }


    protected StringBuilder getRow(RenderContext ctx, List<DisplayColumn> displayColumns)
    {
        StringBuilder row = new StringBuilder();
        for (DisplayColumn dc : displayColumns)
        {
            if (dc.isVisible(ctx))
            {
                row.append(dc.getTsvFormattedValue(ctx));
                row.append('\t');
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
            _pw.println(header.toString());
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
                header.append('\t');
            }
        }

        // displayColumns could be empty
        if (header.length() > 0)
            header.deleteCharAt(header.length() - 1);

        return header;
    }
}
