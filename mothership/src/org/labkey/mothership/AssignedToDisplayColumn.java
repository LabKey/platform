package org.labkey.mothership;

import org.labkey.api.data.DataColumn;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.Container;
import org.labkey.api.security.*;
import org.labkey.api.security.SecurityManager;

import javax.servlet.ServletException;
import java.io.Writer;
import java.io.IOException;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.sql.SQLException;

/**
 * User: jeckels
 * Date: Apr 24, 2006
 */
public class AssignedToDisplayColumn extends DataColumn
{
    private final Container _container;

    //Careful, a renderer without a resultset is only good for input forms
    public AssignedToDisplayColumn(ColumnInfo col, Container c)
    {
        super(col);
        _container = c;
    }

    public void renderInputHtml(RenderContext ctx, Writer out, Object value) throws IOException
    {
        List<User> projectMembers = SecurityManager.getProjectMembers(_container.getProject());
        List<User> list = new ArrayList<User>(projectMembers);
        Collections.sort(list, new UserComparator());

        out.write("<select name='");
        out.write(getInputPrefix());
        out.write(getColumnInfo().getPropertyName());
        out.write("'>\n");
        out.write("<option value=\"\"></option>\n");
        for (User member : list)
        {
            out.write("<option value=\"");
            out.write(Integer.toString(member.getUserId()));
            out.write("\"");
            if (value != null && value instanceof Integer && ((Integer)value).intValue() == member.getUserId())
            {
                out.write(" selected");
            }
            out.write(">");
            out.write(member.getDisplayName(ctx.getViewContext()));
            out.write("</option>\n");
        }
        out.write("</select>");
    }
}
