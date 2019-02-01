package org.labkey.api.util;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.RenderContext;
import org.labkey.api.view.DisplayElement;

import java.io.IOException;
import java.io.Writer;

public class Link extends DisplayElement
{
    private LinkBuilder lb;

    public Link(LinkBuilder linkBuilder)
    {
        lb = linkBuilder;

        if (lb.usePost && null != lb.onClick)
            throw new IllegalStateException("Can't specify usePost and onClick");
    }

    @Override
    public String toString()
    {
        boolean icon = lb.iconCls != null;
        StringBuilder sb = new StringBuilder();

        sb.append("<a ");

        if (null != lb.cssClass || icon)
            sb.append("class=\"").append(icon ? lb.iconCls : lb.cssClass).append("\" ");

        if (null != lb.attributes)
            sb.append(lb.attributes);

        sb.append("href=\"");

        if (lb.usePost)
            sb.append("javascript:void(0);");
        else
            sb.append(PageFlowUtil.filter(lb.href));

        sb.append("\" ");

        if (null != lb.id)
            sb.append(" id=\"").append(lb.id).append("\"");

        if (lb.usePost)
            sb.append(" onClick=\"").append(PageFlowUtil.postOnClickJavaScript(lb.href)).append("\"");
        else if (null != lb.onClick)
            sb.append(" onClick=\"").append(lb.onClick).append("\"");

        if (null != lb.tooltip)
            sb.append(" data-tt=\"tooltip\" data-placement=\"top\" title data-original-title=\"").append(lb.tooltip).append("\"");

        sb.append(">");

        if (!icon)
            sb.append(PageFlowUtil.filter(lb.text));

        sb.append("</a>");

        return sb.toString();
    }

    @Override
    public void render(RenderContext ctx, Writer out) throws IOException
    {
        out.write(toString());
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

        @Override
        public Link build()
        {
            return new Link(this);
        }
    }
}
