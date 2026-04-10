package org.labkey.api.util;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.RenderContext;
import org.labkey.api.view.DisplayElement;
import org.labkey.api.view.HttpView;
import org.labkey.api.writer.HtmlWriter;

import java.util.Collections;

import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static org.apache.commons.lang3.StringUtils.trimToNull;
import static org.labkey.api.util.DOM.A;
import static org.labkey.api.util.DOM.Attribute.disabled;
import static org.labkey.api.util.DOM.at;

public class LinkBuilder extends DisplayElementBuilder<LinkBuilder.Link, LinkBuilder>
{
    // Simple unstyled link -- replacement for hand-coded <a> tags
    public static LinkBuilder simpleLink(@NotNull String text)
    {
        return new LinkBuilder(text).clearClasses();
    }

    // Simple unstyled link -- replacement for hand-coded <a> tags
    public static LinkBuilder simpleLink(@NotNull String text, @NotNull URLHelper url)
    {
        return simpleLink(text).href(url);
    }

    // Simple unstyled link -- replacement for hand-coded <a> tags
    public static LinkBuilder simpleLink(@NotNull String text, @NotNull String url)
    {
        return simpleLink(text).href(url);
    }

    // Simple unstyled link -- replacement for hand-coded <a> tags
    public static LinkBuilder simpleLink(@NotNull DOM.Renderable html)
    {
        return new LinkBuilder(html).clearClasses();
    }

    // Simple unstyled link -- replacement for hand-coded <a> tags
    public static LinkBuilder simpleLink(@NotNull DOM.Renderable html, @NotNull URLHelper url)
    {
        return simpleLink(html).href(url);
    }

    // Standard LabKey-styled text link
    public static LinkBuilder labkeyLink(@NotNull String text)
    {
        return new LinkBuilder(text);
    }

    // Standard LabKey-styled text link
    public static LinkBuilder labkeyLink(@NotNull String text, @NotNull URLHelper url)
    {
        return labkeyLink(text).href(url);
    }

    // Standard LabKey-styled text link
    public static LinkBuilder labkeyLink(@NotNull String text, @NotNull String url)
    {
        return labkeyLink(text).href(url);
    }

    public LinkBuilder()
    {
        cssClass = "labkey-text-link";
    }

    // Use factory methods above TODO: Make private
    public LinkBuilder(@NotNull String text)
    {
        this(HtmlString.of(text));
    }

    public LinkBuilder(@NotNull DOM.Renderable html)
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

    public static class Link extends DisplayElement implements HasHtmlString, SafeToRender
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
            String clickEvent = null;

            if (lb.usePost || isNotEmpty(lb.onClick))
            {
                 if (lb.usePost)
                     clickEvent = PageFlowUtil.postOnClickJavaScriptEvent();
                 else
                     clickEvent = lb.onClick;
            }

            String id = lb.id;

            // Allow generation of HTML outside an active request, where JavaScript event handlers are unlikely to be important
            if (HttpView.hasCurrentView())
            {
                var page = HttpView.currentPageConfig();
                if (isBlank(id))
                    id = page.makeId("a_");
                page.addHandler(id, "click", clickEvent);
                if (isNotEmpty(lb.onMouseOver))
                    page.addHandler(id, "mouseover", lb.onMouseOver);
            }

            return A(at(lb.attributes==null ? Collections.emptyMap() : lb.attributes)
                    .cl(lb.iconCls != null, lb.iconCls, lb.cssClass)
                    .id(id)
                    .at(!lb.usePost, DOM.Attribute.href,  defaultIfBlank(lb.href, "#"), "#")
                    .data(lb.usePost, "href", lb.href)
                    .data(lb.usePost, "confirmmessage", trimToNull(lb.confirmMessage))
                    .at(DOM.Attribute.target, lb.target)
                    .at(DOM.Attribute.rel, lb.rel)
                    .at(DOM.Attribute.title, lb.title)
                    .at(DOM.Attribute.style, lb.style)
                    .at(DOM.Attribute.name, lb.name)
                    .at(DOM.Attribute.tabindex, lb.tabindex)
                    .at(!lb.enabled, disabled, true)
                    .data(null != lb.tooltip, "tt", "tooltip")
                    .data(null != lb.tooltip, "placement","top")
                    .data(null != lb.tooltip, "original-title", lb.tooltip)
                    .aria(lb.iconCls != null && lb.tooltip != null, "label", lb.tooltip),
                    (lb.iconCls != null ? null : lb.html)
            ).appendTo(out);
        }

        @Override
        public String toString()
        {
            return getHtmlString().toString();
        }

        @Override
        public void render(RenderContext ctx, HtmlWriter out)
        {
            out.write(this);
        }
    }
}
