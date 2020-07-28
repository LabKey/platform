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
import org.labkey.api.util.DOM;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.util.element.Input;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;

import javax.servlet.jsp.JspException;
import javax.servlet.jsp.tagext.BodyTagSupport;
import java.io.IOException;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

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


    HtmlString endTag;

    @Override
    public int doStartTag() throws JspException
    {
        var formAttributes = DOM.at(DOM.Attribute.id, getId());
        if (isNotBlank(name))
            formAttributes.at(DOM.Attribute.name, name);
        if (isNotBlank(method))
            formAttributes.at(DOM.Attribute.method, method);
        if (!HtmlString.isBlank(action))
            formAttributes.at(DOM.Attribute.action, action);
        if (isNotBlank(enctype))
            formAttributes.at(DOM.Attribute.enctype, enctype);
        if (isNotBlank(target))
            formAttributes.at(DOM.Attribute.target, target);
        if (isNotBlank(onsubmit))
            formAttributes.at(DOM.Attribute.onsubmit, onsubmit);
        if (isNotBlank(style))
            formAttributes.at(DOM.Attribute.style, style);
        if (isNotBlank(autoComplete))
            formAttributes.at(DOM.Attribute.autocomplete, autoComplete);
        if (isNoValidate())
            formAttributes.at(DOM.Attribute.novalidate, Boolean.TRUE);

        var form =
                DOM.LK.FORM(formAttributes
                    .cl(isNotBlank(_class), _class)
                    .cl(Input.Layout.HORIZONTAL.toString().equalsIgnoreCase(getLayout()), "form-horizontal")
                    .cl(Input.Layout.INLINE.toString().equalsIgnoreCase(getLayout()), "form-inline"),
                    DOM.BODY_PLACE_HOLDER);
        endTag = DOM.renderTemplate(form, pageContext.getOut());

        return BodyTagSupport.EVAL_BODY_INCLUDE;
    }

    @Override
    public int doEndTag() throws JspException
    {
        try
        {
            pageContext.getOut().print(endTag);
        }
        catch(IOException x)
        {
            throw UnexpectedException.wrap(x);
        }

        return BodyTagSupport.EVAL_PAGE;
    }
}
