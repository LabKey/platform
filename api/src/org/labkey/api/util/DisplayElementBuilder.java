package org.labkey.api.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DisplayElement;
import org.springframework.web.servlet.mvc.Controller;

import java.util.Map;

public abstract class DisplayElementBuilder<T extends DisplayElement, BUILDER extends DisplayElementBuilder<T, BUILDER>>
{
    String text;
    String href;
    String id;
    String onClick;
    String attributes;
    String cssClass;
    String tooltip;
    String iconCls;
    boolean usePost = false;

    public DisplayElementBuilder()
    {
    }

    abstract protected BUILDER getThis();

    public BUILDER href(@Nullable String href)
    {
        this.href = href;
        return getThis();
    }

    public BUILDER href(@Nullable URLHelper href)
    {
        return href(null != href ? href.toString() : null);
    }

    public BUILDER href(@NotNull ReturnURLString returnHref)
    {
        return href(returnHref.toString());
    }

    public BUILDER href(@NotNull Class<? extends Controller> actionClass, Container container)
    {
        return href(new ActionURL(actionClass, container));
    }

    public BUILDER id(String id)
    {
        this.id = id;
        return getThis();
    }

    public BUILDER onClick(String onClick)
    {
        this.onClick = onClick;
        return getThis();
    }

    public BUILDER attributes(Map<String, String> attributes)
    {
        if (attributes != null && !attributes.isEmpty())
        {
            StringBuilder sAttributes = new StringBuilder();
            for (String attribute : attributes.keySet())
                sAttributes.append(PageFlowUtil.filter(attribute)).append("=\"").append(PageFlowUtil.filter(attributes.get(attribute))).append("\"");
            this.attributes = sAttributes.toString();
        }
        else
            this.attributes = null;

        return getThis();
    }

    public BUILDER addClass(@NotNull String cssClass)
    {
        if (this.cssClass == null)
            this.cssClass = "";
        this.cssClass += " " + cssClass;
        return getThis();
    }

    public BUILDER clearClasses()
    {
        this.cssClass = null;
        return getThis();
    }

    public BUILDER tooltip(String tooltip)
    {
        this.tooltip = tooltip;
        return getThis();
    }

    public BUILDER iconCls(String iconCls)
    {
        this.iconCls = iconCls;
        return getThis();
    }

    public BUILDER usePost()
    {
        this.usePost = true;
        return getThis();
    }

    abstract public T build();

    @Override
    public String toString()
    {
        return build().toString();
    }
}
