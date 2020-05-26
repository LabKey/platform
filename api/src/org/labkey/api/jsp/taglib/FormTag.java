/*
 * Copyright (c) 2014-2019 LabKey Corporation
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
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.CSRFUtil;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.element.Input;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;

import javax.servlet.jsp.JspException;
import javax.servlet.jsp.JspWriter;
import javax.servlet.jsp.tagext.BodyTagSupport;
import java.io.IOException;

public class FormTag extends BodyTagSupport
{
    private String autoComplete;
    private Boolean isNoValidate;
    private String name;
    private String method="GET";
    private HtmlString action;
    private String enctype;
    private String target;
    private String onsubmit;
    private String style;
    private String _class;
    private String _layout;

    public void setName(String name)
    {
        this.name = name;
    }

    public String getMethod()
    {
        return method;
    }

    public void setMethod(String method)
    {
        this.method = method.toUpperCase();
    }

    public Object getAction()
    {
        return action;
    }

    // Our JSP tag classes expect unencoded parameters (they encode everything at render time), but this method accepts
    // an encoded action for backward compatibility purposes. TODO: Migrate these cases and remove this method.
    @Deprecated
    public void setAction(HtmlString action)
    {
        this.action = action;
    }

    public void setAction(String s)
    {
        if (AppProps.getInstance().getUseContainerRelativeURL() &&
                StringUtils.containsNone(s, "/-") && (StringUtils.endsWith(s, ".view") || StringUtils.endsWith(s, ".post")))
        {
            ViewContext ctx = HttpView.getRootContext();
            if (null != ctx)
                s = ctx.getActionURL().getController() + "-" + s;
        }

        this.action = HtmlString.of(s);
    }

    public void setAction(ActionURL action)
    {
        this.action = null==action ? null : action.getHtmlString();
    }

    public String getEnctype()
    {
        return enctype;
    }

    public void setEnctype(String enctype)
    {
        this.enctype = enctype;
    }

    public String getTarget()
    {
        return target;
    }

    public void setTarget(String target)
    {
        this.target = target;
    }

    public String getOnsubmit()
    {
        return onsubmit;
    }

    public void setOnsubmit(String onsubmit)
    {
        this.onsubmit = onsubmit;
    }

    public String getStyle()
    {
        return style;
    }

    public void setStyle(String style)
    {
        this.style = style;
    }

    public void setClass(String aClass)
    {
        _class = aClass;
    }

    public String getClassName()
    {
        return _class;
    }

    public void setClassName(String aClass)
    {
        _class = aClass;
    }

    public String getLayout()
    {
        return _layout;
    }

    public void setLayout(String layout)
    {
        _layout = layout;
    }

    public void setAutoComplete(String autoComplete)
    {
        this.autoComplete = autoComplete;
    }

    public void setIsNoValidate(Boolean isNoValidate)
    {
        this.isNoValidate = isNoValidate;
    }

    public Boolean isNoValidate()
    {
        return isNoValidate == null ? false : isNoValidate;
    }

    @Override
    public int doStartTag() throws JspException
    {
        // TODO: HtmlString or HTML DOM
        StringBuilder sb = new StringBuilder();
        sb.append("<form");
        if (StringUtils.isNotEmpty(getId()))
            sb.append(" id=\"").append(getId()).append("\"");
        if (StringUtils.isNotEmpty(name))
            sb.append(" name=\"").append(name).append("\"");
        if (StringUtils.isNotEmpty(method))
            sb.append(" method=\"").append(method).append("\"");
        if (null != action)
            sb.append(" action=\"").append(action).append("\"");
        if (StringUtils.isNotEmpty(enctype))
            sb.append(" enctype=\"").append(enctype).append("\"");
        if (StringUtils.isNotEmpty(target))
            sb.append(" target=\"").append(target).append("\"");
        if (StringUtils.isNotEmpty(onsubmit))
            sb.append(" onsubmit=\"").append(onsubmit).append("\"");
        if (StringUtils.isNotEmpty(style))
            sb.append(" style=\"").append(style).append("\"");
        if (StringUtils.isNotEmpty(autoComplete))
            sb.append(" autocomplete=\"").append(autoComplete).append("\"");

        if (isNoValidate())
            sb.append(" novalidate");

        String cls = "";
        if (StringUtils.isNotEmpty(_class))
            cls += " " + _class;
        if (getLayout() != null)
        {
            if (Input.Layout.HORIZONTAL.toString().equalsIgnoreCase(getLayout()))
                cls += " form-horizontal";
            else if (Input.Layout.INLINE.toString().equalsIgnoreCase(getLayout()))
                cls += " form-inline";
        }

        if (StringUtils.isNotEmpty(cls))
            sb.append(" class=\"").append(cls).append("\"");
        sb.append(">");
        try
        {
            JspWriter out = pageContext.getOut();
            out.print(sb.toString());
        }
        catch (IOException e)
        {
            throw new JspException(e);
        }
        return BodyTagSupport.EVAL_BODY_INCLUDE;
    }


    @Override
    public int doEndTag() throws JspException
    {
        try
        {
            JspWriter out = pageContext.getOut();
            String csrf = CSRFUtil.getExpectedToken(pageContext);
            if (StringUtils.equals("POST", method))
            {
                out.print("<input type=\"hidden\" name=\"" + CSRFUtil.csrfName + "\" value=\"" + PageFlowUtil.filter(csrf) + "\">");
            }
            out.print("</form>");
        }
        catch (IOException e)
        {
            throw new JspException(e);
        }
        return BodyTagSupport.EVAL_PAGE;
    }
}
