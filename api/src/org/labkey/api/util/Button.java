/*
 * Copyright (c) 2014 LabKey Corporation
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

/**
 * Created by Nick Arnold on 2/27/14.
 * Testing of this class can be found in TestController.ButtonAction
 */
public class Button
{
    // Button constants
    private static final String CLS = "labkey-button";
    private static final String DISABLEDCLS = "labkey-disabled-button";

    // Composable members
    private String text; // required
    private String href;
    private String onClick;
    private String id;
    private String attributes;
    private boolean disableOnClick;
    private boolean enabled = true;
    private boolean submit;
    private boolean textAsHTML;

    private Button(ButtonBuilder builder)
    {
        this.text = builder.text;
        this.textAsHTML = builder.textAsHTML;
        this.href = builder.href;
        this.onClick = builder.onClick;
        this.id = builder.id;
        this.attributes = builder.attributes;
        this.disableOnClick = builder.disableOnClick;
        this.enabled = builder.enabled;
        this.submit = builder.submit;
    }

    public String getText()
    {
        return text;
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
        StringBuilder sb = new StringBuilder();
        String submitId = GUID.makeGUID();

        if (isSubmit())
        {
            sb.append("<input type=\"submit\" tab-index=\"-1\" style=\"position: absolute; left: -9999px; width: 1px; height: 1px;\" ");
            sb.append("id=\"").append(submitId).append("\"/>");
        }

        // enabled
        sb.append("<a class=\"").append(isEnabled() ? CLS : DISABLEDCLS).append("\" ");
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

        // attributes
        sb.append(getAttributes() == null ? "" : getAttributes());
        //-- attributes

        sb.append("><span>");
        if (getText() != null)
            sb.append(isTextAsHTML() ? getText() : PageFlowUtil.filter(getText()));
        sb.append("</span></a>");

        return sb.toString();
    }

    public static class ButtonBuilder
    {
        private String id;
        private String text;
        private String href;
        private String onClick;
        private String attributes;
        private boolean disableOnClick;
        private boolean enabled = true;
        private boolean submit;
        private boolean textAsHTML;

        public ButtonBuilder(@NotNull String text)
        {
            this.text = text;
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

        public ButtonBuilder href(@NotNull HString href)
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

        public ButtonBuilder id(String id)
        {
            this.id = id;
            return this;
        }

        public ButtonBuilder attributes(String attributes)
        {
            this.attributes = attributes;
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
