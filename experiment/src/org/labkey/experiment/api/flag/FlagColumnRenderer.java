/*
 * Copyright (c) 2006-2010 LabKey Corporation
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

package org.labkey.experiment.api.flag;

import org.labkey.api.data.DataColumn;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.security.ACL;
import org.labkey.api.jsp.JspBase;
import org.labkey.api.jsp.JspLoader;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.util.UnexpectedException;

import java.io.Writer;
import java.io.IOException;

public class FlagColumnRenderer extends DataColumn
{
    public FlagColumnRenderer(ColumnInfo colinfo)
    {
        super(colinfo);
    }

    static private final String key_scriptrendered = "~~~~FlagColumnScriptRendered~~~";
    public void renderFlagScript(RenderContext ctx, Writer out)
    {
        if (ctx.containsKey(key_scriptrendered))
            return;
        ctx.put(key_scriptrendered, true);
        JspBase page = (JspBase) JspLoader.createPage(ctx.getRequest(), FlagColumnRenderer.class, "setFlagScript.jsp");
        try
        {
            ((HttpView) HttpView.currentView()).include(new JspView(page), out);
        }
        catch (Exception e)
        {
            throw UnexpectedException.wrap(e);
        }
    }


    public void renderFlag(RenderContext ctx, Writer out) throws IOException
    {
        renderFlagScript(ctx, out);
        Object boundValue = getColumnInfo().getValue(ctx);
        if (boundValue == null)
            return;
        FlagColumn displayField = (FlagColumn) getColumnInfo().getDisplayField();
        String comment = (String) displayField.getValue(ctx);
        String objectId = (String) getValue(ctx);
        String src;
        if (objectId == null)
            return;

        if (comment == null)
        {
            src = displayField.urlFlag(false);
        }
        else
        {
            src = displayField.urlFlag(true);
        }
        boolean canUpdate = ctx.getViewContext().hasPermission(ACL.PERM_UPDATE);
        if (canUpdate)
        {
            out.write("<a href=\"#\" onclick=\"return setFlag(");
            out.write(hq(objectId));
            out.write(")\">");
        }

        out.write("<img height=\"16\" width=\"16\" src=\"");
        out.write(h(src));
        out.write("\"");
        if (comment != null)
        {
            out.write(" title=\"");
            out.write(h(comment));
            out.write("\"");
        }
        out.write(" flagId=\"");
        out.write(h(objectId));
        out.write("\"");
        out.write(">");
        if (canUpdate)
        {
            out.write("</a>");
        }
    }

    public void renderDetailsCellContents(RenderContext ctx, Writer out) throws IOException
    {
        renderFlag(ctx, out);
    }

    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
    {
        renderFlag(ctx, out);
    }

    @Override
    protected Object getInputValue(RenderContext ctx)
    {
        FlagColumn displayField = (FlagColumn) getColumnInfo().getDisplayField();
        String comment = (String) displayField.getValue(ctx);
        return comment;
    }

}
