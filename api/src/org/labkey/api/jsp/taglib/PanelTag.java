/*
 * Copyright (c) 2017 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.util.PageFlowUtil;

import javax.servlet.jsp.JspException;
import javax.servlet.jsp.tagext.BodyTagSupport;
import java.io.IOException;

public class PanelTag extends BodyTagSupport
{
    private String className = null;
    private String id = null;
    private String type = "default";
    private String title = null;
    private Integer width = null;

    public String getClassName()
    {
        return className;
    }

    public void setClassName(String className)
    {
        this.className = className;
    }

    @Override
    public String getId()
    {
        return id;
    }

    @Override
    public void setId(String id)
    {
        this.id = id;
    }

    public String getType()
    {
        return type;
    }

    public void setType(String type)
    {
        this.type = type;
    }

    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public Integer getWidth()
    {
        return width;
    }

    public void setWidth(Integer width)
    {
        this.width = width;
    }

    public int doStartTag() throws JspException
    {
        StringBuilder sb = new StringBuilder();

        sb.append("<div class=\"panel panel-" + getType());
        if (StringUtils.isNotEmpty(getClassName()))
            sb.append(" " + getClassName().trim());
        sb.append("\"");

        if (StringUtils.isNoneEmpty(getId()))
            sb.append(" id=\"" + getId() + "\"");

        String style = getWidth() != null ? "width: " + getWidth() + "px;" : "";
        if (StringUtils.isNoneEmpty(style))
            sb.append(" style=\"" + style + "\"");

        sb.append(">");

        if (StringUtils.isNoneEmpty(getTitle()))
        {
            sb.append("<div class=\"panel-heading\">");
            sb.append(" <h3 class=\"panel-title pull-left\">" + PageFlowUtil.filter(title) + "</h3>");
            sb.append(" <div class=\"clearfix\"></div>");
            sb.append("</div>");
        }

        sb.append("<div class=\"panel-body\">");

        write(sb);
        return BodyTagSupport.EVAL_BODY_INCLUDE;
    }

    public int doEndTag() throws JspException
    {
        StringBuilder sb = new StringBuilder();

        sb.append("</div></div>");

        write(sb);
        return BodyTagSupport.EVAL_PAGE;
    }

    private void write(StringBuilder sb) throws JspException
    {
        try
        {
            pageContext.getOut().write(sb.toString());
        }
        catch (IOException e)
        {
            throw new JspException(e);
        }
    }
}
