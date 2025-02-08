/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

package org.labkey.wiki;

import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.WebPartView;

import java.io.PrintWriter;

/**
 * User: Tamra Myers
 * Date: Jun 2, 2006
 * Time: 3:29:33 PM
 */
public class LinkBarView extends WebPartView<Object>
{
    private final NavTree[] _links;
    private boolean _drawLine = false;

    public LinkBarView(NavTree... links)
    {
        super(FrameType.DIV);
        _links = links;
    }

    public void setDrawLine(boolean fDrawLine)
    {
        _drawLine = fDrawLine;
    }

    @Override
    protected void renderView(Object model, PrintWriter out)
    {
        out.write("<table width=\"100%\" cellpadding=0><tr><td>");
        for (NavTree link : _links)
        {
            out.write(PageFlowUtil.link(link.getText()).href(link.getHref()) + "&nbsp;");
        }
        out.write("</td></tr>");
        if (_drawLine)
        {
            out.write("<tr><td colspan=3 class=\"labkey-title-area-line\"></td></tr>");
        }
        out.write("</table>");
    }
}
