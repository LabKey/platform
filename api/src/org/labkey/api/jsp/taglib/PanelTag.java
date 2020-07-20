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

import org.labkey.api.util.DOM;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.Pair;
import org.labkey.api.util.UnexpectedException;

import javax.servlet.jsp.JspException;
import javax.servlet.jsp.tagext.BodyTagSupport;
import java.io.IOException;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.labkey.api.util.DOM.DIV;
import static org.labkey.api.util.DOM.H3;
import static org.labkey.api.util.DOM.cl;

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

    Pair<HtmlString,HtmlString> tag;

    @Override
    public int doStartTag() throws JspException
    {
        String style = getWidth() != null ? "width: " + getWidth() + "px;" : "";

        tag = DOM.renderWithPlaceHolder(
            DIV(cl("panel", "panel-" + getType(), getClassName()).at(DOM.Attribute.id, getId(), DOM.Attribute.style, style),
                isBlank(getTitle()) ? null :
                    DIV(cl("panel-heading"),
                        H3(cl("panel-title", "pull-left"), getTitle()),
                        DIV(cl("clearfix"))
                    ),
                DIV(cl("panel-body"), DOM.BODY_PLACE_HOLDER)
            )
        );

        try
        {
            pageContext.getOut().print(tag.first);
        }
        catch(IOException x)
        {
            throw UnexpectedException.wrap(x);
        }

        return BodyTagSupport.EVAL_BODY_INCLUDE;
    }

    @Override
    public int doEndTag() throws JspException
    {
        try
        {
            pageContext.getOut().print(tag.second);
        }
        catch(IOException x)
        {
            throw UnexpectedException.wrap(x);
        }

        return BodyTagSupport.EVAL_PAGE;
    }
}
