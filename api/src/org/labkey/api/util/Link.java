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
import java.util.Collections;

import static org.labkey.api.util.DOM.A;
import static org.labkey.api.util.DOM.Attribute.name;
import static org.labkey.api.util.DOM.Attribute.href;
import static org.labkey.api.util.DOM.Attribute.onclick;
import static org.labkey.api.util.DOM.Attribute.rel;
import static org.labkey.api.util.DOM.Attribute.style;
import static org.labkey.api.util.DOM.Attribute.target;
import static org.labkey.api.util.DOM.Attribute.title;
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
        return HtmlString.unsafe(appendTo(new StringBuilder()).toString());
    }

    @Override
    public Appendable appendTo(Appendable out)
    {
        A(at(lb.attributes==null ? Collections.emptyMap() : lb.attributes)
                .cl(lb.iconCls != null, lb.iconCls, lb.cssClass)
                .id(lb.id)
                .at(lb.usePost, href, null, lb.href)
                .at(target, lb.target)
                .at(lb.usePost, onclick, PageFlowUtil.postOnClickJavaScript(lb.href, lb.confirmMessage), lb.onClick)
                .at(rel, lb.rel)
                .at(title, lb.title)
                .at(style, lb.style)
                .at(name, lb.name)
                .data(null != lb.tooltip, "tt", "tooltip")
                .data(null != lb.tooltip, "placement","top")
                .data(null != lb.tooltip, "original-title", lb.tooltip),
            lb.iconCls!=null ? null : lb.html
        ).appendTo(out);
        return out;
    }

    @Override
    public String toString()
    {
        return getHtmlString().toString();
    }

    @Override
    public void render(RenderContext ctx, Writer out) throws IOException
    {
        appendTo(out);
    }

    public static class LinkBuilder extends DisplayElementBuilder<Link, LinkBuilder>
    {
        public LinkBuilder()
        {
            cssClass = "labkey-text-link";
        }

        public LinkBuilder(@NotNull String text)
        {
            this(HtmlString.of(text));
        }

        public LinkBuilder(@NotNull HtmlString html)
        {
            this();
            this.html = html;
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
