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

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.util.PageFlowUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Select extends Input
{
    private final boolean _multiple;
    private final List<Option> _options;

    private Select(SelectBuilder builder)
    {
        super(builder);
        _multiple = builder._multiple;
        _options = builder._options;
    }

    public boolean isMultiple()
    {
        return _multiple;
    }

    public List<Option> getOptions()
    {
        return _options;
    }

    @Override
    protected void doInput(StringBuilder sb)
    {
        sb.append("<select")
                .append(" name=\"").append(getName()).append("\"");

        if (StringUtils.isNotEmpty(getId()))
            sb.append(" id=\"").append(getId()).append("\"");
        if (StringUtils.isNotEmpty(getClassName()))
            sb.append(" class=\"").append(PageFlowUtil.filter(getClassName())).append("\"");
        if (StringUtils.isNotEmpty(getPlaceholder()))
            sb.append(" placeholder=\"").append(PageFlowUtil.filter(getPlaceholder())).append("\"");
        if (getSize() != null)
            sb.append(" size=\"").append(getSize()).append("\"");
        if (isMultiple())
            sb.append(" multiple");

        doInputEvents(sb);

        if (isDisabled())
            sb.append(" disabled");

        sb.append(">");

        doOptions(sb);

        sb.append("</select>");
    }

    private void doOptions(StringBuilder sb)
    {
        if (getOptions() != null)
        {
            for (Option o : getOptions())
            {
                if (o != null)
                    sb.append(o.toString());
            }
        }
    }

    public static class SelectBuilder extends InputBuilder<SelectBuilder>
    {
        private boolean _multiple;
        private List<Option> _options;

        public SelectBuilder()
        {
        }

        public SelectBuilder addOption(Option option)
        {
            return addOptions(Collections.singletonList(option));
        }

        public SelectBuilder addOptions(List<Option> options)
        {
            if (options != null)
            {
                if (_options == null)
                    _options = new ArrayList<>();

                for (Option o : options)
                {
                    if (o != null)
                        _options.add(o);
                }
            }

            return this;
        }

        public SelectBuilder multiple(boolean multiple)
        {
            _multiple = multiple;
            return this;
        }

        public Select build()
        {
            return new Select(this);
        }
    }
}
