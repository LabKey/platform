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
package org.labkey.api.util;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.RenderContext;
import org.labkey.api.view.DisplayElement;

import java.io.IOException;
import java.io.Writer;
import java.util.Collections;
import java.util.Map;

import static org.labkey.api.util.DOM.A;
import static org.labkey.api.util.DOM.Attribute;
import static org.labkey.api.util.DOM.Attribute.onclick;
import static org.labkey.api.util.DOM.Attribute.tabindex;
import static org.labkey.api.util.DOM.Attribute.title;
import static org.labkey.api.util.DOM.Attribute.type;
import static org.labkey.api.util.DOM.INPUT;
import static org.labkey.api.util.DOM.LK.FA;
import static org.labkey.api.util.DOM.SPAN;
import static org.labkey.api.util.DOM.at;
import static org.labkey.api.util.DOM.createHtmlFragment;


/**
 * Basic button UI element. Might be a simple link, have a JavaScript handler, etc.
 * Created by Nick Arnold on 2/27/14.
 * Testing of this class can be found in the Core module's TestController.ButtonAction
 */
public class Button extends DisplayElement implements HasHtmlString
{
    // Button constants
    private static final String CLS = "labkey-button";
    private static final String DISABLEDCLS = "labkey-disabled-button";
    private static final String MENU_CLS = "labkey-menu-button";
    private static final String PRIMARY_CLS = "primary";

    // Composable members
    private final String cssClass;
    private final String iconCls;
    private final String text;
    private final HtmlString html; // required
    private final String href;
    private final String onClick;
    private final String id;
    private final Map<String, String> attributes;
    private final String tooltip;
    private final String typeCls;
    private final boolean disableOnClick;
    private final boolean dropdown;
    private final boolean enabled;
    private final boolean submit;
    private final boolean usePost;
    private final String rel;
    private final String name;
    private final String style;
    private final String confirmMessage;
    private final String target;

    private Button(ButtonBuilder builder)
    {
        this.cssClass = builder.cssClass;
        this.dropdown = builder.dropdown;
        this.text     = builder.text;   // used as tooltip if tooltip not provided
        this.html     = builder.html;
        this.href = builder.href;
        this.onClick = builder.onClick;
        this.iconCls = builder.iconCls;
        this.id = builder.id;
        this.attributes = builder.attributes == null ? Collections.emptyMap() : builder.attributes;
        this.disableOnClick = builder.disableOnClick;
        this.enabled = builder.enabled;
        this.submit = builder.submit;
        this.tooltip = builder.tooltip;
        this.typeCls = builder.typeCls;
        this.usePost = builder.usePost;
        this.rel = builder.rel;
        this.name = builder.name;
        this.style = builder.style;
        this.confirmMessage = builder.confirmMessage;
        this.target = builder.target;

        if (this.usePost && null != this.onClick)
            throw new IllegalStateException("Can't specify both usePost and onClick");
    }

    public String getCssClass()
    {
        return this.cssClass;
    }

    public String getText()
    {
        return text;
    }

    public String getTarget()
    {
        return target;
    }

    public boolean isDropdown()
    {
        return dropdown;
    }

    public String getHref()
    {
        return usePost ? "javascript:void(0);" : href;
    }

    public String getName()
    {
        return name;
    }

    public String getRel()
    {
        return rel;
    }

    public String getOnClick()
    {
        return onClick;
    }

    public String getIconCls()
    {
        return iconCls;
    }

    public String getStyle()
    {
        return style;
    }

    public String getId()
    {
        return id;
    }

    public Map<String,String> getAttributes()
    {
        return attributes;
    }

    public boolean isDisableOnClick()
    {
        return disableOnClick;
    }

    public boolean isEnabled()
    {
        return enabled;
    }

    public boolean isSubmit()
    {
        return submit;
    }

