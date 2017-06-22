/*
 * Copyright (c) 2012-2017 LabKey Corporation
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
public class AutoCompleteTextTag extends AutoCompleteTag
{
    private String _type;
    private int _size = 30;

    public String getType()
    {
        return _type;
    }

    public void setType(String type)
    {
        _type = type;
    }

    public int getSize()
    {
        return _size;
    }

    public void setSize(int size)
    {
        _size = size;
    }

    @Override
    protected String getTagConfig()
    {
        StringBuilder sb = new StringBuilder();

        sb.append("      tagConfig   : {\n" +
            "                tag     : 'input',\n" +
            "                type    : 'text',\n");
        addOptionalAttrs(sb);
        sb.append(
            "                name    : " + PageFlowUtil.jsString(getName()) + ",\n" +
            "                size    : " + getSize() + ",\n" +
            "                autocomplete : 'off'\n" +
            "            }\n");

        return sb.toString();
    }

    protected void addOptionalAttrs(StringBuilder sb)
    {
        super.addOptionalAttrs(sb);

        if (getValue() != null)
            sb.append("                value : ").append(PageFlowUtil.jsString(getValue())).append(",\n");
    }
}
