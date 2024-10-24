/*
 * Copyright (c) 2017-2018 LabKey Corporation
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
import org.labkey.api.util.HtmlString;

import java.io.IOException;

public class DisplayField extends Input
{
    private DisplayField(DisplayFieldBuilder builder)
    {
        super(builder);
    }

    @Override
    protected void doInput(Appendable sb) throws IOException
    {
        sb.append("<p class=\"form-control-static\">");
        if (!HtmlString.isEmpty(getValue()))
            sb.append(h(getValue()));
        sb.append("</p>");
    }

    @Override
    protected void doLabel(Appendable sb) throws IOException
    {
        boolean needsLayoutWrapping = Layout.HORIZONTAL.equals(getLayout()) && needsWrapping();

        sb.append("<span");

        String cls = "";
        if (StringUtils.isNotEmpty(getLabelClassName()))
            cls += " " + getLabelClassName();
        if (needsLayoutWrapping)
            cls += " col-sm-3 col-lg-2";

        if (StringUtils.isNotEmpty(cls))
            sb.append(" class=\"").append(h(cls)).append("\"");

        sb.append(">");

        if (getLabel() != null)
            sb.append(h(getLabel())).append(":");

        if (Layout.INLINE.equals(getLayout()) && !HtmlString.isEmpty(getContextContent()))
            super.doContextField(sb);

        sb.append("</span> ");
    }

    public static class DisplayFieldBuilder extends InputBuilder<DisplayFieldBuilder>
    {
        @Override
        public DisplayField build()
        {
            return new DisplayField(this);
        }
    }
}
