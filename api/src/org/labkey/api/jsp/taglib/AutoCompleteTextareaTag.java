/*
 * Copyright (c) 2012-2019 LabKey Corporation
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
package org.labkey.api.jsp.taglib;

import org.labkey.api.util.PageFlowUtil;

/**
 * User: klum
 * Date: 11/12/12
 */
public class AutoCompleteTextareaTag extends AutoCompleteTag
{
    int _rows = 8;
    int _cols = 60;
    int _tabindex;

    public int getRows()
    {
        return _rows;
    }

    public void setRows(int rows)
    {
        _rows = rows;
    }

    public int getCols()
    {
        return _cols;
    }

    public void setCols(int cols)
    {
        _cols = cols;
    }

    public int getTabindex()
    {
        return _tabindex;
    }

    public void setTabindex(int tabindex)
    {
        _tabindex = tabindex;
    }

    // TODO: HtmlString

    @Override
    protected String getTagConfig(String padding)
    {
        StringBuilder sb = new StringBuilder()
            .append(padding).append("tagConfig: {\n")
            .append(padding).append("    tag          : 'textarea',\n");

        addOptionalAttrs(sb, padding);

        sb
            .append(padding).append("    name         : ").append(PageFlowUtil.jsString(getName())).append(",\n")
            .append(padding).append("    rows         : ").append(getRows()).append(",\n")
            .append(padding).append("    cols         : ").append(getCols()).append(",\n")
            .append(padding).append("    autocomplete : 'off'\n")
            .append(padding).append("}\n");

        return sb.toString();
    }

    @Override
    protected void addOptionalAttrs(StringBuilder sb, String padding)
    {
        super.addOptionalAttrs(sb, padding);

        sb.append(padding).append("    tabIndex     : ").append(getTabindex()).append(",\n");

        if (getValue() != null)
            sb.append(padding).append("    html         : ").append(PageFlowUtil.jsString(getValue())).append(",\n");

        sb.append(padding).append("    class        : \"form-control\", \n");
    }
}
