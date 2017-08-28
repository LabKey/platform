package org.labkey.api.data;

import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.view.WebPartView;

import java.io.PrintWriter;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;

public class ResultSetView extends WebPartView
{
    private final ResultSet _rs;
    private final int _linkColumn;
    private final String _link;

    public ResultSetView(ResultSet rs, String title) throws SQLException
    {
        this(rs, title, 0, null);
    }

    public ResultSetView(ResultSet rs, String title, int linkColumn, String link) throws SQLException
    {
        super(title);
        _rs = rs;
        _linkColumn = linkColumn;
        _link = link;
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

                    boolean createLink = null != _link && _linkColumn == i && null != val && shouldLink(_rs);

                    if (createLink)
                    {
                        out.print("<a href=\"");
                        out.print(PageFlowUtil.filter(_link + val.toString()));
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
