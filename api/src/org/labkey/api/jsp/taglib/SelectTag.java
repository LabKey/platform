/*
 * Copyright (c) 2017-2019 LabKey Corporation
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
package org.labkey.api.jsp.taglib;

import org.labkey.api.util.PageFlowUtil;

import javax.servlet.jsp.JspException;
import javax.servlet.jsp.tagext.BodyTagSupport;
import java.io.IOException;

@Deprecated // Migrate all remaining usages to SelectBuilder and delete this class
public class SelectTag extends BodyTagSupport
{
    private String label = null;
    private String name = null;

    public String getLabel()
    {
        return label;
    }

    public void setLabel(String label)
    {
        this.label = label;
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    @Override
    public int doStartTag() throws JspException
    {
        StringBuilder sb = new StringBuilder();

        sb.append("<label class=\"control-label\">");
        sb.append(PageFlowUtil.filter(getLabel()));
        sb.append("</label>");

        sb.append("<select")
            .append(" class=\"").append("form-control").append("\"")
            .append(" name=\"").append(getName()).append("\"");

        if (getId() != null)
            sb.append(" id=\"").append(getId()).append("\"");

        sb.append(">");

        print(sb);
        return BodyTagSupport.EVAL_BODY_INCLUDE;
    }

    @Override
    public int doEndTag() throws JspException
    {
        StringBuilder sb = new StringBuilder();

        sb.append("</select>");

        print(sb);
        return BodyTagSupport.EVAL_PAGE;
    }

    private void print(StringBuilder sb) throws JspException
    {
        try
        {
            pageContext.getOut().print(sb.toString());
        }
        catch (IOException e)
        {
            throw new JspException(e);
        }
    }
}
