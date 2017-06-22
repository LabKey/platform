/*
 * Copyright (c) 2006-2017 LabKey Corporation
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

package org.labkey.mothership;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.RenderContext;
import org.labkey.api.security.User;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

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
        List<User> list = MothershipManager.get().getAssignedToList(_container);

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
            out.write(member.getDisplayName(ctx.getViewContext().getUser()));
            out.write("</option>\n");
        }

        out.write("</select>");
    }
}
