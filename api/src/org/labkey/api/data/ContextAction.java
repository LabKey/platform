/*
 * Copyright (c) 2017 LabKey Corporation
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
package org.labkey.api.data;

import org.labkey.api.util.PageFlowUtil;

public class ContextAction
{
    private String iconCls;
    private String onClick;
    private String onClose;
    private String text;
    private String tooltip;
    private boolean closable;

    private ContextAction(Builder builder)
    {
        iconCls = builder.iconCls;
        closable = builder.closable;
        onClick = builder.onClick;
        onClose = builder.onClose;
        text = builder.text;
        tooltip = builder.tooltip;
    }

    public String getIconCls()
    {
        return iconCls;
    }

    public String getOnClick()
    {
        return onClick;
    }

    public String getOnClose()
    {
        return onClose;
    }

    public String getText()
    {
        return text;
    }

    public String getTooltip()
    {
        return tooltip;
    }

    public boolean isClosable()
    {
        return closable;
    }

    @Override
    public String toString()
    {
        String cssClass = "lk-region-context-action";

        if (getOnClick() != null)
            cssClass += " selectable";

        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"").append(cssClass).append("\"");

        if (getTooltip() != null)
            sb.append(" data-tt=\"tooltip\" data-placement=\"top\" title=\"").append(PageFlowUtil.filter(getTooltip())).append("\"");

        if (getOnClick() != null)
            sb.append(" onclick=\"").append(getOnClick()).append("\"");

        sb.append(">"); // end of <div> start tag

        if (getOnClose() != null)
        {
            sb.append("<i class=\"fa fa-close\"");
            if (getOnClose() != null)
                sb.append(" onclick=\"").append(getOnClose()).append("\"");
            sb.append("></i>");
        }

        if (getIconCls() != null)
            sb.append("<i class=\"fa fa-").append(getIconCls()).append("\"></i>");

        if (getText() != null)
            sb.append("<span>").append(PageFlowUtil.filter(getText())).append("</span>");

        sb.append("</div>");

        return sb.toString();
    }

    public static class Builder
    {
        private String iconCls;
        private String onClick;
        private String onClose;
        private String text;
        private String tooltip;
        private boolean closable;

        public Builder()
        {
        }

        public Builder closable(boolean closable)
        {
            this.closable = closable;
            return this;
        }

        public Builder iconCls(String iconCls)
        {
            this.iconCls = iconCls;
            return this;
        }

        public Builder onClick(String onClick)
        {
            this.onClick = onClick;
            return this;
        }

        public Builder onClose(String onClose)
        {
            this.onClose = onClose;
            return this;
        }

        public Builder text(String text)
        {
            this.text = text;
            return this;
        }

        public Builder tooltip(String tooltip)
        {
            this.tooltip = tooltip;
            return this;
        }

        public ContextAction build()
        {
            return new ContextAction(this);
        }
    }
}
