/*
 * Copyright (c) 2017-2018 LabKey Corporation
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
package org.labkey.api.util.element;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.HasHtmlString;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.PageFlowUtil;

import javax.validation.constraints.NotNull;

public class Option implements HasHtmlString
{
    private final @NotNull HtmlString _label;
    private final @NotNull String _value;
    private final boolean _disabled;
    private final boolean _selected;

    private Option(OptionBuilder builder)
    {
        _label = builder._label;
        _value = builder._value;
        _disabled = builder._disabled;
        _selected = builder._selected;
    }

    public boolean isDisabled()
    {
        return _disabled;
    }

    public HtmlString getLabel()
    {
        return _label;
    }

    public boolean isSelected()
    {
        return _selected;
    }

    public @NotNull String getValue()
    {
        return _value;
    }

    @Override
    public String toString()
    {
        return getHtmlString().toString();
    }

    @Override
    public HtmlString getHtmlString()
    {
        // TODO: This method should do the rendering via DOM or HtmlStringBuilder
        StringBuilder sb = new StringBuilder();

        sb.append("<option")
            .append(" value=\"").append(PageFlowUtil.filter(getValue())).append("\"");

        if (isDisabled())
            sb.append(" disabled");

        if (isSelected())
            sb.append(" selected");

        sb.append(">");

        if (!HtmlString.EMPTY_STRING.equals(getLabel()))
            sb.append(getLabel());

        sb.append("</option>");

        return HtmlString.unsafe(sb.toString());
    }

    public static class OptionBuilder implements HasHtmlString
    {
        private @NotNull HtmlString _label;
        private @NotNull String _value;
        private boolean _disabled;
        private boolean _selected;

        public OptionBuilder()
        {
            _label = HtmlString.EMPTY_STRING;
        }

        public OptionBuilder(String label, @Nullable Object value)
        {
            label(label);
            value(value);
        }

        public OptionBuilder disabled(boolean disabled)
        {
            _disabled = disabled;
            return this;
        }

        public OptionBuilder label(String label)
        {
            _label = HtmlString.of(label);
            return this;
        }

        public OptionBuilder label(@NotNull HtmlString label)
        {
            assert null != label;
            _label = label;
            return this;
        }

        public OptionBuilder selected(boolean selected)
        {
            _selected = selected;
            return this;
        }

        public OptionBuilder value(@Nullable Object value)
        {
            _value = null == value ? "" : value.toString();
            return this;
        }

        public Option build()
        {
            return new Option(this);
        }

        @Override
        public String toString()
        {
            return build().toString();
        }

        @Override
        public HtmlString getHtmlString()
        {
            return build().getHtmlString();
        }
    }
}
