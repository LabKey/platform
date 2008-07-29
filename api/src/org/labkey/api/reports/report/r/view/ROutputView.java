/*
 * Copyright (c) 2008 LabKey Corporation
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

package org.labkey.api.reports.report.r.view;

import org.labkey.api.view.HttpView;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.reports.report.r.ParamReplacement;

import java.io.PrintWriter;
import java.io.File;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: May 5, 2008
 */
public class ROutputView extends HttpView
{
    private String _label;
    private boolean _collapse;
    private boolean _showHeader = true;
    private File _file;

    public ROutputView(ParamReplacement param)
    {
        _file = param.getFile();
        _showHeader = param.getHeaderVisible();
    }

    public String getLabel()
    {
        return _label;
    }

    public void setLabel(String label)
    {
        _label = label;
    }

    public boolean isCollapse()
    {
        return _collapse;
    }

    public void setCollapse(boolean collapse)
    {
        _collapse = collapse;
    }

    public boolean isShowHeader()
    {
        return _showHeader;
    }

    public void setShowHeader(boolean showHeader)
    {
        _showHeader = showHeader;
    }

    public File getFile()
    {
        return _file;
    }

    public void setFile(File file)
    {
        _file = file;
    }

    protected void renderTitle(Object model, PrintWriter out) throws Exception
    {
        StringBuffer sb = new StringBuffer();

        if (_showHeader)
        {
            sb.append("<tr class=\"labkey-wp-header\"><th colspan=2 align=left>");
            sb.append("   <a href=\"#\" onclick=\"return toggleLink(this, false);\">");
            sb.append("   <img src=\"");
            sb.append(getViewContext().getContextPath());
            sb.append("/_images/");
            sb.append(_collapse ? "plus.gif" : "minus.gif");
            sb.append("\"></a>&nbsp;");
            sb.append(PageFlowUtil.filter(_label));
            sb.append("</th></tr>");
        }
        out.write(sb.toString());
    }
}
