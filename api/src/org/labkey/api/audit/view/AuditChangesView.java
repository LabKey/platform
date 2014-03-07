/*
 * Copyright (c) 2012-2014 LabKey Corporation
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
package org.labkey.api.audit.view;

import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.HttpView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.Map;

/**
 * User: klum
 * Date: 10/21/12
 */
public class AuditChangesView extends HttpView
{
    private String _comment;
    private final Map<String,String> oldData;
    private final Map<String,String> newData;
    private String _returnUrl;

    public AuditChangesView(String comment, Map<String,String> oldData, Map<String,String> newData)
    {
        this._comment = comment;
        if (oldData != null)
        {
            this.oldData = new CaseInsensitiveHashMap<>(oldData);
        }
        else
        {
            this.oldData = Collections.emptyMap();
        }
        if (newData != null)
        {
            this.newData = new CaseInsensitiveHashMap<>(newData);
        }
        else
        {
            this.newData = Collections.emptyMap();
        }
    }

    @Override
    protected void renderInternal(Object model, HttpServletRequest request, HttpServletResponse response) throws Exception
    {
        int modified = 0;
        PrintWriter out = response.getWriter();

        out.write("<table>\n");
        out.write("<tr class=\"labkey-wp-header\"><th colspan=\"2\" align=\"left\">Item Changes</th></tr>");
        out.write("<tr><td colspan=\"2\">Comment:&nbsp;<i>" + PageFlowUtil.filter(_comment) + "</i></td></tr>");

        boolean update = !oldData.isEmpty() && !newData.isEmpty();

        for (Map.Entry<String, String> entry : oldData.entrySet())
        {
            out.write("<tr><td class=\"labkey-form-label\">");
            out.write(entry.getKey());
            out.write("</td><td>");

            StringBuilder sb = new StringBuilder();
            String oldValue = entry.getValue();
            if (oldValue == null)
                oldValue = "";
            sb.append(PageFlowUtil.filter(oldValue));

            String newValue = newData.remove(entry.getKey());
            if (newValue == null)
                newValue = "";
            if (update && !newValue.equals(oldValue))
            {
                modified++;
                sb.append("&nbsp;&raquo;&nbsp;");
                sb.append(PageFlowUtil.filter(newValue));
            }
            out.write(sb.toString());
            out.write("</td></tr>\n");
        }

        for (Map.Entry<String, String> entry : newData.entrySet())
        {
            modified++;
            out.write("<tr><td class=\"labkey-form-label\">");
            out.write(entry.getKey());
            out.write("</td><td>");

            StringBuilder sb = new StringBuilder();
            if (update)
                sb.append("&nbsp;&raquo;&nbsp;");
            String newValue = entry.getValue();
            if (newValue == null)
                newValue = "";
            sb.append(PageFlowUtil.filter(newValue));
            out.write(sb.toString());
            out.write("</td></tr>\n");
        }
        if (update)
        {
            out.write("<tr><td colspan=\"2\">Summary:&nbsp;<i>");
            out.write(modified + " field(s) were modified</i></td></tr>");
        }

        if (_returnUrl != null)
        {
            out.write("<tr><td>");
            out.write(PageFlowUtil.button("Done").href(_returnUrl).toString());
            out.write("</td></tr>");
        }
        out.write("</table>\n");
    }

    public String getReturnUrl()
    {
        return _returnUrl;
    }

    public void setReturnUrl(String returnUrl)
    {
        _returnUrl = returnUrl;
    }
}
