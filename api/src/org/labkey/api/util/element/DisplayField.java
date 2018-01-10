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
package org.labkey.api.util.element;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.util.PageFlowUtil;

public class DisplayField extends Input
{
    private DisplayField(DisplayFieldBuilder builder)
    {
        super(builder);
    }

    @Override
    protected void doInput(StringBuilder sb)
    {
        sb.append("<p class=\"form-control-static\">");
        doValue(sb);
        sb.append("</p>");
    }

    @Override
    protected void doValue(StringBuilder sb)
    {
        if (getValue() != null && !"".equals(getValue()))
        {
            sb.append(isUnsafeValue() ? getValue() : PageFlowUtil.filter(getValue()));
        }
    }

    @Override
    protected void doLabel(StringBuilder sb)
    {
        boolean needsLayoutWrapping = Layout.HORIZONTAL.equals(getLayout()) && needsWrapping();

        sb.append("<span");

        String cls = "";
        if (StringUtils.isNotEmpty(getLabelClassName()))
            cls += " " + getLabelClassName();
        if (needsLayoutWrapping)
            cls += " col-sm-3 col-lg-2";

        if (StringUtils.isNotEmpty(cls))
            sb.append(" class=\"").append(PageFlowUtil.filter(cls)).append("\"");

        sb.append(">");

        if (getLabel() != null)
            sb.append(PageFlowUtil.filter(getLabel())).append(":");

        if (Layout.INLINE.equals(getLayout()) && StringUtils.isNotEmpty(getContextContent()))
            super.doContextField(sb);

        sb.append("</span> ");
    }

    public static class DisplayFieldBuilder extends InputBuilder<DisplayFieldBuilder>
    {
        public DisplayField build()
        {
            return new DisplayField(this);
        }
    }
}
