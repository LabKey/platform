/*
 * Copyright (c) 2019 LabKey Corporation
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
package org.labkey.api.util;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.RenderContext;
import org.labkey.api.view.DisplayElement;

import java.io.IOException;
import java.io.Writer;

import static org.labkey.api.util.DOM.at;

public class Link extends DisplayElement implements HasHtmlString
{
    private final LinkBuilder lb;

    public Link(LinkBuilder linkBuilder)
    {
        lb = linkBuilder;

        if (lb.usePost && null != lb.onClick)
            throw new IllegalStateException("Can't specify both usePost and onClick");
    }

    @Override
    public HtmlString getHtmlString()
    {
        StringBuilder sb = new StringBuilder();

        sb.append("<a");

        if (null != lb.href && !lb.usePost)
            sb.append(" href=\"").append(PageFlowUtil.filter(lb.href)).append("\"");

        boolean icon = lb.iconCls != null;

        if (null != lb.cssClass || icon)
            sb.append(" class=\"").append(icon ? lb.iconCls : lb.cssClass).append("\"");

        if (null != lb.attributes)
        {
            var attrs = at(lb.attributes);
            attrs.forEach(a -> {
                sb.append(" ").append(PageFlowUtil.filter(a.getKey())).append("=\"").append(PageFlowUtil.filter(a.getValue())).append("\"");
            });
        }

        if (null != lb.id)
            sb.append(" id=\"").append(lb.id).append("\"");

        if (lb.usePost)
            sb.append(" onClick=\"").append(null != lb.confirmMessage ? PageFlowUtil.confirmAndPostJavaScript(lb.confirmMessage, lb.href) : PageFlowUtil.postOnClickJavaScript(lb.href)).append("\"");
        else if (null != lb.onClick)
            sb.append(" onClick=\"").append(lb.onClick).append("\"");

        if (null != lb.tooltip)
            sb.append(" data-tt=\"tooltip\" data-placement=\"top\" title data-original-title=\"").append(lb.tooltip).append("\"");

        sb.append(">");

        if (!icon)
            sb.append(PageFlowUtil.filter(lb.text));

        sb.append("</a>");

        return HtmlString.unsafe(sb.toString());
    }

    @Override // TODO: HtmlString - remove this
    public String toString()
    {
        return getHtmlString().toString();
    }

    @Override
    public void render(RenderContext ctx, Writer out) throws IOException
    {
        out.write(getHtmlString().toString());
    }

    public static class LinkBuilder extends DisplayElementBuilder<Link, LinkBuilder>
    {
        public LinkBuilder()
        {
            cssClass = "labkey-text-link";
        }

        public LinkBuilder(@NotNull String text)
        {
            this();
            this.text = text;
        }

        @Override
        protected LinkBuilder getThis()
        {
            return this;
        }

        @NotNull
        @Override
        public Link build()
        {
            return new Link(this);
        }
    }
}
