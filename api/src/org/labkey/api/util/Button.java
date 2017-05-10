/*
 * Copyright (c) 2014-2016 LabKey Corporation
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
import org.labkey.api.data.Container;
import org.labkey.api.view.ActionURL;
import org.springframework.web.servlet.mvc.Controller;

import java.util.Map;

/**
 * Basic button UI element. Might be a simple link, have a JavaScript handler, etc.
 * Created by Nick Arnold on 2/27/14.
 * Testing of this class can be found in the Core module's TestController.ButtonAction
 */
public class Button
{
    // Button constants
    private static final String CLS = "labkey-button";
    private static final String DISABLEDCLS = "labkey-disabled-button";
    private static final String MENU_CLS = "labkey-menu-button";

    // Composable members
    private String cssClass;
    private String iconCls;
    private String text; // required
    private String href;
    private String onClick;
    private String id;
    private String attributes;
    private boolean disableOnClick;
    private boolean dropdown;
    private boolean enabled = true;
    private boolean submit;
    private boolean textAsHTML;

    private Button(ButtonBuilder builder)
    {
        this.cssClass = builder.cssClass;
        this.dropdown = builder.dropdown;
        this.text = builder.text;
        this.textAsHTML = builder.textAsHTML;
        this.href = builder.href;
        this.onClick = builder.onClick;
        this.iconCls = builder.iconCls;
        this.id = builder.id;
        this.attributes = builder.attributes;
        this.disableOnClick = builder.disableOnClick;
        this.enabled = builder.enabled;
        this.submit = builder.submit;
    }

    public String getCssClass()
    {
        return this.cssClass;
    }

    public String getText()
    {
        return text;
    }

    public boolean isDropdown()
    {
        return dropdown;
    }

    public boolean isTextAsHTML()
    {
        return textAsHTML;
    }

    public String getHref()
    {
        return href;
    }

    public String getOnClick()
    {
        return onClick;
    }

    public String getIconCls()
    {
        return iconCls;
    }

    public String getId()
    {
        return id;
    }

    public String getAttributes()
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
        String onClickMethod = getOnClick();

        // we're modifying the javascript, so need to use whatever quoting the caller used
        char quote = PageFlowUtil.getUsedQuoteSymbol(onClickMethod);

        // quoted CSS classes used in scripting
        String qCls = quote + CLS + quote;
        String qDisabledCls = quote + DISABLEDCLS + quote;

        String checkDisabled = "if (this.className.indexOf(" + qDisabledCls + ") != -1){ return false; }";
        if (onClickMethod != null)
            onClickMethod = checkDisabled + onClickMethod;
        else
            onClickMethod = checkDisabled;

        if (isDisableOnClick())
        {
            String replaceClass = ";LABKEY.Utils.replaceClass(this, " + qCls + ", " + qDisabledCls + ");";
            onClickMethod += replaceClass;
        }

        if (isSubmit())
        {
            String submitCode = "submitForm(document.getElementById(" + quote + id + quote + ").form); return false;";

            // look at the original onclick value to determine generation
            if (getOnClick() == null || "".equals(getOnClick()))
                onClickMethod += submitCode;
            else
            {
                // we allow the onclick method to cancel the submit -- doesn't support isDisableOnClick()
                // Question: This isn't activated when the user hits 'enter' so why support this?
                onClickMethod = checkDisabled + "this.form = document.getElementById(" + quote + id + quote + ").form; if (isTrueOrUndefined(function(){" + getOnClick() + "}.call(this))) {" + submitCode + "}";
            }
        }

