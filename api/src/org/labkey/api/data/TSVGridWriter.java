/*
 * Copyright (c) 2004-2014 Fred Hutchinson Cancer Research Center
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

import java.io.IOException;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class TSVGridWriter extends TSVColumnWriter implements ExportWriter
{
    protected int _dataRowCount;

    private Results _rs;
    protected List<DisplayColumn> _displayColumns;

    public TSVGridWriter(RenderContext ctx, TableInfo tinfo, List<DisplayColumn> displayColumns) throws SQLException, IOException
    {
        this(ctx, tinfo, displayColumns, tinfo.getName());
    }

    public TSVGridWriter(RenderContext ctx, TableInfo tinfo, List<DisplayColumn> displayColumns, String name) throws IOException
    {
        List<ColumnInfo> selectCols = RenderContext.getSelectColumns(displayColumns, tinfo);
        LinkedHashMap<FieldKey, ColumnInfo> fieldMap = QueryService.get().getColumns(tinfo, Collections.<FieldKey>emptySet(), selectCols);
        try
        {
            Results rs = ctx.getResultSet(fieldMap, tinfo, null, null, Table.ALL_ROWS, Table.NO_OFFSET, name, false);
            init(rs, displayColumns);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }


    public TSVGridWriter(Results results) throws SQLException
    {
        init(results, results.getFieldMap().values());
    }

    /**
     * Create a TSVGridWriter for a Results (ResultSet/fieldMap) and a set of DisplayColumns.
     * You can use use {@link QueryService#getColumns(TableInfo, Collection<FieldKey>, Collection<ColumnInfo>)}
     * to obtain a fieldMap which will include any extra ColumnInfo required by the selected DisplayColumns.
     *
     * @param rs Results (ResultSet/Map<FieldKey,ColumnInfo>).
     * @param displayColumns The DisplayColumns.
     */

    public TSVGridWriter(Results rs, List<DisplayColumn> displayColumns)
    {
        init(rs, displayColumns);
    }

    protected TSVGridWriter(List<DisplayColumn> displayColumns)
    {
        init(null, displayColumns);
    }


    private void init(Results rs, Collection<ColumnInfo> cols)
    {
        List<DisplayColumn> dataColumns = new LinkedList<>();

        for (ColumnInfo col : cols)
            dataColumns.add(col.getDisplayColumnFactory().createRenderer(col));

        init(rs, dataColumns);
    }


    private void init(Results rs, List<DisplayColumn> displayColumns)
    {
        _rs = rs;
        _displayColumns = displayColumns;
        for (DisplayColumn displayColumn : _displayColumns)
        {
            displayColumn.setHtmlFiltered(false);
        }
    }


    public Map<FieldKey, ColumnInfo> getFieldMap()
    {
        return null==_rs ? null : _rs.getFieldMap();
    }

    @Override
    public void writeColumnHeaders()
    {
        RenderContext context = getRenderContext();
        writeColumnHeaders(context, _displayColumns);
    }

    private RenderContext getRenderContext()
    {
        return HttpView.hasCurrentView() ? new RenderContext(HttpView.currentContext()) : new RenderContext();
    }

    @Override
    protected void writeBody()
    {
         writeResultSet(_rs);
    }

    public void writeResultSet(Results rs)
    {
        RenderContext context = getRenderContext();
        context.setResults(rs);
        writeResultSet(context, rs);
    }


    public void writeResultSet(RenderContext ctx, Results rs)
    {
        try
        {
            // Output all the data cells
            ResultSetRowMapFactory factory = ResultSetRowMapFactory.create(rs);

            while (rs.next())
            {
                ctx.setRow(factory.getRowMap(rs));
                writeRow(ctx, _displayColumns);
            }
        }
        catch (SQLException ex)
        {
            throw new RuntimeSQLException(ex);
        }
    }


    @Override
    public void close() throws IOException
    {
        if (_rs != null) try { _rs.close(); } catch (SQLException e) {}
        super.close();
    }

    protected void writeRow(RenderContext ctx, List<DisplayColumn> displayColumns)
    {
        writeLine(getValues(ctx, displayColumns));
        _dataRowCount++;
    }

    @Override
    public int getDataRowCount()
    {
        return _dataRowCount;
    }

}
