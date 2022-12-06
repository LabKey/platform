/*
 * Copyright (c) 2012-2019 LabKey Corporation
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
import org.labkey.api.util.UniqueID;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;

import java.io.IOException;
import java.io.Writer;

/**
 * User: klum
 * Date: 11/12/12
 */
public abstract class AutoCompleteTag extends SimpleTagBase
{
    private String _name;
    private String _id;
    private ActionURL _url;
    private String _value;

    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }

    public String getId()
    {
        return _id;
    }

    public void setId(String id)
    {
        _id = id;
    }

    public ActionURL getUrl()
    {
        return _url;
    }

    public void setUrl(ActionURL url)
    {
        _url = url;
    }

    public String getValue()
    {
        return _value;
    }

    public void setValue(String value)
    {
        _value = value;
    }

    @Override
    public void doTag() throws IOException
    {
        // TODO: SafeToRenderBuilder

        String renderId = "auto-complete-div-" + UniqueID.getRequestScopedUID(HttpView.currentRequest());
        StringBuilder sb = new StringBuilder()
            .append("<script type=\"text/javascript\" nonce=\"" + HttpView.currentPageConfig().getScriptNonce() + "\">\n")
            .append("    LABKEY.requiresScript('completion',function(){\n")
            .append("        Ext4.onReady(function(){\n")
            .append("            Ext4.create('LABKEY.element.AutoCompletionField', {\n")
            .append("                renderTo: ").append(PageFlowUtil.jsString(renderId)).append(",\n")
            .append("                completionUrl: ").append(PageFlowUtil.jsString(getUrl().getLocalURIString())).append(",\n")
            .append(getTagConfig(StringUtils.repeat(' ', 16)))
            .append("            })\n")
            .append("        })\n")
            .append("    });\n")
            .append("</script>\n")
            .append("<div id=\"")
            .append(renderId)
            .append("\"></div>");

        Writer out = getWriter();

        out.write(sb.toString());
    }

    // Allow subclasses to override to provide a generic Writer (not a JspWriter)
    protected Writer getWriter()
    {
        return getOut();
    }

    protected abstract String getTagConfig(String padding);

    protected void addOptionalAttrs(StringBuilder sb, String padding)
    {
        // optional attribute
        if (getId() != null)
            sb.append(padding).append("    id           : ").append(PageFlowUtil.jsString(getId())).append(",\n");
    }
}
