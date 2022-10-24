/*
 * Copyright (c) 2017-2018 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.WebPartView;

import java.io.PrintWriter;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;

public class ResultSetView extends WebPartView
{
    private final ResultSet _rs;
    private final int _linkColumn;
    private final String _unencodedLink;

    public ResultSetView(ResultSet rs, String title)
    {
        this(rs, title, null, null);
    }

    public ResultSetView(ResultSet rs, String title, @Nullable String linkColumnName, @Nullable ActionURL linkUrl)
    {
        super(title);
        _rs = rs;
        try
        {
            _linkColumn = linkColumnName != null ? rs.findColumn(linkColumnName) : 0;
        }
        catch (SQLException e)
        {
            throw new RuntimeException(e);
        }
        _unencodedLink = linkUrl != null ? linkUrl.toString() : null;
    }

    @Override
    protected void renderView(Object model, PrintWriter out) throws Exception
    {
        out.println("<table class=\"labkey-data-region-legacy labkey-show-borders\">");

        try
        {
            ResultSetMetaData md = _rs.getMetaData();
            int columnCount = md.getColumnCount();

            out.print("  <tr>");

            for (int i = 1; i <= columnCount; i++)
            {
                out.print("<td class=\"labkey-column-header\">");
                out.print(PageFlowUtil.filter(md.getColumnLabel(i)));
                out.print("</td>");
            }

            out.println("</tr>\n");

            long rowCount = 0;

            while (_rs.next())
            {
                if (rowCount % 2 == 0)
                {
                    out.print("  <tr class=\"labkey-row\">");
                }
                else
                {
                    out.print("  <tr class=\"labkey-alternate-row\">");
                }

                for (int i = 1; i <= columnCount; i++)
                {
                    Object val = _rs.getObject(i);

                    out.print("<td>");

                    boolean createLink = null != _unencodedLink && _linkColumn == i && null != val && shouldLink(_rs);

                    if (createLink)
                    {
                        out.print("<a href=\"");
                        out.print(PageFlowUtil.filter(_unencodedLink + val.toString()));
                        out.print("\">");
                    }

                    out.print(null == val ? "&nbsp;" : PageFlowUtil.filter(val));

                    if (createLink)
                        out.print("</a>");

                    out.print("</td>");
                }

                out.println("</tr>\n");
                rowCount++;
            }
        }
        finally
        {
            ResultSetUtil.close(_rs);
        }

        out.println("</table>\n");
    }

    protected boolean shouldLink(ResultSet rs) throws SQLException
    {
        return true;
    }
}
