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

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DisplayElement;
import org.springframework.web.servlet.mvc.Controller;

import java.util.Map;
import java.util.TreeMap;

public abstract class DisplayElementBuilder<T extends DisplayElement & HasHtmlString, BUILDER extends DisplayElementBuilder<T, BUILDER>> implements HasHtmlString, SafeToRender
{
    HtmlString html;
    String href;
    String id;
    String onClick;
    Map<String, String> attributes;
    String cssClass;
    String tooltip;
    String iconCls;
    boolean usePost = false;
    String confirmMessage = null;
    String rel;
    String style;
    String name;
    String target;
    String title;

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
        if (attributes != null && attributes.containsKey(null))
            throw new NullPointerException("Unexpected key value in attributes");
        if (attributes != null && !attributes.isEmpty())
            this.attributes = new TreeMap<>(attributes);
        else
            this.attributes = null;
        return getThis();
    }

    public BUILDER title(String title)
    {
        this.title = title;
        return getThis();
    }

    public BUILDER addClass(@NotNull String cssClass)
    {
        if (StringUtils.isEmpty(this.cssClass))
            this.cssClass = cssClass;
        else
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

    public BUILDER nofollow()
    {
        return rel("nofollow");
    }

    public BUILDER rel(String rel)
    {
        this.rel = rel;
        return getThis();
    }

    public BUILDER target(String target)
    {
        this.target = target;
        return getThis();
    }

    public BUILDER name(String name)
    {
        this.name = name;
        return getThis();
    }

    public BUILDER style(String style)
    {
        this.style = style;
        return getThis();
    }

    /**
     * Non-null confirmMessage causes clicking on the element to display a confirmation dialog with the specified message
     * and then, if confirmed, to POST to the href. A null confirmMessage results in a POST with no confirmation dialog.
     * @param confirmMessage The confirmation message
     * @return this builder
     */
    public BUILDER usePost(String confirmMessage)
    {
        this.confirmMessage = confirmMessage;
        return usePost();
    }

    abstract public @NotNull T build();

    @Override
    public HtmlString getHtmlString()
    {
        return build().getHtmlString();
    }

    @Override
    public String toString()
    {
        return getHtmlString().toString();
    }
}