    private String generateOnClick(String id)
    {
        // prepare onclick method and overrides
        final String onClick = usePost ? PageFlowUtil.postOnClickJavaScript(href, confirmMessage) : StringUtils.defaultString(getOnClick());

        // we're modifying the javascript, so need to use whatever quoting the caller used
        char quote = PageFlowUtil.getUsedQuoteSymbol(onClick);

        // quoted CSS classes used in scripting
        final String qDisabledCls = quote + DISABLEDCLS + quote;

        // check if the disabled class is applied, if so, do nothing onclick
        String onClickMethod = "if(this.className.indexOf(" + qDisabledCls + ") != -1){return false;}";

        if (isDisableOnClick())
        {
            onClickMethod += ";LABKEY.Utils.addClass(this," + qDisabledCls + ");";
        }

        if (isSubmit())
        {
            final String submitCode = "submitForm(document.getElementById(" + quote + id + quote + ").form);return false;";

            if ("".equals(onClick))
                onClickMethod += submitCode;
            else
            {
                // we allow the onclick method to cancel the submit
                // Question: This isn't activated when the user hits 'enter' so why support this?
                onClickMethod += "this.form=document.getElementById(" + quote + id + quote + ").form;";
                onClickMethod += "if(isTrueOrUndefined(function(){" + onClick + "}.call(this))){" + submitCode + "}";

                if (isDisableOnClick())
                {
                    onClickMethod += "else{LABKEY.Utils.removeClass(this," + qDisabledCls + ");}";
                }
            }
        }
        else
        {
            onClickMethod += onClick;
        }

        return onClickMethod;
    }

    @Override
    public void render(RenderContext ctx, Writer out) throws IOException
    {
        out.write(toString());
    }

    @Override // TODO: HtmlString - remove this
    public String toString()
    {
        return getHtmlString().toString();
    }

    @Override
    public HtmlString getHtmlString()
    {
        boolean iconOnly = getIconCls() != null;
        String submitId = GUID.makeGUID();
        final String tip = StringUtils.defaultString(tooltip, !iconOnly ? null : StringUtils.trimToNull(getText()));

        var attrs = at(attributes)
            .id(getId())
            .at(Attribute.href, getHref(), title, tip, onclick, generateOnClick(submitId), Attribute.rel, getRel(), Attribute.name, getName(), Attribute.style, getStyle(), Attribute.target, getTarget())
            .data("tt", (StringUtils.isBlank(tip) ? null : "tooltip"))
            .data("placement", "top")
            .cl(CLS, typeCls, getCssClass())
            .cl(!isEnabled(), DISABLEDCLS)
            .cl(isSubmit(), PRIMARY_CLS)
            .cl(isDropdown(), "labkey-down-arrow")
            .cl(iconOnly, "icon-only");

        return createHtmlFragment(
            isSubmit() ?
            INPUT(at(type,"submit",tabindex,"-1",Attribute.style,"position:absolute;left:-9999px;width:1px;height:1px;",Attribute.id,submitId)) : null,
            A(attrs, iconOnly ? FA(getIconCls()) : SPAN(html))
        );
    }

    public static class ButtonBuilder extends DisplayElementBuilder<Button, ButtonBuilder>
    {
        private final HtmlString html;

        private String typeCls;
        private boolean disableOnClick;
        private boolean dropdown;
        private boolean enabled = true;
        private boolean submit;

        public ButtonBuilder(@NotNull String text)
        {
            this.text = text;
            this.html = HtmlString.of(text);
        }

        public ButtonBuilder(@NotNull HtmlString html)
        {
            this.text = null;
            this.html = html;
        }

        @Override
        protected ButtonBuilder getThis()
        {
            return this;
        }

        public ButtonBuilder dropdown(boolean dropdown)
        {
            this.dropdown = dropdown;
            return this;
        }

        public ButtonBuilder disableOnClick(boolean disableOnClick)
        {
            this.disableOnClick = disableOnClick;
            return this;
        }

        public ButtonBuilder enabled(boolean enabled)
        {
            this.enabled = enabled;
            return this;
        }

        public ButtonBuilder primary(boolean primary)
        {
            if (primary)
                this.typeCls = PRIMARY_CLS;
            // explicitly set typeCls to something other than null so that the default cls isn't applied
            else
                this.typeCls = "";
            return this;
        }

        public ButtonBuilder submit(boolean submit)
        {
            this.submit = submit;
            return this;
        }

        @NotNull
        @Override
        public Button build()
        {
            return new Button(this);
        }
    }
}
