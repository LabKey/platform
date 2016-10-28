/*
 * Copyright (c) 2008-2016 LabKey Corporation
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

import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.collections.ResultSetRowMapFactory;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Writes a ResultSet as a minimally-styled and formatted HTML table.
 *
 * User: Dave
 * Date: Mar 7, 2008
 */
public class HtmlWriter implements ExportWriter
{
    private int _dataRowCount;
    private ResultSet _rs = null;
    private List<DisplayColumn> _columns = null;
    private HttpServletResponse _response = null;
    private RenderContext _ctx = null;
    private boolean _fullHtmlPage = false;
    private PrintWriter _out = null;

    public ResultSet getResultSet()
    {
        return _rs;
    }

    public List<DisplayColumn> getColumns()
    {
        return _columns;
    }

    public HttpServletResponse getResponse()
    {
        return _response;
    }

    public RenderContext getRenderContext()
    {
        return _ctx;
    }

    public boolean isFullHtmlPage()
    {
        return _fullHtmlPage;
    }

    public PrintWriter getWriter()
    {
        return _out;
    }

    public void setWriter(PrintWriter out)
    {
        _out = out;
    }

    public String getOpenValue()
    {
        return "<td>";
    }

    public String getCloseValue()
    {
        return "</td>\n";
    }

    public String getOpenRow()
    {
        return "<tr>\n";
    }

    public String getCloseRow()
    {
        return "</tr>\n";
    }

    public String getOpenHeader()
    {
        return "<th>";
    }

    public String getCloseHeader()
    {
        return "</th>\n";
    }

    public void write(ResultSet rs, List<DisplayColumn> columns,
                      HttpServletResponse response, RenderContext ctx,
                      boolean fullHtmlPage) throws IOException, SQLException
    {
        assert null != rs && null != columns && null != response && null != ctx;
        _rs = rs;
        _columns = columns;
        _response = response;
        _ctx = ctx;
        _fullHtmlPage = fullHtmlPage;

        try
        {
            setWriter(prepare(_response));
            if(_fullHtmlPage)
                openPage();

            openResults();
            writeHeaders();

            ResultSetRowMapFactory factory = ResultSetRowMapFactory.create(rs);

            while(rs.next())
            {
                ctx.setRow(factory.getRowMap(rs));
                writeRow();
            }

            closeResults(_out);
            if(fullHtmlPage)
                closePage();
        }
        finally
        {
            ResultSetUtil.close(rs);
        }
    }

    protected void openPage() throws IOException
    {
        getWriter().write("<html><body>");
    }

    protected void closePage() throws IOException
    {
        getWriter().write("</body></html>");
    }

    protected void openResults() throws IOException
    {
        getWriter().write("<table>\n");
    }

    protected void closeResults(PrintWriter out) throws IOException
    {
        out.write("</table>\n");
    }

    protected void writeHeaders() throws IOException
    {
        PrintWriter out = getWriter();
        out.write(getOpenRow());
        if(null != getColumns())
        {
            for(DisplayColumn dc : getColumns())
            {
                out.write(getOpenHeader());
                out.write(PageFlowUtil.filter(dc.getName())); //FIX: 5649
                out.write(getCloseHeader());
            }
        }
        out.write(getCloseRow());
    }

    protected void writeRow() throws IOException, SQLException
    {
        PrintWriter out = getWriter();
        RenderContext ctx = getRenderContext();

        out.write(getOpenRow());
        if(null != getColumns())
        {
            for(DisplayColumn dc : getColumns())
            {
                out.write(getOpenValue());
                out.write(PageFlowUtil.filter(dc.getTsvFormattedValue(ctx))); //FIX: 5696 
                out.write(getCloseValue());
            }
        }
        out.write(getCloseRow());
        _dataRowCount++;
    }

    protected PrintWriter prepare(HttpServletResponse response) throws IOException
    {
        return response.getWriter();
    }

    @Override
    public int getDataRowCount()
    {
        return _dataRowCount;
    }
}