        return onClickMethod;
    }

    @Override
    public String toString()
    {
        boolean newUI = PageFlowUtil.useExperimentalCoreUI();
        boolean iconOnly = newUI && getIconCls() != null;
        StringBuilder sb = new StringBuilder();
        String submitId = GUID.makeGUID();
        final String text = getText() != null ? (isTextAsHTML() ? getText() : PageFlowUtil.filter(getText())) : null;

        if (isSubmit())
        {
            sb.append("<input type=\"submit\" tab-index=\"-1\" style=\"position: absolute; left: -9999px; width: 1px; height: 1px;\" ");
            sb.append("id=\"").append(submitId).append("\"/>");
        }

        // enabled
        // OLD UI: MENU_CLS is used in place of CLS
        // NEW UI: CLS is always applied, MENU_CLS is not used
        sb.append("<a class=\"");
        if (isEnabled())
            sb.append(isDropdown() ? (newUI ? CLS : MENU_CLS) : CLS);
        else
        {
            if (newUI)
                sb.append(CLS);
            sb.append(" ").append(DISABLEDCLS);
        }
        if (newUI && isDropdown())
            sb.append(" labkey-down-arrow");
        if (getCssClass() != null)
            sb.append(" ").append(getCssClass());
        if (iconOnly)
            sb.append(" icon-only");
        sb.append("\" ");
        //-- enabled

        // id
        if (getId() != null)
            sb.append("id=\"").append(PageFlowUtil.filter(getId())).append("\" ");
        // -- id

        // href
        if (getHref() != null)
            sb.append("href=\"").append(PageFlowUtil.filter(getHref())).append("\" ");
        //-- href

        // onclick
        sb.append("onClick=\"").append(PageFlowUtil.filter(generateOnClick(submitId))).append("\" ");
        //-- onclick

        // attributes -- expected to be pre-filtered
        sb.append(getAttributes() == null ? "" : getAttributes());
        //-- attributes

        if (iconOnly && text != null)
        {
            sb.append("data-toggle=\"tooltip\" data-placement=\"top\" title=\"").append(text).append("\" ");
        }

        sb.append(">");

        if (iconOnly)
        {
            sb.append("<i class=\"fa fa-").append(getIconCls()).append("\"></i>");
            return sb.append("</a>").toString(); // for now, just show icon w/o text
        }

        sb.append("<span>");
        if (text != null)
            sb.append(text);
        sb.append("</span></a>");

        return sb.toString();
    }

    public static class ButtonBuilder
    {
        private String cssClass;
        private String iconCls;
        private String id;
        private String text;
        private String href;
        private String onClick;
        private String attributes;
        private boolean disableOnClick;
        private boolean dropdown;
        private boolean enabled = true;
        private boolean submit;
        private boolean textAsHTML;

        public ButtonBuilder(@NotNull String text)
        {
            this.text = text;
        }

        public ButtonBuilder addClass(@NotNull String cssClass)
        {
            if (this.cssClass == null)
                this.cssClass = "";
            this.cssClass += " " + cssClass;
            return this;
        }

        public ButtonBuilder dropdown(boolean dropdown)
        {
            this.dropdown = dropdown;
            return this;
        }

        public ButtonBuilder href(@NotNull String href)
        {
            this.href = href;
            return this;
        }

        public ButtonBuilder href(@NotNull URLHelper href)
        {
            this.href = href.toString();
            return this;
        }

        public ButtonBuilder href(@NotNull ReturnURLString returnHref)
        {
            this.href = returnHref.toString();
            return this;
        }

        public ButtonBuilder href(@NotNull Class<? extends Controller> actionClass, Container container)
        {
            this.href = new ActionURL(actionClass, container).toString();
            return this;
        }

        public ButtonBuilder onClick(String onClick)
        {
            this.onClick = onClick;
            return this;
        }

        public ButtonBuilder iconCls(String iconCls)
        {
            this.iconCls = iconCls;
            return this;
        }

        public ButtonBuilder id(String id)
        {
            this.id = id;
            return this;
        }

        @Deprecated // use Map<String, String> version instead
        public ButtonBuilder attributes(String attributes)
        {
            this.attributes = attributes;
            return this;
        }

        public ButtonBuilder attributes(Map<String, String> attributes)
        {
            if (attributes != null && attributes.size() > 0)
            {
                String sAttributes = "";
                for (String attribute : attributes.keySet())
                    sAttributes += PageFlowUtil.filter(attribute) + "=\"" + PageFlowUtil.filter(attributes.get(attribute)) + "\"";
                this.attributes = sAttributes;
            }
            else
                this.attributes = null;

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

        public ButtonBuilder submit(boolean submit)
        {
            this.submit = submit;
            return this;
        }

        public ButtonBuilder textAsHTML(boolean textAsHTML)
        {
            this.textAsHTML = textAsHTML;
            return this;
        }

        public Button build()
        {
            return new Button(this);
        }

        @Override
        public String toString()
        {
            return build().toString();
        }
    }
}
