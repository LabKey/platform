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

public class TextArea extends Input
{
    private final int _columns;
    private final int _rows;

    private TextArea(TextAreaBuilder builder)
    {
        super(builder);
        _columns = builder._columns == null ? -1 : builder._columns;
        _rows = builder._rows == null ? -1 : builder._rows;
    }

    public int getColumns()
    {
        return _columns;
    }

    public int getRows()
    {
        return _rows;
    }

    @Override
    protected void doInput(StringBuilder sb)
    {
        sb.append("<textarea")
                .append(" name=\"").append(getName()).append("\"");

        if (getColumns() != -1)
            sb.append(" cols=\"").append(getColumns()).append("\"");
        if (getRows() != -1)
            sb.append(" rows=\"").append(getRows()).append("\"");

        if (StringUtils.isNotEmpty(getId()))
            sb.append(" id=\"").append(getId()).append("\"");
        if (StringUtils.isNotEmpty(getClassName()))
            sb.append(" class=\"").append(PageFlowUtil.filter(getClassName())).append("\"");
        if (StringUtils.isNotEmpty(getPlaceholder()))
            sb.append(" placeholder=\"").append(PageFlowUtil.filter(getPlaceholder())).append("\"");

        doInputEvents(sb);

        if (isDisabled())
            sb.append(" disabled");

        sb.append(">");

        doValue(sb);

        sb.append("</textarea>");
    }

    @Override
    protected void doValue(StringBuilder sb)
    {
        if (getValue() != null && !"".equals(getValue()))
        {
            sb.append(isUnsafeValue() ? getValue() : PageFlowUtil.filter(getValue()));
        }
    }

    public static class TextAreaBuilder extends InputBuilder<TextAreaBuilder>
    {
        private Integer _columns;
        private Integer _rows;

        public TextAreaBuilder columns(Integer columns)
        {
            _columns = columns;
            return this;
        }

        public TextAreaBuilder rows(Integer rows)
        {
            _rows = rows;
            return this;
        }

        public TextArea build()
        {
            return new TextArea(this);
        }
    }
}
