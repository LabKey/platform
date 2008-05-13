/*
 * Copyright (c) 2005-2007 LabKey Corporation
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
package org.labkey.pipeline;

import org.labkey.api.view.HttpView;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.pipeline.PipelineProvider;
import org.labkey.api.data.ActionButton;

import java.util.List;
import java.io.PrintWriter;

/**
 */
public class DirectoryView extends HttpView
{
    List<PipelineProvider.FileEntry> _entries;

    public DirectoryView(List<PipelineProvider.FileEntry> entries)
    {
        _entries = entries;
    }

    protected void renderStyle(PrintWriter out)
    {

    }

    @Override
    protected void renderInternal(Object model, PrintWriter out) throws Exception
    {
        out.write("<table>\n");
        for (PipelineProvider.FileEntry entry : _entries)
        {
            out.write("\n<tr onmouseover=\"this.style.backgroundColor='gray'\" onmouseout=\"this.style.backgroundColor='white'\">\n<td>");
            if (entry.isDirectory())
            {
                out.write("<a href=\"");
                out.write(PageFlowUtil.filter(entry.getHref()));
                out.write("\">");
            }
            if (entry.getImageURL() != null)
            {
                out.write("<img border=\"0\" src=\"");
                out.write(PageFlowUtil.filter(entry.getImageURL()));
                out.write("\">");
            }
            out.write(PageFlowUtil.filter(entry.getLabel()));
            if (entry.isDirectory())
            {
                out.write("</a>");
            }
            out.write("</td><td>");
            for (PipelineProvider.FileAction action : entry.getActions())
            {
                ActionButton button = new ActionButton(action.getLabel());
                button.setCaption(action.getLabel());
                button.setDisplayType(ActionButton.DISPLAY_TYPE_IMG);
                button.setURL(action.getHref());
                button.renderView(getViewContext(), out);
            }
            out.write("</td></tr>");
        }
        out.write("</table>");
    }
}
