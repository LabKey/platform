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
package org.labkey.api.util.element;

import org.labkey.api.util.PageFlowUtil;

public class Option
{
    private boolean _disabled;
    private String _label;
    private boolean _selected;
    private String _value;

    private Option(OptionBuilder builder)
    {
        _disabled = builder._disabled;
        _label = builder._label;
        _selected = builder._selected;
        _value = builder._value;
    }

    public boolean isDisabled()
    {
        return _disabled;
    }

    public String getLabel()
    {
        return _label;
    }

    public boolean isSelected()
    {
        return _selected;
    }

    public String getValue()
    {
        return _value;
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();

        sb.append("<option")
                .append(" value=\"").append(getValue() == null ? "" : PageFlowUtil.filter(getValue())).append("\"");

        if (isDisabled())
            sb.append(" disabled");

        if (isSelected())
            sb.append(" selected");

        sb.append(">");

        if (getLabel() != null && !"".equals(getLabel()))
            sb.append(PageFlowUtil.filter(getLabel()));

        sb.append("</option>");

        return sb.toString();
    }

    public static class OptionBuilder
    {
        private boolean _disabled;
        private String _label;
        private boolean _selected;
        private String _value;

        public OptionBuilder()
        {
        }

        public OptionBuilder disabled(boolean disabled)
        {
            _disabled = disabled;
            return this;
        }

        public OptionBuilder label(String label)
        {
            _label = label;
            return this;
        }

        public OptionBuilder selected(boolean selected)
        {
            _selected = selected;
            return this;
        }

        public OptionBuilder value(String value)
        {
            _value = value;
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
    }
}
